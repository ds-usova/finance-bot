---
description: Translate a settled design file into a step-by-step implementation plan before starting to code. Use when starting a new complex feature, refactoring, or when the user explicitly asks for a plan.
argument-hint: [ design file path, or a description of the feature to plan ]
---

# Plan Task

When the user asks you to plan a task, write a step-by-step implementation plan to a file before starting work.

**This skill decides structure and sequencing. It never decides behaviour.** Which classes exist, which layer each
sits in, which ports they talk through, which test covers what, and in what order it all gets built — all of that is
settled here. What the change *does*, including what it does about a failure, a duplicate request or a missing
constraint, was settled by `design-task`. Anything this skill finds unsettled goes back there, never into this plan
as a new question.

> **Layering is the module's, not this framework's.** Its conventions name the layers, the packages they map to, and
> which may depend on which. Read them and apply what they say.
>
> **Placing a class is this file's decision.** The component diagram below is where that placement is checked against
> the module's dependency rule.
>
> A module whose conventions name no layers is planned the same way. The diagram then groups classes the way that
> module really organizes code, and no dependency rule is enforced beyond the ones its conventions state. What the
> phase structure below depends on is the module's **test types**, which its testing conventions map.

## 1. Require a Settled Design

Every plan is written from a design file — `docs/<n>-<task-name>/design.md`, produced by `design-task`. Read it in
full before anything else. It carries the **Objective**, the **Proposed Solution**, the flow the change follows, the
**Acceptance Scenarios** a person signed off, and the calls this plan's steps have to encode. Those calls are in
two places: **Decisions** holds the ones the user made, and **Design Findings** holds the ones the repository
answered. Both bind the plan.

**Two gates, both hard:**

- **No design file for this task** — stop and say so. Do not write the plan and do not reconstruct the design
  inline. Point the user at `design-task`.
- **`design.sh settled` exits non-zero** — stop and repeat what it printed. Those entries decide what the steps are.
  The script ships with the `design-task` skill at `scripts/design/design.sh` — under `${CLAUDE_PLUGIN_ROOT}` when
  installed as a plugin, under `.claude/` in a plain checkout.

A design gap found *while* planning — a case neither **Decisions** nor **Design Findings** covers — is amended in
the design file: a **Design Findings** row where the repository answers it, a new `D` entry escalated to the user
where nothing does. It is never absorbed into the plan.

## 2. Create a Plan File per Module

The design already created its task directory and `design.md` inside it. Write **one plan per module** the design's
**Affected Modules** names:

| Design's Affected Modules       | Where the plan goes                               |
|---------------------------------|---------------------------------------------------|
| one module                      | `docs/<n>-<task-name>/plan.md`                    |
| several                         | `docs/<n>-<task-name>/<module>/plan.md`, one each |
| several, with a shared artifact | one more: `docs/<n>-<task-name>/shared/plan.md`   |

> **Naming rule:** the directory carries the name; the file does not repeat it. A task directory holds `design.md`
> and `plan.md`, or `design.md` and `module-a/plan.md` and `module-b/plan.md`.

**The design is never split; the plans always are.** A plan's unit is what gets implemented and verified — one
module, one toolchain, one set of conventions. Each plan is implemented on its own, with its own verification
between waves.

**A plan is self-contained.** Its own step IDs, its own dependency graph, its own Open Questions. IDs restart in each
file, so the same ID can exist in two plans and means nothing without its path.

**Affected Modules** in the header names the one module that plan implements.

### The Shared Plan

Everything that crosses between modules goes in `shared/plan.md`, and nothing crosses any other way. It is
implemented first, alone, before any module plan starts, so a module plan never waits on another and never names
one.

**What belongs in it**, in three layers:

1. **The artifact** — an API schema, a message schema, any file more than one module's **build** reads, whether it
   generates sources from it or merely validates against it.
2. **Each consuming module's wiring to it** — the generator invocation, the build hookup, the script that produces
   the generated sources.
3. **Every call site the change to it breaks.** Those call sites are stabilized here, because no module plan can
   compile until they are.

**The artifact is shared; the behaviour it describes is not.** A schema both modules generate from goes here. The
endpoint that schema declares does not — the module serving it implements it in its own plan, and the consumer
mocks the call. The test is what breaks while the thing is missing: another module's **build** means shared, a
mocked call means it belongs to whoever implements it.

**Layer 3 gets it compiling, nothing more.** Its items follow the **Interface-First / Build Stabilization** rules
unchanged:

| What the change broke        | What layer 3 does                                |
|------------------------------|--------------------------------------------------|
| a method's signature         | keep its logic, add a `TODO`, return the minimum |
| a method that now must exist | add a stub with an intent comment                |
| a test that cannot compile   | disable it, naming the step that will rework it  |

Making any of it work again is the module plan's job.

**Its header names every module on the seam:**

```
**Affected Modules:** `module-a`, `module-b`
**Design:** [<task name>](../design.md)
```

The plan is finished when **every module in that list** compiles, passes its architecture test, and still has a
green pre-existing suite. The list is where those commands come from, one set per module's build conventions.

**It has a Stabilization group and no other.** Every item ID is an `ST`. It reads the repository-tier conventions
plus each listed module's build section, and needs no layer or test mapping — it runs no test phase.

**Write it only when the design names a shared artifact.** No file means nothing crosses.

> **Archiving rule:** Once every checklist item in the **entire** plan file is ticked (`[x]`), move **the task's
> whole directory** from `docs/` into `docs/implemented/<n>-<task-name>/`. In-progress work lives in `docs/`. Never
> archive a plan because one section is complete; archive it only when no unchecked `- [ ]` item is left anywhere in
> the file.

## 3. Read Module Conventions

After determining the **Affected Modules**, read `<module>/docs/conventions.md` for **every** affected module before
generating the plan's layer sections. Also read the repo-root `docs/conventions.md` if it exists — it holds
conventions shared by all modules, and a module's own file overrides and extends it.

The conventions file tells the skill the module's tech stack and test tooling, how it organizes code, its dependency
rule if it has one, its naming conventions, its file locations, and — the one this skill cannot proceed without —
**which of its parts fall into which test type**.

**A module whose conventions carry no test-type mapping cannot be planned.** Say so and ask for it, rather than
reading a test type off a package name.

If a module has no conventions file at all: use generic defaults, and add an entry under **Open Questions /
Blockers** in the generated plan asking the user to run `init-conventions`, or to fill in the templates at
`templates/conventions/` under the plugin root by hand. Never fail silently and never guess module conventions.

## 4. Plan Structure

The plan file MUST contain the following sections. Headings below that say "reference" or "Step Format" are
instructions for writing those sections, not sections to reproduce in the plan.

### Header

Two lines at the very top of the plan, immediately after the title:

```
**Affected Modules:** `module-a`
**Design:** [<task name>](design.md)
```

**Affected Modules** is read differently by the two kinds of plan, and neither reading is inferred:

| Plan             | The property names                               |
|------------------|--------------------------------------------------|
| a module plan    | the one module it implements                     |
| `shared/plan.md` | every module on the seam, producer and consumers |

Never omit it. The design's own **Affected Modules** is the list of all of them.

**No plan declares an order.** `shared/plan.md` finishes before any module plan starts, and nothing else crosses. So
no plan has anything to wait for, and none says it does.

`after:` is for steps inside one file. It never names a step in another plan — `plan.sh validate` fails on an ID it
cannot find. The one place a plan mentions another is a disabled test's reason, which points at the step that will
rework it. That is a note for a reader, not a dependency.

**Design** links the design file this plan translates. The objective, the behaviour, the schema and the flow live
there and are **not** repeated here. What lives here is the structure that behaviour gets built in.

The link is relative and survives archiving: `design.md` from a single-module plan, `../design.md` from a per-module
or shared one.

### Components

The classes this module gets, and how they connect. One component diagram (C4 level 3), plus a table of what a box
cannot carry.

**What to write it in comes from the module's conventions file** — its **Diagram Format** entry names the language
and any preamble. Where a module names none, use PlantUML with the bundled C4-PlantUML standard library: a fenced
` ```plantuml ` block and `!include <C4/C4_Component>`. Angle brackets, no `.puml` extension. Where a renderer
predates the bundled stdlib, fall back to
`https://raw.githubusercontent.com/plantuml-stdlib/C4-PlantUML/master/C4_Component.puml`.

- **Every new or changed class**, grouped into the boundaries the module's conventions name. Where those conventions
  split a layer by direction — an inbound adapter and an outbound one — the diagram splits it too. Where they name
  no layers, group by whatever the module really organizes code by, and say in a line beneath the diagram what the
  grouping is.
- **Draw the dependency between each pair**, pointing the way the dependency really runs. An arrow the conventions'
  dependency rule forbids is a violation, and this is where it costs a line instead of a rewrite. Where they state
  no such rule, the arrows are still drawn.
- **One diagram per subject, not one per change.** A change touching widgets and their parents draws one each.
  Where no arrow crosses between two groups of boxes, they were never one diagram.
- **Keep it small.** An untouched class is drawn only where an arrow needs it. A class belonging to no subject — a
  filter chain, an exception handler — is left to the table.

The wiring is drawn, never written. Which class calls, implements, or wraps which never appears as a sentence or a
table row. Beneath the diagram, a table carries only what a box cannot: a port signature, a record's fields, an
invariant, an exception-to-status mapping.

**This is the plan's own content, not a copy of anything.** The design named responsibilities; naming the classes
that hold them is this section's work, and the step map below targets exactly the classes drawn here.

### Step-by-Step Implementation Map (To-Do List)

A checklist of actionable, sequential steps required to complete the task, as Markdown checkboxes.

The map is organized into **four major groups**, each a `### <Group>` heading directly under this section, in this
fixed order — use only the groups the task actually needs:

1. **Stabilization** — every pre-TDD artifact and prep step: contract-defining artifacts (API schema, CLI interface,
   message schema — whatever the module's conventions file defines), database changes, interface/signature sync,
   configuration, and shared test infrastructure. Always first; nothing in Red Phase can start before it.
2. **Red Phase** — RED-phase TDD steps across all three test layers. Tests compile and are expected to fail at
   runtime; no production implementation happens here.
3. **Green Phase** — GREEN-phase TDD steps across all three test layers, implemented against Red Phase's tests, unit
   and integration before system.
4. **Post-Implementation Steps** — steps that only make sense once the feature is fully implemented and green (e.g.
   updating manual `.http` request files). Always last.

Within each group, its sections appear as `#### <Section>` headings, in the fixed order listed below for that group
— use only the sections that apply.

**Every checklist item carries an ID**, written immediately after the checkbox and separated from the rest by ` · `.
The ID names the item everywhere else it comes up — `after:` dependencies, blocker records, sub-agent prompts, and
step reports:

| Prefix | Items                     | Prefix | Items                       |
|--------|---------------------------|--------|-----------------------------|
| `ST`   | Stabilization             | `GU`   | TDD Unit Green Phase        |
| `RU`   | TDD Unit Red Phase        | `GI`   | TDD Integration Green Phase |
| `RI`   | TDD Integration Red Phase | `GS`   | TDD System Test Green Phase |
| `RS`   | TDD System Test Red Phase | `P`    | Post-Implementation Steps   |

Numbering restarts at `01` per prefix and follows the order the items are listed. An ID is never reused or
renumbered once the plan is written — a dropped step leaves a gap.

**An ID never leaves those places.** Not a commit message, not a test or display name, not a class, a file or a
comment. Each of those outlives the plan directory, which moves into `docs/implemented/` the moment the work
lands — so an ID written into one stops resolving exactly when a reader meets it. The same holds for a design's
`D`, `F` and `A` entries, an Open Question's `Q`, and a findings file's `R`. Say what the thing does instead. A
`@Disabled` reason is the one exception, since it names the step that owes the rework and clears itself when that
step lands.

**An `update:` bullet is written from the test's body, never from its name.** Open the method, read what it
asserts, and say what changes about those assertions. `plan.sh validate` only checks that the method exists, so
a bullet written off the name passes and reaches a step agent describing work nobody verified. Where the same
change reaches many tests of one class, do not stretch one sentence over a list of names: write a **premise**
bullet — the fact about the change and what follows for a test that meets it — and let the step agent decide
test by test which bodies meet it. The forms are in `step-formats.md`'s **Existing-test updates rule**.

`plan.sh validate` checks the result: duplicate IDs, items with no ID, `after:` naming an ID nothing defines,
dependency cycles, a `given:`/`when:`/`then:` left as a placeholder, an `update:` bullet naming a test method that
exists nowhere in the repository, and — once the review has run — a finding missing its `Resolution:`, or a
`mechanical` one whose `Action:` was never written. Run it before handing the plan over, and again after applying
findings. The script ships with these instructions at `scripts/plan/plan.sh` — under `${CLAUDE_PLUGIN_ROOT}` when
installed as a plugin, under `.claude/` in a plain checkout.

#### Stabilization

Its sections — **API Contract**, **Database**, **Interface-First / Build Stabilization** and its three labelled
sub-groups, and the closing architecture-test item — are
[`templates/stabilization-group.md`](../../templates/stabilization-group.md). How each item is carried out
is [`stabilizing.md`](../../templates/stabilizing.md) beside it.

#### Red Phase

The three types are separated by **what is real and what is faked**, and the module's conventions map its own parts
onto them.

- **TDD Unit Red Phase** — write meaningful unit tests that build for classes the conventions map to the unit type,
  with every dependency mocked; tests are expected to fail at this stage (stubs return null/defaults); no production
  implementation yet
- **TDD Integration Red Phase** — write meaningful integration tests that build for classes the conventions map to
  the integration type, each against the **real** thing it talks to. Two variants: a class that calls infrastructure
  is driven directly, against a real database, store or stub server as the conventions define it; a class the
  framework calls is reached through the framework, with what it delegates to mocked. Same RED-phase rules — no
  production implementation yet
- **TDD System Test Red Phase** — write a thin set of end-to-end system tests that build, against the fully wired
  application with **nothing mocked**, entered the way production enters it: an HTTP request via the module's
  API-level test client, or the framework firing the entry point itself — a test-configured schedule, a message
  published to the test broker — never a direct method call. Scope is what only the whole application can prove: per
  entry point, one happy path and a representative error path raised from deep in the stack. Validation matrices
  belong to the entry point's integration step. Tests are expected to fail at runtime until the full stack is
  implemented — no production implementation yet

#### Green Phase

- **TDD Unit Green Phase** — implement the production logic for each class from `TDD Unit Red Phase`, one class per
  step, until its unit tests pass
- **TDD Integration Green Phase** — implement each class from `TDD Integration Red Phase`, one class per step, until
  its integration tests pass; for an entry-point step that is the class itself — binding, mapping, validation
  wiring, error mapping — never what it delegates to, which has its own step
- **TDD System Test Green Phase** — run each system test class from `TDD System Test Red Phase` and confirm all
  tests pass; fix implementation bugs anywhere in the stack (never the tests) until the full test class is green. An
  entry point with an integration step is already implemented there; one without — a framework-fired trigger — is
  wired here

#### Post-Implementation Steps

- **Manual Request Files** — manual request files (e.g. `.http`), only if the module's conventions file lists this
  as a convention

Sections here come from the module's conventions — whatever they say a finished change earns, filtered to what this
plan can produce, in the order they list it. Follow the conventions index to wherever that is stated. The framework
prescribes none of them beyond the rule that they run last.

Where the conventions put an artifact under the user's approval, that approval is a question under
[Open Questions / Blockers](#open-questions--blockers) like any other, and only an answered yes becomes an item
here.

### Step Formats — reference

The exact shape of every Red Phase and Green Phase item, **The Three Test Types** that decide which phase a step
belongs to, and the scenario-authoring rules that bind them all, are
[`templates/step-formats.md`](../../templates/step-formats.md). Read it before writing or reviewing a step;
`plan.sh validate` checks what it can of the result.
[`templates/example-plan.md`](../../templates/example-plan.md) is a complete worked plan in those formats.

### Open Questions / Blockers

**Scope:** this section holds questions about *executing* the plan — a blocker foreseen in a step, a tool or
credential that may be missing, an approval a conventions file requires. Questions about what the change should
**do** belong in the design file's **Decisions** section and are settled before this plan exists; a design question
appearing here means step 1's gate was skipped.

Generate placeholders for the user's answers beneath each open question, nested under it, for example:

- **Q1:** [Your question here]?
  - A:

- **Q2:** [Next question]?
  - A:

The answer is nested and a blank line separates the questions: flat bullets render as one undifferentiated list.

**Number every question** (`Q1`, `Q2`, …) so it can be referenced in conversation, in a commit, or from another
document. Numbers are assigned once and never renumbered: a question that is answered or withdrawn keeps its number,
and a new one takes the next unused value.

**An artifact the module's conventions put under the user's approval is asked here, never assumed.** Where a
conventions file says a post-implementation artifact is written only with the user's consent, the plan asks for it
as a numbered question — what would be written, and what holds the same fact if it is not — and a `yes` becomes the
item in **Post-Implementation Steps** that authorizes it.

### Review Findings

Populated by the `review-plan` subagent invoked in the next step — leave this section as a placeholder while writing
the rest of the plan. Each finding uses this exact format:

```
- **F1:** [what's wrong or missing, with file/class/scenario reference]
  - Resolution: mechanical | decision
  - Action:

- **F2:** [the next one]
  - Resolution: …
```

**`Resolution` and `Action` are nested under their finding, and a blank line separates one finding from the next.**
The same holds for `Escalated:` where a finding carries one.

Findings are numbered on the same terms as the questions above — `F1`, `F2`, … assigned once, never renumbered, and
continuing past the highest existing number on a re-review.

`Resolution:` is the reviewer's classification of **who** resolves the finding — `mechanical` when a written rule or
the code already determines the fix, `decision` when it is a genuine choice. The reviewer assigns it; the
orchestrator acts on it in step 6. It is deliberately not the planner's call: a planner grading the review of its
own plan is how a real objection gets reclassified into something that can be quietly applied.

If the review has nothing to report, this section still contains a single "No issues found" statement (or
equivalent) — its presence must be consistent across every plan, clean or not.

## 5. Invoke the Review Subagent

Once every section in **4. Plan Structure** is written, spawn the **`review-plan` agent** against the just-created
plan file, on the model the module conventions' **Sub-Agent Models** section names for deciding work (reviewing a
plan is exactly that); without such a section, the default model.

Never review the plan in this context instead — the reviewer must verify the plan's claims against the repository
unbiased by the reasoning that produced them, and this session holds that reasoning.

**The reviewer writes nothing.** It reports, and this session writes its findings into the plan's **Review
Findings** section, replacing the placeholder. Assign the `F` numbers here, past the highest already in the section
— this session is the only one that knows them all. Carry each finding's `Resolution:` across unchanged: regrading
the reviewer's verdict is what step 6 forbids, and it is no more allowed while transcribing it.

A finding the plan already answers is written down anyway, with that answer as its `Action:`. The record of it being
raised is what stops the next review raising it again.

Only then proceed to **6. Resolve the Mechanical Findings** below.

## 6. Resolve the Mechanical Findings

Apply every finding the reviewer marked `Resolution: mechanical` to the plan, then write under it what changed:

```
  - Action: applied — [what changed in the plan, in a clause]
```

A finding marked `Resolution: decision` keeps that classification — this step never regrades the reviewer's verdict.
It still gets **attempted against the repository**: the sibling service's code, the module conventions, an existing
ADR, the schema. Answer it when the evidence is there and write the evidence into `Action:`
(`resolved — the sibling service's own `WidgetPort` imposes no `UPDATE` rule`). Leave `Action:` empty for the user only
when the answer is a product, operational, or business rule that exists nowhere yet — and add a
`- Missing: [what the repository does not say]` line beside it, nested under the finding like the rest, so the user
answers a question rather than picking from a menu.

**An `Action:` prescribing a mechanism this session did not exercise ends `— unverified`.** Reading that a thing
exists is evidence that it exists, and nothing more; whether it behaves as the fix assumes is a separate claim, and
the two reach a step agent in one voice unless the line separates them. The agent that consumes an `unverified`
`Action:` tests it before building on it.

How to apply them:

- **Batch by affected step, not by finding.** Two findings often rewrite the same checklist item; applied one at a
  time they produce an incoherent step. Group the findings by the item each one touches and rewrite that item once,
  satisfying all of them together.
- **Compress the finding as you apply it.** In the same edit, cut it to one sentence — keeping the `- **F<n>:**` /
  `- Resolution:` / `- Action:` shape, so `plan.sh validate` and the readiness gate are unaffected:

  ```
  - **F1:** RU01's scenarios omitted the unknown `parentId` and the duplicate name under one parent.
  - Resolution: mechanical
  - Action: applied — added both scenarios.
  ```

  Keep the ID and its number, one sentence of what was wrong, and the `Action:` line. Drop the reasoning, the
  file-and-line citations, and the instruction of what to change — the plan now carries all three. A finding that
  was **not** applied keeps its full text: an empty `Action:`, a `- Missing:` line, an `- Escalated:` line.
- **Stay inside the finding.** Apply what the finding says to change and nothing adjacent that looks improvable.
- **Escalate rather than guess.** If a `mechanical` finding does not say clearly enough what to change, or applying
  it would cross one of the boundaries `review-plan` lists (adding or removing a checklist item, changing a step's
  target class, touching a contract artifact, contradicting an answered Open Question), do not apply it: leave
  `Action:` empty, add a line `- Escalated: [why]` beneath it, and let the user decide.
- **Re-run `plan.sh validate`** afterwards. Rewriting steps in bulk is exactly when an ID or an `after:` reference
  breaks.

## 7. Review Only — Do NOT Implement

- Present the generated plan file to the user.
- **Report what step 6 applied** — the findings' IDs and a clause each, in one short list. An automatic edit the
  user cannot see is an automatic edit the user cannot catch.
- **Ask what is still open, in one batch, via `AskUserQuestion`** — every unanswered Open Question, every `decision`
  finding, and anything escalated, each with the options that are actually defensible and a recommendation first. Do
  not print them and wait for the file to come back edited.
- **Write each answer into the plan file verbatim**, as the `- A:` under its question or the `- Action:` under its
  finding, and correct anything elsewhere in the plan that the answer invalidates in the same edit. The
  conversation is not the record; the file is, and the readiness gate reads the file. An answer that prescribes
  content is quoted, not summarized — the implementing step is given those words.
- **A question the user leaves unanswered stays in the file, unanswered.** Do not guess one to fill the gate, and do
  not ask again in a second round.
- **Stop here. Do not implement anything.** Do not write code, create files, or run commands.
- Wait for the user to explicitly ask you to start implementation before doing any work.
- Tell the user that implementation will not start while any Open Question lacks an `A:` or any Review Finding lacks
  an `Action:` — the `implement-plan` skill's plan-readiness gate checks exactly this, so resolving them now saves a
  blocked run later.
