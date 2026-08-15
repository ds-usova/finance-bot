---
description: Fix a bug that already exists, across one module or several. Reproduces it, writes a fix file with a stabilize/red/green step for each module, stops for approval, then applies them — one sub-agent per module, concurrently — logging every approach that failed and why.
argument-hint: [ a bug report, a failing test, a stack trace, or a findings entry ]
---

# Fix Bug

Something the repository already does is wrong. Three kinds of step put it right, and no others.

| Kind        | What it does                                                                                               |
|-------------|------------------------------------------------------------------------------------------------------------|
| `stabilize` | moves whatever the fix needs to exist first — a signature, an interface, a contract between two services |
| `red`       | one test that reproduces the bug and fails on its symptom                                                  |
| `green`     | production code, until that test passes and the suite stays green                                          |

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

## Phase 0 — Resume, or Reproduce and Baseline

**First, look for a run to resume.** A directory under `docs/` holding a `bug.md` is an interrupted run, and
`fix.sh task <that directory>` says whether anything in it is open.

**It is resumed only where it is the same bug.** Compare its `# Bug:` line and its `## What happens` against
what was asked. A different symptom means the argument is a new bug, and the interrupted one is named in the
report rather than continued. Where the two might be the same, ask.

**A resumed run reads everything before it acts** — `bug.md`, every `fix.md`, and every `## Attempts` entry — so
that what has already been ruled out is not tried again. Then it goes to Phase 3.

**An unanswered Open Question stops the resume here, not in Phase 3.** An agent that blocked wrote one into its
own file, and `fix.sh validate` refuses that file until it is answered. Ask it now, the way Phase 2 asks, and
write the answer in.

A resumed run keeps the original `**Baseline:**`, and the gates below do not apply to what the run itself
produced. Expect all three of these, and none of them is a reason to stop:

| On disk                                    | Because                                             |
|--------------------------------------------|-----------------------------------------------------|
| a committed `red` test that fails          | that is the step working                            |
| tests disabled by a `stabilize` step       | the `red` step named in `disables:` has not run yet |
| uncommitted edits under the step in flight | the run was stopped inside it                       |

**The step to resume is the first unticked one**, which `fix.sh status` names. The `**In flight:**` line says
what was being tried, and it is read for that alone. **It never decides what to revert.** It is written by hand
around an operation that commits, so it goes stale exactly when a step lands, and reverting on its word would
undo work that is finished.

**Revert what is uncommitted under that step, and start it again from its own beginning.** Uncommitted work
anywhere else, and anything red no step accounts for, stops the resume and is reported.

**The revert is this level's, and it happens before any agent is spawned**, while no file has an owner.

**Otherwise, reproduce the bug before anything else.** Run what the report describes — the test, the request,
the command.

- **There is something to run, and it fails every time**: record exactly what it produced. That output is what
  `reproduces:` is written against, and what the fix is measured by. **Run it twice before believing "every
  time".** One run cannot tell the two branches below apart, and which branch this is governs every gate that
  follows.
- **There is nothing to run yet**, because the symptom is only visible through a test nobody has written — the
  common case for a defect in code the suite reaches but no test covers. **Write that test now, as a probe**,
  under the probe rules in Phase 1. Its failure is the reproduction, its output is what gets quoted, and it is
  reverted with every other probe. Phase 3 writes it again as the `red` step, from the file rather than from
  memory. **This is not the `red` step arriving early**: nothing is committed, and the fix is still approved
  before a source file is kept.
- **It fails sometimes**: run it enough times to see it fail twice, and record both numbers — failures and
  runs. That pair is the rate, and it governs the rule below.
- **It does not reproduce here, and could**: stop and say so, with what was run and what happened instead. Ask
  for the missing condition. Never write a fix for a bug nobody has seen fail. A `red` step for a symptom that
  was guessed at will pass, and the run will call that a fix.
- **It cannot reproduce here at all** — it needs production data, a load level, or an environment this machine
  does not have. **Stop, and say what would be needed.** This skill fixes what it can first make fail, and a
  fix with no reproduction is not one it can verify or even validate. Report the diagnosis, and put the
  reproduction where a person can run it: a **Manual test** in a findings file, or a task to build the
  environment. Whether to fix it blind is the user's call, and it is not this skill's run.

### An Intermittent Bug

**A single green run proves nothing about a bug that fails one run in ten.** Where Phase 0 recorded a rate, both
gates are repeated instead of run once, and `reproduces:` carries the rate as `<failures> in <runs>`.

- **The `red` step runs until it has failed twice**, or until it has run three times the runs Phase 0 needed,
  whichever comes first. Failing twice is the step passing its own gate. Reaching the run count without two
  failures refuses it: what Phase 0 saw is not what the test reproduces.
- **The `green` step runs three times whatever the `red` step needed**, and passes every time.

**Both counts go in the fix file, and Phase 2 asks whether they are enough**, as a numbered Open Question. They
are a floor derived from one measurement, not a confidence bound, and a rare bug against an expensive suite is
a cost only the user can accept. Where they answer with a number, that number binds instead.

**A rate nobody could establish is a bug that does not reproduce**, and it takes the branch above.

**Then the affected modules are clean**, whatever else the tree carries. Uncommitted work under an affected
module: name the files and stop. Uncommitted work elsewhere is left alone.

Then run the full build and the entire test suite of every affected module, using each module's own commands.

- **Green**: record the commit and the total and skipped counts it was measured at. Those figures are what every
  later guardrail compares against.
- **Red only where the bug already has a failing test**: that is the baseline, and the failing test is named.
  The `red` step still exists. It names that test in `test-files:` and in `runs:`, and its work is to make the
  test assert the reported symptom rather than whatever it asserts today.
- **Anything else red**: stop, change nothing, report the failures. Fixing them is not this fix's scope.

**A measurement is not repeated over an unchanged tree.** Where a whole-suite run already answers for the commit
this starts from, its figures are read rather than taken again. The condition is checkable: whoever is about to
run knows what has been written since. This holds at every gate in this skill.

## Phase 1 — Diagnose, and Write the Files

### Making the Bug Observable

**A probe is allowed here, and it is the one thing that may touch a source file before the user approves
anything.** A log line, a counter, a breakpoint condition, a query run by hand, a seam that lets a test see what
a class is doing: a chain of causes cannot always be read out of the source, and the alternative to a probe is a
diagnosis with unverified links.

- **Every probe's edits are reverted before the files are written.** Phase 2 presents a tree nothing has been
  written to.
- **A probe that failed is an attempt**, filed under `diagnosis`.
- **A probe that worked is not lost.** Where the fix needs it again — the seam a `red` step will drive, the
  metric that will prove the symptom is gone — it becomes a `stabilize` step, and the diagnosis says which one.
  Where the fix does not need it, the `## Why it happens` link it established says how it was established.
- **A test written to make the bug fail is a probe like any other**, and it is the commonest one: a symptom no
  existing test can see is observed by writing the test that sees it. Its output is the reproduction, it is
  reverted with the rest, and Phase 3 writes it again as the `red` step.

**A probe is never left in the tree and never committed.** The only thing that survives Phase 1 is a written
file and a step.

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

**Every `fix.md` is owned by exactly one module.** The module agents run concurrently and each writes its own
attempt log, so two of them must never hold the same file open.

**Which modules the fix reaches is a decision, not a reading of the diagnosis.** A chain of causes crossing two
services can usually be cut in either of them, and the two fixes are not equivalent:

| Cut it where                       | When that is right                                                    |
|------------------------------------|-----------------------------------------------------------------------|
| the module whose promise is broken | its own contract or documentation already says what it should do      |
| the module the cause is in         | the promise it breaks is one it makes to everybody, not to one caller |

**Prefer the smallest scope that makes the symptom impossible without contradicting a contract.** A module
behaving exactly as its contract says is not the bug, however clearly the cause passes through it — changing it
is a design change, and it goes to `design-task`. Say which cut was chosen in `## Why it happens`, and name the
one rejected.

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
assertion to the reported symptom, and the run fails as any `red` step must. This is not weakening a test. The
test asserted something the repository was wrong about, and `bug.md` is where that is argued.

**A test whose assertion this fix inverts is named in `## What the fix must not break`**, with what it was
protecting and why that is not lost. A `red` step that rewrites a test nobody discussed is the one edit in this
skill that can silently delete a requirement.

### `shared/fix.md`

**Written only where the bug spans a contract between modules.** It holds `stabilize` steps and nothing else —
the schema, each consuming module's wiring to it, and every call site the change breaks, carried back to
compiling. It is a `fix.md` in every other respect, and its header names every module on the seam:

```
**Affected Modules:** `module-a`, `module-b`
**Bug:** [<the bug>](../bug.md)
**In flight:** <as in any other fix file>
```

**The contract is shared; the behaviour behind it is not.** A schema both modules build from goes here. The
handler that schema declares does not — the module serving it fixes it in its own file, and the consumer's own
`red` step drives its side at its own test boundary.

**A reproduction runs inside one module.** Where the bug only shows with two services really running, it belongs
to the module that owns the entry point, with the counterpart at whatever boundary that module's conventions give
its integration tests: a stub server, a test broker. Where no module can host it, say so. The reproduction
becomes a **Manual test** in `review/findings.md`, and the fix says which module's `red` step comes closest.

**Where the counterpart's stand-in is itself wrong, correcting it is a `stabilize` step in the module that owns
it.** A bug in what two services believe about each other is usually a bug the stand-in shares, so the
reproduction is unwritable until the stand-in tells the truth. That correction is named in the diagnosis, it
lands before the `red` step, and it changes no production behaviour.

**No module's `red` step may depend on another module's `green` step.** Where the diagnosis says one does, the
fix is written the other way round: the module whose `green` step comes first owns the reproduction, and the
second module's part is a `stabilize` step or nothing. `validate` enforces the mechanical half of this — no step
names a step in another file — and the diagnosis owes the rest. A fix that cannot be written this way is one
bug reported as two, and it is put to the user in Phase 2 rather than started.

## Phase 2 — Stop

Present the files and stop. Ask every Open Question in one batch via `AskUserQuestion`, and write each answer
into its file as its `- A:`.

**Do not touch a source file until the user asks for the steps to be applied.** A step whose kind the user
disputes is re-classified in the file first.

**A fix that settles a decision worth recording asks here whether to record it**, as a numbered Open Question
like any other. An answered `yes` is what authorizes the archiving pass to write it down. Most bug fixes settle
none and ask nothing.

## Phase 3 — Apply

**`fix.sh validate <the directory>` exits 0 before the first source file is touched.** One call reads `bug.md`
and every `fix.md` the bug holds. Anything it reports is fixed in the file first.

**The order is the same in every fix: every `stabilize`, then every `red`, then every `green`.** No file declares
it and no step schedules it.

1. **`shared/fix.md`, alone**, where the directory holds one. It is applied by its own `fix-bug-module` agent,
   given every module on the seam, and nothing else starts until it lands. Its exit condition is that every one
   of those modules compiles, passes whatever its conventions name as the check on its layering rule, and still
   has the suite phase 0 measured. A module whose conventions name no such check owes the other two. A blocked
   shared fix stops the run here, with no module agent started.

   **Write it only where the contract change breaks something.** A schema edit that changes a description, an
   example or a comment regenerates identical code and stabilizes nothing, so it belongs in `files:` of the step
   that needs it. A shared file costs a whole sequential run before anything else starts, and it is there for
   call sites that will not compile.
2. **One `fix-bug-module` sub-agent per `fix.md`**, spawned concurrently. Each is given its own file path, its
   module's phase-0 figures, and the conventions its module names. Nothing waits: the shared file landed
   everything that crosses, so the module fixes are independent by construction.

   **Each is also given whatever the shared fix disabled in its module**, as the `disables:` lines say it. An
   agent measuring its module against a baseline taken before the shared fix landed would otherwise find skipped
   tests it cannot account for and cannot look up.
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

### Amending an Approved Fix

**The diagnosis is a hypothesis, and applying the steps is what tests it.** Three things routinely disprove it
mid-run, and none of them is a reason to end the run with a failing test committed:

| What the run found                                             | What changes                                                          |
|----------------------------------------------------------------|-----------------------------------------------------------------------|
| the symptom survives a correct `green` step — a second cause | a new `red` and `green` pair, written into the same file              |
| the cause is in another module                                 | a step is added to that module's file, and the original is struck out |
| a step's kind was wrong                                        | the step is re-classified                                             |

**Only this level amends a file, and only with the user's answer.** An agent that finds one of these returns and
says so. Put it to the user as a numbered Open Question in the file it belongs to, write the answer in, re-run
`fix.sh validate`, and re-spawn the module's agent, which starts at the first unticked step.

**A file is amended only once its own agent has returned.** Module A returning with "the cause is in module B"
almost always arrives while B's agent is still running, and B's agent is writing that file. Wait for it. Where
it is still running and the amendment cannot wait, say so and stop it first.

**A step that turns out to be unnecessary is struck from the checklist**, and its row in the **Steps** table
stays, marked abandoned with one clause of why. Its ID is retired and never reused, so no other step renumbers.

**Striking a step out is not free, and `validate` says why.** Removing a `green` step leaves its `red` step
paired with nothing, and the file will not validate. Either both go, or the reproduction stays and something
fixes it. That refusal is correct: a committed test that reproduces a bug nobody fixed is the one thing this
skill must never leave behind.

### Abandoning a Fix

A fix the user calls off, or one whose diagnosis is disproved with nothing to replace it, does not simply stop.
What the run already committed is behaviour nobody chose.

**This is the other half of Phase 0's resume.** An interrupted directory is picked up or abandoned, and which
one is the user's answer when the diagnosis is what failed. Ask before resuming a fix whose log says the chain
of causes was disproved.

Then, at this level, never inside an agent:

1. **Revert every step that landed**, newest first — the `green` edits, the `red` test, and each `stabilize`
   step, so nothing in `disables:` is left off. A skipped count above the baseline blocks the next fix in that
   module, and a disabled test naming a step that will never run is never noticed again.

   **A revert that conflicts stops here.** Work that landed after a `stabilize` step may be built on the
   signature it moved, and undoing it would break code this fix never touched. Report what conflicts and leave
   the tree as it is: the choice between reverting further and keeping the stabilization is the user's.
2. **Run every affected module's suite** and confirm the baseline's figures are back.
3. **Keep the directory**, with every attempt log intact, and say in the report that the fix was abandoned and
   what the log rules out. That is the whole value the run produced.
4. **The directory is not archived**, and nothing in `docs/implemented/` refers to it.

**Where the conventions commit, the revert is committed too**, on the same terms as the steps were.

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
   formatting gate. **Where one of those commands runs the suite itself, it is item 2**, not a second run of it.
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
7. **Archive** on a clean closing gate and `fix.sh task` reporting every file complete. Move the whole
   `docs/<n>-<name>/` directory into `docs/implemented/`, and commit the move where the conventions commit at
   all. A manual check still open in `review/findings.md` never blocks this.
8. **What the conventions run over finished work.** Every affected module's conventions say what happens once a
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
