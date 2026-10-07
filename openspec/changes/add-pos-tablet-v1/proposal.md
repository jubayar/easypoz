## Why

Small grocery shops in Malaysia run on either legacy Windows POS (e.g. Aronium — dense desktop UI, staff training needed) or pure-cloud tablet POS that stops working when the internet drops mid-transaction. There is no affordable, offline-reliable, touch-first POS purpose-built for the small grocery counter. EasyPOS v1 closes that gap: a paid, local-first Android tablet app that sells, tracks stock and cash, prints receipts, enforces staff roles, and syncs safely to a cloud account when online.

## What Changes

This is a **greenfield build** — no existing code. The change delivers the first shippable product (v1, P0 scope from `app_idea.md`):

- **Native Android tablet app** (Kotlin + Jetpack Compose, landscape-first) with local Room/SQLite database — every feature works fully offline
- **Sales terminal**: barcode scan-to-cart, category-tab product grid with on-tile stock badges, always-visible cart, hold & resume carts, cash tender + change with quick-tender chips, configurable payment types (Cash / DuitNow / TNG / Card — record-only, with optional shop QR display for DuitNow tenders), quick-sell custom item, line void with reason, sales history with reprint entry point
- **Product catalog**: CRUD with categories, emoji/color tiles for photo-less products, shelf locations, initial stock on create, live margin preview, CSV import/export (migration path off Aronium)
- **Stock management**: auto-decrement on sale, manual adjustments with audited reasons, low/zero/negative filters, valuation at cost and sale price with margin estimate
- **Cash management**: cash in/out with mandatory reason (presets or free text), live drawer balance, full who/when audit trail, end-of-day summary with drawer reconciliation and top sellers
- **User access control**: PIN login, quick user switching, Admin / Manager / Cashier roles, user attribution on every action
- **Receipts**: ESC/POS Bluetooth thermal printing (58/80mm) with shop branding, PDF save/share fallback, cash-drawer kick on sale
- **Shop settings & onboarding**: self-serve first-run wizard (shop name → products → first sale in ~10 minutes), Bahasa Malaysia/English toggle, printer discovery + test print
- **Account & license**: shop account creation, free trial, paid activation (subscription/license key) — paid from day one
- **Data sync**: local-first operation-log sync to a thin per-shop cloud backend (auth, tenant registry, sync API, PostgreSQL); honest sync-status UI; sync doubles as automatic backup

Not in this change (deferred to P1/P2): shifts & X/Z reports (the trading day is the v1 reconciliation unit), credit ("utang") sales, discounts & approvals, customer membership/loyalty, expiry tracking, on-tablet charts, owner phone app, web dashboard, emailed owner reports, integrated payments (gateway/webhook), scales, promotions, price lists, LHDN e-invoice.

## Capabilities

### New Capabilities

- `sales-terminal`: the checkout flow — scan/search/browse to cart, quantity edit, payment capture (record-only types), change calculation, quick-sell items, voids with reason, sale completion
- `product-catalog`: products and category management, product tiles, CSV import/export
- `stock-management`: stock-on-hand tracking driven by sales, stock list views with filters, stock valuation
- `cash-management`: cash in/out entries with reasons, audit trail, end-of-day cash summary
- `user-access-control`: PIN-based login/switching, role permissions (Admin/Manager/Cashier), user attribution on all mutating actions
- `receipts`: receipt rendering, ESC/POS Bluetooth printing, PDF generation & sharing, cash-drawer kick
- `shop-settings`: shop profile, currency (RM), BM/EN language, receipt branding, printer setup, first-run onboarding wizard
- `account-license`: shop cloud account, trial period, paid activation and license state
- `data-sync`: offline-first operation journal, background push/pull sync with the cloud backend, sync status visibility, restore on device replacement

### Modified Capabilities

(none — greenfield; `openspec/specs/` is empty)

## Impact

- **New systems**: an Android application (new `android-app/` or repo-root Gradle project) and a thin backend service (new `backend/` module — auth, tenant registry, sync API, PostgreSQL). Both are created by this change.
- **Hardware dependencies**: Bluetooth 1D/2D scanner (keyboard wedge), ESC/POS-compatible 58/80mm thermal printer, cash drawer triggered via printer kick code
- **External services**: cloud hosting for the sync backend; payment for licenses handled outside the app in v1 (bank transfer / toyyibPay), license keys issued manually
- **Data migration**: Aronium CSV export → EasyPOS product import mapping (documented format)
- **Localization**: all user-facing strings in English and Bahasa Malaysia from day one
