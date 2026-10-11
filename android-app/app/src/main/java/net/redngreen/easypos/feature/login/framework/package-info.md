# framework/

Platform-bound sources for the login feature. Empty by design: PIN
verification is core infrastructure (`core/session`, `core/security`);
`data/SessionLoginRepository` coordinates them without touching platform
APIs directly. Sources land here if the feature gains Bluetooth/NFC badge
readers or similar.
