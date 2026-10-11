## Why

The v1 app (`add-pos-tablet-v1`) ships on a pragmatic two-module layered layout: `:app` holds all UI in flat `ui/<feature>` packages and `:core` holds all data, sync, and domain code in layer packages, wired with Hilt. That was the right call for shipping speed (design D2), but the project has since adopted eight Android skills (clean architecture, Koin DI, data layer, MVI, navigation, testing, error handling, Compose UI) whose conventions do not match the current structure — so they cannot be applied consistently, and features can quietly reach into each other because nothing enforces boundaries. After v1 completes, the Android app should be restructured to the clean-architecture conventions so future features (P1/P2 roadmap) are built on enforced boundaries instead of a grab-bag core.

## What Changes

- Restructure `android-app/` from layer-based packages to feature-first packages: every feature lives at `feature/<name>/` with four subpackages — `ui/`, `domain/`, `data/`, `framework/` — inside `:app`.
- `:core` shrinks to cross-cutting infrastructure and base classes only: `Result`/`DataError` types, Room database (entities, DAOs, journal), sync engine, Ktor `HttpClient` factory + safe-call helpers + token storage, money, security, ESC/POS receipt core, and shared models.
- **BREAKING (internal, no user-visible change)**: replace Hilt with Koin — `startKoin` in the `Application` class, constructor-injected ViewModels resolved via `koinViewModel()`, one Koin module per feature layer assembled in `:app`. Supersedes design decision D2 of `add-pos-tablet-v1`.
- Replace the custom `HttpTransport` with a Ktor `HttpClient` (engine injected, mockable in tests) per the data-layer skill; tokens move to DataStore-backed storage.
- Adopt typed `Result<T, E>` error handling at layer boundaries (no raw exceptions for expected failures).
- Presentation adopts the State/Action/Event model — MVI for complex screens (e.g., sell), MVVM style for simple ones; Root/Screen composable split with `koinViewModel()` at the Root only.
- Navigation stays on Nav3 but reorganized to the skill's patterns: `@Serializable` route objects, one nav graph per feature, cross-feature navigation via callbacks only.
- All behavior of the 9 `add-pos-tablet-v1` capability specs is preserved; a conformance pass re-verifies it after the restructure.
- Sequencing (user decision, 2026-10-11): the restructure starts now, interleaving with the remaining `add-pos-tablet-v1` tasks; Android-side v1 work (account/license 13.x, hardening 14.x) proceeds on restructured code as the relevant migrations land, backend-only tasks (3.4, 3.6) are unaffected.

## Capabilities

None — this change deliberately opts out of specs (`skip_specs: true`). The restructure is behavior-preserving: every requirement of the 9 `add-pos-tablet-v1` capabilities (sell flow, catalog, sync, cash, stock, reports, users, backup, settings) remains satisfied without modification. Architecture conventions are not spec-level behavior; they are codified in this change's `design.md` and enforced at edit time by the `.claude/skills/android-*` skills, which are the source of truth for those conventions.

## Impact

- **Code**: the entire `android-app/` source tree (~90 Kotlin files across `:app` and `:core`) is reorganized; `backend/` is untouched.
- **Dependencies**: remove Hilt + KSP Hilt compiler; add Koin (`core`, `android`, `compose`), Ktor client (`core`, `content-negotiation`, `auth`, logging); keep Room, kotlinx.serialization, WorkManager, Navigation 3.
- **Tests**: existing `:core` unit tests (sync engine, soak, money, CSV, journal) move with their code and must stay green; ViewModel tests updated for Koin resolution; sell-path performance re-verified (scan → cart < 100 ms).
- **Risks**: large mechanical refactor of a working app — mitigated by behavior-preserving moves, feature-by-feature migration order, the existing test suite as the safety net, and a final spec-conformance + performance pass.
