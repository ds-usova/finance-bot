---
description: Restructure code that already exists without changing what it does. Reads the code, writes a rework file with the edits at code level, stops for approval, then applies them — one agent per module, concurrently — against the suite that is already green.
argument-hint: [ a findings entry, a file or class, a description of what to change, or the path of an existing rework.md ]
---

# Rework

Change the shape of code the repository already has, never what it does. The suite that is already green is the
safety net.

## The Five Kinds of Step

- `inline` reshapes or relocates inside classes that already exist
- `extract` moves responsibility to another class, new or already there
- `tests` restructures test code and touches no production file
- `pin` adds, tightens or drops a check or a setting
- `stabilize` moves a signature and carries every call site it breaks to compiling

What each edits, runs and owes is [`applying-a-step.md`](applying-a-step.md); its lines are
[`step-format.md`](step-format.md). Both sit beside this file.

**Moved code needs no red leg.** **A `stabilize` step is the precondition of whatever works against the signature
it moved.**

## When Not To Use It

- **Anything a caller could not ask for before** — a feature, a field, a changed outcome, however small the diff.
  That is `design-task`, then `plan-task`.
- **A bug** — `fix-bug`.
- **Cleanup inside a plan still being implemented** — its own refactor pass owns that diff. A row the plan's
  finished `review/findings.md` left behind is this skill's input, not that pass's.

## Input Resolution

The argument is a row from a task's `review/findings.md`, a file or class name, or a description. Each is a
starting point; the scope comes from reading the code. **A path to an existing `rework.md` resumes it** under
[`resuming.md`](../../templates/resuming.md), which replaces Phase 0 and Phase 1.

Read the repository-wide conventions and `<module>/docs/conventions.md` for every module the change reaches.
They answer the build and test commands, the layering rule and what checks it, the diagram format, how a test is
disabled, what runs before a commit, the commit policy, and **what this repository says about refactoring** — its
priorities, where extracted code goes, what it will not have touched. All of it binds a step.

## Phase 0 — Baseline

**The affected modules are clean before anything is measured.** Uncommitted work under one: name the files and
stop. Uncommitted work elsewhere is left alone.

Run the full build and the entire suite of every affected module with its own commands. A whole-suite run that
already answers for this commit is read, not repeated; that holds at every gate in this skill.

- **Green**: record the commit.
- **Anything red**: stop, change nothing, report the failures. Fixing them is not this rework's scope.

Nothing is written before this passes.

## Phase 1 — Write the Files

A rework owns `docs/<n>-<name>/`: `rework.md`, and one steps file per agent where it reaches more than one
module. What each holds is [`the-files.md`](the-files.md). `rework.sh` (`scripts/rework/` under the plugin
root, README beside it) reads, ticks and validates them.

Run `rework.sh validate --file <each file>` until it exits 0 before presenting anything.

## Phase 2 — Stop

Present the files and stop. Ask every Open Question in one batch via `AskUserQuestion`, and write each answer
into the file as its `- A:`.

**Do not touch a source file until the user asks for the steps to be applied.** A step whose kind the user
disputes is re-classified in the file first.

**A rework that settles a decision worth recording asks here whether to record it**, as a numbered Open Question
like any other. An answered `yes` is what authorizes the archiving pass to write it down. Most reworks settle
none and ask nothing.

## Phase 3 — Apply the Steps

**`rework.sh validate` exits 0 on every steps file before the first source file is touched**, again after any
answer or re-classification written in Phase 2.

1. **`shared/steps.md` first, alone**, where there is one, by its own `rework-module` agent given every module
   on the seam. Its exit condition: every module on the seam compiles, passes its layering check, and its suite
   stands where phase 0 left it apart from exactly what its `disables:` turned off. A blocked shared file stops
   the run there.
2. **One `rework-module` agent per steps file, concurrently**, spawned and waited for as
   [`templates/sub-agents.md`](../../templates/sub-agents.md) says, each handed its file's path, its module, its
   baseline figures, what the shared file disabled in its module, and `rework.md`. It applies its steps in ID
   order and returns finished or blocked. A blocked agent's question is written into its file; answer it there
   and spawn the agent again.

**A step reaches a sub-agent as `rework.sh show <ID> --file <steps>`**, never as a prompt retelling it.

**Whether anything is committed is the conventions' Version Control rules.** A repository silent on it gets no
commits anywhere in this skill. Where they do commit, the step's own green run is the guardrail the commit
follows, and the commit is provisional: the closing full run proves the whole.

### What Is Never Done

- A test is never deleted or weakened to make a step green. A `stabilize` step may disable one, under the rule
  [`applying-a-step.md`](applying-a-step.md) gives for it.
- A defect found along the way is reported, never fixed. It is a new rework or a new task.
- Nothing outside the steps is improved because it was nearby. A step never reaches past its own boundary.

## Phase 4 — Finish

1. **Full build and full suite of every affected module, green**, with nothing left in any `disables:` still
   off. Anything red or still disabled names the step that left it, and the directory is not archived. An
   invariant from **What must stay true** that could not be kept, and a step abandoned, are reported here.
2. **A refactor round per module** — see below. Each module's suite runs over its result.
3. **Write `review/findings.md`** into the rework's directory, in the shape
   [`findings.md`](../../templates/findings.md) gives: **Critical**, **Bug**, and **Manual test** where the
   change needs a person to look. Where the rework touched one module, the section's opening line names it
   instead of the module-first rule. **A rework files no refactoring candidates**; something worth doing later
   goes in the report, and the user decides whether it becomes a rework. A rework with nothing open still gets
   the file.
4. **Close the row this rework came from.** Where `Source:` names a findings file and a row, set the row's
   `Status`: `done · <this rework's number>`, or leave it `open` with one clause naming what remains. Re-emit the
   count line. A row set to `done` leaves `docs/backlog.md` in the same edit
   ([`backlog.md`](../../templates/backlog.md)); one left `open` keeps its backlog row. Nothing here blocks.
5. **Archive** once the closing gate is clean and `rework.sh status` reports every steps file ticked — a manual
   check open in `review/findings.md` never blocks: move `docs/<n>-<name>/` into `docs/implemented/`, and commit
   the move where the conventions commit at all.
6. **What the conventions run over finished work.** Every affected module's conventions list what happens once a
   change is complete — a coverage guardrail, a formatting gate, a measurement, a documentation pass. Run that
   list in its order, passing each entry the archived `rework.md`. An entry listed by several modules runs once,
   and a gate item 1 already ran over the same tree is not run again.

### The Refactor Round

**One `tdd-refactor-phase` agent per affected module, over that module's diff**, on the model the module's
conventions name for the refactor pass — the session's model where they name none. Never one pass across two
modules: that is a diff no single set of refactoring conventions describes. Each gets its module's diff from the
**Baseline:** commit, `rework.md` as the brief a tidier shape must not contradict, and the module's conventions
by name.

## Report

What the finished rework tells the user is [`report-format.md`](report-format.md), beside this file.
