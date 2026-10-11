---
name: android-clean-architecture
description: Enforces strict Clean Architecture conventions when creating or refactoring Android features with Kotlin, Jetpack Compose, Navigation Compose, Ktor, and Koin DI. Use this skill when creating a new feature, refactoring an existing one, or deciding where a class belongs within a feature's layers.
---

# Android Clean Architecture Skill

You are a senior Android engineer. Every feature you build or refactor **must** follow the strict Clean Architecture conventions defined in this document. Do not invent new layer names, fold layers together, or skip repository interfaces.

---

## 1. Stack Assumptions & Principles

- **Tech Stack:** Kotlin, Jetpack Compose, Material 3, Navigation Compose (`@Serializable` routes), Coroutines (`StateFlow`, `Channel`), Ktor `HttpClient`, `kotlinx.serialization`.
- **Dependency Injection:** Koin. Follow the `android-di-koin` skill for module definitions, assembly, and injection. No Hilt, Dagger, or manual `ViewModelProvider.Factory` factories.
- **Dependency Direction:** `presentation ──▶ domain ◀── data ◀── framework`
- **Domain Independence:** `domain` depends on nothing feature-external except `core.networking.Result` / `NetworkError`. No Ktor, Android UI, or Compose imports in domain.

---

## 2. Directory & Package Structure

Target package pattern: `{{APP_PACKAGE}}.<feature>`

```
<feature>/
├── data/
│   ├── {{Feature}}DataSource.kt          # Interface
│   ├── {{Feature}}RepositoryImpl.kt      # Implements domain interface
│   ├── di/
│   │   └── {{Feature}}DataModule.kt      # Koin module
│   └── dto/
│       └── {{Entity}}Dto.kt              # @Serializable + mappers
├── domain/
│   ├── {{Feature}}Repository.kt          # Interface
│   ├── {{Feature}}UseCase.kt             # suspend operator fun invoke()
│   └── entity/
│       └── {{Entity}}.kt                 # Pure Kotlin data class
├── framework/
│   ├── Remote{{Feature}}DataSource.kt    # Ktor implementation
│   └── {{Framework}}{{Feature}}DataSource.kt  # Other framework-bound impls (Room beyond DAOs,
│                                            # Credential Manager, sensors, etc.)
└── presentation/
    └── {{screen}}/
        ├── {{Screen}}Root.kt             # Root composable, koinViewModel()
        ├── {{Screen}}Screen.kt           # @Composable UI (stateless)
        ├── {{Screen}}ViewModel.kt        # ViewModel, constructor-injected
        ├── compose/                      # (Optional) Sub-composables
        ├── di/
        │   └── {{Feature}}PresentationModule.kt  # Koin module
        └── model/                        # UiState, Action/Intent, Event/Effect
```

---

## 3. Data Layer

### DTO & Mappers (`data/dto/{{Entity}}Dto.kt`)
```kotlin
@Serializable
data class {{Entity}}Dto(
    val id: String,
    val name: String?
)

fun {{Entity}}Dto.toEntity(): {{Entity}} = {{Entity}}(
    id = id,
    name = name.orEmpty()
)

fun {{Entity}}.toDto(): {{Entity}}Dto = {{Entity}}Dto(
    id = id,
    name = name
)
```

### Framework Implementations (`framework/`)

`framework/` is the outermost ring: every class that talks to a framework or platform API (Ktor `HttpClient`, Credential Manager, sensors, Bluetooth, etc.) implements an interface declared in `data/` and lives here. Both remote and local/on-device sources follow this rule. Plain Room DAOs and `@Serializable` DTOs are the exception — they already are the data layer's own technology and stay in `data/`.

#### Remote Implementation (`Remote{{Feature}}DataSource.kt`)
```kotlin
class Remote{{Feature}}DataSource(
    private val httpClient: HttpClient,
    private val tokenRepository: TokenRepository
) : {{Feature}}DataSource {

    override suspend fun get{{Entity}}(): Result<{{Entity}}, NetworkError> {
        return safeApiCall(serializer = RestResponse.serializer({{Entity}}Dto.serializer())) {
            httpClient.get("path/to/resource")
        }.map { it.data.toEntity() }
    }
}
```

### Koin Module (`data/di/{{Feature}}DataModule.kt`)

```kotlin
val {{feature}}DataModule = module {
    singleOf(::Remote{{Feature}}DataSource) { bind<{{Feature}}DataSource>() }
    singleOf(::{{Feature}}RepositoryImpl) { bind<{{Feature}}Repository>() }
}
```

---

## 4. Domain Layer

### Entity & Repository Interface (`domain/`)

```kotlin
// domain/entity/{{Entity}}.kt
data class {{Entity}}(val id: String, val name: String)

// domain/{{Feature}}Repository.kt
interface {{Feature}}Repository {
    suspend fun get{{Entity}}(): Result<{{Entity}}, NetworkError>
}
```

### Use Case (`domain/{{Feature}}UseCase.kt`)

```kotlin
class Get{{Entity}}UseCase(private val repository: {{Feature}}Repository) {
    suspend operator fun invoke(): UseCaseResponse<{{Entity}}, ErrorResponse> =
        when (val result = repository.get{{Entity}}()) {
            is Result.Success -> UseCaseResponse.Success(result.data)
            is Result.Error   -> UseCaseResponse.Error(ErrorResponse(null, result.error.name))
        }
}
```

Domain has **no Koin module** — use cases are provided by the feature's data module or resolved via constructor injection where they are wired:

```kotlin
// in {{feature}}DataModule (or a dedicated {{feature}}DomainModule if the feature is domain-heavy)
factoryOf(::Get{{Entity}}UseCase)
```

---

## 5. Presentation Layer

Choose **MVVM** for standard screens or **MVI** for complex forms and unidirectional state machines. Follow the `android-presentation-mvi` skill for the canonical State/Action/Event split.

### Style A: MVVM Standard

```kotlin
// presentation/{{screen}}/model/{{Screen}}Models.kt
data class {{Screen}}UiState(
    val isLoading: Boolean = false,
    val viewData: {{Screen}}ViewData? = null,
    val errorMsg: String? = null
)

sealed class {{Screen}}Action {
    data object OnRefresh : {{Screen}}Action()
}

sealed class {{Screen}}Event {
    data class ShowError(val message: String) : {{Screen}}Event()
}

// presentation/{{screen}}/{{Screen}}ViewModel.kt — constructor injection only
class {{Screen}}ViewModel(
    private val get{{Entity}}UseCase: Get{{Entity}}UseCase
) : ViewModel() {

    var state by mutableStateOf({{Screen}}UiState())
        private set

    private val eventsChannel = Channel<{{Screen}}Event>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()

    fun onAction(action: {{Screen}}Action) {
        when (action) {
            is {{Screen}}Action.OnRefresh -> loadData()
        }
    }

    private fun loadData() = viewModelScope.launch {
        state = state.copy(isLoading = true)
        when (val res = get{{Entity}}UseCase()) {
            is UseCaseResponse.Success -> state = state.copy(isLoading = false, viewData = res.data.toViewData())
            is UseCaseResponse.Error   -> state = state.copy(isLoading = false, errorMsg = res.error.message)
        }
    }
}
```

### Koin Module (`presentation/di/{{Feature}}PresentationModule.kt`)

```kotlin
val {{feature}}PresentationModule = module {
    viewModelOf(::{{Screen}}ViewModel)
}
```

### Root Composable (`presentation/{{screen}}/{{Screen}}Root.kt`)

```kotlin
@Composable
fun {{Screen}}Root(
    onNavigate: ...,
    viewModel: {{Screen}}ViewModel = koinViewModel()
) {
    val state by viewModel.state
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is {{Screen}}Event.ShowError -> { /* snackbar / toast */ }
        }
    }
    {{Screen}}Screen(
        state = state,
        onAction = viewModel::onAction
    )
}
```

### Style B: MVI Reducer

```kotlin
// presentation/{{screen}}/model/{{Screen}}MviModels.kt
@Immutable
data class {{Screen}}State(val email: String = "", val isLoading: Boolean = false)

sealed interface {{Screen}}Intent {
    data class EmailChanged(val value: String) : {{Screen}}Intent
    data object Submit : {{Screen}}Intent
}

sealed interface {{Screen}}Effect {
    data object NavigateNext : {{Screen}}Effect
}

class {{Screen}}Reducer {
    fun reduce(current: {{Screen}}State, intent: {{Screen}}Intent): {{Screen}}State = when (intent) {
        is {{Screen}}Intent.EmailChanged -> current.copy(email = intent.value)
        is {{Screen}}Intent.Submit       -> current.copy(isLoading = true)
    }
}
```

---

## 6. Execution Checklist for New Features

When asked to generate or refactor a feature, strictly perform these steps in order:

1. [ ] Create `<feature>/{data,domain,framework,presentation}` subpackages.
2. [ ] Define Domain Entity in `domain/entity/`.
3. [ ] Define DTO and mapping functions in `data/dto/`.
4. [ ] Define `DataSource` interface in `data/` and implement it in `framework/` — remote (Ktor) and local/on-device (Credential Manager, sensors, …) sources alike.
5. [ ] Create `Repository` interface in `domain/` and implement `RepositoryImpl` in `data/`.
6. [ ] Create UseCases in `domain/` returning `UseCaseResponse`.
7. [ ] Create `{{feature}}DataModule` in `data/di/` and register it in `:app`'s `startKoin`.
8. [ ] Model UI state (`UiState`, `Action`/`Intent`, `Event`/`Effect`) in `presentation/{{screen}}/model/`.
9. [ ] Implement `ViewModel` with constructor injection — **no companion factory**.
10. [ ] Create `{{feature}}PresentationModule` in `presentation/di/` and register it in `:app`'s `startKoin`.
11. [ ] Create Root composable injecting the ViewModel via `koinViewModel()`, plus a stateless `Screen` composable.
12. [ ] Register `@Serializable` route in `AppNavGraph.kt`.
