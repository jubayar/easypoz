## Purpose

Tracking the physical money in the drawer that isn't sales: owner float top-ups, till withdrawals for restocking ("cash out to buy stock"), and small expenses — each with a reason and an accountable name, so end-of-day reconciles.

## ADDED Requirements

### Requirement: Cash in entry
The system SHALL let a user record cash added to the drawer with an amount (greater than zero, two decimals) and a mandatory reason — picked from quick presets (float top-up, supplier restock, e-wallet cash-out, petty cash) or entered as free text — stamped with the acting user and timestamp. The entry SHALL be immutable once saved; corrections SHALL be made by a compensating entry.

#### Scenario: Record cash in
- **WHEN** SALIMAN adds cash 930.00 with reason "Abdullah Al Mamun — given from TNG"
- **THEN** the entry appears in the cash log attributed to SALIMAN with timestamp and cannot be edited or deleted

#### Scenario: Preset reason with note
- **WHEN** the user records cash out 25.00 with the "Petty Cash" preset and note "plastic bags & receipt rolls"
- **THEN** the entry saves with both the preset and the note visible in the cash log

#### Scenario: Reason is mandatory
- **WHEN** the user tries to save a cash entry with an empty reason
- **THEN** the save is rejected with a prompt to enter the reason

### Requirement: Cash out entry
The system SHALL let a user record cash removed from the drawer with the same rules as cash in (amount, mandatory reason — preset or free text, attribution, immutability).

#### Scenario: Record cash out
- **WHEN** the Admin removes 150.00 with reason "gas"
- **THEN** a cash-out entry appears in the log attributed to the Admin with timestamp

### Requirement: Live drawer balance
The system SHALL display the current expected drawer cash — all cash sales plus all cash in minus all cash out to date — on the cash in/out screen, labeled as expected (not counted) money and updating immediately after every cash-affecting action. When the owner banks drawer money, the removal is simply a cash out, keeping the figure self-consistent.

#### Scenario: Balance updates after cash in
- **WHEN** the drawer shows RM 842.50 and the user records a cash-in of 50.00
- **THEN** the displayed balance becomes RM 892.50 immediately

#### Scenario: Cash sale moves the balance
- **WHEN** a cash sale of RM 17.00 completes
- **THEN** the expected drawer cash increases by 17.00

### Requirement: Cash log audit trail
The system SHALL show all cash entries newest-first with direction, amount, reason, acting user, and timestamp, and the list SHALL be scrollable through the full history. Cash entries SHALL be visible to Admin and Manager; Cashiers SHALL see entries they created. Only Admin and Manager SHALL create cash entries by default.

#### Scenario: Browse history
- **WHEN** the user opens the cash log with 157 historical entries
- **THEN** entries are listed newest-first showing direction, amount, reason, user, and timestamp, and scrolling loads the full history

#### Scenario: Cashier cannot log cash movements
- **WHEN** a Cashier attempts to open the cash in/out screen
- **THEN** access is denied and the attempt is logged

### Requirement: End-of-day summary
The system SHALL generate a day summary for any chosen date showing: number of sales, total sales broken down by payment type, cash in total, cash out total, net cash movement, average basket value, and the day's top-selling products ranked by units sold. The summary SHALL be viewable on-device, printable on the connected thermal printer (80mm slip), and exportable/sharable as PDF, computed entirely from local data. In v1 the trading day is the reconciliation unit — there is no separate shift concept.

#### Scenario: Day summary matches day's activity
- **WHEN** a day had 42 sales (37 cash totaling RM 530.20, 5 DuitNow totaling RM 88.00), one cash-in of 100.00 and one cash-out of 150.00
- **THEN** the day summary shows exactly those figures and net cash movement -50.00

#### Scenario: Share day summary
- **WHEN** the Admin taps share on a day summary
- **THEN** a PDF is generated and offered via the system share sheet (WhatsApp, email, save)

#### Scenario: Print day summary
- **WHEN** the Manager taps print on a day summary with an 80mm printer connected
- **THEN** a formatted summary slip prints via ESC/POS

### Requirement: Drawer reconciliation
The day summary SHALL support reconciling the drawer: it SHALL compute the day's expected drawer cash (opening context + cash sales + cash in − cash out), accept an entered physical count, and display the variance (match, over, or short). Each recorded count SHALL be saved with expected and counted amounts, variance, acting user, and timestamp.

#### Scenario: Counted short
- **WHEN** expected drawer cash is RM 792.50 and the Admin counts and enters RM 790.00
- **THEN** the summary shows a shortage of RM 2.50 and records the count with attribution

#### Scenario: Clean match
- **WHEN** the counted amount equals the expected amount
- **THEN** the summary shows a match (variance 0.00) and records the count
