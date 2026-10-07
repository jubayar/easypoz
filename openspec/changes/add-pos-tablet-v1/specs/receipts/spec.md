## Purpose

The physical proof of sale: rendering a receipt with the shop's branding, printing it on a Bluetooth thermal printer, kicking the cash drawer open, and offering a PDF when the shop prefers paperless.

## ADDED Requirements

### Requirement: Receipt content
The system SHALL render each sale as a receipt containing: shop name and contact footer, sequential sale number, date and time, cashier name, line items (name, qty, unit price, line total), subtotal, tax (if any), grand total, payment type, cash tendered and change (for cash sales), and a customizable footer message. All amounts SHALL display in RM with two decimals.

#### Scenario: Receipt reflects the sale
- **WHEN** a cash sale of 2 items totaling RM 8.50 with RM 10.00 tendered completes
- **THEN** the receipt shows both lines, total 8.50, tendered 10.00, change 1.50, the cashier's name, and a unique sequential sale number

### Requirement: Thermal printing via ESC/POS
The system SHALL print receipts over Bluetooth to ESC/POS-compatible thermal printers, supporting both 58mm and 80mm paper widths (selectable in settings), including the shop logo as a bitmap on the receipt header when provided.

#### Scenario: Print on 80mm printer
- **WHEN** a sale completes with an 80mm printer connected and set as default action
- **THEN** a correctly formatted 80mm receipt prints with logo, items, totals, and footer

#### Scenario: Printer unreachable at sale time
- **WHEN** a sale completes while the paired printer is off or out of range
- **THEN** the sale still completes, the app shows a printer error with a retry, and the receipt remains available for later reprint

### Requirement: Receipt as PDF fallback
The system SHALL generate any receipt as a PDF and offer it via the system share sheet (WhatsApp, email, save to device), so a shop can operate without any printer.

#### Scenario: Paperless shop
- **WHEN** a shop has no printer configured and a sale completes
- **THEN** the app offers the receipt as a shareable PDF instead of printing

### Requirement: Reprint
The system SHALL allow reprinting or re-sharing the receipt of any past sale from the sales history, marked visibly as a reprint.

#### Scenario: Reprint last receipt
- **WHEN** the user opens the most recent sale and taps reprint
- **THEN** the same receipt prints again with a "REPRINT" mark

### Requirement: Cash drawer kick
The system SHALL trigger the cash drawer (via the printer's kick code, before or after printing) when a cash sale completes, with the behavior configurable on/off in settings.

#### Scenario: Drawer opens on cash sale
- **WHEN** a cash sale completes with drawer kick enabled
- **THEN** the drawer opens via the connected printer

#### Scenario: Drawer stays closed on non-cash sale
- **WHEN** a DuitNow QR sale completes with drawer kick enabled
- **THEN** the drawer does not open
