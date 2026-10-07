## Purpose

One tablet holds the shop's data; the cloud keeps a safe copy and the foundation for the future owner app. Sync is an operation journal pushed and pulled in the background — never on the critical path of a sale, always honest about its state.

## ADDED Requirements

### Requirement: Full offline operation
The system SHALL perform every local feature (selling, catalog, stock, cash, receipts, reports) with zero network connectivity, with no degraded modes and no data loss. Sync failures SHALL never block any local action.

#### Scenario: Internet outage during trading hours
- **WHEN** connectivity is lost for a full day of trading
- **THEN** all sales, cash entries, and edits work normally and are queued for sync

### Requirement: Operation journal
The system SHALL record every data mutation locally as an ordered, idempotent operation (created-at timestamp, device ID, actor, entity, change) appended before the mutation is visible to the user, forming the unit of sync push.

#### Scenario: Sale becomes operations
- **WHEN** a sale completes
- **THEN** the sale and its stock deltas are recorded in the journal before the receipt flow begins

### Requirement: Background push and pull
The system SHALL, whenever online and authenticated, push pending journal operations to the shop's cloud account and pull any changes recorded from the backend (e.g., products edited via future web tools or a support restore), applying them locally. Sync SHALL retry with backoff on failure and resume after outages without duplicates (server-side idempotency by operation ID).

#### Scenario: Reconnect after outage
- **WHEN** connectivity returns after 30 queued sales
- **THEN** all 30 upload automatically, no duplicates are created on retry, and sync status returns to synced

#### Scenario: Pull a product price change
- **WHEN** a price change exists on the backend for the shop
- **THEN** the next sync applies it locally and the new price is used at the terminal

### Requirement: Single-writer conflict policy
With one tablet per shop in v1, the system SHALL resolve rare conflicts (e.g., a product edited on the tablet and on the backend) by last-write-wins on updated-at timestamp, and SHALL log every resolved conflict for review.

#### Scenario: Concurrent price edit
- **WHEN** a product price was changed in the cloud at 10:02 and on the tablet at 10:05
- **THEN** the 10:05 tablet value wins on both sides and the conflict is logged

### Requirement: Honest sync status
The system SHALL always display current sync state — synced, syncing, offline with pending count, or error — visible from the main screen without navigation, and SHALL never report "synced" while operations remain unconfirmed by the backend.

#### Scenario: Pending count shown
- **WHEN** 12 operations await upload while offline
- **THEN** the status shows offline with "12 pending" and clears only after backend confirmation

### Requirement: Device replacement and restore
The system SHALL allow signing into the shop account on a new device and restoring the full shop dataset (products, categories, users, sales, cash entries, settings) from the cloud, after which the new device continues syncing as the shop's active tablet.

#### Scenario: Broken tablet replacement
- **WHEN** the owner signs into the account on a new tablet and confirms restore
- **THEN** the complete shop dataset is restored locally and the app is ready to sell, with the old device deregistered or demoted

### Requirement: Sync doubles as backup
Because all shop data syncs to the cloud account, the system SHALL treat the backend as the automatic backup of record: a data-deleting event on the tablet (uninstall, factory reset) SHALL be recoverable via device restore without depending on manual export files.

#### Scenario: Recover after factory reset
- **WHEN** the tablet is factory reset and the app reinstalled, then signed into the shop account
- **THEN** all pre-reset data is restored from the cloud
