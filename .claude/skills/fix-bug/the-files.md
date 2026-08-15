# `bug.md` and `fix.md`

What Phase 1 writes. They follow the repository's documentation conventions like any other document.

## The directory

`docs/<n>-<name>/`, `<n>` one more than the highest used in `docs/` and `docs/implemented/`.

| The bug reaches                     | The directory holds                  |
|-------------------------------------|--------------------------------------|
| one module                          | `bug.md`, `fix.md`                   |
| several                             | `bug.md`, `<module>/fix.md` for each |
| several, on a contract between them | one more: `shared/fix.md`            |

**`bug.md` holds the bug. Each `fix.md` holds one module's work and is owned by exactly one agent** — the module
agents run concurrently, so two of them never write the same file.

## `bug.md`

```
# Bug: <the symptom, in the user's terms>

**Affected Modules:** `module-a`, `module-b`
**Source:** <one line — a findings file and row, a report, an issue, or the request>
**Baseline:** <the commit, then per module: total, skipped, and any machine state a skip depends on>
**Attempts:** <per file, the numbers logged: `bug.md · A1–A3, module-a/fix.md · A1, module-b/fix.md · —`>

## What happens

- **Given** <the state the system is in>
- **When** <what happens>
- **Then** <what should follow>
- **Actual** <what follows instead>

## How it reproduces

<the exact command, request or test, and the output it produced — quoted, not described>

## Why it happens

<the chain of causes, from the symptom back to the line that is wrong>

## What the fix must not break

<one line per behaviour that currently works and depends on the code being changed>

## Attempts

<see templates/attempts.md — the diagnosis's failed approaches>
```

**`## Why it happens` is a chain, and every link is evidence.** A link nothing proved is marked `unverified` on
its own line; a chain with one is a hypothesis, and the section says what would settle it.

**`**Attempts:**` is the line a new session reads first.** It names every attempt in every file of the fix.
Phase 1 writes the label with nothing after it; `fix.sh attempts` fills it and refreshes it, so nothing depends
on somebody remembering.

**`**Closed:** <why>`** in the header means the fix was decided against or abandoned. **`## Structure`**, two
component diagrams Now and Target in the module's diagram language, is added only where the fix moves
responsibility between classes. **`## Open Questions`**, in the `- **Q1:** … / - A:` form, is added only where
Phase 2 has something to ask.

## Each `fix.md`

```
# Fix: <what changes in this module>

**Affected Module:** `module-a`
**Bug:** [<the bug>](../bug.md)
**In flight:** <the step being applied and the approach being tried — empty between steps>

## Steps

| #   | Kind | What changes | Touches |
|-----|------|--------------|---------|

<the checklist — see step-format.md>

## Attempts

<see templates/attempts.md — this module's failed approaches>
```

**The table is the whole fix to anyone not applying it.** `What changes` starts with a verb and is one clause —
a row that needs two is two steps. `Touches` is the one class or package the step reaches, so the blast radius
is read down one column. What proves a step is its kind's, and is not repeated per row. Write the table from
the steps, never the steps from the table.

**`In flight:` is what a stopped run otherwise leaves nowhere.** `fix.sh start` writes it when a step starts,
`fix.sh tick` empties it. A resumed run reads it for what was being tried and for nothing else — the first
unticked step, not this line, says where to pick up.

**`## Open Questions`** appears in a `fix.md` only when its agent returns blocked and writes the question it
needs answered.

## A Test That Pins the Wrong Behaviour

**The commonest bug is one an existing test asserts.** That test is the `red` step's: it goes in `test-files:`,
the step changes its assertion to the reported symptom, and the run fails as any `red` step must. This is not
weakening a test. The test is named in `## What the fix must not break`, with what it was protecting and why that
is not lost.
