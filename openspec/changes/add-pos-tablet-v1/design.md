## Context

Greenfield build per `proposal.md` — no existing code, empty repo besides planning artifacts and `app_idea.md` (decision log). The product is a local-first native Android tablet POS for Malaysian grocery shops, multi-tenant SaaS backend, 1 tablet per shop in v1. All 9 capability specs in `specs/` define required behavior; this document fixes the technical approach.

Key constraints that shape everything:

- **Sell path is sacred**: scanning → cart → payment must never wait on disk locks, network, or license checks beyond a local cache read.
- **Solo developer + AI assistance**: prefer boring, well-documented mainstream choices over clever ones; minimize moving parts.
- **Tablet hardware**: Bluetooth keyboard-wedge scanner (appears as text input), ESC/POS printer (raw BT socket), drawer kick via printer.

## Goals / Non-Goals

**Goals:**

- An Android app (Kotlin, Jetpack Compose, Room) that runs the whole shop from one tablet, fully offline.
- A thin backend (one service + PostgreSQL) providing auth, tenant registry, license state, and sync — nothing else in v1.
- A sync design that is safe for one writer, honest about state, and doubles as backup/restore.
- Architecture that lets P1/P2 features (owner phone app, web dashboard, integrated payments) be added without rewrites: tenant-scoped data, operation journal, provider interfaces.

**Non-Goals:**

- No web dashboard, owner app, or multi-device sync in v1 (single active tablet per shop, enforced by device registration).
- No payment gateway integration (record-only payment types; `PaymentType` list is data, not code).
- No offline-conflict UI beyond conflict logging (last-write-wins).
- No iOS, no PWA, no desktop.

## Decisions

### D1. Repo layout: monorepo with two modules

```
EasyPOS/
├── android-app/        # Kotlin Gradle project (app + domain modules)
├── backend/            # Sync backend service + DB migrations
├── docs/               # CSV import format, printer compatibility notes
└── openspec/           # (existing) specs & changes
```

*Rationale:* one repo for solo+AI workflow — atomic changes across app and backend, one history. *Alternative:* two repos — rejected as friction for a solo developer with no org-scale needs.

### D2. Android stack

- **Kotlin + Jetpack Compose**, Material 3, landscape tablet-first layout (~10.1" 1280×800 the design baseline; must remain usable at 8").
- **Room** over SQLite with a single database. UI pattern: **MVVM with plain ViewModel + StateFlow** — no DI framework beyond Hilt, no multi-module CLEAN layering beyond `app` (UI) / `core` (data + domain) to start.
- **Kotlin Serialization** for JSON (sync payloads, backup file).
- **Min SDK 26** (Android 8) — covers essentially all cheap tablets sold in Malaysia; avoids desugaring edge cases.

*Rationale:* Compose is the maintained UI path and excels at the grid/cart split view; Room is the lowest-friction robust local DB. *Alternatives:* Flutter (rejected — user chose native for full BT/ESC-POS control), XML views (rejected — legacy path).

### D3. Money and integers

All money stored and computed as **`Long` cents (sen)**; formatting to RM at the UI edge via a single `MoneyFormatter`. No `Double` anywhere in money paths, including CSV import (parse decimal strings to cents), tax, change calculation, and valuation.

*Rationale:* floating-point money bugs are the classic POS failure. *Alternative:* BigDecimal — rejected as allocation-heavy on hot paths; Long cents with tests is sufficient for one-currency v1.

### D4. Data model (core entities)

```
TenantConfig(shop_id, name, address, phone, logo, currency='MYR', language)
User(id, shop_id, name, role: ADMIN|MANAGER|CASHIER, pin_salt, pin_hash, active)
Category(id, shop_id, name, sort_order)
Product(id, shop_id, category_id, name, barcode?, sku?, cost_cents, price_cents,
        tax_rate?, active, low_stock_threshold, emoji?, color?, photo?, location?, created/updated)
Sale(id, shop_id, local_no [per-shop sequential], user_id, at, total_cents,
     payment_type_id, tendered_cents?, change_cents?, voided?)
SaleLine(sale_id, product_id?, custom_name?, qty, unit_cents, line_cents, void_reason?)
HeldSale(id, shop_id, user_id, label?, lines JSON, held_at)  -- parked cart; local-only, not synced; resumes as a new Sale
StockDelta(id, product_id, delta, reason: SALE|ADJUST|IMPORT, note?, ref_id?, user_id, at)  -- note = human reason for a manual ADJUST ("Kiraan Fizikal", "Rosak", free text); null for SALE/IMPORT
CashEntry(id, shop_id, direction: IN|OUT, amount_cents, reason, user_id, at)  -- immutable; reason is free text — UI presets are suggestions, stored as text
DrawerCount(id, shop_id, at, expected_cents, counted_cents, variance_cents, user_id)  -- drawer reconciliation record
PaymentType(id, shop_id, name, kind: CASH|OTHER, active, sort)
Settings(key, value)  -- printer MAC/width, drawer kick on/off, footer text, idle lock mins, language
JournalOp(id UUID, shop_id, device_id, seq, actor_user, entity, op_type,
          payload JSON, created_at, synced_at?)  -- append-only
```

Every row carries `shop_id` and `updated_at` from day one (multi-tenant-ready, and the LWW conflict key). IDs are **UUIDs generated on-device** — no central counter dependency for offline inserts.

Day-summary analytics (average basket, top sellers, margin estimate) and the live drawer balance are **computed queries** over these tables — no extra schema. The trading day is the reconciliation unit in v1 (no shift entity); `DrawerCount` persists reconciliation outcomes for audit. `HeldSale` is deliberately excluded from sync: a parked cart is terminal state, not shop books.

*Rationale:* UUID ids + updated_at make the operation journal and future multi-device evolution mechanical. *Alternative:* autoincrement — rejected, breaks sync idempotency.

### D5. Sync protocol: operation journal, push-pull, LWW

- **Local writes**: every mutation runs in one Room transaction that (a) applies the change, (b) appends a `JournalOp` describing it. Committed together; journal never lags state.
- **Push**: batch pending ops (≤200/batch, ordered by seq) `POST /sync/push` with shop auth token; server applies each op idempotently (op ID as uniqueness key), returns per-op ack.
- **Pull**: `GET /sync/pull?since=<cursor>` returns ops from other sources (backend edits, restore pushes). Client applies with **last-write-wins on `updated_at`**, logging dropped writes to `ConflictLog`.
- **Cursor**: per-shop monotonic change sequence on the server; client persists its cursor.
- **Trigger**: WorkManager periodic + connectivity callback + app foreground. Never user-initiated on the sell path.
- **Device registration**: a shop account has ≤1 *active* tablet; registering a new device demotes the old (its pending ops are accepted during a grace window). Restore = pull from zero, then device becomes active.

*Rationale:* single-writer keeps this a journal replay problem instead of CRDT research; the `since` cursor makes restore and pull the same code path. *Alternatives:* full-state snapshots (wasteful at 1,900 products × daily sales), CRDTs (overkill for one writer).

### D6. Backend stack

- **Node.js + TypeScript (strict) + NestJS** + **Prisma** (schema → migrations → generated typed client) + **Zod** (runtime validation of journal-op payloads at the boundary) over **PostgreSQL**; JWT auth (`@nestjs/jwt` + route guards), `helmet`, `@nestjs/throttler`; endpoints: `POST /auth/register|verify|login`, `POST /device/register`, `POST /sync/push`, `GET /sync/pull`, `POST /license/activate`, `GET /license/status`.
- Prisma schema mirrors the domain entities plus `shop_account`, `device`, `license`, `change_log` (the server-side journal/cursor source). Idempotent op applies use `INSERT … ON CONFLICT` via `prisma.$executeRaw` inside `prisma.$transaction` where the client API falls short.
- Deployment: single `node:lts-slim` container on a cheap VPS (e.g., RM-tier Hetzner/DO) + managed Postgres; no k8s, no microservices. Tests: Vitest + Supertest + testcontainers-node against disposable Postgres.

*Rationale:* the sync API is JSON-first and this is the most-documented, most-AI-assisted boring-mainstream stack available; strict TS + Zod at the boundary preserves the type discipline Kotlin would have given. Language parity with the app is relinquished — it was aspirational (no shared module was planned) and the app speaks plain HTTPS+JSON; TS types become reusable if the P2 web dashboard happens. *Alternatives:* Kotlin + Ktor or Spring Boot (rejected — stack preference; JWT/ORM/validation support is equal, so fluency decides), serverless/BaaS (rejected — ops simplicity, cost predictability, and avoiding vendor lock-in for sync — the heart of the product).

### D7. Printing: raw ESC/POS over Bluetooth socket

- No vendor SDK. Connect to a paired printer via classic Bluetooth socket and write ESC/POS byte streams: `ESC @` init, `GS v 0` for logo bitmap, `GS V` for drawer kick (configurable pulse), codepage selection for BM text (CP437/858 covers English; BM uses ASCII range so safe).
- A small `EscPosBuilder` DSL renders the receipt model to bytes; 58/80mm differ by char width (32/42 cols) and line feeds.
- **PDF fallback**: Android's `PdfDocument` renders the same receipt model; share via `FileProvider`.
- Printer communication runs on its own dispatcher; failure never blocks sale completion (sale commits → print async with retry chip in UI, per spec).

*Rationale:* ESC/POS is a 40-year de-facto standard; raw sockets work with the RM 60–150 printers Malaysian shops actually buy. *Alternatives:* Android Print framework (adds pairing friction, poor 58mm support), vendor SDKs (per-vendor fork).

### D8. Scanner: search field IS the scanner input

The sell screen keeps a hidden-always-focused text field; BT wedge scanners type + Enter. Debounced handling: exact barcode match on Enter → add to cart; otherwise treat as search. No scanner SDK needed.

*Rationale:* keyboard-wedge is how every cheap 1D/2D BT scanner presents itself; zero-integration reliability.

### D9. Auth & security

- Local: PIN (4–6 digits) hashed with **Argon2id** (or PBKDF2 fallback) + per-user salt; auto-lock after configurable idle; denial log.
- Transport: TLS only; device token = JWT (30 days) + refresh; offline license state cached with signed expiry payload from server (signed with server key) so the tablet cannot be tricked by clock rollback beyond grace.
- PINs, tokens, and hashes are excluded from the manual backup file by design.

### D10. Localization & i18n

All UI strings through Android `strings.xml` with `values-ms/` (Bahasa Malaysia) from day one; BM reviewed by a native speaker before public release. Currency is a display concern (D3), not a locale guess.

### D11. Testing strategy

- **Unit**: money math, sale totals/tax/change, LWW conflict resolution, CSV parser (Aronium fixture file), journal op mapping.
- **Integration**: Room DAO tests (in-memory), backend sync push/pull against a containerized Postgres (Vitest + Supertest + testcontainers-node) — replay, idempotency, restore.
- **UI**: Compose tests for the sell flow happy path (scan → cart → cash → receipt), role gating.
- **Manual checklist**: physical printer matrix (58/80mm, 2 brands), scanner wedge behavior, airplane-mode soak test.

## Risks / Trade-offs

- [Printer zoo — cheap ESC/POS clones diverge on codepages/commands] → EscPosBuilder keeps commands minimal and central; maintain a tested-printer matrix in `docs/`; 80mm + logo is the guaranteed path, 58mm degrades to no-logo.
- [Clock rollback to extend trial offline] → signed license payload with server timestamp + bounded grace (spec'd in `account-license`).
- [Bluetooth stack flakiness across Android skins (MIUI etc.)] → printer runs strictly off the sale-critical path; "print pending" retry model; docs recommend known-good tablets.
- [Solo-dev backend security] → minimal endpoints, JWT + rate limiting, no card data ever (record-only payments), dependency scanning in CI, reviewed migrations.
- [Prisma migrations less battle-tested than Flyway] → keep migrations small, review generated SQL before applying to production, `pg_dump` backup before each deploy.
- [Aronium CSV surprises (encodings, group names, price formats)] → tolerant parser + row-level error report per spec; fixture-driven tests with a real export file.
- [LWW can silently drop a same-minute edit] → acceptable for v1 single device; every conflict row is logged and surfaced in settings for review.
- [Room single-DB growth on cheap tablets] → sales rows are tiny; index on (shop, at); yearly archive is a P2 concern, not v1.

## Migration Plan

1. Land backend + Flyway migrations first (`backend/`), deploy to staging VPS.
2. Build app skeleton → domain → sync engine behind feature flag → terminal.
3. Cutover for the pilot shop: export Aronium products → CSV import → one parallel-run week (Aronium and EasyPOS side by side) → EasyPOS primary.
4. Rollback: the manual backup file (per `shop-settings`) restores the full catalog; Aronium remains installable during pilot.

## Open Questions

- Exact trial length (14 vs 30 days) and pricing — business decision, data-only change, no code impact.
- Whether the pilot shop's Aronium export includes cost prices (screenshots suggest yes) — verified during cutover task; importer tolerates missing columns.
