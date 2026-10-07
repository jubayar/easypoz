# EasyPOS — App Idea Record

> Status: Explored & decided · Recorded: 2026-10-05
> Formalized in OpenSpec change `add-pos-tablet-v1` (proposal · 9 specs · design · 60 tasks) — implementation not started
> Companion references: `ref image/` (Aronium screenshots = functional DNA; Hayabusha screenshot = UI/UX bar)

---

## 1. One-liner

A **local-first, native Android tablet POS app for small grocery shops in Malaysia** — sold as a paid product to many shops. Works fully offline, syncs to each shop's cloud account when online. Owner phone app and web dashboard arrive later, riding on the same synced data.

## 2. Background & motivation

- The reference system is **Aronium POS** (Windows desktop) — currently running a real shop with **1,894 products**, 19 category groups, 157 cash in/out audit entries, multiple staff users, sales history since Aug 2025.
- EasyPOS keeps Aronium's *functional soul* (products, stock, cash log, receipts, users, reports) but re-expresses it for a **tablet** with a **modern, touch-first UI** (inspired by the Hayabusha reference: light theme, card grid, category tabs, big touch targets).
- Positioning: cheaper and easier than legacy desktop POS, offline-reliable unlike pure-cloud POS, purpose-built for small grocery (not restaurants, not chains).

## 3. Product decisions (all confirmed)

| # | Question | Decision | Rationale |
|---|----------|----------|-----------|
| 1 | Who is it for? | **Product for many shops** (multi-tenant) | Not just one family shop — a sellable product |
| 2 | Platform | **Native Android tablet app** (Kotlin + Jetpack Compose) | Cheapest tablets, dominant in target market, full BT/printer control |
| 3 | First market | **Malaysia** | RM (MYR), Bahasa Malaysia + English UI |
| 4 | Payments v1 | **Record-only** | Shop's own DuitNow QR / TNG / cash; app records how each sale was paid. Pluggable `PaymentProvider` interface so integrated payments drop in later |
| 5 | Hardware v1 | **BT barcode scanner + ESC/POS thermal printer (58/80mm) + cash drawer** (kick via printer) | The classic trio; no weighing scale in v1 |
| 6 | Devices per shop | **1 tablet** (owner phone app is a later phase) | Single-writer sync = no conflict-resolution nightmare |
| 7 | Business model | **Paid from day one** | Free trial (e.g., 14 days) → subscription/license activation |
| 8 | Connectivity | **Local-first + cloud sync** | 100% functional offline (Room/SQLite); syncs to per-shop cloud account when online; sync is also the backup story |

### Payment types (v1, all configurable)
Cash · DuitNow QR · Touch 'n Go eWallet · Card — shop takes payment through its own channels; EasyPOS tags each sale with the method.

### Watch-items (not v1)
- **LHDN e-Invoice** — Malaysia's e-invoicing mandate phases in by revenue threshold; small grocers currently exempt. Keep data model extensible (document numbers, timestamps) so compliance can be added as a module, like Aronium's KSA plugin.
- **Integrated DuitNow/TNG** via gateway (iPay88, GHL, Billplz…) once merchants demand it.

## 4. v1 architecture

```
 TABLET (Kotlin · Compose · Room)                CLOUD (thin, minimal)
┌──────────────────────────────┐               ┌─────────────────────────┐
│ Sell · Catalog · Stock       │   operation   │ Auth (shop account)     │
│ Cash · Users · Reports       │──log push──▶  │ Tenant/store registry   │
│ Receipts (ESC/POS · PDF)     │◀──catalog──── │ Sync API (per shop)     │
│ Account · License · Sync UI  │   pull        │ PostgreSQL              │
└──────────────────────────────┘               └─────────────────────────┘
  works 100% offline · syncs when online          future: owner app,
                                                  web dashboard consume
                                                  the same data
```

Key architecture principles:
- **Local-first**: every feature works with zero internet; the app never blocks a sale.
- **Single-writer sync**: one tablet per shop → append-only operation log pushed to cloud, catalog pulled down; last-write-wins is sufficient.
- **Honest sync UI**: always show state ("Synced ✓ / Offline — 12 sales pending").
- **Multi-tenant-ready data**: shop/tenant ID in every record from day one.
- **3 taps max** for any daily action; all touch targets ≥48px; barcode-first, grid-second.

## 5. Feature list (P0 — Launch)

Mirrors the formal specs in `openspec/changes/add-pos-tablet-v1/specs/` (9 capabilities, 40+ testable scenarios).

### 5.1 Sales terminal (`sales-terminal`)
- Barcode scan-to-cart: scanner or typed barcode adds/increments instantly, no confirmation tap; field auto-clears and refocuses
- Unknown barcode → offer "create product with this barcode" or quick-sell (cart untouched)
- Category-tab product grid (tappable tiles, one category at a time, usage-frequency order); tap adds to cart
- Persistent always-visible cart: name, unit price, qty ±, line total; live subtotal / tax / total; remove line; clear-all with confirm
- Quick-sell custom item: free-text name + price, no product record needed
- Payment capture (record-only): shop-configured types (Cash, DuitNow QR, Touch 'n Go, Card); cash tender with prominent change display; exact-tender fast path; under-tender blocked
- Void a cart line with reason, logged with acting user
- Sale completion is local-first: persists immediately, decrements stock, works 100% offline — network never blocks a sale
- Every sale attributed to its cashier with a sequential local sale number (on receipt & day summary)

### 5.2 Product catalog (`product-catalog`)
- Product CRUD: name, barcode (unique), category, cost, sale price, active flag — Admin/Manager only; deletion is soft-deactivation
- Categories: create, rename, reorder; tab ordering on sell screen
- Product tiles render beautifully without photos (emoji/color identity + name + price)
- CSV import with documented mapping; auto-creates missing categories; per-row result report (created/updated/failed + reason); failed-row retry — Aronium migration path
- CSV export of full catalog (incl. inactive) as backup
- Search: partial name / barcode / SKU prefix; exact barcode resolves instantly

### 5.3 Stock management (`stock-management`)
- Auto-decrement on every completed sale (quick-sell items don't touch stock)
- Negative stock allowed but visually flagged (sell past zero keeps trading)
- Stock list with filters: all / low (per-product threshold, default 5) / zero / negative, with count badges and category grouping
- Valuation totals at cost and at sale price for the current filter
- Every stock delta attributable: who, when, how much, why (sale / adjustment / import) — per-product history

### 5.4 Cash management (`cash-management`)
- Cash in / cash out entries: amount + **mandatory reason**, stamped with user & timestamp, immutable (corrections via compensating entry)
- Full audit log newest-first (direction, amount, reason, user, timestamp); Admin/Manager see all, Cashiers see own entries
- End-of-day summary: sales count, sales by payment type, cash in/out totals, net cash movement — on-device, exportable as PDF (WhatsApp/share)

### 5.5 User access control (`user-access-control`)
- PIN login (4–6 digits, salted Argon2id hashes, never plaintext)
- Quick user switching from any screen via user badge — preserves open carts
- Roles: **Cashier** (sell, void own lines) / **Manager** (+ catalog, stock, cash, reports) / **Admin** (+ users, settings, license); denials logged
- Action attribution on every mutation: sale, void, cash entry, product edit, stock change, settings change
- Auto-lock after configurable idle (default 5 min) or manual lock; state preserved

### 5.6 Receipts (`receipts`)
- Receipt content: shop name/contact, sequential sale no., date/time, cashier, items, subtotal, tax, total, payment type, tendered + change, custom footer — all RM 2dp
- ESC/POS Bluetooth printing, 58mm & 80mm, shop logo as header bitmap
- Printer unreachable ≠ blocked sale: sale completes, print retries with visible error
- PDF fallback with system share sheet (printerless shops)
- Reprint/share any past sale's receipt, marked REPRINT
- Cash-drawer kick via printer on cash sales (configurable on/off); never on non-cash

### 5.7 Shop settings & onboarding (`shop-settings`)
- First-run wizard: Admin account → shop profile → printer (optional, with test print) → import/add products → sell screen; resumable, ~10 min to first sale
- Shop profile (name, address/phone, logo) applied to receipts
- All money displayed as RM with 2 decimals everywhere
- BM/EN language toggle, instant, persisted
- Printer discovery + width selection + test print
- Receipt branding: header line, footer message, logo, with preview
- Manual full-backup export to a single file (catalog + sales + cash + users minus PIN hashes + settings)

### 5.8 Account & license (`account-license`)
- Shop cloud account (phone/email + OTP verification), one device = one shop tenant; skippable — app fully works offline first, non-blocking prompt after
- Free trial (default 14 days): countdown, expiry warnings, offline grace
- License activation via key (out-of-app payment: bank transfer/toyyibPay) or subscription sign-in; signed expiry cached locally, clock-rollback resilient
- Restricted mode after expiry: history & export still work; new sales require activation
- Admin-only plan status view

### 5.9 Data sync (`data-sync`)
- 100% offline operation for every feature; sync failures never block local actions
- Operation journal: every mutation = idempotent ordered op (UUID, device, actor, entity), appended atomically with the change
- Background push (batches, retry with backoff, server-side idempotency) + pull (per-shop cursor) whenever online
- Last-write-wins conflict resolution on updated_at, all conflicts logged and reviewable
- Honest sync status always visible: synced / syncing / offline N pending / error
- Device replacement: sign in on new tablet → full restore from cloud → old device demoted
- Sync = automatic backup of record (survives uninstall/factory reset)

### 🅿️1 — Fast follow

- Hold / park sale · resume later
- Shifts: open/close with float, X/Z reports
- Credit sales ("utang") with customer ledger + repayment
- Low-stock alerts + restock list
- Quick inventory count + adjustments (with history log)
- Discounts (line & basket) + manager-PIN approvals for sensitive actions
- Expiry / batch tracking with "expiring soon" report
- On-tablet sales charts (daily/weekly/monthly, top products/groups)

### 🅿️2 — Later

- Owner phone app (overview + approvals) — the synced cloud data makes this an increment, not a rewrite
- Web dashboard for owner
- Promotions · price lists (retail vs wholesale) · price-tag/barcode label printing
- Product variants / pack sizes
- Weighing scale + weight barcodes for produce
- Suppliers & stock-in (purchase receiving)
- Integrated DuitNow/TNG payments via gateway (the plug-and-play payment module)
- LHDN e-invoice readiness module
- Multi-device per shop

### Explicitly out of v1
Multi-device · kitchen/print stations · fiscal compliance (KSA/Vanuatu/Fiji-style) · dual currency · restaurant flows (dine-in/takeaway) · loyalty programs · email-scheduled reports.

## 6. UX principles

1. **Three taps max** — any daily action reachable in ≤3 taps
2. **Finger-first** — all targets ≥48px, no desktop-density grids
3. **Barcode beats browse** — optimize the scan path; grid is the fallback
4. **Forgiving** — void/undo everywhere; nothing destructive without confirmation
5. **Honest offline** — always show sync state; never silently lose a sale
6. **Hayabusha's look** — light, clean, card-based, category tabs, blue accent

## 7. Research sources

- Aronium (reference system): app screenshots in `ref image/` + feature pages at aronium.com
- Hayabusha: UI/UX reference screenshot (card-grid, light theme)
- [SelectHub — Loyverse POS review](https://www.selecthub.com/pos-software/loyverse-pos-review/) (closest analog: free tablet POS, offline, employees, inventory)
- [IT Retail — Best Grocery Store POS Systems 2026](https://www.itretail.com/blog/best-grocery-store-pos-system) (grocery-specific: scales, perishables, weighted items, department reporting)

## 8. Open threads (when work begins)

- Sync protocol details (batching, retry, device re-registration after tablet loss)
- Trial/licensing mechanics (activation via bank transfer / toyyibPay / license keys)
- Receipt template design (58mm vs 80mm layouts)
- Aronium CSV export → EasyPOS import mapping
- Bengali/BM/EN string architecture (i18n from day one)
