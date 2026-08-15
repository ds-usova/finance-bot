# `rework.md` and `steps.md`

What Phase 1 writes. They follow the repository's documentation conventions like any other document.

## The directory

`docs/<n>-<name>/`, `<n>` one more than the highest `<number>-*` in `docs/` and `docs/implemented/`. The
directory carries the number and the name; no file repeats them.

| The rework reaches                 | The directory holds                                          |
|------------------------------------|--------------------------------------------------------------|
| one module                         | `rework.md`, steps included                                  |
| several                            | `rework.md` without steps, and `<module>/steps.md` for each  |
| several, across a shared signature | one more: `shared/steps.md`, holding `stabilize` steps only  |

**Each steps file is owned by exactly one agent**, so two agents never write one file. **`rework.md` is the
artifact a fresh session resumes from** — see [`resuming.md`](../../templates/resuming.md).

## `rework.md`

```
# Rework: <what changes>

**Affected Modules:** `module-a`
**Source:** <one line, a path not a link — a findings file and the row's number, a file, or the request>
**Baseline:** <the commit the suite was green at>

## The fix

<the change in one sentence, then what that sentence needs to be believed — the mechanism it turns on, or the
chain of causes it cuts and where. Where diagrams follow, say so and stop.>

## What changes

**<the first move, in a clause>**

| #   | Kind | What changes | Touches |
|-----|------|--------------|---------|

**<the next move>**

| #   | Kind | What changes | Touches |
|-----|------|--------------|---------|

**Not changed:** <one line — what a reader might fear this rework alters and it does not>

## What the code does now

| What | Where | What is wrong with it |
|------|-------|-----------------------|

## Structure

<Now and Target component diagrams, in the module's diagram language and under its rules for boundaries and
marking. Only where the rework moves responsibility between classes, creates one or removes one; otherwise the
section is left out. A created class is marked as an addition in Target; a deleted one is drawn only in Now.
Whatever moves between the two is labelled with the step IDs that move it.>

## What must stay true

<one line per invariant no test asserts, and how it would be noticed if it broke>

## Steps

<the checklist — step-format.md>

## Open Questions

- **Q1:** …
  - A:
```

## Each `steps.md`

A `<module>/steps.md` carries `**Affected Module:**` and `**Rework:** [<the rework>](../rework.md)` above its
`## Steps`; `shared/steps.md` names every module on the seam. **`## What changes` stays in `rework.md`** and
covers every steps file.

## What changes

**This section is the rework to a person.** A reader who agrees with **The fix** and these tables can stop; the
rest is evidence.

- **Rows are grouped by the move they make**, under a bold clause saying what that group achieves. A rework is
  usually three or four moves; twenty rows in ID order are not readable.
- **`What changes` starts with a verb** — `move`, `split`, `rename`, `narrow`, `add check`, `drop` — and is one
  clause. A row that needs two is two steps.
- **`Touches` is the one class or package the step reaches**, so the blast radius is read down one column.
- **`Not changed:` names what a reader would fear this rework alters and it does not** — the SQL text, a test
  assertion, a public port.

**Write the tables from the checklist, never the checklist from the tables.** They are written once at Phase 1;
a step re-classified in Phase 2 edits both. Ticks live in the checklist only.
