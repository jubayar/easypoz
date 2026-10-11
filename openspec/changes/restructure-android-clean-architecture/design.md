## Context

See `proposal.md` — Why. Current state being restructured: `android-app/` has two Gradle modules. `:app` holds all Compose UI in flat `ui/<feature>` packages plus `print/`, `theme/`, `i18n/`; `:core` holds all data and domain code in layer packages (`data/repo`, `data/dao`, `data/sync`, `data/entity`, `security`, `print`, `csv`, `money`, `model`, `session`) — wired with Hilt, Navigation 3, a custom `HttpTransport`, and plain MVVM + StateFlow. `add-pos-tablet-v1` is 66/79 tasks complete; per user decision (2026-10-11) this restructure starts now and interleaves with the remaining v1 tasks instead of waiting for completion.

Constraints that shape the design:

- **User-fixed decisions**: two Gradle modules only (`:app`, `:core`); feature-first packages inside `:app` with four subpackages per feature — `ui/`, `domain/`, `data/`, `framework/`; `:core` holds core setup and base/shared infrastructure; Koin replaces Hilt; Nav3 is kept.
- **Skills are the convention source**: the eight `.claude/skills/android-*` skills define the patterns; this design maps them onto the two-module layout and records where this app deviates and why.
- **Behavior must be preserved**: every `add-pos-tablet-v1` spec scenario and the sell-path performance invariant (scan → cart < 100 ms, never blocked on network/sync) must hold unchanged after the restructure.

## Goals / Non-Goals

**Goals:**

- Feature-first package structure with `ui/domain/data/framework` per feature, matching the clean-architecture skill's layer model (`ui` = the skill's `presentation`).
- Enforced-by-convention boundaries: one simple, checkable rule for what may import what.
- Koin DI with per-feature-layer modules assembled in `:app`; Ktor `HttpClient` with injectable engine; typed `Result<T, E>` at layer boundaries.
- The app stays green and shippable at every migration step.

**Non-Goals:**

- No new Gradle modules, no multi-module split (deliberate user choice; revisit only if the package-level boundaries prove insufficient).
- No behavioral changes, no backend changes, no sync-protocol changes, no UI redesign — screens move and rewire, they do not change what they do.
- No Hilt/Koin long-term coexistence — Hilt is removed at the end of migration, not kept as an option.
- No retrofit of the existing `:core` JUnit4 test suite to the android-testing skill's stack; that stack applies to new/rewritten ViewModel tests only.

## Decisions

### D1. Package structure (user-fixed)

```
android-app/
├── app/src/main/java/net/redngreen/easypos/
│   ├── EasyPosApp.kt                 # Application; startKoin assembles all Koin modules
│   ├── MainActivity.kt
│   ├── navigation/                   # app-level Nav3 host: NavDisplay wiring, feature graph assembly
│   ├── designsystem/                 # theme, shapes, typography, shared composables (PosKeypad, …)
│   ├── i18n/                         # LanguageController
│   ├── shared/                       # UiText, DataError.toUiText(), ObserveAsEvents (skills' core:presentation)
│   └── feature/
│       ├── login/                    # PIN entry + idle re-lock (merges today's session + lock screens)
│       ├── sell/  ├── catalog/  ├── stock/  ├── cash/  ├── history/
│       ├── reports/  ├── users/  ├── settings/  ├── sync/  ├── backup/
│       ├── onboarding/  └── activity/
│           └── each feature:  ui/  domain/  data/  framework/
└── core/src/main/java/net/redngreen/easypos/core/
    ├── result/      # Result<T,E>, DataError, helpers (skills' core:domain)
    ├── network/     # Ktor HttpClient factory, safe-call helpers, DataStore token storage
    ├── database/    # Room: all entities, DAOs, journal, migrations (skills' core:database)
    ├── sync/        # SyncEngine, RemoteApplier, SyncWorker, BackendApi, SyncWire, status repo
    ├── session/     # SessionManager (base infra used by shell + all features)
    ├── security/    # PinHasher
    ├── money/       # MoneyFormatter (Long-cents → RM, UI edge only)
    ├── model/       # Permissions, shared enums
    └── print/       # Receipt models, EscPosBuilder (pure) + BT/PDF framework pieces from today's app/print
```

`:app` depends on `:core`; `:core` stays free of Compose (UI only in `:app`). Feature unit tests live in `:app/src/test` mirroring feature packages; `:core` tests move with their code.

*Rationale:* matches the user's fixed layout and the skills' layer model with `ui`↔`presentation` as the only rename. *Alternative:* feature Gradle modules (rejected by user — the package rule below carries the boundary enforcement instead).

### D2. Ownership and the cross-feature import rule

Each feature owns its vertical: screens, ViewModels, State/Action/Event models, nav graph, Koin modules (`<feature>UiModule`, `<feature>DataModule`) in `ui/` and `data/`; repository interfaces, domain entities, use cases, feature error enums in `domain/`; data source implementations, DTOs, mappers in `data/`; platform-bound sources (Room-backed beyond plain DAO calls, Ktor remote sources, Bluetooth, PDF) in `framework/`.

**The one boundary rule: a feature may import another feature's `domain` package only — never its `ui`, `data`, or `framework`.** Cross-feature navigation uses callbacks (D7), never route imports.

Ownership of shared business data (write-side owns the aggregate):

| Feature | Owns (domain iface + data impl) |
|---|---|
| sell | SaleRepository, HeldSale, cart/checkout use cases, ReceiptRepository, payment selection |
| catalog | CatalogRepository (products + categories), CSV import parser + DTOs |
| stock | StockRepository (deltas + on-hand queries) |
| cash | CashRepository (entries, drawer counts, drawer balance) |
| users | UserRepository |
| settings | SettingsRepository, TenantConfigRepository, PaymentTypeRepository, printer setup |
| backup | BackupRepository |
| history / reports / activity | read-side use cases over `sell`/`cash`/`core` domains — no own repositories |

The sell path composes `catalog.domain` + `stock.domain` + its own sales aggregate via use cases — legitimate domain-only cross-imports. `SessionManager`, sync engine, and all Room/journal code are `:core` infrastructure (D5).

*Rationale:* the POS's core flow spans catalog/stock/sales/cash; either features import owners' domains or everything collapses back into `:core` (today's problem). Domain-only imports keep one direction of coupling and stay checkable (D10). *Alternative:* shared repositories in `:core` — rejected, it re-creates the grab-bag the restructure exists to remove.

### D3. DI: Hilt → Koin

- `startKoin { androidContext(...); modules(allCoreModules, allFeatureDataModules, allFeatureUiModules) }` in `EasyPosApp`; assembled only in `:app` (android-di-koin skill).
- Constructor-reference definitions (`singleOf`, `viewModelOf`, `factoryOf`) with `bind<Interface>()`; repositories singletons, ViewModels `viewModelOf`, use cases `factoryOf` provided by the feature's data module.
- ViewModels resolved via `koinViewModel()` in Root composables only, never passed down.
- `SyncWorker` gets its dependencies via Koin (`KoinWorkerFactory` or direct `GlobalContext.get()` in `doWork` — decided at implementation, both are supported patterns).
- Coexistence staging: Koin is added alongside Hilt; features migrate one at a time; the last migration removes Hilt, its KSP processor, and `hilt-navigation-compose` in the same commit (D9).

*Rationale:* user-fixed decision; supersedes `add-pos-tablet-v1` D2's Hilt choice. Koin's runtime resolution cost is irrelevant to the sell path (resolution happens at screen entry, not scan time).

### D4. Networking: custom HttpTransport → Ktor HttpClient

- `core/network` provides `HttpClientFactory.create(engine)` (ContentNegotiation + kotlinx.serialization JSON, Auth bearer with DataStore-backed token storage, timeouts matching today's `SyncConfig`), injectable engine so tests use Ktor's mock engine.
- Safe-call helpers (`safeCall`, `responseToResult`, typed `HttpClient.get/post` extensions) map responses to `Result<T, DataError.Network>` per the error-handling skill.
- `BackendApi`/`SyncWire` keep their exact wire contract — same endpoints, same payloads, same push/pull semantics; only the transport under them changes. Sync integration behavior is re-verified by the existing `:core` sync test suite.

*Rationale:* data-layer skill mandates Ktor; the injectable engine restores what the custom `HttpTransport` abstraction existed to provide (testability). *Alternative:* keep `HttpTransport` — rejected, diverges from the skills the change exists to adopt.

### D5. Room stays centralized in `core/database`

All entities, DAOs, the single `EasyPosDatabase`, journal, and migrations stay in `:core` — a documented deviation from the data-layer skill's "entities in `feature:data`". Features wrap their DAOs in `framework/` data sources and map entities to domain models in `data/` mappers; features never expose Room entities upward.

*Rationale:* the journal, sync replay (`RemoteApplier`), LWW conflict handling, and backup/restore span every entity; scattering entities across features would force cross-feature `data` imports (violates D2) or a separate shared module (violates the two-module choice). Centralizing the storage ring while keeping domain mapping in features preserves both constraints.

### D6. Presentation: State/Action/Event, Root/Screen split

- Every screen pair: `<Screen>Root` (injects ViewModel via `koinViewModel()`, observes events, wires navigation callbacks) + `<Screen>Screen` (stateless, state + `onAction` parameters).
- State/Action/Event models in `feature/<name>/ui/model/`; MVI reducer pattern for complex flows (sell cart + payment, onboarding wizard, CSV import); plain MVVM-style StateFlow for simple screens — per the clean-architecture skill's Style A/B choice.
- `SavedStateHandle` for process-death survival on cart and wizard state (android-presentation-mvi skill).
- `UiText` + `DataError.toUiText()` in `app/shared/`; feature-specific error enums map in the feature's `ui`.
- Existing screens keep their visuals and behavior; ViewModels are restructured to the State/Action/Event shape, not redesigned.

### D7. Navigation: Nav3 kept, skill patterns applied

Route objects (`@Serializable` `data object`/`data class` per screen) and one graph function per feature defined in `feature/<name>/ui`; `:app`'s `navigation/` assembles them and owns the start destination; intra-feature navigation via the feature's own back stack; cross-feature destinations are lambda callbacks only. Existing Nav3 APIs (`NavDisplay`, key-based back stack) are kept — the skill's Navigation-Compose snippets translate to Nav3 equivalents.

### D8. Error handling: typed `Result<T, E>` at boundaries

`core/result` holds the wrapper, `DataError`, and helpers. Data sources catch framework exceptions and convert (`SQLiteException` → `DataError.Local`, Ktor/HTTP → `DataError.Network`); domain use cases return `Result`/feature error enums; ViewModels never see raw exceptions for expected failures. Existing repository signatures change accordingly — this is the one widespread signature change beyond moves.

### D9. Migration order (app stays green at each step)

1. `:core` restructure: create `result/`, `network/` (Ktor factory + safe calls + token storage), move database/sync/session/security/money/model/print into their packages; port `HttpTransport` call sites in sync to Ktor; `:core` tests green. Hilt still active.
2. Koin bootstrap: add Koin + core modules to `EasyPosApp` alongside Hilt; add the architecture guard test (D10).
3. Feature migrations, dependency-ordered — each lands as its own commit with the app building and core tests green: `login` (session+lock merge) → `onboarding` → `catalog` → `sell` (largest; cart + payment + receipt) → `stock` → `cash` → `history` → `reports` → `users` → `settings` → `sync` → `backup` → `activity`; each removes that feature's Hilt usage and registers its Koin modules.
4. Shell/nav reorg: app-level `navigation/` with per-feature graphs, route objects, callback wiring; remove old `Navigation.kt`.
5. Hilt removal: strip Hilt plugins/deps, `startKoin` becomes the only DI; full build + test pass.
6. Verification: spec-conformance walk of all 9 `add-pos-tablet-v1` capabilities, sell-path performance measurement, offline soak re-run.

Rollback: the change lands on a branch; any step can be reverted independently since each keeps the app green. Pre-v1 ship, the main branch remains the shippable fallback.

### D10. Boundary enforcement without Gradle modules

A unit "architecture guard" test in `:app` asserts the D2 rule mechanically: no class under `feature/*/ui|data|framework` imports another feature's non-`domain` package; no `:core` import of Compose; `feature/*/domain` imports no Android framework classes (domain purity). Cheap reflection-free classpath scanning; grows into Konsist later only if wanted.

*Rationale:* the user chose packages over modules, so the compiler won't enforce boundaries — a test that fails CI is the next best thing and keeps the rule from eroding.

## Risks / Trade-offs

- [Large mechanical refactor of a working, nearly-shipped app] → behavior-preserving moves only; existing `:core` test suite is the safety net; staged per-feature commits; runs after v1 completes so no ship pressure.
- [Ktor transport behaves differently than HttpTransport (timeouts, TLS, retry)] → port `SyncConfig` values into client config; sync engine tests + offline soak re-run must pass unchanged.
- [Domain-only import rule erodes over time (no compiler enforcement)] → D10 guard test in CI; skills enforce at edit time; revisit feature Gradle modules if violations repeat.
- [Koin + Hilt coexistence window introduces dual-DI confusion] → time-boxed, one-directional (Hilt usage only shrinks); step 5 removes Hilt completely.
- [Sell-path perf regression from restructure] → scan→cart path stays Room-read-only with no new layers on the hot path; re-measured in step 6 against the <100 ms budget.
- [Merging session + lock into `login` changes lock behavior subtly] → screens move, PIN verify + idle-lock semantics untouched; conformance pass covers the lock scenarios.

## Migration Plan

Covered in D9 (staged, per-feature, app green at each step; branch-based with per-step revertibility). No backend or data migration involved; Room schema is untouched.

## Open Questions

- Ktor engine on Android (OkHttp vs CIO) — deferrable; the factory takes an injected engine either way.
- `SyncWorker` DI style (Koin worker factory vs `GlobalContext` lookup) — deferrable; both are supported Koin patterns.
- Whether history/reports eventually get their own read-model repositories — deferrable; current use-case-over-domain design does not block it.
