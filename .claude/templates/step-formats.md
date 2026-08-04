# Plan Step Formats

The exact shape of every checklist item in a plan's **Red Phase** and **Green Phase**, and the rule for deciding
which phase a step belongs to. Written by [`plan-task`](../commands/plan-task.md), consumed by the step agents
`implement-plan` spawns.

Read this when writing or reviewing a step. The stage order, the ID scheme, the group and section structure, and
the guardrails are the skill's; only the shape of an item is here.
[`example-plan.md`](example-plan.md) is a complete worked plan in these formats.

## Test Layer Mapping — reference, **not** a section of the plan

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
  the system test exists to prove. Both forms exercise the same wiring end-to-end — only the entry point differs.
  System tests are a **thin slice** keeping what only the fully wired application can prove: per entry
  point, one end-to-end happy path, a representative error path originating below the inbound adapter, and any
  cross-cutting wiring concern (bean graph, serialization config, transactions). Field-validation matrices and the
  inbound adapter's own request/response handling belong to the adapter's integration test, not here.

## Scenario-Authoring Rules

These bind every Red Phase format below.

**Coverage balance rule.** Before listing scenarios, review the existing tests in the step's test class to
understand what is already covered. Only list scenarios that add **new** coverage. Do NOT list scenarios that are
already covered by existing tests — even if they are related to the changed code. The goal is a well-balanced,
non-redundant test suite, not a mechanical one-test-per-plan-bullet mapping. If existing tests already cover a
scenario adequately, omit it from the plan.

**Existing-test updates rule.** The same review cuts the other way: when the change alters behaviour an existing
test already covers — a new field the test's assertions would now omit, or a grown enum/case set that an
exhaustive parameterized test iterates — list the required change as an explicit `update:` sub-bullet under the
affected method:

```
- update: `existingTestMethod()` — [what to add or change, e.g. assert the new `status` field]
```

The step sub-agent implements exactly the scenarios and `update:` bullets listed; it never decides on its own which
existing tests to touch. An update missing here is a plan defect, caught by the sub-agent's report or the plan review
— not the sub-agent's call to fix.

**Removed behaviour is searched for, not remembered.** Reviewing the classes a step already names finds a test that
asserts *more* than it should; it does not find the test in some other class that asserts something the change
**deletes**. When a decision removes anything a test can observe — a response field, a status, a component of a
record, a value a message carries — search the test tree for that value and list every hit as an `update:` bullet,
whatever class it lands in. The search term is the removed thing itself, not the classes the plan happens to touch.

**A new call on a shared path is searched for the same way.** When the change makes an existing flow reach a
collaborator it did not reach before — an outbound port, a new stub, a new fixture — every test that drives that
flow needs the new arrangement, not only the tests the plan happens to name. Search the test tree for the *entry
point*, not for the classes in the step list, and list every hit as an `update:` bullet. A step that adds a
delivery to the end of a message flow breaks every test that sends a message, including the ones about something
else entirely; those fail at a stage guardrail, where the cause is furthest from the change.

**Assert the invariant, not the mechanism.** Where a scenario's outcome depends on how a dependency routes a call
internally, state what must hold whichever route it takes, never which route is taken — and never a disjunction
over every possible outcome, which asserts nothing. A scenario resting on a design decision whose `Basis:` is
`deferred` is the signal: the step agent observes the real behaviour before writing the assertion. The module's
testing conventions own this rule; it is repeated here because it is written into scenarios, not into code.

## TDD Unit Red Phase Step Format

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
  write; the sub-agent derives the method name from the scenario following project naming conventions

Each step represents the **RED phase** of TDD: write meaningful tests that build for the listed methods.
The tests are expected to **fail at runtime** (stubs return null/defaults from the stabilization phase) — this is
intentional.
**No production implementation is done in this section.** Implementation happens in a separate phase after all tests are
written.
Steps are intentionally small and focused — one class, one concern.
Usecase tests fake/mock the outbound ports they depend on — they never touch real infrastructure; that belongs to the
Integration phase.

**Exclusion — simple delegation**: Do NOT add a class to this section if every method under test is a simple
delegation (e.g., a one-line usecase method that only calls an outbound port with no logic of its own — no
conditionals, no transformations, no error handling). Such trivial pass-through changes belong in the
**Interface-First / Build Stabilization** section instead.

## TDD Integration Red Phase Step Format

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
  write; the sub-agent derives the method name from the scenario following project naming conventions

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

## TDD System Test Red Phase Step Format

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

System steps are a **thin slice** (see [Test Layer Mapping](#test-layer-mapping--reference-not-a-section-of-the-plan)):
per entry point, one end-to-end happy path and a representative error path originating below the inbound adapter.
Do not list field-validation scenarios or the inbound adapter's own request/response handling here — those belong
to the adapter's Integration Red Phase step. Only list scenarios not yet covered by an existing system test class
or already owned by a lower layer.

## TDD Unit Green Phase Step Format

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

## TDD Integration Green Phase Step Format

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

## TDD System Test Green Phase Step Format

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
