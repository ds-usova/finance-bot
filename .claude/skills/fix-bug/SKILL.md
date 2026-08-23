---
description: Fix a bug that already exists, across one module or several. Reproduces it with a test, diagnoses it, writes a fix file per module, stops for approval, then applies them — one sub-agent per module, concurrently — logging every approach that failed and why. Given an existing bug.md, resumes it without retrying what its log rules out.
argument-hint: [ a bug report, a failing test, a stack trace, a findings row, or the path of an existing bug.md ]
---

# Fix Bug

Make code that already exists do what the repository already promised.

## When Not To Use It

**A bug is behaviour the repository already promises and does not deliver.** Where nobody promised it, this is a
feature.

- **New behaviour** — that is `design-task`, then `plan-task`.
- **Restructuring code that behaves correctly** — that is `rework`.
- **A failure inside a plan still being implemented** — its own green phase owns that diff.

## The Three Kinds of Step

| Kind        | What it does                                                                                             |
|-------------|----------------------------------------------------------------------------------------------------------|
| `stabilize` | moves whatever the fix needs to exist first — a signature, an interface, a contract between two services |
| `red`       | one test that reproduces the bug and fails on its symptom                                                |
| `green`     | production code, until that test passes and the suite stays green                                        |

**Every fix ends with a `red` step that failed and a `green` step that made it pass.** Their grammar is
[`step-format.md`](step-format.md). **Every approach that failed on the way is written into `## Attempts`**,
with the output it produced — [`attempts.md`](../../templates/attempts.md).

## The Files

The fix owns `docs/<n>-<name>/`: one `bug.md`, one `fix.md` per module, and `shared/fix.md` where two modules
must agree on a contract. What each carries is [`the-files.md`](the-files.md). Where the diagnosis reaches more
than one module, [`crossing-modules.md`](crossing-modules.md) decides which module the fix is cut in and what
`shared/fix.md` holds. `fix.sh` (`scripts/fix/` under the plugin root, README beside it) reads, ticks and
validates them, and
writes the two lines that otherwise go stale — `In flight:` and `bug.md`'s `Attempts:`.

## Conventions

Read the repository-wide conventions and `<module>/docs/conventions.md` for every module the bug reaches. They
answer the build and test commands, the test types and what each may fake, the layering check, how a test is
disabled, parallelism, sub-agent models, what runs before a commit, the commit policy, and what runs over
finished work. Every tier binds a step.

## Phase 0 — Reproduce and Baseline

**Given the path of an existing `bug.md`, [`resuming.md`](resuming.md) replaces this phase and Phase 1.**

**The affected modules are clean.** Uncommitted work under one of them: name the files and stop. Uncommitted work
elsewhere, and untracked leavings that are nobody's work, are named and left alone.

**Reproduce the bug.** Run what the report describes — the test, the request, the command.

- **It fails every time** — run it twice to know that. Record the exact output; `reproduces:` is written against
  it.
- **There is nothing to run yet.** Write the test that shows the symptom and take its failure as the
  reproduction. Then disable it, in the form the module's conventions give for a disabled test, so it stays in
  the tree — uncommitted — and the baseline is green. The `red` step names it in `test-files:` and its work is to
  enable it. Where the conventions give no way to disable it, revert it and let the `red` step write it again
  from `## How it reproduces`. Take the cheapest test type that fails for the bug's own reason, and say in the
  diagnosis why a cheaper one does not.
- **It fails some runs and not others.** Run until it has failed twice and record `<failures> in <runs>`; that
  pair goes into `reproduces:`. The `red` step then runs until it has failed twice, giving up at three times that
  run count. The `green` step runs three times the runs the `red` step took, and passes every time.
- **It does not reproduce here, and could.** Stop, say what was run and what happened, ask for the missing
  condition.
- **It cannot reproduce here at all.** Stop, say what environment or data it needs. Whether to fix it blind is
  the user's call.

**Never write a fix for a bug nobody has seen fail.**

**Baseline.** Full build and full suite of every affected module, with the reproduction test disabled or
reverted. Record the commit and, per module, the total and skipped counts, plus any machine state a skip depends
on. **The disabled reproduction test is named beside them**: it is the one skip the baseline carries that the
`red` step will clear, so the closing gate expects the count one lower than measured. Green, or red only on the test
the report already names, is a baseline; anything else red stops the run. A whole-suite run that already answers
for this commit is read, not repeated.

## Phase 1 — Diagnose, and Write the Files

**Write `bug.md` as soon as the symptom and the reproduction are known**, so the attempts have somewhere to land
as they happen. Then work out why the bug happens: `## Why it happens` is a chain from the symptom to the line
that is wrong, every link proved. A probe — a log line, a counter, a query by hand, a seam a test can drive — is
how a link is proved. **A probe that failed is an attempt**, phase `diagnosis`. A probe the fix needs again
becomes a `stabilize` step. Every probe's edits are reverted before the files are presented — the disabled
reproduction test is the one edit that stays, uncommitted. An effect an edit does not undo — a migration run, an
offset consumed — is named in the diagnosis and put back by hand where possible.

Then write every `fix.md`, and run `fix.sh validate <the directory>` until it exits 0.

## Phase 2 — Stop

Present the files and stop. Nothing touches a source file until the user asks for the steps to be applied.

**Open Questions are rare here.** One is written only where the diagnosis needs a call the user must make, or
where the fix settles a decision worth an ADR under the Follow-Up conventions. Ask them in one batch via
`AskUserQuestion` and write each answer in as `- A:`.

**A fix turned down here** gets `**Closed:** <why>` in `bug.md`'s header, is left where it is, and is reported as
closed. The disabled reproduction test is reverted.

## Phase 3 — Apply

**Every `stabilize`, then every `red`, then every `green`**, in every fix. No file declares the order.

1. **`shared/fix.md` first, alone**, where there is one, by its own `fix-bug-module` agent given every module on
   the seam. Nothing else starts until it lands.
2. **One `fix-bug-module` agent per `fix.md`, concurrently**, spawned and waited for as
   [`templates/sub-agents.md`](../../templates/sub-agents.md) says. Each gets its file path, `bug.md`, its
   module's phase-0 figures, the conventions its module names, and what the shared fix disabled in that module.
   Cap the number running at once, and pick the model, by what the conventions say about parallelism and
   sub-agent models.
3. **What happens inside an agent is its own** — its steps, its guardrails, its attempt log, its ticks. Never
   edit a file an agent owns while it runs. Report per module as each returns.

**An agent that returns blocked changes the plan, not the rules.** It returns for one of: three failed attempts
on a step, a symptom that survives a correct `green` step, a cause in another module, a step whose kind is wrong,
a test asserting the old behaviour that nobody foresaw, or a refusal from [`applying-a-step.md`](applying-a-step.md).
Wait for the module's agent to return, amend `bug.md` and the fix files — a new `red`/`green` pair moves whole,
a struck step keeps its table row with `abandoned — <why>`, an ID is never reused — re-run `fix.sh validate`, and
**stop for approval again as in Phase 2**. Then re-spawn that module's agent; it starts at its first unticked
step.

**A fix the user calls off** is reverted step by step, newest first, in the skill and never in an agent, until
every module's suite is back at its baseline figures. A revert that conflicts stops and reports. The directory
stays with its log intact, `bug.md` takes its `**Closed:**` line, and nothing is archived.

### What Is Never Done

- A test is never deleted or weakened to make a step green. A `stabilize` step may disable one, and a `red` step
  clears it.
- A second defect found along the way is reported, never fixed.
- Nothing outside the steps is improved because it was nearby.
- An approach that failed is never dropped in silence.

## Phase 4 — Finish

1. **`fix.sh task docs/<n>-<name>/` exits 0.** Anything open: leave the directory in place and summarize what is
   open. The phases end there.
2. **Full build and full suite of every affected module, green**, nothing left in `disables:` still off, and the
   skipped count back to phase 0's — apart from a machine state the baseline recorded, restored where it can be.
3. **The diff says what the steps claimed.** Read the diff from `**Baseline:**`, scoped to the affected modules'
   paths: every test file it touches is named in some step's `test-files:`, and each step changed only what it
   named. A test changed under no step is a defect whatever the suite says.
4. **Whatever else the modules' build conventions require of a finished change** — a coverage guardrail, a
   formatting gate. A guardrail that fails blocks the archive.
5. **One refactor round over the whole diff**: one `tdd-refactor-phase` agent per module's diff, on the model
   the module's conventions name for the refactor pass — the session's model where they name none. It gets the
   diff from `**Baseline:**`, `bug.md` and every `fix.md` as its brief, and the module's conventions by name. **It never
   touches a `red` step's test** — say so in the prompt. Run
   the suite again once it returns.
6. **`fix.sh attempts docs/<n>-<name>/`** — it rewrites `bug.md`'s `**Attempts:**` line from every file's log, so
   one file tells the next session where the log is.
7. **Close the row this fix came from**, where `Source:` names a findings file, in that file's own form.
8. **Archive**: move `docs/<n>-<name>/` into `docs/implemented/`, and commit the move where the conventions
   commit at all.
9. **What the conventions run over finished work**, in their order, each entry once, each handed the archived
   `bug.md`.

## Version Control

Whether and how this run commits is the conventions' **Version Control** rules, at the tier that binds every
module. Missing or silent means no commits. Several module agents commit into one history at once; follow what
the rules say about scoping and about a concurrent commit, and report a refusal they do not cover.

## Report

[`report-format.md`](report-format.md).
