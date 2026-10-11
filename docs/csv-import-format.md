# EasyPOS Product CSV Import Format

Canonical column mapping for product import/export. The same mapping is used
for export (`product-catalog` spec), so a round-trip export → import is
lossless for all importable fields.

## File rules

- **Encoding:** UTF-8. A leading UTF-8 BOM is tolerated. Windows line endings
  (CRLF) are tolerated.
- **Header row:** required, first row. Column order does not matter; unknown
  columns are ignored (this is what makes raw Aronium exports importable).
- **Delimiter:** comma `,`. Values may be quoted with `"` per RFC 4180
  (quoting is required when a value contains a comma).
- **Empty rows** are skipped.
- Import is **row-independent**: a malformed row fails alone; it never aborts
  the import (see product-catalog spec: per-row created/updated/failed report
  with retry of failed rows only).

## Columns

| Column      | Required | Type            | Notes |
|-------------|----------|-----------------|-------|
| `name`      | yes      | text            | Product name, trimmed, non-empty after trim |
| `barcode`   | no       | text (digits)   | Unique when present. Stored as text to preserve leading zeros. Duplicate barcode in file or DB → row fails with the conflicting product named |
| `sku`       | no       | text            | Free-form stock code |
| `category`  | no       | text            | Matched case-insensitively against existing categories; unknown names are **auto-created**. Blank → `Uncategorized` |
| `cost`      | no       | money           | Decimal string, parsed to integer sen (cents); blank/missing → `0.00` |
| `price`     | yes      | money           | Sale price. Required; a row without a parseable price fails |
| `active`    | no       | boolean         | `true`/`false`; tolerant of `yes`/`no`, `1`/`0`. Blank or missing → `true` |
| `stock`     | no       | integer         | Initial/updated stock on hand. Blank → left unchanged (0 for new products). Setting stock journals an `ADJUST` stock delta attributed to the importing user |
| `location`  | no       | text            | Shelf location shown in stock views (e.g. `Rak A-03`) |

### Money parsing (tolerant)

The canonical form is a plain dot-decimal string (`9.50`). The parser also
accepts, per row and independently:

- a leading currency symbol or code: `RM 9.50`, `rm9.50`, `MYR 9.50`
- thousands separators: `1,234.50`
- trailing negatives in parentheses: `(9.50)` → `-9.50`

Values are parsed to **Long sen** immediately at import; no floating-point
money survives past the parser (design D3).

### Update semantics

A row **updates** an existing product when its `barcode` matches an existing
product, or when `barcode` is blank and `name`+`category` match exactly one
product; otherwise it **creates**. `stock` on an update sets the on-hand count
journaled as an adjustment. Rows that fail report the row number and reason.

## Example

```csv
name,barcode,category,cost,price,active,stock,location
Milo Activ-Go 1kg,8886467920123,DRINKS,9.50,12.00,true,18,Rak A-03
Plastic bag (kecil),,UMUM,0.10,0.30,yes,500,
Dabur Red Paste 200g,8901012000123,TOOTHPASTE,4.20,5.90,true,24,Rak B-11
,,BROKEN,1,2,true,,
```

Row 4 fails (`name` empty). Rows 1–3 import; `UMUM` and any other unknown
category are created automatically.

## Aronium export mapping

Aronium's product export imports directly thanks to unknown-column tolerance.
Expected correspondences (verified at pilot cutover — task 14.4):

| Aronium column | EasyPOS column |
|----------------|----------------|
| Name / Product Name | `name` |
| Barcode | `barcode` |
| Group | `category` |
| Cost / Purchase Price | `cost` |
| Price / Sell Price | `price` |
| Active/Is Active (if present) | `active` |

Aronium encodes some exports as UTF-16 or with Windows-1252 accents; the
importer detects UTF-8 BOM, UTF-16 BOM, and falls back to Windows-1252 before
giving up on a row.
