---
description: Restructure code that already exists, or change what it already does. Reads the code, writes a rework file with the edits at code level, stops for approval, then applies them one at a time against the suite that is already green.
argument-hint: [ a findings entry, a file or class, or a description of what to change ]
---

# Rework

Change code the repository already has, against a suite that is already green and is the safety net.

## The Six Kinds of Step

| Kind        | The edit                                                                 | What proves it                                                            |
|-------------|--------------------------------------------------------------------------|---------------------------------------------------------------------------|
| `inline`    | reshape inside classes that already exist                                | the tests it names pass, with no assertion edited                         |
| `extract`   | responsibility moves to another class, new or already there              | `frozen:` bites, stays unedited, stays green; `cover:` proven by mutation |
| `behaviour` | code that already exists is made to do something else                    | its test changes first and fails before any code moves                    |
| `tests`     | test code is restructured, production code is not touched                | the module's suite green, and every scenario in `survives:` found again   |
| `pin`       | a check or a setting is added, tightened, or dropped as no longer needed | the module's suite, and mutation wherever the step added or tightened     |
| `stabilize` | a signature moves, and every call site it breaks is carried to compiling | the module's suite, the architecture check, and what it disabled named    |

**Moved code needs no red leg.**

**A `behaviour` step changes what existing code does. It never serves something no caller could ask for
before**, however small the diff.

**A `stabilize` step is the precondition of whatever works against the signature it moved.**

## When Not To Use It

- **New behaviour** — a feature, an endpoint, a field nobody serves yet. That is `design-task`, then `plan-task`.
- **A bug fix** — write the failing test and fix it.
- **Cleanup inside a plan that is still being implemented** — its own refactor pass owns that diff. A row the
  plan's finished `review/findings.md` left behind is this skill's input, not that pass's.

## Input Resolution

The argument is a row from a task's `review/findings.md`, a file or class name, or a description. Each is a
starting point; the scope comes from reading the code.

Read the repository-wide conventions and `<module>/docs/conventions.md` for every module the change reaches.
They answer the build and test commands, the layering rule and what checks it, the diagram format, how a test is
disabled, what must run before a commit, and the commit policy. They also answer **what this repository says
about refactoring** — its priorities, where extracted code goes, and what it will not have touched. **All three
bind a step**, not only the last. Follow the conventions index to wherever each lives.

## Phase 0 — Baseline

**The affected modules are clean before anything is measured**, whatever else the tree carries. Uncommitted work
under an affected module: name the files and stop. Uncommitted work elsewhere is left alone.

Then run the full build and the entire test suite of every affected module, using each module's own commands.

**A measurement is not repeated over an unchanged tree.** Where a whole-suite run already answers for the commit
this starts from, its figures are read rather than taken again. The condition is checkable: whoever is about to
run knows what has been written since. This holds at every gate in this skill.

- **Green**: record the commit it was measured at.
- **Anything red**: stop, change nothing, report the failures. Fixing them is not this rework's scope.

Nothing is written before this passes.

## Phase 1 — Write the Rework File

A rework owns a directory, numbered and named the way a task is: `docs/<n>-<name>/`, with `rework.md` inside it.
`<n>` is one more than the highest already used, scanning `<number>-*` in both `docs/` and `docs/implemented/`.
The directory carries the number and the name; the file does not repeat them.

**This file is the artifact**, and a fresh session resumes from it. Write it under the repository's
documentation conventions like any other document.

**`Source:` is one line and holds a path, not a relative link.**

**It is written at code level.** A step names the file it edits and the test class that holds it.

```
# Rework: <what changes>

**Affected Modules:** `module-a`
**Source:** <one line — a findings file and the row's number, a file, or the request>
**Baseline:** <the commit the suite was green at>

## The fix

<what the change is — see below>

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

### The fix

**What the change is, in a sentence, before anything about what is wrong.**

Then what that sentence needs to be believed: the mechanism it turns on, or the chain of causes it cuts and
where.

**Where the rework draws diagrams, this section says so and stops.**

### Structure

Two component diagrams, **Now** and **Target**, in the language the module's conventions name, following their
rules for boundaries, layout and marking. Where those rules say how an addition is marked, the Target diagram
marks every class the rework creates that way. A class it deletes is drawn only in Now.

**Draw them only when the rework moves responsibility between classes, creates one, or removes one.** Where none
is drawn, the section is left out rather than left empty.

### Steps

**The table comes first, and it is the whole rework to anyone not applying it.** One row per step: its ID, what
changes in a clause, and what proves it in a clause.

The checklist underneath is for the agent applying a step and for `rework.sh`. Its grammar is
[`step-format.md`](step-format.md), beside this file.

**Write the table from the steps, never the steps from the table.** A row that cannot be written in two clauses
is a step doing two things.

## Phase 2 — Stop

Present the file and stop. Ask every Open Question in one batch via `AskUserQuestion`, and write each answer
into the file as its `- A:`.

**Do not touch a source file until the user asks for the steps to be applied.** A step whose kind the user
disputes is re-classified in the file first.

**A rework that settles a decision worth recording asks here whether to record it**, as a numbered Open Question
like any other. An answered `yes` is what authorizes the archiving pass to write it down; a decision nobody was
asked about is proposed there instead, and never written as a fact. Most reworks settle none and ask nothing.

## Phase 3 — Apply the Steps

**`rework.sh validate` exits 0 before the first source file is touched.** Anything it reports is fixed in the
file first.

**A step handed to a sub-agent is handed as `rework.sh show <ID>`**, never as a prompt retelling it.

**What a step of each kind edits, what it runs, how it mutates, and when it refuses** is
[`applying-a-step.md`](applying-a-step.md), beside this file. A step is applied against it and against
`step-format.md`.

**Then, every kind:** run whatever the conventions require before a commit, `rework.sh tick <ID>`, and commit
the file together with the paths the step named.

**Whether anything is committed at all is the conventions' Version Control rules.** A repository silent on it
gets no commits, here or anywhere else in this skill. Where they do commit, the step's own green run is the
guardrail the commit follows.

**A commit here is provisional.** The closing full run is what proves the whole thing.

### What Is Never Done

- A test is never deleted or weakened to make a step green. A `stabilize` step may disable one, under the rule
  [`applying-a-step.md`](applying-a-step.md) gives for it.
- A defect found along the way is reported, never fixed. It is a new rework or a new task.
- Nothing outside the steps is improved because it was nearby. A step never reaches past its own boundary.

## Phase 4 — Finish

1. **Full build and full suite of every affected module, green**, with nothing left in `disables:` still off.
   Anything red or still disabled names the step that left it, and the directory is not archived. An invariant
   from **What must stay true** that could not be kept, and a step abandoned, are reported here rather than
   filed.
2. Whatever else the module's **build** conventions require of a finished change — a coverage guardrail, a
   formatting gate. What runs *after* a change is finished is item 7, not this one — and where item 7 measures
   the same module over a tree only documentation has moved since, one of the two runs answers for both.
3. **A refactor round over the whole diff**, spawned as a sub-agent — see below.

4. **Write `review/findings.md`** into the rework's own directory, in the shape
   [`findings.md`](../../templates/findings.md) gives. A rework fills **Critical**, **Bug**, and **Manual test**
   where the change needs a person to look. Skip the module-first rule where the rework touched one module: the
   section's opening line names it instead.

   **A rework files no refactoring candidates.** Something worth doing later goes in the report to the user, who
   decides whether it becomes a rework.

   A rework with nothing open still gets the file.
5. **Close the row this rework came from.** Where `Source:` names a findings file and a row, open that file and
   set the row's `Status`: `done · <this rework's number>` where the row is fully closed, or leave it `open`
   with one added clause naming what still remains. Re-emit the opening count line. The report says which row
   was set and to what. **Nothing here blocks.**
6. **Archive** on a clean closing gate and `rework.sh status` reporting every step ticked — a manual check still
   open in `review/findings.md` never blocks: move the whole `docs/<n>-<name>/` directory into
   `docs/implemented/`, and commit the move where the conventions commit at all.
7. **What the conventions run over finished work.** Every affected module's conventions say what happens once a
   change is complete — a measurement, a documentation pass. Follow the conventions index to wherever they say
   it, and run that list in its order, passing each entry the archived `rework.md`. An entry listed by several
   affected modules runs once. Each states its own commit behaviour.

### The Refactor Round

**One `tdd-refactor-phase` agent, once, over everything the rework changed**, on the model the module's
conventions name for the refactor pass — the session's model where they name none. It is handed three things:

| It gets                                | So that                                                        |
|----------------------------------------|---------------------------------------------------------------|
| the diff from the **Baseline:** commit | it sees what the steps add up to, not what any one did         |
| the rework file, as its brief          | a shape contradicting the rework's aim is refused and reported |
| the module's conventions, by name      | it applies this repository's priorities                        |

The suite runs over the result before anything else in this phase.

## Report

What the finished rework tells the user is [`report-format.md`](report-format.md), beside this file.
