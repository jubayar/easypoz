## Purpose

Knowing what is on the shelf: stock quantities driven by sales, visibility into low/zero/negative items, and what the inventory is worth — all computed locally, offline.

## ADDED Requirements

### Requirement: Stock tracks sales automatically
The system SHALL decrement a product's stock on hand by the quantity sold whenever a sale completes, and SHALL record the delta against the sale. Quick-sell custom items SHALL not affect any product stock.

#### Scenario: Stock decrements on sale
- **WHEN** a product with 10 units on hand sells twice in one sale
- **THEN** its stock on hand becomes 8 immediately after sale completion

### Requirement: Negative stock is allowed but flagged
The system SHALL permit stock to go negative (sales continue when stock hits zero) but SHALL visually flag negative-quantity products in every stock view so staff can correct the record.

#### Scenario: Selling past zero
- **WHEN** a product with 0 on hand is sold
- **THEN** the sale completes and the product's stock shows -1, flagged visibly in the stock list

### Requirement: Stock list with quantity filters
The system SHALL provide a stock overview listing products with category, quantity on hand, cost, and value, filterable to show all, low-stock, zero-quantity, or negative-quantity items, with counts per filter and per-category grouping. Low-stock SHALL be defined per product by a configurable threshold (default 5).

#### Scenario: Filter negative stock
- **WHEN** the user applies the negative-quantity filter and 3 products are negative
- **THEN** exactly those 3 products are listed with a negative-quantity indicator and a count badge showing 3

#### Scenario: Low-stock threshold
- **WHEN** a product's threshold is set to 10 and its quantity is 9
- **THEN** the product appears under the low-stock filter

### Requirement: Manual stock adjustment
The system SHALL let an Admin or Manager adjust a product's stock on hand — by setting a physical count or by entering a receive/remove delta — requiring a mandatory reason (physical count, damaged, new stock, or free text). Each adjustment SHALL be journaled as a stock delta with old and new quantities, reason, acting user, and timestamp, and SHALL be visible in the product's stock history. Stock adjustment SHALL be denied to Cashiers.

#### Scenario: Stocktake correction
- **WHEN** the shelf holds 38 units but the system shows 42, and the Manager sets the count to 38 with reason "Kiraan Fizikal"
- **THEN** stock becomes 38 and the adjustment appears in the product's stock history with both quantities, the reason, and the Manager's name

#### Scenario: Record damaged goods
- **WHEN** the Manager removes 2 units with reason "Rosak"
- **THEN** stock drops by 2 and the delta is journaled with the damage reason

#### Scenario: Cashier cannot adjust stock
- **WHEN** a Cashier attempts to open the stock adjustment sheet
- **THEN** the action is denied and the attempt is logged

### Requirement: Stock valuation
The system SHALL display total stock value at cost and at sale price for the current filter, computed live from the visible stock list, together with the estimated gross margin between the two (amount and percentage).

#### Scenario: Valuation for whole shop
- **WHEN** the user views the unfiltered stock list
- **THEN** totals show inventory value at cost and at sale price for every product

#### Scenario: Margin alongside valuation
- **WHEN** the unfiltered stock list shows RM 72,418.56 at cost and RM 119,503.92 at sale price
- **THEN** the totals also show the estimated margin (RM 47,085.36 · 39.4%)

### Requirement: Stock changes are attributable
The system SHALL record who changed stock, when, by how much, and why (sale reference, cash-session adjustment, or manual edit) for every stock delta, viewable per product.

#### Scenario: Investigate a stock drop
- **WHEN** the user opens a product's stock history and the last change was a sale by SALIMAN
- **THEN** the history shows that sale with timestamp, quantity change, and the acting user
