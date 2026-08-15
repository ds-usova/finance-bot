---
description: Fix a bug that already exists, across one module or several. Reproduces it, writes a fix file with a stabilize/red/green step for each module, stops for approval, then applies them — one sub-agent per module, concurrently — logging every approach that failed and why.
argument-hint: [ a bug report, a failing test, a stack trace, or a findings entry ]
---

# Fix Bug

Three kinds of step put a bug right, and no others.

| Kind        | What it does                                                                                               |
|-------------|------------------------------------------------------------------------------------------------------------|
| `stabilize` | moves whatever the fix needs to exist first — a signature, an interface, a contract between two services |
| `red`       | one test that reproduces the bug and fails on its symptom                                                  |
| `green`     | production code, until that test passes and the suite stays green                                          |

**Every fix ends with a `red` step that failed and a `green` step that made it pass.**

**Everything that fails on the way is written down**, in the fix's `## Attempts` log, with the output it
produced.

## When Not To Use It

- **New behaviour** — a feature, an endpoint, a field nobody serves yet. That is `design-task`, then `plan-task`.
- **Restructuring code that behaves correctly** — that is `rework`.
- **A failure inside a plan that is still being implemented** — its own green phase owns that diff.

**A bug is behaviour the repository already promises and does not deliver.** Where nobody promised it, this is a
feature.

## Input Resolution

The argument is a bug report, a failing test, a stack trace, a row from a task's `review/findings.md`, or a
description. Each is a starting point; the scope comes from reproducing it and reading the code.

Read the repository-wide conventions and `<module>/docs/conventions.md` for every module the bug reaches. Follow
the conventions index to wherever each lives. They answer the build and test commands, the test types and what a
test of each type may fake, the layering rule and what checks it, the diagram format, how a test is disabled, the
parallelism limits, what must run before a commit, and the commit policy. **All the tiers bind a step**, not only
the last.

## Phase 0 — Resume, or Reproduce and Baseline

**First, look for a run to resume.** A directory directly under `docs/` holding a `bug.md`, where `fix.sh task
<that directory>` reports something open and the `bug.md` carries no `**Closed:**` header line, is an
interrupted run. Where there is one, [`resuming.md`](resuming.md) takes over Phase 0 and hands back to Phase 3.
**It can also hand back here** — a directory that turns out to be a different bug is reported and left alone,
and this phase carries on as if there were none.

**Otherwise the tree is clean before anything is run.** Which modules the bug reaches is
not settled yet, so this looks at every module the report or the failing test points at, and widens with the
diagnosis. Uncommitted work under one of them: name the files and stop. Uncommitted work elsewhere is left alone,
and so is an untracked file that is nobody's work — a crash dump, a log, an editor's leavings. Say which those
were rather than stopping on them.

**Then reproduce the bug.** Run what the report describes — the test, the request, the command.

- **There is something to run, and it fails every time**: record exactly what it produced. That output is what
  `reproduces:` is written against, and what the fix is measured by. **Run it twice before believing "every
  time".** One run cannot tell the two branches below apart, and which branch this is governs every gate that
  follows.
- **There is nothing to run yet**, because the symptom is only visible through a test nobody has written.
  **Write that test now, as a probe**, under the probe rules in Phase 1, which say what becomes of it.
  **This is not the `red` step arriving early**: nothing is committed, and the fix is still approved before a
  source file is kept.
- **It fails sometimes**: this is an intermittent bug, and [`an-intermittent-bug.md`](an-intermittent-bug.md)
  governs its rate and every run count after it.
- **It does not reproduce here, and could**: stop and say so, with what was run and what happened instead. Ask
  for the missing condition. Never write a fix for a bug nobody has seen fail.
- **It cannot reproduce here at all** — it needs production data, a load level, or an environment this machine
  does not have. **Stop, and say what would be needed.** Report the diagnosis, and put the reproduction where a
  person can run it: a **Manual test** in a findings file, or a task to build the environment. Whether to fix it
  blind is the user's call, and it is not this skill's run.

**Then run the full build and the entire test suite of every affected module**, using each module's own
commands. A probe written to reproduce the bug is reverted first, so this measures the tree as it stands.

- **Green**: record the commit, and **per module** its total and skipped counts. Those figures are what every
  later guardrail compares against, and each module's agent is handed its own.
- **Red only where the bug already has a failing test**: that is the baseline, and the failing test is named.
  The `red` step still exists and names that test in `test-files:` and `runs:`. Its work is to make the test
  assert the reported symptom — unless the test already asserts it and fails for exactly that reason, in which
  case the step's work is to run it and record the failure, and it edits nothing.
- **Anything else red**: stop, change nothing, report the failures. Fixing them is not this fix's scope.

**A skipped count is only comparable against the same machine in the same state.** Where a module's conventions
say its suite skips whole classes when something is absent — a container runtime, a credential — record that
state beside the counts.

**At the closing gate, only a state recorded here can explain a count that has not come back down**, and only
for the classes those conventions name. Anything else is a test some step turned off and nothing turned back
on. Where the state really did change, restore it and measure again; a re-baseline is what happens when it
cannot be restored, and the report says which.

**Mid-run this does not apply.** A `stabilize` step's `disables:` raises the count on purpose, and the `red`
step named there brings it back down.

**This baseline is not re-measured over an unchanged tree.** Where a whole-suite run already answers for the
commit this starts from, and nothing has been written since, its figures are read rather than taken again.

**This is the baseline and nothing else.** Every guardrail after it runs, whatever the tree looks like: with
module agents running concurrently, "nothing relevant moved" is not something any of them can know.

## Phase 1 — Diagnose, and Write the Files

### Making the Bug Observable

**A probe is allowed here, and it is the one thing that may touch a source file before the user approves
anything.** A log line, a counter, a breakpoint condition, a query run by hand, a seam that lets a test see what
a class is doing.

- **Every probe's edits are reverted before Phase 2 presents anything**, and before the baseline suite runs.
  The files are written while probes may still be in the tree; what the user is shown is a tree nothing has
  been written to.
- **A probe that failed is an attempt**, filed under `diagnosis`.
- **A probe that worked is not lost.** Where the fix needs it again — the seam a `red` step will drive, the
  metric that will prove the symptom is gone — it becomes a `stabilize` step, and the diagnosis says which one.
  Where the fix does not need it, the `## Why it happens` link it established says how it was established.
- **A test written to make the bug fail is a probe like any other**, and it is the commonest one: a symptom no
  existing test can see is observed by writing the test that sees it. Its output is the reproduction, it is
  reverted with the rest, and Phase 3 writes it again as the `red` step.

**Which kind of test that is, is a decision worth a sentence in the diagnosis.** A module's conventions map its
parts to test types, and the same symptom can often be reproduced at more than one of them — cheaply, close to
the wrong code, or expensively, close to what the user saw. **Take the cheapest type that fails for the bug's
own reason.** Where only the expensive one fails for that reason, take it and say why the cheap one does not.

**A probe is never left in the tree and never committed.**

**Reverting a file does not revert what the probe did.** An applied migration, a consumed offset, a burned log,
a written row: where the effect outlives the edit, say so in the diagnosis and in the attempt, name what was
left changed, and put the machine back by hand where that is possible. A probe whose effect cannot be undone is
one to ask about before running, not to explain afterwards.

**A run stopped during Phase 1 leaves probes and no directory**, so nothing can resume it. Write `bug.md` as
soon as the symptom and the reproduction are known, before the diagnosis is finished, and the attempts have
somewhere to land as they happen.

A fix owns a directory, numbered and named the way a task is: `docs/<n>-<name>/`. `<n>` is one more than the
highest already used, scanning `<number>-*` in both `docs/` and `docs/implemented/`. The directory carries the
number and the name; the files do not repeat them.

| The bug reaches                     | The directory holds                  |
|-------------------------------------|--------------------------------------|
| one module                          | `bug.md`, `fix.md`                   |
| several                             | `bug.md`, `<module>/fix.md` for each |
| several, on a contract between them | one more: `shared/fix.md`            |

**Every `fix.md` is owned by exactly one module**, and the module agents run concurrently, so two of them must
never hold the same file open.

**Where the diagnosis reaches more than one module, [`crossing-modules.md`](crossing-modules.md) decides which
one the fix is cut in, whether the seam gets its own file, and where the reproduction lives.** It is read before
the files are written, because it changes what they are.

Write these files under the repository's documentation conventions like any other document.

### `bug.md`

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

<the chain of causes, from the symptom back to the line that is wrong — see below>

## What the fix must not break

<one line per behaviour that currently works and depends on the code being changed>

## Structure

<component diagrams — see below, and only where the fix moves responsibility between classes>

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

### Each `fix.md`

```
# Fix: <what changes in this module>

**Affected Module:** `module-a`
**Bug:** [<the bug>](../bug.md)
**In flight:** <the step being applied, and the approach being tried — empty between steps>

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

**`In flight:` is the line a resumed run reads.** Whoever applies a step writes it when the step starts — the ID
and what is being tried, in a clause — and empties it when the step ticks. The attempt log holds the approaches
that failed; this holds the one still being tried, which is the thing a stopped run otherwise leaves nowhere.

### A Test That Pins the Wrong Behaviour

**The commonest bug is one an existing test asserts.** The reproduction cannot be written beside it, because the
suite would then assert both answers.

**That test is the `red` step's, and the step rewrites it.** It goes in `test-files:`, the step changes the
assertion to the reported symptom, and the run fails as any `red` step must. This is not weakening a test.

**A test whose assertion this fix inverts is named in `## What the fix must not break`**, with what it was
protecting and why that is not lost.

## Phase 2 — Stop

Present the files and stop. Ask every Open Question in one batch via `AskUserQuestion`, and write each answer
into its file as its `- A:`.

**Do not touch a source file until the user asks for the steps to be applied.** A step whose kind the user
disputes is re-classified in the file first.

**A fix the user turns down here has nothing to revert and is not archived.** Write the answer into the file
that killed it, leave the directory where it is, and say in the report that it is closed and why.

**Then add `**Closed:** <why, in a clause>` to `bug.md`'s header.** Phase 0's resume reads that line and reports
the directory rather than picking it up. Anything Phase 1 wrote before a stop takes the same line, for the same
reason.

**A fix that settles a decision worth recording asks here whether to record it**, as a numbered Open Question
like any other. An answered `yes` is what authorizes the archiving pass to write it down. Most bug fixes settle
none and ask nothing.

## Phase 3 — Apply

**`fix.sh validate <the directory>` exits 0 before the first source file is touched.** One call reads `bug.md`
and every `fix.md` the bug holds. Anything it reports is fixed in the file first.

**The order is the same in every fix: every `stabilize`, then every `red`, then every `green`.** No file declares
it and no step schedules it.

1. **`shared/fix.md`, alone**, where the directory holds one, on the terms
   [`crossing-modules.md`](crossing-modules.md) gives. Nothing else starts until it lands.
2. **One `fix-bug-module` sub-agent per `fix.md`**, spawned concurrently. Each is given its own file path, its
   module's phase-0 figures, `bug.md`, the conventions its module names, and — where a shared fix ran — what it
   disabled in that module. **No module agent waits on another**: the shared file landed everything that
   crosses.
3. **How many start at once is the repository tier's answer.** One machine runs every module, and a module's own
   conventions cannot see what a sibling is doing. Read the **Parallelism** rules at the level that binds all the
   modules and start no more agents than they allow, starting the next as a running one finishes. If no such
   rules exist, start them all.

   **Where those rules cap runs rather than agents, the cap is on agents here.** Every agent in this skill
   applies steps against its module's suite, so an agent is a run in flight. A tier that caps agents separately
   is followed as it is written.

4. **The model each agent runs on is the conventions' answer**, read from whatever they say about sub-agent
   models. An agent here both executes steps and decides refusals, so where the conventions split those, it
   takes whatever they name for work that is not solely one or the other, and the session's model where they
   name nothing.

**What happens inside an agent is its own** — its steps, its guardrails, its attempt log, its ticks. Reconcile
nothing about a step, and never edit a file an agent owns.

**Report per module as each returns.** One finishing does not wait for another.

**A fix already under way sometimes has to change**, and [`changing-course.md`](changing-course.md) is how. Read
it whenever an agent returns with a diagnosis that did not hold, a cause in another module, a step of the wrong
kind, or a test nobody foresaw — and whenever the user calls the fix off. Amending and abandoning both belong to
this skill, never to an agent it spawned.

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
   them are complete. **Anything else: run item 3 over what did land, then leave the directory in place and
   summarize what is open.** The phases end there. A module that finished while a sibling blocked has committed
   work in the tree, and it is the work least likely to be looked at again.
2. **Full build and full suite of every affected module, green**, with nothing left in `disables:` still off, and
   the skipped count back to phase 0's. Anything red or still disabled names the step that left it, and the
   directory is not archived. A behaviour from **What the fix must not break** that could not be kept, and a step
   abandoned, are reported here rather than filed.
3. **The diff says what the steps claimed.** Read the diff from the `**Baseline:**` commit and check three
   things against the fix files. They cost one read, and they are the only claims in this skill an agent's own
   report cannot settle:

    - **Every test file the diff touches is named in some step's `test-files:`.** A test changed under no step
      is a `green` step that edited its own proof, which is the one edit this skill exists to prevent. It is
      reported as a defect whatever the suite says.
    - **What each step actually changed is inside what it named.** A `test-files:` listing more than the step
      needed passes the check above and defeats it, so read the list against the diff rather than the other way
      round. A test-support file — a stub, a fixture, a builder — is a test file for this purpose, and belongs
      to the `stabilize` step that prepared it or the `red` step that needed it.
    - **Where the conventions commit, each `red` step's commit carries no production file**, and lands before
      the commit of the `green` step that names it.

   **That last one corroborates the ordering and does not prove it.** A commit names the paths it takes, so an
   agent holding both edits can stage the test alone and produce the same history. Only running the `red`
   test at its own commit and watching it fail would prove it — an execution rather than a read, and a cost the
   user decides on. **Say in the report which of the two the ordering rests on**, and where the conventions
   commit nothing, say that it rests on the agent's account alone.

   **Read the diff scoped to the affected modules' paths.** One working tree may carry another change's files,
   and an unscoped diff reports them as this fix's defect.
4. Whatever else the modules' **build** conventions require of a finished change — a coverage guardrail, a
   formatting gate. **Where one of those commands runs the suite itself, it is item 2**, not a second run of it.
   A guardrail that fails is reported with its own verdict and blocks the archive; it is not argued with.
5. **A refactor round over the whole diff**, spawned as a sub-agent — see below.
6. **Write `review/findings.md`** into the fix's own directory, in the shape
   [`findings.md`](../../templates/findings.md) gives. A fix fills **Critical**, **Bug**, and **Manual test** —
   the last is where whatever the suite cannot see ends up. Skip the module-first rule where the fix
   touched one module: the section's opening line names it instead.

   **A fix files no refactoring candidates.** Something worth doing later goes in the report to the user, who
   decides whether it becomes a rework.

   A fix with nothing open still gets the file.
7. **Close the row this fix came from.** Where `Source:` names a findings file and a row, open that file and set
   the row's `Status` in the form that file's own template gives, naming this fix. Where the row is not fully
   closed, leave it `open` with one added clause naming what still remains. Re-emit the opening count line.
   **Nothing here blocks.**
8. **Archive** on a clean closing gate and `fix.sh task` reporting every file complete. Move the whole
   `docs/<n>-<name>/` directory into `docs/implemented/`, and commit the move where the conventions commit at
   all. A manual check still open in `review/findings.md` never blocks this.
9. **What the conventions run over finished work.** Every affected module's conventions say what happens once a
   change is complete — a measurement, a documentation pass. Follow the conventions index to wherever they say
   it, and run that list in its order. An entry listed by several affected modules runs once. Each states its
   own commit behaviour.

   **Every entry is handed the archived `bug.md`, never a `fix.md`.** `bug.md` sits directly in the directory,
   so an entry that locates the work by walking up from the file it was given finds the fix's own directory. A
   per-module `fix.md` sits one level deeper and would send that entry into the module's subdirectory. Where an
   entry names its argument after a plan, `bug.md` is what takes that place.

### The Refactor Round

**One sub-agent, once, over everything the fix changed** — the refactor agent the module's conventions name for a
finished body of work, spawned the way they say. **Where they name a model but no agent**, use the repository's
own agent for a finished diff on that model; where they name neither, the session's model and that same agent.

| It gets                                   | So that                                                     |
|-------------------------------------------|-------------------------------------------------------------|
| the diff from the **Baseline:** commit    | it sees what the steps add up to, not what any one did      |
| `bug.md` and every `fix.md`, as its brief | a shape contradicting the fix's aim is refused and reported |
| the module's conventions, by name         | it applies this repository's priorities                     |

**It never touches the `red` step's test.** That test is the proof the bug is fixed, and a refactor pass that
rewrites it removes the evidence. Say so in the prompt.

**The diff is one module's, so a fix spanning two gets one pass each**, under that module's own conventions —
never one pass across two stacks, which is a diff no single set of refactoring conventions describes.

**Run the suite again once the agent returns**, before the phase continues. This pass is the last edit the fix
receives and the only one no step's own run covered.

## Version Control

Whether this run commits at all, and how, is the conventions' **Version Control** rules. A repository has one
history however many modules it has, so expect them at the level that binds all of them.

**Missing or silent means no commits.** Never invent a commit policy.

**Several module agents commit into that one history at once.** That is a constraint on the policy, not something
this skill resolves. Follow whatever it says about scoping a commit and about a concurrent one, and report a
refusal it does not cover rather than improvising a retry.

## Report

What the finished fix tells the user is [`report-format.md`](report-format.md), beside this file.
