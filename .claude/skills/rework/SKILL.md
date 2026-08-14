---
description: Restructure code that already exists, or change what it already does. Reads the code, writes a rework file with the edits at code level, stops for approval, then applies them one at a time against the suite that is already green.
argument-hint: [ a findings entry, a file or class, or a description of what to change ]
---

# Rework

Change code the repository already has. The suite is already green, and that green is the safety net.

## The Six Kinds of Step

| Kind        | The edit                                                                 | What proves it                                                            |
|-------------|--------------------------------------------------------------------------|---------------------------------------------------------------------------|
| `inline`    | reshape inside classes that already exist                                | the tests it names pass, with no assertion edited                         |
| `extract`   | responsibility moves to another class, new or already there              | `frozen:` bites, stays unedited, stays green; `cover:` proven by mutation |
| `behaviour` | code that already exists is made to do something else                    | its test changes first and fails before any code moves                    |
| `tests`     | test code is restructured, production code is not touched                | the module's suite green, and every scenario in `survives:` found again   |
| `pin`       | a check or a setting is added, tightened, or dropped as no longer needed | the module's suite, and mutation wherever the step added or tightened     |
| `stabilize` | a signature moves, and every call site it breaks is carried to compiling | the module's suite, the architecture check, and what it disabled named    |

**Moved code needs no red leg.** A test written after an extraction passes on the first run, and making it fail
first would mean deleting the body just moved.

**A `behaviour` step changes what existing code does. It never serves something no caller could ask for
before**, however small the diff — that is the line against **New behaviour** below.

**A `stabilize` step is the precondition of whatever works against the signature it moved.**

## When Not To Use It

- **New behaviour** — a feature, an endpoint, a field nobody serves yet. That is `design-task`, then `plan-task`.
- **A bug fix** — write the failing test and fix it.
- **Cleanup inside a plan that is still being implemented** — its own refactor pass owns that diff. A row the
  plan's finished `review/findings.md` left behind is this skill's input, not that pass's: the refactor pass may
  not change a signature, cross a package boundary, or add a check, which is most of what such a row asks for.

## Input Resolution

The argument is a row from a task's `review/findings.md`, a file or class name, or a description. Each is a
starting point; the scope comes from reading the code.

Read the repository-wide conventions and `<module>/docs/conventions.md` for every module the change reaches.
They answer the build and test commands, the layering rule and what checks it, the diagram format, how a test is
disabled, what must run before a commit, and the commit policy. They also answer **what this repository says
about refactoring** — its priorities, where extracted code goes, and what it will not have touched. **All three
bind a step**, not only the last: an extraction that ignores where the conventions put extracted code is a
defect whatever else it improves. Follow the conventions index to wherever each lives.

## Phase 0 — Baseline

**The affected modules are clean before anything is measured**, whatever else the tree carries. Uncommitted work
under an affected module: name the files and stop. Uncommitted work elsewhere is somebody else's change in
progress, and it is left alone.

Then run the full build and the entire test suite of every affected module, using each module's own commands.

- **Green**: record the commit it was measured at. Every failure from here on is attributable to the rework.
- **Anything red**: stop, change nothing, report the failures. They predate the rework and fixing them is not
  its scope.

Nothing is written before this passes.

## Phase 1 — Write the Rework File

A rework owns a directory, numbered and named the way a task is: `docs/<n>-<name>/`, with `rework.md` inside it.
`<n>` is one more than the highest already used, scanning `<number>-*` in both `docs/` and `docs/implemented/`.
The directory carries the number and the name; the file does not repeat them.

**This file is the artifact**, and a fresh session resumes from it. Write it under the repository's
documentation conventions like any other document.

**`Source:` is one line and holds a path, not a relative link.** The directory moves when the rework is
archived, so a link written from where the file sat resolves to nothing from where it ends up. A value wrapped
onto a second line is read by neither a person scanning the header nor anything else.

**It is written at code level.** A step names the file it edits and the test class that holds it.

```
# Rework: <what changes>

**Affected Modules:** `module-a`
**Source:** <one line — a findings file and the row's number, a file, or the request>
**Baseline:** <the commit the suite was green at>

## What the code does now

| What | Where | What is wrong with it |
|------|-------|-----------------------|

## Structure

<component diagrams — see below>

## What must stay true

<one line per invariant no test asserts, and how it would be noticed if it broke>

## Steps

| #   | What changes | What proves it |
|-----|--------------|----------------|

<the checklist — see below>

## Open Questions

- **Q1:** …
  - A:
```

### Structure

Two component diagrams, **Now** and **Target**, in the language the module's conventions name, following their
rules for boundaries, layout and marking. Where those rules say how an addition is marked, the Target diagram
marks every class the rework creates that way. A class it deletes is drawn only in Now.

**Draw them only when the rework moves responsibility between classes, creates one, or removes one.** Where none
is drawn, the section is left out rather than left empty.

### Steps

**The table comes first, and it is the whole rework to anyone not applying it.** One row per step: its ID, what
changes in a clause, and what proves it in a clause. A reader who wants the shape of the change reads six rows
and stops.

```
| #   | What changes                                  | What proves it              |
|-----|-----------------------------------------------|-----------------------------|
| R01 | the slot read moves to a persistence catalogue | `ReplicationSlotMonitorTest` |
```

The checklist underneath is for the agent applying a step and for `rework.sh`. Its grammar — the labelled lines,
which kind owes which, what `validate` refuses — is [`step-format.md`](step-format.md), beside this file.

**Write the table from the steps, never the steps from the table.** A row that cannot be written in two clauses
is a step doing two things.

## Phase 2 — Stop

Present the file and stop. Ask every Open Question in one batch via `AskUserQuestion`, and write each answer
into the file as its `- A:`. The conversation is not the record.

**Do not touch a source file until the user asks for the steps to be applied.** A step whose kind the user
disputes is re-classified in the file first.

**A rework that settles a decision worth recording asks here whether to record it**, as a numbered Open Question
like any other. An answered `yes` is what authorizes the archiving pass to write it down; a decision nobody was
asked about is proposed there instead, and never written as a fact. Most reworks settle none and ask nothing.

## Phase 3 — Apply the Steps

**`rework.sh validate` exits 0 before the first source file is touched.** Anything it reports is fixed in the
file first.

**A step handed to a sub-agent is handed as `rework.sh show <ID>`**, never as a prompt retelling it. A retold
step is a second copy of the file that can drift from the first, in the one place no review looks.

| Kind        | Edit                                                                               | Then run                                                                                                  |
|-------------|------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| `inline`    | only what `files:` names; in a test, only a mechanical edit                        | `runs:` — they pass                                                                                       |
| `extract`   | break the body in place first, then move it without rewriting, and wire the caller | `frozen:` goes red, then unedited and green · write `cover:` and **mutate** each · the architecture check |
| `behaviour` | the test first, to assert `then:`                                                  | `runs:` — **it must fail** · then edit `files:` until green                                               |
| `tests`     | move or reshape test code, with no `files:` at all                                 | the module's whole suite · then find every scenario in `survives:` running again                          |
| `pin`       | add, tighten, or drop the check or setting, and nothing else                       | the module's whole suite · then **mutate**, where anything was added or tightened                         |
| `stabilize` | carry each broken call site back to compiling, nothing more                        | the module's whole suite · the architecture check                                                         |

**A whole-suite run in that column is the step's proof, not a safety net.** What a step's own guardrail does not
reach, phase 4 does.

**To mutate is to break the target deliberately and confirm the failure.** Restore it, and run again before the
commit.

- **One mutation per test, and each targets what that test asserts.** A single breakage that reds every test at
  once proves one thing about the class and nothing about the assertions.
- **A `pin` mutates by undoing what it just did**: putting back the dependency or the layer violation its check
  forbids, or restoring the value its setting replaced. `proves:` records that, and the failure it produced. A
  `pin` that **drops** something has nothing to undo — its `proves:` says the suite is green without it, which
  is the whole claim.
- **A run that executed no test is neither a pass nor a failure**, so read the runner's own verdict before
  believing a red or a green. A test that could not compile means the step is waiting on a `stabilize` one.

**Each scenario in `survives:` is found again by name**, and the line then carries the test that runs it. A green
suite says nothing here: the tests that vanished are exactly the ones that cannot fail.

**A mechanical test edit is a call this step renamed or re-shaped** — a constructor argument, an import, a
method name. An assertion, a fixture value, and the removal of a test are not mechanical.

**`frozen:` must bite before it can vouch.** Break the body where it stands today and confirm `frozen:` goes
red; restore, and only then move it. A `frozen:` that passes whatever the extraction did is not a net, and the
step is refused. This asks only whether the tests reach the body, so one breakage answers it — `cover:`'s
mutation asks whether each assertion bites, which is why that one runs per test method.

**`frozen:` is verified against the step's own start.** Where the run commits per step, that start is the last
commit and version control answers it exactly. Where the conventions commit nothing there is no anchor, so copy
each `frozen:` file before editing anything and compare against the copy.

**`extract` runs whatever the module's conventions name as the check on its layering rule.** A run filtered to a
few test classes may not include it, and moving a class between packages is what that check exists to catch.

**A `stabilize` step disables the least it can**, in the form the module's conventions give for a disabled test.

**Where a step refuses:**

- **`inline` needing more than a mechanical test edit** is not `inline`.
- **`extract` whose body cannot move unrewritten** is not `extract`. Whatever the original held across its
  statements — a connection, a transaction, a lock — travels with the body, passed in rather than acquired
  again, and the step stays an `extract`. Where even that is impossible, stop and put it to the user: the step
  has no other kind, since nothing about the behaviour changed and no test can be made to fail first. A resource
  re-acquired inside the new class is the one case `frozen:` cannot catch.
- **`behaviour` whose test passes before the code is touched** does not cover what the step claims.
- **`tests` with a scenario it cannot find running again** dropped it, and it is restored before anything else.
- **`pin` that survives its own mutation** pins nothing. A `pin` that only drops something has no mutation, and
  this does not apply to it.

Each of these reverts the step and puts it back to the user, re-classified or repaired.

**Three things look like refusals and are not:**

- **A step whose run is red for a reason other than its own claim** is waiting on what its `needs:` names. It is
  not finished, it does not commit, and it is not put back to the user either.
- **A `stabilize` that finds a call site its `files:` does not name** widens the line in `rework.md` and says so
  in the report. A broken build cannot wait for another step. It never widens into a behaviour change: the new
  site keeps its logic and takes a `TODO`.
- **A `pin` whose new check reds a file the step does not name** widens the line the same way and reports it.
  The alternative is a step that weakens its own check to fit a boundary written before anyone had run it.

**Then, every kind:** run whatever the conventions require before a commit, `rework.sh tick <ID>`, and commit
the file together with the paths the step named.

**Whether anything is committed at all is the conventions' Version Control rules.** A repository silent on it
gets no commits, here or anywhere else in this skill. Where they do commit, the step's own green run is the
guardrail the commit follows.

**A commit here is provisional.** The closing full run is what proves the whole thing.

### What Is Never Done

- A test is never deleted or weakened to make a step green. A `stabilize` step may disable one, under the rule
  above.
- A defect found along the way is reported, never fixed. It is a new rework or a new task.
- Nothing outside the steps is improved because it was nearby. What the steps add up to is looked at once, by
  the refactor round in phase 4, and never by a step reaching past its own boundary.

## Phase 4 — Finish

1. **Full build and full suite of every affected module, green**, with nothing left in `disables:` still off.
   Anything red or still disabled names the step that left it, and the directory is not archived. An invariant
   from **What must stay true** that could not be kept, and a step abandoned, are reported here rather than
   filed — a rework that did not do what it set out to do is not finished work.
2. Whatever else the module's **build** conventions require of a finished change — a coverage guardrail, a
   formatting gate. What runs *after* a change is finished is item 7, not this one.
3. **A refactor round over the whole diff**, spawned as a sub-agent — see below. It runs before the findings
   file, so what it changes is judged with everything else.

4. **Write `review/findings.md`** into the rework's own directory, in the shape
   [`findings.md`](../../templates/findings.md) gives. A rework fills **Critical**, **Bug**, and **Manual test**
   where the change needs a person to look. Skip the module-first rule where the rework touched one module: the
   section's opening line names it instead.

   **A rework files no refactoring candidates.** That section belongs to a task, which is the first sighting of
   an area and legitimately produces a list. A rework is a closing action on one row of such a list, and giving
   it the same output turns the list into a queue that refills itself — six rounds later nobody can say what is
   left. Something worth doing later goes in the report to the user, who decides whether it becomes a rework.

   A rework with nothing open still gets the file, so a clean one and a missing one never look the same.
5. **Close the row this rework came from.** Where `Source:` names a findings file and a row, open that file and
   set the row's `Status`: `done · <this rework's number>` where the row is fully closed, or leave it `open`
   with one added clause naming what still remains. Re-emit the opening count line. The report says which row
   was set and to what.

   **Nothing here blocks.** A row left `open` on purpose — deferred by the user, or half-closed by design — is
   a true statement about the work, and a gate that refused to archive over it would be answered by marking the
   row done, which is the one thing this item exists to prevent.

6. **Archive** on a clean closing gate and `rework.sh status` reporting every step ticked — a manual check still
   open in `review/findings.md` is what that file is for, and never blocks: move the whole `docs/<n>-<name>/` directory into
   `docs/implemented/`, and commit the move where the conventions commit at all.
7. **What the conventions run over finished work.** Every affected module's conventions say what happens once a
   change is complete — a measurement, a documentation pass. Follow the conventions index to wherever they say
   it, and run that list in its order, passing each entry the archived `rework.md`. An entry listed by several
   affected modules runs once. Each states its own commit behaviour.

### The Refactor Round

A step sees its own boundary and nothing else. Duplication two steps wrote independently, an idiom that drifted
between them, a helper one step added that another reimplemented: none of it is visible from inside a step, and
all of it is visible in the diff they add up to.

**One sub-agent, once, over everything the rework changed** — the refactor agent the module's conventions name
for a finished body of work, spawned the way they say. It is handed three things:

| It gets                                      | So that                                                  |
|----------------------------------------------|----------------------------------------------------------|
| the diff from the **Baseline:** commit       | it sees what the steps add up to, not what any one did   |
| the rework file, as its brief                | a shape contradicting what the rework set out to do is refused, and reported instead |
| the module's conventions, by name            | it applies this repository's priorities                  |

The suite runs over the result before anything else in this phase.

## Report

- **What is left where this rework came from**, in one line: `<that file> — 1 of 4 open (R1)`. It answers "does
  this ever end" every round, for the cost of one read, and a stale line here is visible rather than binding.
- Each step, its kind, and the files it touched.
- **The failure text of every `behaviour` step's red run**, quoted.
- **The mutation results of every step that ran one** — what was broken, and which test or check caught it. A
  target that stayed green under mutation is a finding, not a footnote. A `pin` that only dropped something ran
  none, and says so.
- **Every edit any step made to a test file**, so a mechanical change that was really an assertion change is
  visible, and so is an assertion a `tests` step quietly dropped while the scenario name survived.
- Steps re-classified or abandoned, and why.
- Defects found and not fixed.
- **What the conventions' finished-work list did**, per entry — the pages rewritten from the `docs:` lines, and
  any page a step named that the pass left alone.
