---
description: Settle the design of a change before any plan exists — objective, solution, diagrams, and every judgment call the change requires, recorded as answered decisions. Runs the grill-design subagent, then puts only the genuinely open questions in front of the user.
argument-hint: [ description of the feature or task to design ]
---

# Design Task

Settle **what** the change does and **what it does when things go wrong**. Record every judgment call it makes as
an answered decision.

This skill produces one file per task and stops. It writes no checklist items, no test scenarios, and no step IDs.

**It works one level above the code.** What the change adds at the module's edges — what calls it, what it calls,
what it stores — and how it behaves at each of them. Package structure, layering and classes are the plan's, and
none of them appear here.

## 1. Create the Design File

A task owns a directory under the repository-root `docs/`. Create it as `docs/<number>-<task-name>/` and write the
design inside it as `design.md` — `docs/7-create-expense/design.md`. Whatever else the task accumulates joins it
there. The directory carries the number and the task name; the files do not repeat them.

> **Numbering rule:** `<number>` is one more than the highest already in use, scanning the directory names
> `<number>-*` in **both** `docs/` and `docs/implemented/`. The number and the task name are the change's, not
> this file's.

> **Archiving rule:** active work lives in `docs/`, completed work in `docs/implemented/`. A design is never
> archived here: this skill leaves the directory in `docs/`.

### One Subject per Task

A request that adds more than one subject — a new store *and* a new consumer *and* a new prompt — is one task per
subject, each with its own directory and design, numbered in dependency order. Every task is then one grill, one
plan and one delivery. The first design names the sequence in its **Objective**; a later one cites an earlier
one's `D` and `F` by task number, the way it cites an implemented design's, and lists it in **Context**.

Split **before** writing, not after the grill: a design that reaches a second `####` section on a subject its
**Objective** did not name has already crossed the line. The measure is subjects, not lines — a plan grows with
the former, and a design that is mostly diagrams may run long. `design.sh validate` prints the counts so the size
is in front of the session on every run.

The seam between two tasks is a stored table, a flag, or a message shape the earlier one ships. Where the split
leaves nothing user-visible until the last task lands, every task ships behind the same flag, and the design says
so.

## 2. Read Module Conventions

After determining the **Affected Modules**, read `<module>/docs/conventions.md` for every affected module, and the
repo-root `docs/conventions.md` if it exists. The conventions give the stack, the diagram format, and the file
locations the **Proposed Solution** has to be written in terms of.

If a module has no conventions file, record a `must-decide` decision asking the user to run `init-conventions`.
Never silently guess a module's conventions.

## 3. Read What Already Exists

Before writing anything, read the closest existing feature end to end — its domain types, its usecase, its
adapters, its migration — and the conventions that govern them. Name it in **Context**; every later section is
allowed to say "as `X` does".

The same holds for a contract a **library generates** rather than the code declaring — a tool or endpoint schema
derived from a signature, a serializer's wire form, a generated client. What reaches the wire is the generator's
reading of the annotated declaration, not the declaration. Read the generator itself before the design fixes the
shape, decompiling it from the dependency if the source is not at hand.

## 4. Design Structure

The file MUST contain these sections, in this order.

### Affected Modules

A single line at the very top, immediately after the title:

```
**Affected Modules:** `module-a`, `module-b`
```

Only the top-level modules whose code, config, or migrations change. One module is still listed.

Where the list holds more than one, the design owes one fact about the boundary between them:

- **Which artifacts are shared** — an API schema, a message schema, anything outside both modules that both read
  at build time. Name each in the **Proposed Solution** where it is described, along with the modules that read
  it. No module owns one: a shared artifact is implemented on its own, before either module's work.

A design whose modules wait on each other for anything *else* has not found the boundary — say so, or move the
seam.

### Objective

What needs to be achieved, and why it matters to whoever asked. A short paragraph.

### Context

What already exists that this change builds on or mirrors. It is a reading list, not an argument. The grill reads
it to find ground truth, and every "same as X" elsewhere in the file resolves against it.

A table — `What exists` | `Where` | `What this change does with it` — one row per thing, one line each. `Where` is
a link. A row that needs a paragraph is carrying a fact the **Proposed Solution** acts on, and that section owns
it.

### Proposed Solution

What the change adds at the surfaces a person can see: what crosses the module's boundary, what it stores, and
how it behaves.

- A database change includes the migration content in the module's migration format.
- An API contract change includes the endpoint and schema changes.
- **Name responsibilities, not classes.** "The read side answers a page of expenses" is this file's; which class
  holds it, in which package, is the plan's.

**Order: the proposal, then the diagrams, then the details.** Details before diagrams make a reader scan for the
picture, and a reader who has to hunt stops reading.

- **What the change adds** — the API surface, the file layout, the shape of a response. Short.
- **Diagrams** — the section below.
- **Details** — one `####` per module and concern, holding only what a box cannot: a field, a signature, an
  invariant, an exception-to-status mapping, the SQL, a build setting.

**There is no closing list of files touched.** Every file the change reaches is already a box in a diagram or a
row in a table. A file that would appear *only* in such a list is the real finding: a config edit, a lint rule, or
a document this change invalidates has no row of its own yet. Give it one.

**The diagram owns what happens and in what order; a table owns only what fits inside a box.** The flow is drawn,
never written. What a box cannot carry goes in a table beneath it.

| Instead of                                                             | Write                                         |
|------------------------------------------------------------------------|-----------------------------------------------|
| "`FooController` calls `ListFooPort`, implemented by `ListFooUseCase`" | nothing — the plan owns every class         |
| a port/use-case/command/answer table                                   | nothing — the plan owns every signature     |
| "the amount is validated before anything is stored"                    | the invariant, in that field's row of a table |
| "the controller answers 400 when the filter is out of bounds"          | an exception/status/cause table               |

Sentence discipline, on top of the repository's documentation conventions: one claim per sentence, under 25
words. A sentence joining two clauses with a dash, a semicolon, or a second "and" is two sentences. Reach for a
paragraph only where a fact needs a reason a reader would otherwise get wrong, and hold it to two sentences.

#### Diagrams

Include diagrams whenever the change introduces new behaviour or a new flow; a one-line stub change needs none.

**What to write them in comes from the module's conventions file** — its **Diagram Format** entry names the
language, the fenced block's language tag, and any preamble a diagram needs. Where a module names none, use
PlantUML with the bundled C4-PlantUML standard library: fenced ` ```plantuml ` blocks, and `!include
<C4/C4_Container>` for a C2. Angle brackets, no `.puml` extension: that resolves against PlantUML's own bundled
stdlib, needing neither a network fetch nor a relative path. Where a renderer's PlantUML predates the bundled
stdlib, fall back to the raw URL for the same file
(`https://raw.githubusercontent.com/plantuml-stdlib/C4-PlantUML/master/C4_Container.puml`).
See `example-design.md`, beside this file, for working syntax.

**No class appears in any of them.** Here a box is a responsibility, a module, or a system.

What each diagram must **show**:

- **Flow diagram** — always. The flow from the entry point, through the change's responsibilities, to whatever it
  calls or stores, showing every alternative branch: a validation failure, a not-found case, an outbound call
  erroring. Every branch a **Decisions** entry settles appears. A straight-line happy path means the failure modes
  were never designed, and the red phase's unhappy-path tests will not exist either.

  Name each box for what it does, not for the class that will do it. "Validate the period", not
  `PeriodValidator`.

  Whether that is a sequence diagram (`alt`/`else`/`end` fragments) or an activity diagram is decided by the
  repository's own diagram conventions — read them and pick. A flow carrying both several participants and real
  branching is two diagrams, not one overloaded one.
- **Container diagram (C4 level 2)** — always. It is the design's structural picture, and the only one: the
  component diagram that draws classes belongs to the plan.

  Every module in **Affected Modules** as a `Container(...)`, plus what each talks to *for this change* — the
  caller, the store, the external system. Draw what crosses, and label it with what it carries: the call, the
  message, the shared table.

  **Where the list holds more than one module, that crossing is the contract between them**, and it is the one
  thing no module's own plan can draw. Where it holds one, the diagram still answers what the module reaches
  outside itself, which is where every failure mode in **Decisions** comes from.

  No system-context diagram (C4 level 1). What systems exist is a property of the repository, not of one change.

### Acceptance Scenarios

What the change does, as behaviour a person can agree or disagree with. One block per entry point, each scenario
numbered `A1`, `A2`, … in this format:

```
- **A1:** [what the scenario is, one line]
  - Given: [the state before]
  - When: [what the user or caller does]
  - Then: [what they get back, and what changed]

- **A2:** [the next one]
  - Given: …
```

**The three lines are nested under the scenario, and a blank line separates one scenario from the next.** Flat
bullets render as one undifferentiated list, where a reader cannot see a scenario begin or end.

**One per branch of the flow diagram**, happy path and every failure alike. A branch drawn but never accepted is
a behaviour nobody agreed to; a scenario with no branch is a flow the diagram is missing.

Numbers are assigned once and never reused. Every red-phase step in the plan cites the scenarios it covers, so a
scenario no step names is a visible gap.

**These are behaviour, never mechanics.** No class, no test class, no layer. "Then: the response is 400 with
`PERIOD_INVALID`" is a scenario; "then `ListExpensesUseCase` throws" is a plan step.

### Decisions

Every judgment call the change requires, one entry each, in this exact format:

```
- **D1:** [the question, one line]
  - Answer: [what the change does]
  - Basis: assumed — [the evidence in the repository] | decided — [what the user chose, and when] | deferred —
    [what is out of scope, and what would bring it back] | must-decide — [what the repository does not say]

- **D2:** [the next question]
  - Answer: …
```

**`Answer` and `Basis` are nested under their entry, and a blank line separates one entry from the next.** Flat
bullets render as one undifferentiated list, where a reader cannot see an entry begin or end.

Numbered `D1`, `D2`, … assigned once and never renumbered: an entry that is answered, withdrawn, or reversed keeps
its number, so anything citing it stays valid for the life of the change.

**Only two bases live here: `decided` and `must-decide`.** This section is what the user reads. It holds the calls
they made and the ones still waiting for them, and nothing else.

An `assumed` or `deferred` question is a **Design Findings** row instead. It was still asked and still answered —
it just needs no reader. `design.sh validate` refuses a `D` entry carrying either basis.

**The four bases — `assumed`, `decided`, `deferred`, `must-decide` — and what each obliges are in
[`example-design.md`](example-design.md), beside the worked entries that use
them.** Read them there before writing the first entry. `must-decide` is the only one that leaves `Answer:`
empty, and it is what `settled` counts.

**Answer against the repository before asking.** Ask only what the repository genuinely cannot answer, and say in
`Basis:` precisely what it does not say — so the user answers a question rather than picks from a menu.

The design is **settled** when no entry carries `Basis: must-decide`.

`design.sh` checks the result: missing or out-of-order sections, duplicate IDs, an entry outside the Decisions
section, an entry whose basis is `assumed` or `deferred`, a missing or repeated `Answer:`/`Basis:`, an unrecognized
basis, a basis with nothing after it, a `must-decide` carrying an answer, any other basis carrying none, and a
Design Findings section the grill never touched. `design.sh settled` answers the separate question — whether anything is still open. The script ships with
these instructions at `scripts/design/design.sh` — under `${CLAUDE_PLUGIN_ROOT}` when installed as a plugin, under
`.claude/` in a plain checkout.

Run `validate` before invoking the grill, and both it and `settled` again before handing the design over.

### Design Findings

Every question the change answered that needs no reader — the `assumed` and the `deferred` — plus one
`Grilled (<date>)` line per grill naming what it examined and found clear. Leave a placeholder while writing the
rest of the file; the grill runs in the next step and this section is written from its report.

One table, one row per question:

| #  | Question | Answer | Evidence |
|----|----------|--------|----------|

- **Very short, all three columns.** A question is a clause, an answer is a clause, and evidence is a link.
- **Evidence is a file** — the class, the migration, the conventions page, the ADR. Never an argument. A row with
  no file to point at is a `must-decide`, not a row.
- **A `deferred` row's answer says what happens instead**, and its evidence is what would bring it back.
- **`F1`, `F2`, … on the same terms as a `D`:** assigned once, never renumbered, never reused. The body cites a
  row the way it cites an entry, and a row that is answered or withdrawn keeps its number.

The `F` sequence is the design's own. A plan's **Review Findings** numbers its own `F1` upward, in its own file,
and the two never meet.

A question that needs a paragraph was not settled. It is a `must-decide` in **Decisions**, and the user answers
it.

See `example-design.md`, beside this file, for a complete worked example of every section above.

## 5. Invoke the Grill Subagent

Once every section above is written, spawn a grill against the design file. Use the model the module conventions'
**Sub-Agent Models** section names for deciding work; without such a section, the default model.

**Which grill depends on what the change touches**, read from each affected module's conventions:

| The module serves          | Spawn            |
|----------------------------|------------------|
| an API, a store, a message | `grill-design`   |
| a user interface           | `grill-frontend` |

**A change spanning both earns both, spawned as one wave and read together**
([`templates/sub-agents.md`](../../templates/sub-agents.md)). The two ask disjoint questions: a
design run only past `grill-design` comes back clean on authorization and idempotency while nothing has asked what
its screen does with an empty list or a name too long to fit. Where both reports raise one thing, it is written
once.

Never grill the design in this context instead — the agent must judge the file as written, not the reasoning that
produced it, and this session holds that reasoning.

**A design going past a grill a second time goes back to the same agent**, with `SendMessage` to the `agentId`
its first run answered with, saying what changed since. It keeps everything it read, so it judges the new half
instead of re-deriving the old one. Spawn a fresh agent only for the first pass, for a grill of a different kind,
or when the first one is no longer reachable.

### Landing the Report

**The grill writes nothing.** It reports, and this session decides where each finding goes. Every finding lands in
exactly one of three places, and never in two:

| The finding                                    | Lands as                                                          |
|------------------------------------------------|---------------------------------------------------------------------|
| changes what the design says gets built        | an edit to the body, plus whichever row or entry its basis calls for |
| the grill marked it `assumed` or `deferred`    | one row in the **Design Findings** table                            |
| the grill marked it `must-decide`              | a `D` entry, which step 6 puts to the user                          |

**The basis decides the home, and the grill already assigned it.** This session re-homes a finding only by
changing its basis — a `must-decide` the repository turns out to answer becomes a row, and an `assumed` whose
evidence does not hold becomes an entry.

**An answer the body already carries is still a row.** The grill cannot see whether the solution section three
pages up already says what it just derived, and the row is what stops the next grill deriving it again.

Assign the `D` and `F` numbers here, each past the highest already in its own sequence. A finding challenging an
existing entry or row becomes a *new* one citing it; neither is ever rewritten, except to correct a claim a
finding proved false.

Assign the `D` numbers here, past the highest already in the file. A finding challenging an existing entry becomes
a *new* entry citing it; an existing entry is never rewritten, except to correct a claim a finding proved false.

Then run `design.sh validate` and fix what it reports.

## 6. Put the Open Questions to the User — in One Batch

Read the file's **Decisions** section back after the grill has run and act on it:

- **Try every `must-decide` against the repository once more** before it reaches the user. The grill works in a
  fresh context and does not know what this session has already read. An entry the code answers stops being an
  entry: it becomes a **Design Findings** row with its evidence, and the user never sees it.
- **Ask the rest in a single round**, via `AskUserQuestion` — every remaining `must-decide` in one batch, each with
  the options that are actually defensible and a recommendation first. One question is one entry. Where there are
  more entries than the tool takes in one call, the rest go in a second call in the same turn — never two entries
  folded into one question, since one answer would then settle two calls.
- **Write the answers back into the file** as `Basis: decided — [choice] (user, <date>)` with `Answer:` filled in.
  The chat answer is not the record; the file is. Anything the user's answer invalidates elsewhere in the file — a
  sequence diagram branch, a paragraph of the solution — is corrected in the same edit.

**An answer that adds a subject sends the design back through step 5 before step 7.** Picking between the options
offered needs no second grill. Answering with something the design did not contain — another migration, another
table, a second concern folded in — leaves a half nobody has judged. Send it back to the same grill (step 5).

A widened design also invalidates entries written before it. Re-read the ones the new subject touches.

## 7. Hand Over

- Present the design file.
- **Report what the grill added and what step 6 answered from the repository** — the `D` numbers and a clause each.
- **Run `design.sh validate` and `design.sh settled`** and report what they say.
- List any entry still `must-decide`, and say that the design is unfinished while any remains.
- **Stop.** Do not plan, write code, create other files, or run build commands.

**The design file is the whole handoff, and this session ends with it.** This session holds what the file
deliberately leaves out — a rejected alternative, a question the grill raised and the repository answered, a shape
considered and dropped. Whoever works from the design next must work from the file alone, or they inherit context
nobody else can see. Starting cold is also the format's own test: a design a fresh session cannot work from was
underspecified.

Say so when handing over, so the user knows the stop is the design's, not an unfinished job.
