## Purpose

The product and category records that the terminal sells against: creating and editing products, organizing them into categories, showing them beautifully without photos, and moving data in and out via CSV.

## ADDED Requirements

### Requirement: Product record management
The system SHALL maintain products with name, barcode (optional, unique when present), category, cost price, sale price, active flag, and an optional shelf location (e.g. "Rak A-03", "Chiller 2") shown in stock views. Creating a product SHALL optionally record an initial stock-on-hand quantity. The product form SHALL show the live gross margin computed from cost and sale price as the values are entered. Creating, editing, and deleting (soft-deactivating) products SHALL be restricted to Admin and Manager roles.

#### Scenario: Create a product
- **WHEN** a Manager creates a product "Milo 1kg", category DRINKS, cost 9.50, sale 12.00, barcode 1234567890123, and saves
- **THEN** the product appears in the catalog and is immediately sellable and syncable

#### Scenario: Create a product with starting stock and shelf location
- **WHEN** a Manager creates "Milo 1kg" with 18 units on hand and shelf location "Rak A-03" and saves
- **THEN** the product is sellable with stock 18, the shelf location appears in stock views, and the initial quantity is journaled as a stock movement attributed to the Manager

#### Scenario: Margin preview while entering prices
- **WHEN** the Manager enters cost 9.50 and sale price 12.00 in the product form
- **THEN** the form immediately shows the per-unit gross margin (RM 2.50, 20.8%)

#### Scenario: Barcode uniqueness
- **WHEN** a product is saved with a barcode already used by another product
- **THEN** the system rejects the save with a clear error identifying the conflicting product

#### Scenario: Deactivate instead of delete
- **WHEN** an Admin removes a product that has past sales
- **THEN** the product is marked inactive (hidden from grid and scan results) and its historical sales remain intact

#### Scenario: Cashier cannot edit catalog
- **WHEN** a user with Cashier role attempts to open product editing
- **THEN** the action is denied and the attempt is logged

### Requirement: Category management
The system SHALL organize products into named categories (e.g. DRINKS, GROCERIES, TOILETRIES) displayed as tabs on the sell screen. Categories SHALL be creatable, renamable, and reorderable by Admin and Manager; a product SHALL always belong to exactly one category.

#### Scenario: Rename a category
- **WHEN** a Manager renames TAMAK to TOBACCO
- **THEN** the sell-screen tab and all member products reflect the new name and no product records are lost

### Requirement: Product display without photos
The system SHALL render every product tile with an optional photo or, when absent, a generated visual identity (category color and/or emoji) plus name and price, so an unpictured catalog remains scannable at a glance.

#### Scenario: Photo-less product tile
- **WHEN** a product has no photo
- **THEN** its tile still renders legibly with its visual identity, name, and price

### Requirement: Product CSV import
The system SHALL import products from a CSV file with a documented column mapping (name, barcode, category, cost, sale price, active), creating missing categories automatically, and SHALL report a per-row result (created / updated / failed with reason) without aborting the whole import on bad rows.

#### Scenario: Migrate from Aronium export
- **WHEN** the user imports a CSV exported from Aronium containing 1,894 rows and 12 of them are malformed
- **THEN** 1,882 rows are imported, a summary reports 12 failures with row numbers and reasons, and the user can retry just the failed rows

#### Scenario: Category auto-creation on import
- **WHEN** an imported row references category BLANKETS that does not exist
- **THEN** the category is created and the product is assigned to it

### Requirement: Product CSV export
The system SHALL export all products (including inactive) to a CSV using the same column mapping as import, usable as a full backup of the catalog.

#### Scenario: Export catalog
- **WHEN** the user exports products
- **THEN** a CSV is written containing every product with its category name and all importable fields

### Requirement: Catalog search
The system SHALL find products by partial name, barcode, or SKU prefix as the user types in the sell-screen search field, ranked so exact barcode matches resolve instantly.

#### Scenario: Search by partial name
- **WHEN** the cashier types "dabur"
- **THEN** products whose names contain "Dabur" (case-insensitive) are listed for tapping or scanning-style selection

### Requirement: Quick price edit from the sell screen
The system SHALL offer a quick-edit sheet when a Manager or Admin long-presses a product tile or a cart line on the sell screen, allowing inline editing of sale price (and cost) without leaving the screen or losing the open cart. The change SHALL be applied immediately at the terminal, journaled, and attributed exactly like a full catalog edit. When a Cashier triggers quick edit, the system SHALL require a Manager or Admin PIN before showing the fields.

#### Scenario: Manager fixes a wrong price mid-sale
- **WHEN** a Manager long-presses the "Milo Activ-Go 1kg" tile, changes sale price from RM 12.00 to RM 12.90, and saves
- **THEN** the sheet closes, the open cart is preserved, the new price applies to subsequent scans and taps immediately, and the change appears in product history with old and new values, actor, and timestamp

#### Scenario: Cashier needs manager approval
- **WHEN** a Cashier long-presses a product tile to edit its price
- **THEN** the system prompts for a Manager or Admin PIN before showing any editable fields, and the completed edit (if approved) is attributed to the approving Manager

#### Scenario: Quick edit works offline
- **WHEN** a quick price edit is saved with no connectivity
- **THEN** the new price applies locally and the edit is journaled for later sync
