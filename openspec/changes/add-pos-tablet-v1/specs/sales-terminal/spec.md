## Purpose

The checkout screen where 80% of staff time is spent: turning scanned, searched, or browsed products into a paid sale. It must be operable with one hand, in under ten seconds per customer, with or without internet.

## ADDED Requirements

### Requirement: Barcode scan adds product to cart
The system SHALL add a product to the cart when its barcode is entered via scanner or typed into the search field, without requiring any confirmation tap. If the barcode is already in the cart, the system SHALL increment its quantity instead.

#### Scenario: Scan a new product
- **WHEN** the cashier scans a barcode that matches one active product
- **THEN** the product is added to the cart with quantity 1 and the search field is cleared and refocused for the next scan

#### Scenario: Scan an already-carted product
- **WHEN** the cashier scans a barcode for a product already in the cart
- **THEN** that line's quantity increases by 1 and no duplicate line is created

#### Scenario: Unknown barcode
- **WHEN** the cashier scans or types a barcode matching no product
- **THEN** the system shows a "not found" affordance offering to create a product with that barcode or quick-sell a custom item, and the cart is unchanged

### Requirement: Browse products by category grid
The system SHALL display active products as tappable tiles in a grid, filterable by one category at a time via tabs, ordered by usage frequency within the selected category. Tapping a tile SHALL add the product to the cart with quantity 1 (or increment if present).

#### Scenario: Tap a product tile
- **WHEN** the cashier taps an available product tile
- **THEN** the product is added to the cart and the grid stays on the current category

#### Scenario: Filter by category tab
- **WHEN** the cashier taps the "Drinks" category tab
- **THEN** the grid shows only active products in the Drinks category

### Requirement: Stock visibility on product tiles
The system SHALL show each product's current stock on hand on its sell-screen tile (e.g. "Baki 14") and SHALL mark tiles whose stock is at or below the product's low-stock threshold with a distinct low-stock indicator (e.g. "Baki 4 · Rendah"). Stock display SHALL never gate selling: zero- and negative-stock products remain tappable per the stock rules.

#### Scenario: Low-stock tile badge
- **WHEN** a product with threshold 5 has 4 units on hand
- **THEN** its tile shows the on-hand count with a low-stock indicator

#### Scenario: Zero-stock tile still sells
- **WHEN** a product with 0 on hand appears in the grid
- **THEN** its tile shows the zero count and tapping it still adds the product to the cart

### Requirement: Persistent cart with running totals
The system SHALL keep the cart visible at all times on the sell screen, showing per-line name, unit price, quantity, and line total, and a cart-level subtotal, tax, and grand total updated immediately on any change. The cashier SHALL be able to increase/decrease quantity, remove a line, and clear the cart (with confirmation).

#### Scenario: Change quantity
- **WHEN** the cashier taps "+" on a cart line
- **THEN** quantity increases by 1 and the line total and cart totals update immediately

#### Scenario: Remove a line
- **WHEN** the cashier taps remove on a cart line
- **THEN** the line is removed and totals update immediately

#### Scenario: Clear cart with confirmation
- **WHEN** the cashier taps clear cart and confirms
- **THEN** the cart becomes empty and no sale record is created

### Requirement: Quick-sell custom item
The system SHALL let the cashier add an unlisted item to the cart by entering a free-text name and price, without creating a product record.

#### Scenario: Quick-sell a stray item
- **WHEN** the cashier opens quick-sell, enters "Plastic bag" and price 0.30, and confirms
- **THEN** a line "Plastic bag" at 0.30 is added to the cart

### Requirement: Hold and resume a cart
The system SHALL let the cashier hold (park) the current cart — with an optional label — and start a fresh empty cart, and SHALL list held carts on the sell screen for one-tap resume. Holding SHALL NOT create a sale, decrement stock, or consume a sale number; a resumed cart restores its lines and totals and completes like a fresh sale when paid.

#### Scenario: Park a cart
- **WHEN** the cashier taps Hold on a 5-line cart and confirms
- **THEN** the cart is parked with its lines intact, the sell screen resets to an empty cart, and no sale or stock movement is recorded

#### Scenario: Resume a held cart
- **WHEN** the cashier taps a held cart in the held list and later completes payment
- **THEN** the sale is recorded with the resuming cashier's attribution and normal sequential numbering

### Requirement: Payment capture with payment types
The system SHALL complete a sale by recording the total, one payment type (from the shop's configured list, defaulting to Cash, DuitNow QR, Touch 'n Go, Card), and — for cash — the amount tendered, calculating and displaying change due prominently. For cash, the system SHALL offer one-tap quick tender amounts (the exact total and common round-up amounts) each showing the change it would return. When the selected payment type has a configured static merchant QR image (e.g. the shop's DuitNow QR), the system SHALL display that image during tender. Payment is record-only: the system does not move money and never waits on a payment gateway.

#### Scenario: Cash sale with change
- **WHEN** the cart totals RM 8.50 and the cashier tenders RM 10.00 as Cash
- **THEN** the system displays "Change RM 1.50" and, on confirmation, completes the sale

#### Scenario: Exact cash tender
- **WHEN** the tendered cash equals the total
- **THEN** the sale completes with change 0.00 and no change screen is shown beyond confirmation

#### Scenario: Under-tender blocked
- **WHEN** the cashier tenders less than the total as cash
- **THEN** the system does not complete the sale and indicates the remaining amount

#### Scenario: Non-cash payment type
- **WHEN** the cashier selects DuitNow QR and confirms the total
- **THEN** the sale completes tagged with payment type DuitNow QR and no tender/change step is required

#### Scenario: Quick tender chip
- **WHEN** the total is RM 17.00 and the cashier taps the "RM 20" quick tender option
- **THEN** the tendered amount is set to 20.00 and the option shows the resulting change (+RM 3.00)

#### Scenario: Static merchant QR shown at tender
- **WHEN** the cashier selects DuitNow QR and the shop has configured its merchant QR image
- **THEN** the payment step displays the stored QR for the customer to scan and the sale completes only when the cashier confirms payment received — offline, with no gateway or webhook involved

### Requirement: Void a cart line with reason
The system SHALL allow removing a line from the cart after it was added (void), requiring a short reason when configured, and SHALL record the void reason and acting user in the sale audit data when the void happens during or after payment.

#### Scenario: Void before payment
- **WHEN** the cashier voids a line before payment and confirms
- **THEN** the line is removed and the void with reason and user is logged

### Requirement: Sale completion is local-first
The system SHALL persist every completed sale to the local database immediately, decrement stock, and remain fully usable with no network connection. Network state SHALL never delay or block completing a sale.

#### Scenario: Sell while offline
- **WHEN** the device has no connectivity and the cashier completes a sale
- **THEN** the sale is stored locally, stock decrements, a receipt can be printed, and the sync status shows the sale as pending upload

### Requirement: Every sale is attributed
The system SHALL stamp every completed sale with the user who made it and a sequential local sale number, and SHALL include both in the day summary and receipt.

#### Scenario: Attribution on receipt
- **WHEN** a cashier completes a sale
- **THEN** the saved sale, its receipt, and the daily summary identify that cashier

### Requirement: Sales history
The system SHALL list completed sales newest-first for any chosen day with sale number, time, cashier, item count, total, and payment type, and SHALL open a sale's full detail (lines, tender, attribution) from the list. The sales history SHALL be the entry point for receipt reprint and re-share.

#### Scenario: Revisit this morning's sale
- **WHEN** the Manager opens sales history for today and taps sale #1042
- **THEN** the detail shows its lines, payment type, total, and cashier, with reprint and share actions available
