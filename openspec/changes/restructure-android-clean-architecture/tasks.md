## 1. Preconditions

- [x] 1.1 Sequencing decision recorded (2026-10-11, user override): change starts mid-v1 and interleaves with remaining `add-pos-tablet-v1` tasks; Android-side v1 tasks (13.x, 14.x) build on restructured code as migrations land, backend-only tasks (3.4, 3.6) are unaffected
- [x] 1.2 Create a restructure branch off main; all work lands there with per-step revertibility (design D9)

## 2. Core restructure (`:core`, Hilt still active)

- [x] 2.1 Create `core/result`: `Result<T, E>`, `DataError`, `EmptyResult`, map/onSuccess/onFailure helpers per android-error-handling skill
- [x] 2.2 Create `core/network`: Ktor `HttpClientFactory` (injectable engine, ContentNegotiation + kotlinx JSON, Auth bearer), safe-call helpers, DataStore-backed token storage replacing `TokenStore`; port `SyncConfig` timeout/TLS values into client config
- [x] 2.3 Port `BackendApi`/`SyncWire`/`HttpTransport` call sites in sync to the Ktor client; delete `HttpTransport`; wire contract (endpoints, payloads, push/pull semantics) unchanged
- [x] 2.4 Move Room code (entities, DAOs, `EasyPosDatabase`, journal, `LocalIdentity`, `SettingsKeys`) into `core/database` package; schema untouched
- [x] 2.5 Move sync engine (`SyncEngine`, `RemoteApplier`, `SyncWorker`, `SyncScheduler`, `SyncStatusRepository`, `AccountSyncRepository`) into `core/sync`
- [x] 2.6 Move `SessionManager` → `core/session`, `PinHasher` → `core/security`, `MoneyFormatter` → `core/money`, `Permissions`/shared enums → `core/model`; move `app/print` framework pieces (`PrintController`, `BluetoothPrinter`, `PdfReceiptWriter`, `PdfTextWriter`, `LogoRasterizer`, `DaySummaryShare`) into `core/print` alongside existing pure models
- [x] 2.7 Move the affected `:core` JUnit4 tests with their code; full `:core` test suite green (sync engine, offline soak, remote applier must pass unchanged)

## 3. Koin bootstrap (coexists with Hilt)

- [x] 3.1 Add Koin dependencies (core, android, compose) to `:app`/`:core`; define core Koin modules (`networkModule`, `databaseModule`, `syncModule`, `sessionModule`) with `singleOf`/`bind`
- [x] 3.2 Add `startKoin` to `EasyPosApp` assembling core modules alongside existing Hilt
- [x] 3.3 Create `app/shared`: `UiText`, `DataError.toUiText()` (EN + BM strings), `ObserveAsEvents`
- [x] 3.4 Move theme + shared composables (`PosKeypad`, common chips/sections) into `app/designsystem`
- [x] 3.5 Add the architecture guard test (design D10): no cross-feature imports outside `domain`, no Compose in `:core`, `feature/*/domain` has no Android framework imports

## 4. Feature migrations (dependency order; each keeps app building, removes that feature's Hilt usage, registers Koin modules)

- [x] 4.1 `feature/login`: merge session + lock screens into one feature; PIN-verify use case over `core/session` + `core/security`; Root/Screen split; State/Action/Event; `<feature>UiModule`/`<feature>DataModule`; lock semantics unchanged
- [x] 4.2 `feature/onboarding`: wizard as MVI reducer with `SavedStateHandle` survival; `TenantConfigRepository` interface + impl in domain/data (per D2 ownership)
- [x] 4.3 `feature/catalog`: `CatalogRepository` domain interface + data impl over `core/database` DAOs; CSV parser + DTOs + mappers in `data/`; products/categories/product-edit screens to Root/Screen + State/Action/Event
- [ ] 4.4 `feature/sell` domain + data: cart/checkout use cases, `SaleRepository`, `HeldSale`, `ReceiptRepository`, payment selection; Result-based signatures (D8); Koin data module
- [ ] 4.5 `feature/sell` ui: `SellScreen`/`PaymentSheet`/`QuickEdit` Root/Screen split; MVI reducer; `koinViewModel()`; cart survives process death; sell-path stays Room-read-only (no network/sync on scan→cart)
- [ ] 4.6 `feature/stock`: `StockRepository` domain + data; stock screen + adjustment flow to Root/Screen pattern
- [ ] 4.7 `feature/cash`: `CashRepository` (entries, drawer counts, balance) domain + data; cash screens to Root/Screen pattern
- [ ] 4.8 `feature/history`: read-side use cases over `sell.domain`; sales history screen to Root/Screen pattern; reprint uses `sell.domain` `ReceiptRepository`
- [ ] 4.9 `feature/reports`: day-summary use cases over `sell.domain` + `cash.domain`; screen + share/print flow to Root/Screen pattern
- [ ] 4.10 `feature/users`: `UserRepository` domain + data; users list + edit screens to Root/Screen pattern
- [ ] 4.11 `feature/settings`: `SettingsRepository`, `TenantConfigRepository`, `PaymentTypeRepository`, printer setup screens; domain + data + Root/Screen pattern
- [ ] 4.12 `feature/sync`: sync status screen + VM over `core/sync`; Root/Screen pattern
- [ ] 4.13 `feature/backup`: `BackupRepository` domain + data; backup/restore screens to Root/Screen pattern
- [ ] 4.14 `feature/activity`: activity-log screen + read-side use cases; Root/Screen pattern
- [ ] 4.15 Rewrite migrated ViewModel tests per android-testing skill (fake repositories, Turbine, State/Action/Event coverage) for sell, login, catalog, onboarding at minimum

## 5. Shell and navigation (Nav3 kept)

- [ ] 5.1 Define `@Serializable` route objects per screen inside each feature's `ui` package
- [ ] 5.2 Create `app/navigation`: Nav3 host assembling per-feature graphs; cross-feature destinations as lambda callbacks only (D7)
- [ ] 5.3 Rewire `AppShell`/`MainScreen`/`ShellViewModel` onto the new host; delete old `Navigation.kt`/`NavigationKeys.kt`; i18n `LanguageController` settles in `app/i18n`

## 6. Hilt removal

- [ ] 6.1 Remove Hilt plugins, dependencies, KSP processor, and `hilt-navigation-compose`; `startKoin` is the only DI; full build + all tests green

## 7. Verification

- [ ] 7.1 Full test suite green: `:core` JUnit4 suite + `:app` feature/ViewModel tests + architecture guard test
- [ ] 7.2 Spec-conformance walk: every scenario in the 9 archived `add-pos-tablet-v1` capability specs re-verified against the restructured app
- [ ] 7.3 Sell-path performance re-measured: scan → cart < 100 ms; 2k-product grid/search smooth on low-end tablet profile
- [ ] 7.4 Offline soak test re-run (airplane-mode session with sync pending, honest pending counts)
- [ ] 7.5 Wire the architecture guard test into CI (`.github/`); merge restructure branch to main
