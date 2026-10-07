# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

EasyPOS — a local-first native Android tablet POS for small Malaysian grocery shops (RM currency, EN/BM languages), sold as a paid multi-tenant product. The repo is currently **pre-implementation**: planning is complete, code has not been written (`android-app/` and `backend/` do not exist yet). Not a git repository yet.

## Where the truth lives

- `openspec/changes/add-pos-tablet-v1/` — the active (and only) change:
  - `proposal.md` — scope and explicit non-goals (hold/discounts/membership/shifts etc. deferred)
  - `design.md` — decisions D1–D11: repo layout, Android stack, backend stack, data model, sync protocol, printing, scanner, auth, i18n, testing
  - `tasks.md` — the implementation checklist (task numbers like `7.3` are referenced in conversation)
  - `specs/<capability>/spec.md` — 9 capability specs with testable scenarios (sales-terminal, product-catalog, stock-management, cash-management, user-access-control, receipts, shop-settings, account-license, data-sync)
- `app_idea.md` — product decision log: Aronium heritage, P0/P1/P2 feature phasing, UX principles (three-taps-max, barcode-beats-browse)
- `ref image/` — Aronium screenshots (functional DNA) and Hayabusha screenshot (UI bar)
- UI designs live externally in Stitch, project "EasyPOS Grocery Tablet System" (id `9798094753620694253`) — read screens via `get_screen`; the HTML `downloadUrl` curl'd + tag-stripped yields all spec-relevant labels

## Commands

No build/test/lint exists yet — the working commands are the OpenSpec CLI:

```bash
openspec list                              # active changes
openspec status --change add-pos-tablet-v1 --json
openspec validate add-pos-tablet-v1        # run after editing any artifact
openspec show add-pos-tablet-v1 -t tasks   # view an artifact
```

Slash commands `/opsx:explore`, `/opsx:propose`, `/opsx:apply`, `/opsx:update`, `/opsx:sync`, `/opsx:archive` drive the spec workflow (skills live in `.claude/skills/`). Work is organized as changes under `openspec/changes/`; completed changes archive into `openspec/specs/` (currently empty — greenfield).

## Planned architecture (per design.md)

Monorepo once implementation starts:

```
android-app/   Kotlin + Jetpack Compose + Room + Hilt, MVVM + StateFlow, minSdk 26,
               landscape tablet-first; money as Long cents; UUID ids on-device
backend/       Node.js + TypeScript (strict) + NestJS + Prisma + Zod over PostgreSQL;
               single container VPS; 7 endpoints (auth, device, sync push/pull, license)
docs/          CSV import format, printer compatibility matrix
```

Sync: every mutation commits atomically with a `JournalOp` append; background push (batches, idempotent by op ID) + pull (per-shop cursor); single writer per shop, conflicts by last-write-wins on `updated_at`.

## Invariants — do not violate

- **The sell path is sacred**: scan → cart → payment must never wait on network, sync, disk locks, or license checks (license expiry blocks new sales only, never history/export)
- **Money is `Long` cents** everywhere; RM 2-decimal formatting only at the UI edge; no floating-point in money paths
- **Everything works fully offline**; sync is background-only and never blocks local actions; sync status is always honest (pending counts shown)
- **Payments are record-only** in v1 — no gateway/webhook integration; the DuitNow QR display is the shop's own static image
- **Every user-facing string exists in English and Bahasa Malaysia** (`values/` + `values-ms/`) from day one
- **Every mutation is attributed** (acting user + timestamp) and journaled; PINs/tokens/hashes excluded from backups
