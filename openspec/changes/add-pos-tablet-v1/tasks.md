## 1. Monorepo scaffolding

- [x] 1.1 Create `android-app/` Gradle project (Kotlin, Compose, Material 3, minSdk 26) with empty tablet-landscape activity and verify it builds
- [x] 1.2 Create `backend/` NestJS project (TypeScript strict, ESLint + Prettier, tsx dev script, helmet + throttler wired) with health endpoint and Prisma connected to PostgreSQL
- [x] 1.3 Set up CI for both modules (build + unit tests on push)
- [x] 1.4 Create `docs/` with the CSV import format spec (columns, types, example rows)

## 2. Backend foundation

- [x] 2.1 Prisma schema + initial migration: core tables (shop_account, device, license, tenant config, users, categories, products, sales, sale_lines, stock_deltas, cash_entries, drawer_counts, payment_types, change_log) per design D4/D6
- [x] 2.2 Auth endpoints: `POST /auth/register` (phone/email + OTP verification), `POST /auth/login`, JWT issue/refresh
- [x] 2.3 Device registration: `POST /device/register` with one-active-tablet rule (demote old device, grace window for pending ops)
- [x] 2.4 License endpoints: `POST /license/activate` (validate + bind key), `GET /license/status` (returns signed expiry payload per design D9)
- [x] 2.5 Admin license-key issuance script/endpoint (out-of-app payment workflow)

## 3. Backend sync engine

- [x] 3.1 `POST /sync/push`: per-op idempotent apply (op ID uniqueness), batch ack, shop-scoped auth
- [x] 3.2 `GET /sync/pull?since=`: monotonic per-shop cursor over change_log
- [x] 3.3 Op appliers for every entity type (product upsert, sale insert, cash entry insert, settings update) with tenant scoping
- [ ] 3.4 Sync integration tests (testcontainers): replay idempotency, pull cursor correctness, cross-entity ordering
- [x] 3.5 Zod schemas for every JournalOp payload variant (sale, product upsert, cash entry, drawer count, settings) with 400 field-level rejections on malformed ops
- [ ] 3.6 Deploy backend to staging VPS with TLS + backups; smoke test from device

## 4. Android core data layer

- [x] 4.1 Room database + entities and DAOs per design D4 (all money as Long cents)
- [x] 4.2 `MoneyFormatter` (RM, 2dp) + money unit tests (parse, add, tax, change, valuation)
- [x] 4.3 JournalOp table + transactional write helper (apply change + append op atomically)
- [x] 4.4 Sync cursor + pending-count queries; ConflictLog table
- [x] 4.5 i18n scaffolding: `values/` + `values-ms/` string files, in-app language switch (per `shop-settings` spec)

## 5. User access control (app)

- [x] 5.1 PIN hashing (Argon2id/PBKDF2), user CRUD screens for Admin
- [x] 5.2 Lock screen + login/quick-switch flow preserving app state and open carts (per `user-access-control` spec)
- [x] 5.3 Role permission matrix (ADMIN/MANAGER/CASHIER) enforced at ViewModel/action level with denial logging
- [x] 5.4 Auto-lock on configurable idle timeout
- [x] 5.5 Acting-user attribution stamped on every mutating action + denial/activity log viewer for Admin

## 6. Product catalog (app)

- [x] 6.1 Category CRUD with reorder + sell-screen tab data source
- [x] 6.2 Product CRUD screens (Manager/Admin gated) with barcode uniqueness validation, shelf location field, optional initial stock (journaled as ADJUST delta), and live margin preview
- [x] 6.3 Product tile component: photo or emoji/color identity, name, price (per `product-catalog` spec)
- [x] 6.4 CSV import: tolerant parser, category auto-create, row-level result report (created/updated/failed + reason), failed-row retry
- [x] 6.5 CSV export (full catalog incl. inactive)
- [x] 6.6 Catalog search (partial name/barcode/SKU prefix) with instant barcode-exact resolution
- [x] 6.7 Quick price edit sheet: long-press a sell-screen tile or cart line → inline sale/cost edit applied immediately, journaled and attributed; Cashier triggers require a Manager/Admin PIN (per `product-catalog` spec)

## 7. Sales terminal (app)

- [x] 7.1 Sell screen scaffold: landscape split layout — category tabs + product grid / persistent cart panel (Hayabusha-style light theme per `app_idea.md` §6)
- [x] 7.2 Scanner-wedge capture field (always-focused, Enter-terminated) + add/increment logic + unknown-barcode affordance (create product / quick-sell)
- [x] 7.3 Cart panel: qty ±, remove, clear-with-confirm, live subtotal/total
- [x] 7.4 Quick-sell custom item (name + price, no product record)
- [x] 7.5 Payment flow: payment-type selector (shop-configured list), cash tender + change display, under-tender block, non-cash confirm
- [x] 7.6 Sale completion: atomic commit (sale + sale_lines + stock deltas + journal ops + sequential local sale number) fully offline
- [x] 7.7 Line void with reason + audit recording
- [x] 7.8 Sales history list (per day) with detail view and reprint/share entry point
- [x] 7.9 Hold & resume carts: park with optional label, held list on sell screen, resume restores lines; no sale/stock/number effects while held (per `sales-terminal` spec)
- [x] 7.10 Stock badge on product tiles: on-hand count + low-stock indicator ("Baki 4 · Rendah"); zero/negative-stock tiles remain sellable
- [x] 7.11 Payment polish: quick-tender chips (exact + round-ups with change preview) and static merchant QR display for DuitNow tenders (offline-safe, fed from `shop-settings`)

## 8. Stock management (app)

- [x] 8.1 Stock overview: filters (all/low/zero/negative) with count badges, per-category grouping, per-product threshold config
- [x] 8.2 Valuation totals (cost + sale price) for current filter
- [x] 8.3 Product stock history view (deltas with reason, acting user, timestamp)
- [x] 8.4 Manual stock adjustment flow (Admin/Manager): set-count or receive/remove delta, mandatory reason (physical count / damaged / new stock), attribution; feeds stock history and journal

## 9. Cash management (app)

- [x] 9.1 Cash in/out entry flow: amount, mandatory reason, attribution, immutable save (per `cash-management` spec)
- [x] 9.2 Cash log: newest-first full history, role-based visibility
- [x] 9.3 End-of-day summary screen (sales count, by payment type, cash in/out, net, average basket, top sellers) + PDF share + 80mm thermal print
- [x] 9.4 Live expected drawer balance on cash screen (cash sales + in − out) + reason presets on the entry form
- [x] 9.5 Drawer reconciliation in day summary: expected vs counted with variance, persisted as DrawerCount with attribution

## 10. Receipts & printing (app)

- [x] 10.1 Receipt model + renderer (58mm/80mm variants, logo bitmap, header/footer, REPRINT mark)
- [x] 10.2 `EscPosBuilder` ESC/POS byte DSL + classic Bluetooth socket print dispatcher (async, off sale path, retry chip on failure)
- [x] 10.3 Drawer kick (`GS V`) on cash sales, configurable on/off
- [x] 10.4 PDF receipt generation (`PdfDocument`) + share via FileProvider
- [x] 10.5 Printer setup flow: BT discovery, width select, test print (feeds `shop-settings` spec)
- [ ] 10.6 Physical printer test matrix (58mm + 80mm, ≥2 brands) recorded in `docs/`

## 11. Shop settings & onboarding (app)

- [x] 11.1 Settings screens: shop profile (name/contact/logo), receipt branding with preview, idle-lock + drawer-kick toggles, payment-type manager
- [x] 11.2 First-run onboarding wizard: admin account → shop profile → printer (optional) → import/add products → land on sell screen; resumable, no data loss on close (per `shop-settings` spec)
- [x] 11.3 Manual full backup export to single file (excludes PIN hashes/tokens) + restore-from-file
- [x] 11.4 Bahasa Malaysia translation pass on all strings
- [x] 11.5 DuitNow merchant QR image setting: upload/replace/remove with preview, stored locally for offline tender display

## 12. Sync engine (app)

- [x] 12.1 Sync client: auth token storage, push batches (ordered, ≤200), per-op ack, backoff retry (per `data-sync` spec)
- [x] 12.2 Pull + apply with last-write-wins on updated_at + ConflictLog write
- [x] 12.3 Triggers: WorkManager periodic + connectivity + foreground; never on sell path
- [x] 12.4 Sync status UI on main screen: synced / syncing / offline N pending / error
- [x] 12.5 Device replacement: sign-in on new device, full restore from cloud, old device demotion
- [x] 12.6 End-to-end sync test: airplane-mode soak (≥50 queued ops) → reconnect → zero loss/duplicates

## 13. Account & license (app)

- [ ] 13.1 Account creation flow (phone/email + OTP) skippable, non-blocking prompt afterward (per `account-license` spec)
- [ ] 13.2 Trial state machine: start on account creation, cached expiry with offline grace, warnings near expiry
- [ ] 13.3 License activation: key entry, backend bind, signed-expiry caching, clock-rollback resilience
- [ ] 13.4 Restricted mode: block new sales, allow history/export; upgrade CTA
- [ ] 13.5 Account & License settings screen (Admin-only status view)

## 14. Hardening & pilot cutover

- [ ] 14.1 Full spec-conformance pass: walk every scenario in `specs/*/spec.md` against the app, fix gaps
- [ ] 14.2 Performance pass: sell-path latency (scan→cart <100ms), 2k-product grid/search smoothness on a low-end tablet
- [ ] 14.3 Security review: TLS pinning decision, token storage, rate limits, dependency audit
- [ ] 14.4 Pilot shop cutover: Aronium CSV export → import, verify 1,894-product catalog, one-week parallel run checklist
- [ ] 14.5 Play Store listing + APK fallback build, release v1.0
