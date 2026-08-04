---
description: Translate a settled design file into a step-by-step implementation plan before starting to code. Use when starting a new complex feature, refactoring, or when the user explicitly asks for a plan.
argument-hint: [ design file path, or a description of the feature to plan ]
---

# Plan Task

When the user asks you to plan a task, create a step-by-step implementation plan in a file before starting your work.

This skill is **mechanical translation**: a settled design becomes checklist items, test scenarios and a dependency
graph. It decides nothing about what the change does. What it does about a failure, a duplicate request, or a
missing constraint was settled by `design-task`, and anything this skill discovers that is *not* settled goes back
there — never into this plan as a new question.

> **Architecture Contract:** the design file states it and places every class in a layer — a dependency-free
> domain, an application layer of usecases on ports, adapters implementing the outbound ports and driving the
> inbound ones. Steps inherit that placement; a step that would land a class in a different layer than the design
> puts it in is a defect in one of the two, and it is resolved in the design. If a module does not follow this
> architecture, the phase structure below does not apply.

## 1. Require a Settled Design

Every plan is written from a design file — `docs/<n>-<task-name>/design.md`, produced by `design-task`. Read it
in
full before anything else. It carries the **Objective**, the **Proposed Solution** with the real file names, the
diagrams, and the **Decisions** this plan's steps have to encode.

**Two gates, both hard:**

- **No design file for this task** — stop and say so. Do not write the plan and do not reconstruct the design
  inline; a plan that invents its own design is exactly the back-and-forth the two-stage split removes. Point the
  user at `design-task`.
- **`design.sh settled` exits non-zero** — stop and repeat what it printed. Those entries decide what the steps
  are; planning around them writes items that the answer will invalidate. The script ships with the `design-task`
  skill at `scripts/design/design.sh` — under `${CLAUDE_PLUGIN_ROOT}` when installed as a plugin, under `.claude/`
  in a plain checkout.

A design gap found *while* planning — a case the **Decisions** section does not cover — is amended in the design
file (a new `D` entry, answered against the repository or escalated to the user), not absorbed into the plan.

## 2. Create a Plan File

A task owns a directory under the repository-root `docs/`, and its design and plan are the files in it — one
directory whatever the task touches, one module or several.

The design already created `docs/<n>-<task-name>/design.md`. Write the plan beside it, as
`docs/<n>-<task-name>/plan.md` — `docs/1-add-auth/plan.md`.

> **Naming rule:** the directory carries the number and the task name; the files do not repeat them. A task
> directory holds `design.md` and `plan.md`, the same way `docs/conventions/` holds `testing.md` rather than
> `conventions-testing.md`.

> **Archiving rule:** Once every checklist item in the **entire** plan file is ticked (`[x]`), move **the task's
> whole directory** from `docs/` into `docs/implemented/`. Active (in-progress) work lives in `docs/`; completed
> work lives in `docs/implemented/<n>-<task-name>/`. Moving the directory keeps the pair together and keeps every
> link between them working, since neither file's position relative to the other changes.
> Never archive a plan just because one section is complete; archive it only when there are no unchecked `- [ ]` items
> anywhere in the file.

## 3. Read Module Conventions

After determining the **Affected Modules**, read `<module>/docs/conventions.md` for **every** affected module before
generating the plan's layer sections. Also read the repo-root `docs/conventions.md` if it exists — it holds
conventions shared by all modules; a module's own file overrides/extends it.

The conventions file tells the skill the module's tech stack and test tooling, its layer → test-type mapping,
naming conventions, file locations, and inbound adapter types.

If a module has no conventions file: use generic defaults, and add an entry under **Open Questions / Blockers** in
the generated plan asking the user to create one from the template at `.claude/templates/conventions-template.md`.
Never fail and never silently guess module conventions.

## 4. Plan Structure

The plan file MUST contain the following sections. Headings below that say "reference" or "Step Format" are
instructions for writing those sections, not sections to reproduce in the plan.

### Header

Two lines at the very top of the plan, immediately after the title:

```
**Affected Modules:** `module-a`, `module-b`
**Design:** [<task name>](design.md)
```

**Affected Modules** lists only the top-level modules whose code, config, or migrations change as part of this plan
(e.g. a monorepo service directory) — the same list the design file carries. If the task touches only one module,
list that single module; do not omit the property.

**Design** links the design file this plan translates. The objective, the solution, the file names and the diagrams
live there and are **not** repeated here: one fact, one owner. The plan's own content starts at the step map.

The link is a bare sibling filename and survives archiving: the two share a directory, and `implement-plan`
moves that directory whole.

### Step-by-Step Implementation Map (To-Do List)

A checklist of actionable, sequential steps required to complete the task. Use Markdown checkboxes for this list.

The map is organized into **four major groups**, each a `### <Group>` heading directly under this section, in this
fixed order — use only the groups the task actually needs:

1. **Stabilization** — every pre-TDD artifact and prep step: contract-defining artifacts (API schema, CLI
   interface, message schema — whatever the module's conventions file defines), database changes,
   interface/signature sync, configuration, and shared test infrastructure. Always first; nothing in Red Phase can
   start before it.
2. **Red Phase** — RED-phase TDD steps across all three test layers. Tests compile and are expected to fail at
   runtime; no production implementation happens here.
3. **Green Phase** — GREEN-phase TDD steps across all three test layers, implemented against Red Phase's tests,
   unit and integration before system.
4. **Post-Implementation Steps** — steps that only make sense once the feature is fully implemented and green
   (e.g. updating manual `.http` request files). Always last.

Within each group, its sections appear as `#### <Section>` headings, in the fixed order listed below for that
group — use only the sections that apply:

**Every checklist item carries an ID**, written immediately after the checkbox and separated from the rest by
` · `. The ID names the item everywhere else it comes up — `after:` dependencies, blocker records, sub-agent
prompts, and step reports — so an item stays addressable when its wording changes:

| Prefix | Items                                | Prefix | Items                                 |
|--------|--------------------------------------|--------|---------------------------------------|
| `ST`   | Stabilization                        | `GU`   | TDD Unit Green Phase                  |
| `RU`   | TDD Unit Red Phase                   | `GI`   | TDD Integration Green Phase           |
| `RI`   | TDD Integration Red Phase            | `GS`   | TDD System Test Green Phase           |
| `RS`   | TDD System Test Red Phase            | `P`    | Post-Implementation Steps             |

Numbering restarts at `01` per prefix and follows the order the items are listed. An ID is never reused or
renumbered once the plan is written — a dropped step leaves a gap.

`plan.sh validate` checks the result: duplicate IDs, items with no ID, `after:` naming an ID nothing defines,
dependency cycles, a `given:`/`when:`/`then:` left as a placeholder, an `update:` bullet naming a test method that
exists nowhere in the repository, and — once the review has run — a finding missing its `Resolution:`, or a
`mechanical` one whose `Action:` was never written. Run it before handing the plan over, and again after applying
findings. The script ships with these instructions at `scripts/plan/plan.sh` — under `${CLAUDE_PLUGIN_ROOT}` when
installed as a plugin, under `.claude/` in a plain checkout.

#### Stabilization

- **API Contract** — API schema and path changes, in the module's schema format and location (see conventions
  file); first within this group when contract changes are involved
- **Database** — migrations, schema changes, in the module's migration format (see conventions file). Contract
  artifacts — the API schema and migrations — are created entirely in these two sections: red-phase tests must fail
  on assertions, never on a missing table or schema constraint, and TDD step agents never create or edit contract
  artifacts (a gap found later is a blocker back to the plan)
- **Interface-First / Build Stabilization** — update interfaces/signatures first, add temporary stubs for new
  methods, add/update configuration, add or extend shared test infrastructure the upcoming Red Phase steps will
  need, and resolve build errors (compile/type-check per the module's stack) before implementing full logic.
  Always present and always last within this group. Its own checklist items group under the following labeled
  sub-groups (bold labels, not headings), in this order — include only the sub-groups the task actually needs, omit
  one entirely rather than leaving it empty:

  **Interface & Signature Sync**

    - sync all affected API/interface contracts and method signatures,
    - for **new** methods/fields: generate temporary stub implementations — each stub body MUST contain a short
      inline comment describing what the method is supposed to do (implementation intent). Use this when a
      documentation comment alone does not capture the implementation detail (see the worked example in
      `.claude/templates/example-plan.md`).
    - for **existing** methods whose signature changes (e.g., return type): keep all existing logic intact, add a
      `TODO` comment at the insertion point describing what needs to be implemented there, and add the minimal
      return/change needed to get back to build-green. Do NOT replace or stub out existing functionality.
    - update immediate call sites and get the project back to build-green state before full logic implementation.

  **Configuration**

    - add or update any configuration this task's design requires — e.g. an outbound HTTP client's base URL, a
      schedule expression, a connection-pool setting, a new environment variable/property and its default. Config
      belongs here, not inside a step agent's scope, for the same reason contract artifacts do: a red-phase test
      should fail on an assertion, never on a missing property a step agent had to invent on the fly.

  **Shared Test Infrastructure**

    - add or extend any test fixture, builder, or base-class capability more than one upcoming Red Phase step will
      need. Every red-phase step agent is scoped to add "no shared fixtures beyond what this step needs" precisely
      so shared test infrastructure has exactly one owner and gets written once — list it here instead of leaving
      two parallel steps to duplicate it or block on each other waiting for it to exist.

  Close with:

    - after stabilization, confirm the module's architecture-enforcement test (per conventions file, if the module
      has one) still passes.

#### Red Phase

- **TDD Unit Red Phase** — write meaningful unit tests that build for classes the module's conventions file maps to
  the unit layer; tests are expected to fail at this stage (stubs return null/defaults); no production
  implementation yet
- **TDD Integration Red Phase** — write meaningful integration tests that build for adapter classes on **both
  sides of the hexagon**, per the conventions file's integration layer mapping: outbound adapters against real
  infrastructure as defined there (e.g. a containerized database, an HTTP stub server), and inbound adapters (e.g.
  REST controllers) through the framework's slice-test mechanism with their inbound ports mocked; same RED-phase
  rules apply — no production implementation yet
- **TDD System Test Red Phase** — write a thin set of end-to-end system tests that build, entering through an
  inbound port the way production does: via HTTP using the module's API-level test client (per conventions file),
  or, when there is no HTTP layer, by making the framework fire the entry point itself (e.g. a test-configured
  schedule for a cron trigger, a message published to the test broker for a listener) — never by calling the
  inbound-port method directly; scope is what only the fully wired stack can prove — per entry point, one happy path and a
  representative error path originating below the inbound adapter (validation matrices belong to the inbound
  adapter's integration step); tests are expected to fail at runtime until the full stack is implemented — no
  production implementation yet

#### Green Phase

- **TDD Unit Green Phase** — implement the production logic for each class from `TDD Unit Red Phase`, one class per
  step, until its unit tests pass
- **TDD Integration Green Phase** — implement the adapter logic for each class from `TDD Integration Red Phase`, one
  class per step, until its integration tests pass; for an inbound-adapter step this implements only the adapter
  itself (binding, mapping, validation wiring, error mapping) — the usecase behind it is a unit-phase target
- **TDD System Test Green Phase** — run each system test class from `TDD System Test Red Phase` and confirm all
  tests pass; fix implementation bugs in any layer (never the tests) until the full test class is green — inbound
  adapters covered by an Integration Phase step are already implemented there; an entry point without one (e.g. a
  framework-fired trigger) is wired here

#### Post-Implementation Steps

- **Manual Request Files** — manual request files (e.g. `.http`), only if the module's conventions file lists this
  as a convention

Sections here come from the module's conventions file — whatever it lists as work that only makes sense once the
feature is green, in the order it lists them. The framework prescribes none of them beyond the rule that they run
last. A module that names a post-implementation artifact requiring the user's approval says so there, and the
approval is a question under [Open Questions / Blockers](#open-questions--blockers) like any other.

### Step Formats — reference

The exact shape of every Red Phase and Green Phase item, the **Test Layer Mapping** that decides which phase a
step belongs to, and the scenario-authoring rules that bind them all, are
[`.claude/templates/step-formats.md`](../templates/step-formats.md). Read it before writing or reviewing a step;
`plan.sh validate` checks what it can of the result.
[`.claude/templates/example-plan.md`](../templates/example-plan.md) is a complete worked plan in those formats.

### Open Questions / Blockers

**Scope:** this section holds questions about *executing* the plan — a blocker foreseen in a step, a tool or
credential that may be missing, an approval a conventions file requires. Questions about what the change should
**do** belong in the design file's **Decisions** section and are settled before this plan exists; a design question
appearing here means step 1's gate was skipped.

Generate placeholders for the user's answers beneath each open question, for example:

- **Q1:** [Your question here]?
- A:

- **Q2:** [Next question]?
- A:

**Number every question** (`Q1`, `Q2`, …) so it can be referenced in conversation, in a commit, or from another
document. Numbers are assigned once and never renumbered: a question that is answered or withdrawn keeps its
number, and a new one takes the next unused value, so a reference stays valid for the life of the plan.

**An artifact the module's conventions put under the user's approval is asked here, never assumed.** Where a
conventions file says a post-implementation artifact is written only with the user's consent, the plan asks for it
as a numbered question — what would be written, and what holds the same fact if it is not — and a `yes` becomes the
item in **Post-Implementation Steps** that authorizes it. Planning is the moment to ask: the plan is where the
decision is being made, so the candidate list is honest, and asking later means reconstructing intent from finished
code.

### Review Findings

Populated by the `review-plan` subagent invoked in the next step — leave this section as a placeholder while writing
the rest of the plan. Each finding uses this exact format:

```
- **F1:** [what's wrong or missing, with file/class/scenario reference]
- Resolution: mechanical | decision
- Action:
```

Findings are numbered on the same terms as the questions above — `F1`, `F2`, … assigned once, never renumbered,
and continuing past the highest existing number on a re-review.

`Resolution:` is the reviewer's classification of **who** resolves the finding — `mechanical` when a written rule
or the code already determines the fix, `decision` when it is a genuine choice. The reviewer assigns it; the
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

Merge its findings into the plan's **Review Findings** section, replacing the placeholder. Only then
proceed to **6. Resolve the Mechanical Findings** below.

## 6. Resolve the Mechanical Findings

Apply every finding the reviewer marked `Resolution: mechanical` to the plan, then write under it what changed:

```
- Action: applied — [what changed in the plan, in a clause]
```

A finding marked `Resolution: decision` keeps that classification — this step never regrades the reviewer's
verdict. It still gets **attempted against the repository**: the sibling service's code, the module conventions,
an existing ADR, the schema. Answer it when the evidence is there and write the evidence into `Action:`
(`resolved — the connector's own `ExpenseIntent` imposes no `UPDATE` rule`). Leave `Action:` empty for the user
only when the answer is a product, operational, or business rule that exists nowhere yet — and add a line
`- Missing: [what the repository does not say]` beneath it, so the user answers a question rather than picking
from a menu.

How to apply them:

- **Batch by affected step, not by finding.** Two findings often rewrite the same checklist item; applied one at a
  time they produce an incoherent step. Group the findings by the item each one touches and rewrite that item once,
  satisfying all of them together.
- **Compress the finding as you apply it.** Once the plan text embodies the fix, the finding's problem statement
  describes a defect that is no longer there, and it sits between the reader and the findings that still need
  them. In the same edit, cut it to one sentence — keeping the `- **F<n>:**` / `- Resolution:` / `- Action:`
  shape, so `plan.sh validate` and the readiness gate are unaffected:

  ```
  - **F1:** RU07's `toIntents` matrix omitted `OPERATION_DELETE` and the generated `UNRECOGNIZED` constant.
  - Resolution: mechanical
  - Action: applied — added both scenarios.
  ```

  Keep the ID and its number, one sentence of what was wrong, and the `Action:` line. Drop the reasoning, the
  file-and-line citations, and the instruction of what to change — the plan now carries all three. A finding that
  was **not** applied keeps its full text: an empty `Action:`, a `- Missing:` line, an `- Escalated:` line. The
  section's length then tracks the work left rather than the work done.
- **Stay inside the finding.** Apply what the finding says to change and nothing adjacent that looks improvable —
  an unreviewed edit riding along with a reviewed one is the thing this step must not smuggle in.
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
- **Ask what is still open, in one batch, via `AskUserQuestion`** — every unanswered Open Question, every
  `decision` finding, and anything escalated, each with the options that are actually defensible and a
  recommendation first. Do not print them and wait for the file to come back edited: the user answering in the
  conversation is faster, and it puts the answer in your hands as text.
- **Write each answer into the plan file verbatim**, as the `- A:` under its question or the `- Action:` under
  its finding, and correct anything elsewhere in the plan that the answer invalidates in the same edit. The
  conversation is not the record; the file is, and the readiness gate reads the file. An answer that prescribes
  content is quoted, not summarized — the implementing step is given those words.
- **A question the user leaves unanswered stays in the file, unanswered.** Do not guess one to fill the gate,
  and do not ask again in a second round; the file is where an answer can arrive later, in the user's own time.
- **Stop here. Do not implement anything.** Do not write code, create files, or run commands.
- Wait for the user to explicitly ask you to start implementation before doing any work.
- Tell the user that implementation will not start while any Open Question lacks an `A:` or any Review Finding
  lacks an `Action:` — the `implement-plan` skill's plan-readiness gate checks exactly this, so resolving them now
  saves a blocked run later.
