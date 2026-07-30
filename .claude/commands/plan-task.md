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

Every plan is written from a design file — `docs/<n>-design-<task-name>.md`, produced by `design-task`. Read it in
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

All plans live in the repository-root `docs/` folder, regardless of whether the task touches one module or several.

Create a Markdown file in the repository-root `docs/` folder named `<plan-number>-plan-<task-name>.md`. For example,
`1-plan-add-auth.md`.

> **Numbering rule:** `<plan-number>` is the number of the design file this plan is written from, and the task name
> matches it — `7-design-create-expense.md` yields `7-plan-create-expense.md`. The shared number is how the pair is
> known to belong together.

> **Archiving rule:** Once every checklist item in the **entire** plan file is ticked (`[x]`), move **both the plan
> and its design file** from `docs/` into `docs/implemented/`. Active (in-progress) work lives in `docs/`; completed
> work lives in `docs/implemented/`.
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
**Design:** [<task name>](<n>-design-<task-name>.md)
```

**Affected Modules** lists only the top-level modules whose code, config, or migrations change as part of this plan
(e.g. a monorepo service directory) — the same list the design file carries. If the task touches only one module,
list that single module; do not omit the property.

**Design** links the design file this plan translates. The objective, the solution, the file names and the diagrams
live there and are **not** repeated here: one fact, one owner. The plan's own content starts at the step map.

The link is relative and survives archiving, since `implement-plan` moves both files together.

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

### Test Layer Mapping — reference, **not** a section of the plan

This is guidance for deciding which phase a step belongs to. Do **not** write it into the plan file: it restates
what the module's own conventions file already defines, and a copy in every plan is one more place for the two to
drift apart. The plan references the conventions; only the conventions describe the module's layers.

- **Unit** — classes the module's conventions file maps to the unit layer (e.g. domain entities/value objects and
  usecase implementations of inbound ports, as mapped in the module's conventions file). Outbound ports are
  faked/mocked; no real infrastructure, no application-framework context.
- **Integration** — adapter classes on **both sides of the hexagon**, each tested in isolation:
    - *Outbound adapters* (persistence, outbound HTTP clients): the test instantiates/wires only the adapter under
      test and calls **only that adapter's own public methods** directly — never through a usecase, a port
      default, or the full application context. Use real infrastructure as defined in the module's conventions
      file (e.g. a containerized database for persistence adapters, an HTTP stub server for outbound HTTP
      adapters).
    - *Inbound adapters* (e.g. REST controllers): the test boots only the framework's **slice** for the adapter
      under test (per the conventions file's slice-test mechanism) and **mocks the inbound port / usecase beans**
      it delegates to. It enters through the protocol (e.g. HTTP requests via the slice test client), never by
      calling the adapter's methods directly — validation, request binding, and error mapping live in the
      framework machinery, not in the method body. No real infrastructure and no real usecases. This is where the
      endpoint's request-validation matrix, binding/mapping, and status-code contract are tested.
- **System** — the full wired application (all real adapters, real infrastructure), entered through an **inbound
  port** the way production enters it: either via HTTP (using the module's API-level test client per conventions
  file, against the HTTP inbound adapter), or, when there is no HTTP layer, by making the framework fire the entry
  point itself — e.g. a test-configured schedule for a cron trigger, or a message published to the test broker for
  a listener — and awaiting the observable outcome. Never by calling the inbound-port method directly: the trigger
  wiring (the schedule, the queue binding) is production behaviour too, and a direct call would bypass exactly what
  the system test exists to prove. Both forms exercise the same wiring end-to-end — only the entry point differs. System tests are a **thin slice** keeping what only the fully wired application can prove: per entry
  point, one end-to-end happy path, a representative error path originating below the inbound adapter, and any
  cross-cutting wiring concern (bean graph, serialization config, transactions). Field-validation matrices and the
  inbound adapter's own request/response handling belong to the adapter's integration test, not here.

### TDD Unit Red Phase Step Format

Each item in the `TDD Unit Red Phase` section MUST follow this exact format:

```
- [ ] RU<nn> · `<TargetClass>` · test: `<TestClass>` · covers: `method1()`, `method2()`
  - `method1()`:
    - given: [precondition]
      when: [action]
      then: [expected outcome]
    - given: [other precondition]
      when: [action]
      then: [other expected outcome]
  - `method2()`:
    - given: [precondition]
      when: [action]
      then: [expected outcome]
```

- `<TargetClass>` — a class the module's conventions file maps to the unit layer (e.g. a domain entity, value
  object, or domain service, or a usecase class implementing an inbound port) (simple class name)
- `<TestClass>` — the corresponding test class (simple class name)
- `covers:` — comma-separated list of method signatures to implement and test in this step
- Sub-bullets — one `given / when / then` scenario per test case; each block describes one test the sub-agent must
  write;
  the sub-agent derives the method name from the scenario following project naming conventions

Each step represents the **RED phase** of TDD: write meaningful tests that build for the listed methods.
The tests are expected to **fail at runtime** (stubs return null/defaults from the stabilization phase) — this is
intentional.
**No production implementation is done in this section.** Implementation happens in a separate phase after all tests are
written.
Steps are intentionally small and focused — one class, one concern.
Usecase tests fake/mock the outbound ports they depend on — they never touch real infrastructure; that belongs to the
Integration phase.

**Coverage balance rule:** Before listing scenarios, review the existing tests in `<TestClass>` to understand what is
already covered. Only list scenarios that add **new** coverage. Do NOT list scenarios that are already covered by
existing tests — even if they are related to the changed code. The goal is a well-balanced, non-redundant test suite,
not a mechanical one-test-per-plan-bullet mapping. If existing tests already cover a scenario adequately, omit it from
the plan.

**Existing-test updates rule:** The same review cuts the other way: when the change alters behaviour an existing test
already covers — a new field the test's assertions would now omit, or a grown enum/case set that an exhaustive
parameterized test iterates — list the required change as an explicit `update:` sub-bullet under the affected method:

```
- update: `existingTestMethod()` — [what to add or change, e.g. assert the new `status` field]
```

The step sub-agent implements exactly the scenarios and `update:` bullets listed; it never decides on its own which
existing tests to touch. An update missing here is a plan defect, caught by the sub-agent's report or the plan review
— not the sub-agent's call to fix.

**Exclusion — simple delegation**: Do NOT add a class to this section if every method under test is a simple
delegation (e.g., a one-line usecase method that only calls an outbound port with no logic of its own — no
conditionals, no transformations, no error handling). Such trivial pass-through changes belong in the
**Interface-First / Build Stabilization** section instead.

### TDD Integration Red Phase Step Format

Items in this section cover adapters on **both sides of the hexagon** and come in two variants. Each item MUST
follow its variant's exact format.

**Outbound adapter steps** (persistence, outbound HTTP clients — real infrastructure):

```
- [ ] RI<nn> · `<AdapterImplClass>` · test: `<AdapterTestClass>` · covers: `method1()`, `method2()`
  - `method1()`:
    - given: [precondition]
      when: [action]
      then: [expected outcome]
    - given: [other precondition]
      when: [action]
      then: [other expected outcome]
  - `method2()`:
    - given: [precondition]
      when: [action]
      then: [expected outcome]
```

- `<AdapterImplClass>` — the adapter implementation class per the conventions file's integration layer mapping
  (e.g., `WidgetRepositoryAdapter`, `ExternalApiAdapter`)
- `<AdapterTestClass>` — the corresponding integration test class (e.g., `WidgetRepositoryAdapterTest`)
- `covers:` — comma-separated list of adapter method signatures to test in this step
- Sub-bullets — one `given / when / then` scenario per test case; each block describes one test the sub-agent must
  write;
  the sub-agent derives the method name from the scenario following project naming conventions

Each outbound step is the **RED phase** for integration tests: write tests that build and exercise real
infrastructure as defined in the module's conventions file (e.g. a containerized database, an HTTP stub server),
calling **only the adapter under test's own public methods** — never through a usecase, a port default method, or
the full application context.

Tests are expected to **fail at runtime** because adapter implementations are still stubs — this is intentional.
**No adapter implementation is done in this section.**

**Inbound adapter steps** (e.g. REST controllers — framework slice, mocked ports):

```
- [ ] RI<nn> · `<InboundAdapterClass>` · test: `<AdapterTestClass>` · covers: `<entry point>` · mocks: `<InboundPort>`
  - Happy Path:
    - given: [mocked port behaviour]
      when: [request with a valid payload]
      then: [expected call on the mocked port and expected success response]
  - Error Mapping:
    - given: [the mocked port throws or returns an error]
      when: [request]
      then: [expected error status and response body]
  - Validation: `<fieldName>` — [list of constraint violations to cover]
```

- `<InboundAdapterClass>` — the inbound adapter class (e.g., `WidgetController`)
- `<AdapterTestClass>` — the corresponding slice test class
- `covers:` — the entry point the adapter exposes (e.g., `POST /widgets`)
- `mocks:` — the inbound port(s)/usecase(s) the adapter delegates to, mocked in the slice
- Sub-bullets — one scenario per group (Happy Path, Error Mapping, Validation); how groups are realized in test
  code and their names follow the module's conventions file

Each inbound step boots only the framework's slice for the adapter under test (per the conventions file's
slice-test mechanism), mocks the listed inbound ports, and enters through the protocol (e.g. HTTP requests via the
slice test client) — never by calling the adapter's methods directly, since validation, request binding, and error
mapping live in the framework machinery, not in the method body. No real infrastructure and no real usecases.
Validation constraints come from the module's API schema, not from guesses. This step owns the endpoint's
request-validation matrix and status-code contract; they are not repeated at system level. The same RED-phase rules
apply.

**Coverage balance rule:** Before listing scenarios, review the existing tests in `<AdapterTestClass>` to understand
what is already covered. Only list scenarios that add **new** coverage. Do NOT list scenarios already covered by
existing tests. The goal is a well-balanced, non-redundant test suite, not a mechanical one-test-per-plan-bullet
mapping. If existing tests already cover a scenario adequately, omit it from the plan.

**Existing-test updates rule:** as in the Unit Red Phase — when the change alters behaviour an existing integration
test already covers, list the required change as an explicit `update:` sub-bullet under the affected method; the step
sub-agent implements only what is listed.

### TDD Unit Green Phase Step Format

Each item in the `TDD Unit Green Phase` section MUST correspond 1-to-1 with an item from `TDD Unit Red Phase` and MUST
follow this exact format:

```
- [ ] GU<nn> · `<TargetClass>` · test: `<TestClass>` · after: GU<nn>, GU<nn>
```

- `<TargetClass>` — the production class to implement (same class as in the Red Phase step)
- `<TestClass>` — the test class whose tests must be green after this step
- `after:` (optional) — the IDs of other green-phase steps whose target classes this step's tests exercise as
  **real, unmocked collaborators** (e.g. a domain entity or value object the usecase's tests use directly while
  its methods are stubs owned by another unit step). The orchestrator will not start this step before those steps
  are done. Emit `after:` only for real dependencies — mocked collaborators never create one.

One step = one class. The step is complete when all tests in `<TestClass>` pass.

### TDD Integration Green Phase Step Format

Each item in the `TDD Integration Green Phase` section MUST correspond 1-to-1 with an item from
`TDD Integration Red Phase` and MUST follow this exact format:

```
- [ ] GI<nn> · `<AdapterImplClass>` · test: `<AdapterTestClass>` · after: GU<nn>, GU<nn>
```

- `<AdapterImplClass>` — the adapter implementation class to implement (same class as in the Integration Red Phase step)
- `<AdapterTestClass>` — the integration test class whose tests must be green after this step
- `after:` (optional) — the IDs of other green-phase steps (typically unit-phase ones) on the adapter's real
  execution path — integration tests mock nothing, so an unmocked mapper or domain object implemented by another
  green step is a real dependency. Same rule as the unit format: only real, unmocked collaborators, never mocked
  ones.

One step = one adapter class (either variant — an outbound adapter or an inbound one; for inbound steps the
`covers:`/`mocks:` parts mirror the Red Phase step). The step is complete when all tests in `<AdapterTestClass>`
pass. For an inbound-adapter step, implement only the adapter itself — request binding, mapping, validation
wiring, error mapping — never the usecase behind it; that is a unit-phase target with its own green step.

### TDD System Test Red Phase Step Format

Each item in the `TDD System Test Red Phase` section MUST follow this exact format:

```
- [ ] RS<nn> · `<SystemTestClass>` · covers: `<entry point>`
  - Happy Path:
    - given: [preconditions]
      when: [request or invocation with valid data]
      then: [expected outcome]
  - Unhappy Path:
    - given: [condition]
      when: [request or invocation]
      then: [expected error outcome]
```

- `<SystemTestClass>` — the system test class to create (e.g., `CreateWidgetTest`, `ExportWidgetsTest`)
- `covers:` — the entry point under test, in one of two forms:
    - `<HTTP_METHOD> <path>` — for an inbound HTTP adapter (e.g., `POST /expenses`)
    - `<InboundPort>.<method>()` — for a framework-fired entry point with no HTTP layer (e.g. a cron/scheduled
      trigger such as `WidgetReportPort.generateReport()`); the notation only names the entry point — the test
      makes the framework fire it, it never calls the method itself
- Sub-bullets — one scenario per group (Happy Path, Unhappy Path); how groups are realized in test code
  (e.g. nested test classes) and their names follow the module's conventions file; each line describes one test the
  sub-agent must write

Each step is the **RED phase** for system tests: write tests that build and exercise the fully wired application
stack with real adapters wired — using the module's API-level test client (per conventions file) for the HTTP form,
or, for the framework-fired form, inducing the framework's own trigger (per the conventions file's trigger
mechanism) and awaiting the observable outcome. Tests
are expected to **fail at runtime** when the implementation is not yet complete — this is intentional.
**No production implementation is done in this section.**

System steps are a **thin slice** (see Test Layer Mapping): per entry point, one end-to-end happy path and a
representative error path originating below the inbound adapter. Do not list field-validation scenarios or the
inbound adapter's own request/response handling here — those belong to the adapter's Integration Red Phase step.

**Coverage balance rule:** Only list scenarios that are not yet covered by an existing system test class or already
owned by a lower layer (the inbound adapter's integration step owns validation and the status-code contract). Do
not duplicate coverage that already exists.

**Existing-test updates rule:** as in the Unit Red Phase — when the change alters what an existing system test for the
same entry point asserts (e.g. a new response field), list the required change as an explicit `update:` sub-bullet
under the affected scenario group; the step sub-agent implements only what is listed.

### TDD System Test Green Phase Step Format

Each item in the `TDD System Test Green Phase` section MUST correspond 1-to-1 with an item from
`TDD System Test Red Phase` and MUST follow this exact format:

```
- [ ] GS<nn> · `<SystemTestClass>` · covers: `<entry point>`
```

- `<SystemTestClass>` — the system test class to verify (same class as in the System Test Red Phase step)
- `covers:` — the entry point under test, in the same form (`<HTTP_METHOD> <path>` or `<InboundPort>.<method>()`) as
  the System Test Red Phase step

One step = one system test class. The step is complete when all tests in `<SystemTestClass>` pass.
Fix implementation bugs in any layer — inbound adapter (REST controller, messaging handler, cron trigger, etc.),
usecase, or outbound adapter — as needed to make the test pass. **Never modify the test class.**

An inbound adapter that has its own Integration Phase step (e.g. a REST controller) is already implemented by that
step's green phase before this section starts. An entry point without one (e.g. a framework-fired trigger with no
protocol-level behaviour of its own) is wired as part of making the corresponding system step pass; do not add a
separate checklist item or section for it.

See `.claude/templates/example-plan.md` for a complete worked example.

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
- Put the findings still needing them — the `decision` ones and anything escalated — in front of the user
  explicitly, alongside the unanswered Open Questions.
- **Stop here. Do not implement anything.** Do not write code, create files, or run commands.
- Wait for the user to explicitly ask you to start implementation before doing any work.
- Tell the user that implementation will not start while any Open Question lacks an `A:` or any Review Finding
  lacks an `Action:` — the `implement-plan` skill's plan-readiness gate checks exactly this, so resolving them now
  saves a blocked run later.
