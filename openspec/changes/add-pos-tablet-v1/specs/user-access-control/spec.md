## Purpose

Multiple staff share one tablet: each has a PIN, a role that bounds what they can do, and a name that follows every action they take. Protecting the drawer and the data without slowing the counter down.

## ADDED Requirements

### Requirement: PIN-based login
The system SHALL authenticate users with a personal PIN (4-6 digits). PINs SHALL be stored only as salted hashes, never in plaintext. The Admin SHALL create users with name, role, and PIN, and SHALL be able to reset a PIN or deactivate a user.

#### Scenario: Login with PIN
- **WHEN** SALIMAN enters their correct PIN on the lock screen
- **THEN** the app unlocks to the sell screen with SALIMAN shown as the active user

#### Scenario: Wrong PIN
- **WHEN** a wrong PIN is entered
- **THEN** access is denied without revealing whether the user exists, and the attempt is logged

### Requirement: Quick user switching
The system SHALL allow switching the active user from any screen via a user badge tap, requiring the incoming user's PIN, without losing in-progress carts or app state.

#### Scenario: Mid-sale switch
- **WHEN** a cart is open under SALIMAN and the Admin taps the user badge and enters the Admin PIN
- **THEN** the Admin becomes the active user and the open cart is preserved

### Requirement: Role-based permissions
The system SHALL enforce three roles with at least the following permissions — Cashier: sell, void own pre-payment lines, view own cash entries; Manager: everything a Cashier can, plus product/catalog editing, stock views and edits, cash in/out, reports; Admin: everything, plus user management, settings, license/account actions. Denied actions SHALL be logged with user, action, and timestamp.

#### Scenario: Cashier blocked from settings
- **WHEN** a Cashier attempts to open Settings
- **THEN** the action is denied, the denial is logged, and the app remains on the previous screen

#### Scenario: Manager edits products
- **WHEN** a Manager edits a product price
- **THEN** the edit is permitted and recorded with the Manager's identity

### Requirement: Action attribution
The system SHALL stamp the acting user and timestamp on every mutating action (sale, void, cash entry, product edit, stock change, settings change) and SHALL expose this attribution in relevant history views.

#### Scenario: Later audit of a price change
- **WHEN** an Admin reviews the history of a product whose price changed yesterday
- **THEN** the change shows the old value, new value, acting user, and timestamp

### Requirement: Auto-lock
The system SHALL lock to the PIN screen after a configurable period of inactivity (default 5 minutes) or immediately when the active user taps lock, while preserving app state for unlock.

#### Scenario: Idle lock
- **WHEN** the tablet is unused for the configured period while a cart is open
- **THEN** the app locks; unlocking with any valid PIN preserves the open cart
