# AGENTS.md

## Repo state

- **Planning-only today.** No app code exists: `android-app/`, `backend/`, `docs/` are created by tasks in the change below. There is **no build/test/lint/typecheck command** — don't hunt for one until that code lands.
- **Not a git repository** — no history, branches, or commits yet.
- Single source of truth: OpenSpec change `add-pos-tablet-v1` in `openspec/changes/add-pos-tablet-v1/`
  (`proposal.md`, `design.md` decisions D1–D11, 9 capability specs under `specs/`, `tasks.md` = 78 checkboxes). `openspec/specs/` (main specs) is empty until changes archive.
- `CLAUDE.md` — invariants + architecture; read before writing code. `app_idea.md` — product decision log (P0/P1/P2 scope). `ref image/` — reference screenshots, not shippable assets. UI also lives in an external Stitch project (ids/flow in `CLAUDE.md`).

## Commands

The `openspec` CLI (v1.7) resolves the nearest `openspec/` root, so these work from any directory in the repo:

```bash
openspec list                              # active changes + task progress (add --json for counts)
openspec status --change add-pos-tablet-v1 --json
openspec validate add-pos-tablet-v1        # run after EVERY edit to a change artifact
openspec instructions apply --change add-pos-tablet-v1 --json   # task list + workflow state
```

There is no `openspec show … -t tasks`; the task list is the file `openspec/changes/add-pos-tablet-v1/tasks.md`.

## Workflow

- Drive spec work through the opsx commands/skills, not improvised edits: explore, propose, apply, update, sync, archive. OpenCode: `/opsx-apply` etc. (`.opencode/commands/opsx-*.md` + `.opencode/skills/`); Claude Code mirror: `/opsx:apply` etc. (`.claude/commands/opsx/` + `.claude/skills/`).
- Completing a task = immediately flip `- [ ]` → `- [x]` in `openspec/changes/add-pos-tablet-v1/tasks.md`; progress (`openspec list`) is derived from that file.
- If implementation reveals a design/spec conflict, update the artifact via `/opsx:update` first — never silently diverge from `design.md` or a spec.

## Non-negotiable invariants (full list in `CLAUDE.md`)

- Sell path (scan → cart → payment) must never wait on network, sync, disk locks, or license checks.
- Money is `Long` cents everywhere; no floats in money paths; RM formatting only at the UI edge.
- Everything works fully offline; sync is background-only and always reports honest pending counts.
- Every user-facing string exists in English + Bahasa Malaysia (`values/` and `values-ms/`) from day one.
- Payments are record-only in v1 (no gateway/webhooks); every mutation is attributed and journaled.

## Planned layout (design.md D1)

Monorepo with two modules created by tasks 1.1–1.4: `android-app/` (Kotlin, Jetpack Compose, Room, Hilt, money as Long cents, minSdk 26, landscape tablet) and `backend/` (Node.js + TypeScript strict, NestJS, Prisma, PostgreSQL, Zod).
