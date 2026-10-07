## Purpose

Everything a shop owner configures once and forgets: shop identity, language, receipt branding, printer — plus the first-run wizard that takes a brand-new install to its first sale in about ten minutes, without a manual.

## ADDED Requirements

### Requirement: First-run onboarding wizard
The system SHALL guide a new install through: creating the Admin account (name + PIN), setting the shop profile (name, contact, optional logo), optionally connecting a printer with a test print, optionally importing products from CSV or adding the first few, and confirming currency RM. The wizard SHALL be skippable after the Admin account exists so it never blocks selling, and SHALL end on the sell screen ready for the first sale.

#### Scenario: Fresh install to first sale
- **WHEN** the app is first launched and the owner completes the wizard with shop name "Kedai Runcit Jaya", skips import, and adds 3 products
- **THEN** the app lands on the sell screen with the 3 products sellable and the shop name shown

#### Scenario: Wizard cannot orphan the device
- **WHEN** the owner closes the app midway through onboarding after creating the Admin account
- **THEN** reopening the app starts at the PIN screen and onboarding resumes where it left off without data loss

### Requirement: Shop profile
The system SHALL store and apply a shop profile — name, address/phone, logo image — used on receipts and shown in the app header. Changes SHALL require Admin role.

#### Scenario: Update shop name
- **WHEN** the Admin changes the shop name in settings
- **THEN** new receipts print with the updated name

### Requirement: Currency display
The system SHALL display all monetary amounts as MYR with the RM prefix and two decimal places throughout the app and on receipts.

#### Scenario: Amount formatting
- **WHEN** a product price of 12 and a total of 8.5 are displayed
- **THEN** they render as RM 12.00 and RM 8.50

### Requirement: Language toggle
The system SHALL provide the full user interface in English and Bahasa Malaysia, switchable instantly in settings, persisting across restarts, with a default chosen at onboarding.

#### Scenario: Switch language
- **WHEN** the user switches the language from English to Bahasa Malaysia
- **THEN** all UI text updates immediately without an app restart

### Requirement: Printer setup and test
The system SHALL discover nearby Bluetooth ESC/POS printers, allow selecting one, set paper width (58mm or 80mm), and print a test receipt on demand.

#### Scenario: Connect and test printer
- **WHEN** the user pairs an available printer, selects 80mm, and taps test print
- **THEN** a test receipt prints and the printer is saved as the shop default

### Requirement: Receipt branding
The system SHALL allow customizing the receipt header line and footer message, and uploading a logo used on printed receipts. All changes SHALL be previewable before saving.

#### Scenario: Customize footer
- **WHEN** the Admin sets footer "Terima kasih! WhatsApp reorder: 012-3456789"
- **THEN** the next printed receipt ends with that footer line

### Requirement: DuitNow merchant QR image
The system SHALL let the Admin upload, replace, or remove the shop's static merchant QR image (e.g. its DuitNow QR sticker) in settings, with a preview before saving. The image SHALL be stored locally so it can be displayed during DuitNow-style tenders with no connectivity. Uploading the QR does not create any payment integration — payment stays record-only.

#### Scenario: Configure the QR once, use offline
- **WHEN** the Admin uploads the shop's DuitNow QR image and a DuitNow sale is later tendered with no connectivity
- **THEN** the stored QR displays at the payment step without network access

#### Scenario: Remove the QR
- **WHEN** the Admin removes the merchant QR image
- **THEN** DuitNow tenders complete without displaying a QR and no stale image remains on device

### Requirement: Data export for safety
The system SHALL export a complete local backup (products, categories, sales, cash entries, users minus PIN hashes, settings) to a single file the user can copy off the device, in addition to cloud sync.

#### Scenario: Manual backup
- **WHEN** the Admin taps export backup
- **THEN** a single backup file is saved/shareable containing the full local dataset
