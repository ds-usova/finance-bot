---
description: Fix a bug that already exists, across one module or several. Reproduces it, writes a fix file with a stabilize/red/green step for each module, stops for approval, then applies them — one sub-agent per module, concurrently — logging every approach that failed and why.
argument-hint: [ a bug report, a failing test, a stack trace, or a findings entry ]
---

# Fix Bug

Something the repository already does is wrong. Three kinds of step put it right, and no others.

| Kind        | What it does                                                             |
|-------------|--------------------------------------------------------------------------|
| `stabilize` | moves whatever the fix needs to exist first — a signature, an interface, a contract between two services |
| `red`       | one test that reproduces the bug and fails on its symptom                |
| `green`     | production code, until that test passes and the suite stays green        |

**Every fix ends with a `red` step that failed and a `green` step that made it pass.** A bug closed without a
test that first reproduced it is a bug closed on someone's word.

**Everything that fails on the way is written down**, in the fix's `## Attempts` log, with the output it produced.
That log is what a stopped run leaves behind, and what stops the next session repeating the last one.

## When Not To Use It

- **New behaviour** — a feature, an endpoint, a field nobody serves yet. That is `design-task`, then `plan-task`.
- **Restructuring code that behaves correctly** — that is `rework`.
- **A failure inside a plan that is still being implemented** — its own green phase owns that diff.

**A bug is behaviour the repository already promises and does not deliver.** Where nobody promised it, this is a
feature, however much it feels like a defect.

## Input Resolution

The argument is a bug report, a failing test, a stack trace, a row from a task's `review/findings.md`, or a
description. Each is a starting point; the scope comes from reproducing it and reading the code.

Read the repository-wide conventions and `<module>/docs/conventions.md` for every module the bug reaches. Follow
the conventions index to wherever each lives. They answer the build and test commands, the test types and what a
test of each type may fake, the layering rule and what checks it, the diagram format, how a test is disabled, the
parallelism limits, what must run before a commit, and the commit policy. **All the tiers bind a step**, not only
the last.

## Phase 0 — Reproduce, and Baseline

**Reproduce the bug before anything else.** Run what the report describes — the test, the request, the command.

- **It reproduces**: record exactly what it produced. That output is what `reproduces:` is written against, and
  what the fix is measured by.
- **It does not reproduce**: stop and say so, with what was run and what happened instead. Ask for the missing
  condition. Never write a fix for a bug nobody has seen fail — a `red` step for a symptom that was guessed at
  will pass, and the run will call that a fix.

**Then the affected modules are clean**, whatever else the tree carries. Uncommitted work under an affected
module: name the files and stop. Uncommitted work elsewhere is left alone.

Then run the full build and the entire test suite of every affected module, using each module's own commands.

- **Green**: record the commit and the total and skipped counts it was measured at. Those figures are what every
  later guardrail compares against.
- **Red only where the bug already has a failing test**: that is the baseline, and the failing test is named. It
  is the one exception, and it does not exempt a `red` step — a test that already fails is `runs:` for a step
  that carries no `test-files:` beyond it.
- **Anything else red**: stop, change nothing, report the failures. Fixing them is not this fix's scope.

**A measurement is not repeated over an unchanged tree.** Where a whole-suite run already answers for the commit
this starts from, its figures are read rather than taken again. The condition is checkable: whoever is about to
run knows what has been written since. This holds at every gate in this skill.

## Phase 1 — Diagnose, and Write the Files

A fix owns a directory, numbered and named the way a task is: `docs/<n>-<name>/`. `<n>` is one more than the
highest already used, scanning `<number>-*` in both `docs/` and `docs/implemented/`. The directory carries the
number and the name; the files do not repeat them.

| The bug reaches | The directory holds                                                        |
|-----------------|-----------------------------------------------------------------------------|
| one module      | `bug.md`, `fix.md`                                                          |
| several         | `bug.md`, `<module>/fix.md` for each                                        |
| several, on a contract between them | one more: `shared/fix.md`                               |

**`bug.md` is written once and is shared. Every `fix.md` is owned by exactly one module.** That split is not
cosmetic: the module agents run concurrently and each writes its own attempt log, so two of them must never hold
the same file open.

**These files are the artifact**, and a fresh session resumes from them. Write them under the repository's
documentation conventions like any other document.

### `bug.md`

```
# Bug: <the symptom, in the user's terms>

**Affected Modules:** `module-a`, `module-b`
**Source:** <one line — a findings file and the row's number, a report, an issue, or the request>
**Baseline:** <the commit the suite was measured at, and the counts>

## What happens

- **Given** <the state the system is in>
- **When** <what happens>
- **Then** <what should follow>
- **Actual** <what follows instead>

## How it reproduces

<the exact command, request or test, and the output it produced — quoted, not described>

## Why it happens

<the chain of causes, from the symptom back to the line that is wrong — see below>

## What the fix must not break

<one line per behaviour that currently works and depends on the code being changed>

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

### Each `fix.md`

```
# Fix: <what changes in this module>

**Affected Module:** `module-a`
**Bug:** [<the bug>](../bug.md)

## Steps

| #   | What changes | What proves it |
|-----|--------------|----------------|

<the checklist — see below>

## Attempts

<see attempts.md — this module's failed approaches go here>

## Open Questions

- **Q1:** …
  - A:
```

**The table comes first, and it is the whole fix to anyone not applying it.** One row per step: its ID, what
changes in a clause, and what proves it in a clause. The checklist underneath is for the agent applying a step
and for `fix.sh`. Its grammar is [`step-format.md`](step-format.md), beside this file.

**Write the table from the steps, never the steps from the table.**

The `Bug:` link is relative and survives archiving: `bug.md` from a single-module fix, `../bug.md` from a
per-module or shared one.

### `shared/fix.md`

**Written only where the bug spans a contract between modules.** It holds `stabilize` steps and nothing else —
the schema, each consuming module's wiring to it, and every call site the change breaks, carried back to
compiling. Its header names every module on the seam:

```
**Affected Modules:** `module-a`, `module-b`
```

**The contract is shared; the behaviour behind it is not.** A schema both modules build from goes here. The
handler that schema declares does not — the module serving it fixes it in its own file, and the consumer's own
`red` step drives its side at its own test boundary.

**A reproduction runs inside one module.** Where the bug only shows with two services really running, it belongs
to the module that owns the entry point, with the counterpart at whatever boundary that module's conventions give
its integration tests — a stub server, a test broker. Where no module can host it, say so: the reproduction
becomes a **Manual test** in `review/findings.md`, and the fix says which module's `red` step comes closest.

### Structure

Two component diagrams, **Now** and **Target**, in the language the module's conventions name, following their
rules for boundaries, layout and marking. **Draw them only where the fix moves responsibility between classes,
creates one, or removes one** — most fixes do none of that, and then the section is left out rather than left
empty.

## Phase 2 — Stop

Present the files and stop. Ask every Open Question in one batch via `AskUserQuestion`, and write each answer
into its file as its `- A:`.

**Do not touch a source file until the user asks for the steps to be applied.** A step whose kind the user
disputes is re-classified in the file first.

**A fix that settles a decision worth recording asks here whether to record it**, as a numbered Open Question
like any other. An answered `yes` is what authorizes the archiving pass to write it down. Most bug fixes settle
none and ask nothing.

## Phase 3 — Apply

**`fix.sh validate` exits 0 on every file before the first source file is touched.** Anything it reports is
fixed in the file first.

**The order is the same in every fix: every `stabilize`, then every `red`, then every `green`.** No file declares
it and no step schedules it.

1. **`shared/fix.md`, alone**, where the directory holds one. It is applied by its own `fix-bug-module` agent,
   given every module on the seam, and nothing else starts until it lands. Its exit condition is that every one
   of those modules compiles, passes its architecture check, and still has the suite phase 0 measured. A blocked
   shared fix stops the run here, with no module agent started — the cheapest failure this skill can produce.
2. **One `fix-bug-module` sub-agent per `fix.md`**, spawned concurrently. Each is given its own file path, its
   module's phase-0 figures, and the conventions its module names. Nothing waits: the shared file landed
   everything that crosses, so the module fixes are independent by construction.
3. **How many start at once is the repository tier's answer.** One machine runs every module, and a module's own
   conventions cannot see what a sibling is doing. Read the **Parallelism** rules at the level that binds all the
   modules and start no more agents than they allow, starting the next as a running one finishes.

**What happens inside an agent is its own** — its steps, its guardrails, its attempt log, its ticks. Reconcile
nothing about a step, and never edit a file an agent owns.

**Report per module as each returns.** One finishing does not wait for another.

### What Is Never Done

- A test is never deleted or weakened to make a step green. A `stabilize` step may disable one, under the rule
  [`applying-a-step.md`](applying-a-step.md) gives for it.
- A second defect found along the way is reported, never fixed. It is a new fix or a new task.
- Nothing outside the steps is improved because it was nearby.
- **An approach that failed is never dropped in silence.** It goes in `## Attempts` with its output, whether the
  next one worked or not.

## Phase 4 — Finish

When every agent has returned:

1. **`fix.sh task docs/<n>-<name>/`** — it lists every fix file the directory holds and exits 0 only when all of
   them are complete. Anything else: leave the directory in place and summarize what is open. The phases end here.
2. **Full build and full suite of every affected module, green**, with nothing left in `disables:` still off, and
   the skipped count back to phase 0's. Anything red or still disabled names the step that left it, and the
   directory is not archived. A behaviour from **What the fix must not break** that could not be kept, and a step
   abandoned, are reported here rather than filed.
3. Whatever else the modules' **build** conventions require of a finished change — a coverage guardrail, a
   formatting gate.
4. **A refactor round over the whole diff**, spawned as a sub-agent — see below.
5. **Write `review/findings.md`** into the fix's own directory, in the shape
   [`findings.md`](../../templates/findings.md) gives. A fix fills **Critical**, **Bug**, and **Manual test** —
   the last is where a reproduction no module could host ends up. Skip the module-first rule where the fix
   touched one module: the section's opening line names it instead.

   **A fix files no refactoring candidates.** Something worth doing later goes in the report to the user, who
   decides whether it becomes a rework.

   A fix with nothing open still gets the file.
6. **Close the row this fix came from.** Where `Source:` names a findings file and a row, open that file and set
   the row's `Status`: `done · <this fix's number>` where the row is fully closed, or leave it `open` with one
   added clause naming what still remains. Re-emit the opening count line. **Nothing here blocks.**
7. **Archive** on a clean closing gate and `fix.sh task` reporting every file complete — a manual check still open
   in `review/findings.md` never blocks: move the whole `docs/<n>-<name>/` directory into `docs/implemented/`,
   and commit the move where the conventions commit at all.
8. **What the conventions run over finished work.** Every affected module's conventions say what happens once a
   change is complete — a measurement, a documentation pass. Follow the conventions index to wherever they say
   it, and run that list in its order, passing each entry the archived `bug.md`. An entry listed by several
   affected modules runs once. Each states its own commit behaviour.

### The Refactor Round

**One sub-agent, once, over everything the fix changed** — the refactor agent the module's conventions name for a
finished body of work, spawned the way they say. A fix lands minimal code under pressure, often after several
attempts, and this is the pass that reconciles it with the code around it.

| It gets                                | So that                                                       |
|----------------------------------------|---------------------------------------------------------------|
| the diff from the **Baseline:** commit | it sees what the steps add up to, not what any one did        |
| `bug.md` and every `fix.md`, as its brief | a shape contradicting the fix's aim is refused and reported |
| the module's conventions, by name      | it applies this repository's priorities                       |

**It never touches the `red` step's test.** That test is the proof the bug is fixed, and a refactor pass that
rewrites it removes the evidence. Say so in the prompt.

**The diff is one module's, so a fix spanning two gets one pass each**, under that module's own conventions —
never one pass across two stacks, which is a diff no single set of refactoring conventions describes.

The suite runs over the result before anything else in this phase.

## Version Control

Whether this run commits at all, and how, is the conventions' **Version Control** rules. A repository has one
history however many modules it has, so expect them at the level that binds all of them.

**Missing or silent means no commits.** Never invent a commit policy.

**Several module agents commit into that one history at once.** That is a constraint on the policy, not something
this skill resolves. Follow whatever it says about scoping a commit and about a concurrent one, and report a
refusal it does not cover rather than improvising a retry.

## Report

What the finished fix tells the user is [`report-format.md`](report-format.md), beside this file.
