# ESC/POS Printer Compatibility Test Matrix

Task 10.6. Physical verification of EasyPOS receipt printing over Bluetooth.
Fill one row per printer tested; attach a photo of the printout to the PR.

Target: **at least 2 brands** across **58mm and 80mm** widths.

## How to test

1. Pair the printer in Android Bluetooth settings.
2. In EasyPOS → **Printer**: select the device, set the paper width, tap **Test print**.
3. Complete a cash sale; confirm the receipt prints and (if enabled) the drawer kicks.
4. From **Sales history → a sale → Reprint**: confirm the reprint is marked.
5. From **Sales history → a sale → Share**: confirm the PDF opens in the share sheet.

## Matrix

| # | Brand / model | Connection | Width | Test print | Cash sale receipt | Logo | BM text | Drawer kick | Reprint mark | PDF share | Notes |
|---|---------------|-----------|-------|-----------|-------------------|------|---------|-------------|--------------|-----------|-------|
| 1 |               | BT SPP    | 80mm  | ☐         | ☐                 | ☐    | ☐       | ☐           | ☐            | ☐         |       |
| 2 |               | BT SPP    | 58mm  | ☐         | ☐                 | ☐    | ☐       | ☐           | ☐            | ☐         |       |
| 3 |               | BT SPP    |       | ☐         | ☐                 | ☐    | ☐       | ☐           | ☐            | ☐         |       |

## Known characteristics

- **58mm** → 32 columns, **80mm** → 42 columns (`SettingsKeys.columnsForWidthMm`).
- **Logo** degrades on 58mm; 80mm is the guaranteed path (design D7 risk note).
- English and Bahasa Malaysia both use the ASCII range, so CP437/858 codepage
  selection is safe for receipt text.
- Printer failure never blocks a sale: the receipt stays reprintable from history.
