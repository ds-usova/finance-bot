# Plan Step Formats

The exact shape of every checklist item in a plan's **Red Phase** and **Green Phase**, and the rule for deciding
which phase a step belongs to. Written by [`plan-task`](../skills/plan-task/SKILL.md), consumed by the step agents
a pipeline spawns.

Read this when writing or reviewing a step. The stage order, the ID scheme, the group and section structure, and
the guardrails are the skill's; only the shape of an item is here.
[`example-plan.md`](example-plan.md) is a complete worked plan in these formats.

## The Three Test Types — reference, **not** a section of the plan

Every step belongs to one of three types. **What separates them is what is real and what is faked**, and nothing
else — not the architecture, not the package a class sits in.

| Type            | What is real                                      | What is faked                     |
|-----------------|---------------------------------------------------|-----------------------------------|
| **Unit**        | the target class                                  | every dependency it is handed     |
| **Integration** | the target class and the one thing it talks to    | everything past that one thing    |
| **System**      | the whole application, entered as production does | nothing                           |

- **Unit** — the class in isolation. Every collaborator it is given is a mock or a fake. No database, no network,
  no filesystem, no application-framework context. What it proves is the class's own logic.
- **Integration** — the class against the **real** thing it integrates with: a database, a cache, an object
  store, an HTTP API, a message broker, or the application framework's own machinery. One real dependency per
  step, and only the class under test wired to it — never reached through some other class, and never the whole
  application. What it proves is that the two actually fit: the query runs, the payload maps, the framework binds
  and validates as expected.

  A stub server standing in for a third-party HTTP API counts as real: the transport, the serialization and the
  error handling are exercised for real, and only the far side is a stand-in. A mocked collaborator does not.
- **System** — the fully wired application, **nothing mocked**, entered the way production enters it: an HTTP
  request through the real endpoint, a message published to the real broker, a schedule the framework fires. Never
  by calling an internal method directly — the trigger wiring is production behaviour too, and a direct call
  bypasses exactly what the test exists to prove.

  System tests are a **thin slice**: per entry point, one end-to-end happy path, a representative error path
  raised from deep in the stack, and any wiring concern only the whole application can show — the object graph,
  serialization config, transactions. Field-by-field validation belongs to the integration step for the class
  that does the validating.

**Which classes fall into which type is the module's answer, not this file's.** Its testing conventions map its
own layers, folders or roles onto these three, and that mapping is what a step is written against. A module whose
conventions carry no such mapping cannot be planned: ask for it before writing steps, rather than inventing one
from the package names.

Do **not** copy the mapping into the plan file. It lives in the conventions, and a second copy is one more place
for the two to drift apart.

## Scenario-Authoring Rules

These bind every Red Phase format below.

**A scenario comes from the design, not from the planner.** The design's **Acceptance Scenarios** are the
behaviour a person signed off, numbered `A1`, `A2`. Every scenario written here traces to one, and the step names
which: `covers scenarios: A1, A3`, on the step line. A scenario the design does not carry is either a mechanical
case the design never needed — a boundary value, a mapping detail — or a behaviour nobody agreed to. The second
goes back to the design as a new `D` entry, never in here as an invention.

**A design scenario no step names is a gap.** Check the whole set before finishing: every `A<n>` in the design is
covered by at least one step in one of the task's plans.

**Coverage balance rule.** Before listing scenarios, review the existing tests in the step's test class to
understand what is already covered. Only list scenarios that add **new** coverage. Do NOT list scenarios that are
already covered by existing tests — even if they are related to the changed code. The goal is a well-balanced,
non-redundant test suite, not a mechanical one-test-per-plan-bullet mapping. If existing tests already cover a
scenario adequately, omit it from the plan.

**Existing-test updates rule.** The same review cuts the other way: when the change alters behaviour an existing
test already covers, the step carries an `update:` sub-bullet saying so. It takes one of two forms, and the form
is chosen by whether the change is derivable from a premise:

```
- update: `existingTestMethod()` — [one outcome, written from the method's body: assert the new `status` field; delete]
- update: premise — [what the change did to what this class's tests touch] · [what follows for a test that meets it]
```

- **Per method** — one method, one outcome, read off that method's body. For a change no premise derives: a
  test to delete, an assertion a new field needs, a case a grown enum adds. `— delete` is always per method: the
  red exit check counts what left the tree against these bullets.
- **Per class (premise)** — a fact about the change, and what it implies for a test that meets it: `two ports
  merged into one ExpenseRepository; spendingQueryRepository untouched · a test verifying either merged port
  verifies the one mock`. Whichever tests meet the premise is decided by the step agent, from each test's body —
  the premise names the collaborator, the field or the value it turns on, so that reading a body settles it. Never
  a transformation to work out per method with the premise left implicit, and never one sentence stretched over
  methods it does not all hold for. Name a method under a premise bullet only as an example.

The step sub-agent writes exactly the scenarios listed and applies every `update:` bullet — a per-method one as
written, a premise one to each test in its class whose body meets the premise, and to no other. It never widens
a premise to make a test fit; a test the sentence seems to reach but the body does not is left alone and named in
the report. An update missing here is a plan defect, caught by the sub-agent's report or the plan review — not
the sub-agent's call to fix.

**Removed behaviour is searched for, not remembered.** Reviewing the classes a step already names finds a test that
asserts *more* than it should; it does not find the test in some other class that asserts something the change
**deletes**. When a decision removes anything a test can observe — a response field, a status, a component of a
record, a value a message carries — search the test tree for that value and list every hit as an `update:` bullet,
whatever class it lands in. The search term is the removed thing itself, not the classes the plan happens to touch.

**What the change removed gets no scenario of its own.** A retired path, a dropped field, a command that no
longer exists: do not list a scenario asserting that the old one now refuses. Nothing serves it any more, so the
refusal comes from a default the change never chose — a catch-all route, a deny-by-default rule — and the test
pins that default under the old name. What proves the removal is the scenarios on what replaced it, plus the
`update:` bullets above that strip the old thing out of the tests that named it. A scenario like this is worth
one release and then reads as a contract nobody agreed to.

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
- [ ] RU<nn> · `<TargetClass>` · test: `<TestClass>` · covers: `method1()`, `method2()` · scenarios: A1, A3
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

- `<TargetClass>` — a class the module's conventions map to the unit type (simple class name)
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
**Every dependency the class is handed is mocked or faked.** A test here never touches real infrastructure; that
is what the integration type is for.

**Exclusion — simple delegation**: Do NOT add a class to this section if every method under test is a simple
delegation — a one-line method that hands its argument to a collaborator with no logic of its own, no
conditionals, no transformations, no error handling. Such trivial pass-throughs belong in the
**Interface-First / Build Stabilization** section instead.

## TDD Integration Red Phase Step Format

An integration step names **one class and the one real thing it talks to**. Which real thing decides the variant,
and each item MUST follow its variant's exact format.

| The real dependency is                                  | Variant                     |
|---------------------------------------------------------|-----------------------------|
| infrastructure — a database, a cache, a store, an API   | **infrastructure steps**    |
| the application framework itself — routing, binding, serialization, validation | **entry-point steps** |

Which of a module's classes fall into either variant comes from its conventions' test-type mapping. The two
formats differ because what a test drives differs: a class that calls out is called directly, while a class the
framework calls is reached through the framework.

**Infrastructure steps** — the class against a real database, cache, store, broker or HTTP API:

```
- [ ] RI<nn> · `<AdapterImplClass>` · test: `<AdapterTestClass>` · covers: `method1()`, `method2()` · scenarios: A2
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

- `<TargetClass>` — the class that talks to the real dependency (e.g., `WidgetRepositoryAdapter`,
  `ExternalApiClient`)
- `<TestClass>` — the corresponding integration test class
- `covers:` — comma-separated list of the class's method signatures to test in this step
- Sub-bullets — one `given / when / then` scenario per test case; each block describes one test the sub-agent must
  write; the sub-agent derives the method name from the scenario following project naming conventions

Each infrastructure step is the **RED phase** for integration tests: write tests that build and exercise the real
dependency as the module's conventions define it — a containerized database, a stub HTTP server, a test broker —
calling **only the class under test's own public methods**. Never through another class, and never the whole
application: the point is that this class and this dependency fit, and anything else in the path blurs which of
them failed.

Tests are expected to **fail at runtime** because the implementation is still a stub — this is intentional.
**No implementation is done in this section.**

**Entry-point steps** — the class the framework calls, against the framework's real machinery:

```
- [ ] RI<nn> · `<TargetClass>` · test: `<TestClass>` · covers: `<entry point>` · mocks: `<Collaborator>` · scenarios: A1
  - Happy Path:
    - given: [what the mocked collaborator returns]
      when: [request with a valid payload]
      then: [expected call on the mock and expected success response]
  - Error Mapping:
    - given: [the mocked collaborator fails]
      when: [request]
      then: [expected error status and response body]
  - Validation: `<fieldName>` — [list of constraint violations to cover]
```

- `<TargetClass>` — the class the framework routes a request to (e.g., `WidgetController`)
- `<TestClass>` — the corresponding test class, per the conventions' mechanism for booting part of the framework
- `covers:` — the entry point it exposes (e.g., `POST /widgets`)
- `mocks:` — what it hands the work to, mocked so that only the framework's own behaviour is under test
- Sub-bullets — one scenario per group (Happy Path, Error Mapping, Validation); how groups are realized in test
  code and their names follow the module's conventions file

Each entry-point step boots only as much of the framework as that entry point needs, mocks what the class
delegates to, and enters **through the protocol** — an HTTP request, a published message, a fired schedule — never
by calling the class's method directly. Routing, binding, validation and error mapping live in the framework, not
in the method body, and a direct call tests none of them. No real infrastructure past the framework.

Validation constraints come from the module's schema, not from guesses. This step owns the entry point's
validation matrix and its status-code contract; they are not repeated at system level. The same RED-phase rules
apply.

## TDD System Test Red Phase Step Format

Each item in the `TDD System Test Red Phase` section MUST follow this exact format:

```
- [ ] RS<nn> · `<SystemTestClass>` · covers: `<entry point>` · scenarios: A1, A4
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
    - `<HTTP_METHOD> <path>` — where the entry point is an HTTP request (e.g., `POST /expenses`)
    - `<Class>.<method>()` — where the framework fires the entry point itself, such as a scheduled trigger or a
      message listener; the notation only names the entry point. The test makes the framework fire it and never
      calls the method itself.
- Sub-bullets — one scenario per group (Happy Path, Unhappy Path); how groups are realized in test code
  (e.g. nested test classes) and their names follow the module's conventions file; each line describes one test the
  sub-agent must write

Each step is the **RED phase** for system tests: write tests against the fully wired application, with **nothing
mocked** — using the module's API-level test client for the HTTP form, or inducing the framework's own trigger,
per the conventions, and awaiting the observable outcome. Tests are expected to **fail at runtime** while the
implementation is incomplete — this is intentional.
**No production implementation is done in this section.**

System steps are a **thin slice** (see [The Three Test Types](#the-three-test-types--reference-not-a-section-of-the-plan)):
per entry point, one end-to-end happy path and a representative error path raised from deep in the stack. Do not
list field-validation scenarios here — those belong to the entry-point integration step. Only list scenarios not
yet covered by an existing system test class or already owned by another type.

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
- [ ] GI<nn> · `<TargetClass>` · test: `<TestClass>` · after: GU<nn>, GU<nn>
```

- `<TargetClass>` — the class to implement (same class as in the Integration Red Phase step)
- `<TestClass>` — the integration test class whose tests must be green after this step
- `after:` (optional) — the IDs of other green-phase steps whose classes sit on this one's real execution path.
  An integration test mocks little, so a mapper or a domain object implemented by another green step is a real
  dependency. Same rule as the unit format: only real, unmocked collaborators, never mocked ones.

One step = one class, in either variant; for an entry-point step the `covers:`/`mocks:` parts mirror the Red
Phase step. The step is complete when all tests in `<TestClass>` pass.

**Implement only the class the step names.** For an entry-point step that means the binding, the mapping, the
validation wiring and the error mapping — never what it delegates to, which has a green step of its own.

## TDD System Test Green Phase Step Format

Each item in the `TDD System Test Green Phase` section MUST correspond 1-to-1 with an item from
`TDD System Test Red Phase` and MUST follow this exact format:

```
- [ ] GS<nn> · `<SystemTestClass>` · covers: `<entry point>`
```

- `<SystemTestClass>` — the system test class to verify (same class as in the System Test Red Phase step)
- `covers:` — the entry point under test, in the same form (`<HTTP_METHOD> <path>` or `<Class>.<method>()`) as the
  System Test Red Phase step

One step = one system test class. The step is complete when all tests in `<SystemTestClass>` pass.
Fix implementation bugs **anywhere in the stack** as needed to make the test pass. **Never modify the test class.**

An entry point with its own integration step is already implemented before this section starts. One without —
a framework-fired trigger with no protocol behaviour of its own — is wired as part of making this step pass. Do
not add a separate checklist item for it.
