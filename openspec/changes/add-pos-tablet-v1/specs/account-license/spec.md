## Purpose

EasyPOS is a paid product: each shop gets a cloud account, starts on a free trial, and activates a license to continue after the trial. The tablet keeps working offline, so license state must live on-device and reconcile with the cloud when online.

## ADDED Requirements

### Requirement: Shop account creation
The system SHALL register a shop account with the cloud backend (phone or email + verification) during or after onboarding, associating the device with exactly one shop tenant. The app SHALL remain fully usable offline before account creation, storing data locally and prompting to create an account to enable sync and trial.

#### Scenario: Sign up during onboarding
- **WHEN** the owner enters phone number and verifies the OTP during onboarding
- **THEN** a shop account is created on the backend and the device is bound to that tenant

#### Scenario: Skip account initially
- **WHEN** the owner skips account creation
- **THEN** the app works locally for selling, and a persistent (non-blocking) prompt offers account creation to unlock trial/sync

### Requirement: Free trial
The system SHALL start a trial (default 14 days) when the shop account is created, during which all features are enabled. The system SHALL show remaining trial days and SHALL warn as expiry approaches. Trial state SHALL be cached locally so offline days do not disable the app prematurely; a reasonable offline grace period SHALL apply after expiry.

#### Scenario: Trial countdown
- **WHEN** the account is on day 12 of 14
- **THEN** the app shows "2 days left in trial" with an upgrade prompt

#### Scenario: Offline during trial end
- **WHEN** the trial expires while the device is offline beyond the grace period
- **THEN** the app enters restricted mode and, once online, definitively resolves license state with the backend

### Requirement: License activation
The system SHALL activate a paid license by entering a license key issued after out-of-app payment (bank transfer / payment link), or by signing in to a device on an account with an active subscription. Activation SHALL unlock full functionality for the subscription period and cache the expiry locally.

#### Scenario: Activate with license key
- **WHEN** the Admin enters a valid license key while online
- **THEN** the backend validates and binds it to the shop account, and the app shows the license as active with its expiry

#### Scenario: Invalid key
- **WHEN** an unrecognized license key is entered
- **THEN** activation fails with a clear error and the app state is unchanged

### Requirement: Restricted mode after expiry
The system SHALL allow viewing history and exporting data after a trial/license expires, but SHALL require activation before completing new sales.

#### Scenario: Expired but not locked out of data
- **WHEN** the license has expired and the Admin opens day summaries or exports a backup
- **THEN** viewing and export work, while the sell screen directs to activation

### Requirement: License visibility
The system SHALL display plan status (trial days left, or license active until date) in settings, visible to Admin only.

#### Scenario: Check status
- **WHEN** the Admin opens Account & License in settings
- **THEN** current plan state and expiry are shown with a renewal action
