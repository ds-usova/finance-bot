# `bug.md` and `fix.md`

What Phase 1 writes, and what each section owes. Write them under the repository's documentation conventions
like any other document.

## The directory

A fix owns one, numbered and named the way a task is: `docs/<n>-<name>/`. `<n>` is one more than the highest
already used, scanning `<number>-*` in both `docs/` and `docs/implemented/`. The directory carries the number
and the name; the files do not repeat them.

| The bug reaches                     | The directory holds                  |
|-------------------------------------|--------------------------------------|
| one module                          | `bug.md`, `fix.md`                   |
| several                             | `bug.md`, `<module>/fix.md` for each |
| several, on a contract between them | one more: `shared/fix.md`            |

**`bug.md` is written once and holds the bug. Every `fix.md` holds one module's work**, and is owned by exactly
one module: the module agents run concurrently, so two of them must never hold the same file open.

## `bug.md`

```
# Bug: <the symptom, in the user's terms>

**Affected Modules:** `module-a`, `module-b`
**Source:** <one line — a findings file and the row's number, a report, an issue, or the request>
**Baseline:** <the commit, then per module: total, skipped, and any machine state a skip depends on>

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

## Structure

<component diagrams, and only where the fix moves responsibility between classes>

## Attempts

<see attempts.md — the diagnosis's failed approaches go here>

## Open Questions

- **Q1:** …
  - A:
```

**`## Why it happens` is a chain, and every link is evidence.** The symptom, what produced it, what produced
that, down to the line that is wrong. A link nothing proved is marked `unverified` on its own line, and a chain
with an unverified link is a hypothesis — say so, and say what would settle it.

**Where the diagnosis is not obvious, this is the phase that fills `## Attempts`.** Each hypothesis that turned
out wrong is an entry with the output that killed it, written the moment it fails.

**`## What the fix must not break` is where the regression risk is named.** It is read by the `green` step, which
runs the whole suite, and by the refactor round after it.

**`## Structure` holds two component diagrams, Now and Target**, in the language the module's conventions name,
following their rules for boundaries, layout and marking. **Draw them only where the fix moves responsibility
between classes, creates one, or removes one.** Most fixes do none of that, and the section is then left out
rather than left empty. It lives in `bug.md`, whatever number of modules the fix reaches, because it describes
the change as a whole.

**A `**Closed:**` header line means the fix was decided against**, and Phase 0's resume scan reports such a
directory rather than picking it up.

## Each `fix.md`

```
# Fix: <what changes in this module>

**Affected Module:** `module-a`
**Bug:** [<the bug>](../bug.md)
**In flight:** <the step being applied, and the approach being tried — empty between steps>

## Steps

| #   | What changes | What proves it |
|-----|--------------|----------------|

<the checklist — see step-format.md>

## Attempts

<see attempts.md — this module's failed approaches go here>

## Open Questions

- **Q1:** …
  - A:
```

**The table comes first, and it is the whole fix to anyone not applying it.** One row per step: its ID, what
changes in a clause, and what proves it in a clause. The checklist underneath is for the agent applying a step
and for `fix.sh`. Its grammar is [`step-format.md`](step-format.md).

**Write the table from the steps, never the steps from the table.**

The `Bug:` link is relative and survives archiving: `bug.md` from a single-module fix, `../bug.md` from a
per-module or shared one.

**`In flight:` is the line a resumed run reads.** Whoever applies a step writes it when the step starts — the ID
and what is being tried, in a clause — and empties it when the step ticks. The attempt log holds the approaches
that failed; this holds the one still being tried, which is the thing a stopped run otherwise leaves nowhere.

## A Test That Pins the Wrong Behaviour

**The commonest bug is one an existing test asserts.** The reproduction cannot be written beside it, because the
suite would then assert both answers.

**That test is the `red` step's, and the step rewrites it.** It goes in `test-files:`, the step changes the
assertion to the reported symptom, and the run fails as any `red` step must. This is not weakening a test.

**A test whose assertion this fix inverts is named in `## What the fix must not break`**, with what it was
protecting and why that is not lost.
