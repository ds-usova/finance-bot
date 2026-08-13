# Example Plan — Worked Example

This is a complete worked example of a plan file produced by the `plan-task` skill — in a real repo this file would
live at `docs/1-add-widget/plan.md`, and its whole directory moves to `docs/implemented/` once every checkbox
is ticked. It is
illustrated with a Java/Spring-Boot-flavored `Widget` feature purely for concreteness — other stacks adapt the same
structure (plan sections, section order, step formats, RED/GREEN choreography) using their own tech stack, tools,
and file formats as recorded in the module's `docs/conventions.md`
(see `.claude/templates/conventions/`).

What the feature *is* — the objective, the solution, the file names, the diagrams and the decisions behind them —
lives in `.claude/skills/design-task/example-design.md`, the design this plan is written from. The plan links it rather than
restating it, and starts at the step map.

Every item below is in one of the formats specified in [`step-formats.md`](step-formats.md); read that for the
rules, and this for what they look like when written out.

---

# Plan: Add Widget Creation

**Affected Modules:** `module-a`
**Design:** [Add Widget Creation](design.md)

The design lists one module, so this task holds one plan at `docs/1-add-widget/plan.md` and the link above is a
bare sibling. Had it listed two, there would be `module-a/plan.md` and `module-b/plan.md` — each linking
`../design.md` and each run as its own pipeline — plus a `shared/plan.md` holding anything both of them read,
implemented first so that neither waits on the other.

## Components

The design named responsibilities; these are the classes that hold them.

```plantuml
@startuml
' Uses PlantUML's bundled C4-PlantUML stdlib (angle-bracket include — no network fetch, no relative file
' path, resolved the same way regardless of where this diagram is rendered from). If a renderer's PlantUML
' version doesn't have the C4 stdlib bundled, fall back to:
' !include https://raw.githubusercontent.com/plantuml-stdlib/C4-PlantUML/master/C4_Component.puml
!include <C4/C4_Component>

Container_Boundary(domain, "domain") {
  Component(widget, "Widget", "domain entity")
  Component(widgetAssembler, "WidgetAssembler", "domain service")
}
Container_Boundary(application, "application") {
  Component(createWidgetPort, "CreateWidgetPort", "inbound port")
  Component(createWidgetUseCase, "CreateWidgetUseCase", "use case")
  Component(widgetRepository, "WidgetRepository", "outbound port")
}
Container_Boundary(inboundAdapter, "adapter (inbound)") {
  Component(widgetController, "WidgetController", "REST controller")
  Component(widgetUtils, "WidgetUtils", "REST mapper")
}
Container_Boundary(outboundAdapter, "adapter (outbound)") {
  Component(widgetRepositoryAdapter, "WidgetRepositoryAdapter", "persistence adapter")
}

Rel(widgetController, createWidgetPort, "calls")
Rel(createWidgetUseCase, createWidgetPort, "implements")
Rel(widgetController, widgetUtils, "maps via")
Rel(createWidgetUseCase, widgetAssembler, "uses")
Rel(createWidgetUseCase, widget, "produces")
Rel(createWidgetUseCase, widgetRepository, "depends on")
Rel(widgetRepositoryAdapter, widgetRepository, "implements")
@enduml
```

| Type                  | Holds                        | Refuses                                       |
|-----------------------|------------------------------|-----------------------------------------------|
| `Widget`              | `parentId`, `name`, `value`  | a blank `name`, a `value` over 255 characters |
| `CreateWidgetCommand` | `parentId`, `name`, `value`  | —                                             |

| Port               | Methods        |
|--------------------|----------------|
| `CreateWidgetPort` | `create(cmd)`  |
| `WidgetRepository` | `save(widget)` |

| Exception                    | Status | Raised by                    |
|------------------------------|--------|------------------------------|
| `ResourceNotFoundException`  | 404    | an unknown `parentId`        |
| `DuplicateResourceException` | 409    | a name already used under it |
| `PersistenceFailedException` | 503    | any other write failure      |

## Step-by-Step Implementation Map (To-Do List)

### Stabilization

#### API Contract

- [ ] ST01 · Add `POST /widgets` path to the project's API schema file `<api-schema-file>`:
  ```yaml
  /widgets:
    post:
      operationId: createWidget
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/CreateWidgetRequest'
      responses:
        '200':
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Widget'
  ```
- [ ] ST02 · Add `CreateWidgetRequest` schema to `<api-schema-file>`: `name` (string, required, max 255), `value` (string,
  required, max 255)
- [ ] ST03 · Add `Widget` response schema to `<api-schema-file>`: `id` (integer), `name` (string), `value` (string)

#### Database

- [ ] ST04 · Add migration `<migration-file>` (named per the project's migration tool conventions), as designed:
  ```sql
  CREATE TABLE widget (
      id        BIGSERIAL PRIMARY KEY,
      parent_id BIGINT       NOT NULL REFERENCES parent (id) ON DELETE CASCADE,
      name      VARCHAR(255) NOT NULL,
      value     VARCHAR(255) NOT NULL
  );

  CREATE UNIQUE INDEX idx_widget_parent_name ON widget (parent_id, name);
  ```

#### Interface-First / Build Stabilization

New-method stubs must carry a short inline comment describing the implementation intent, for example:

```java
public Settings loadSettings(long userId) {
    // retrieves language settings and the user's word lists for the given user
    return null;
}
```

**Interface & Signature Sync**

- [ ] ST05 · Add `createWidget(CreateWidgetCommand command): Widget` to the `CreateWidgetPort` inbound port
  (interface only — the implementation stub goes on `CreateWidgetUseCase` below)
- [ ] ST06 · Add `save(Widget widget): Widget` to the `WidgetRepository` outbound port
- [ ] ST07 · Stub `CreateWidgetUseCase.createWidget()`:
  ```java
  public Widget createWidget(CreateWidgetCommand command) {
      // validates the command, assembles a Widget via WidgetAssembler, and persists it via WidgetRepository
      return null;
  }
  ```
- [ ] ST08 · Stub `WidgetRepositoryAdapter.save()`:
  ```java
  public Widget save(Widget widget) {
      // maps the domain Widget to a WidgetEntity, persists it, and returns the domain Widget with its generated id
      return null;
  }
  ```
- [ ] ST09 · Update `WidgetController.createWidget()` to call `createWidgetPort.createWidget(...)` and fix any remaining
  compile errors until the module builds green

**Shared Test Infrastructure**

- [ ] ST10 · Add a `WidgetTestDataFactory` (`aWidget()`, `aWidget().withName(...)`) to the module's shared test-fixture
  location — both `WidgetRepositoryAdapterTest` (Integration Red Phase) and `CreateWidgetTest` (System Test Red
  Phase) need a valid widget precondition, and neither Red Phase step is scoped to create shared fixtures on its
  own

### Red Phase

#### TDD Unit Red Phase

- [ ] RU01 · `CreateWidgetUseCase` · test: `CreateWidgetUseCaseTest` · covers: `createWidget()`, `validateRequest()` · scenarios: A1, A2
    - `createWidget()`:
        - given: a valid request
          when: createWidget() is called
          then: returns the created widget
        - given: an invalid request
          when: createWidget() is called
          then: throws IllegalArgumentException
    - `validateRequest()`:
        - given: a valid request
          when: validateRequest() is called
          then: no exception is thrown
        - given: a null request
          when: validateRequest() is called
          then: throws NullPointerException
- [ ] RU02 · `WidgetAssembler` · test: `WidgetAssemblerTest` · covers: `assemble()`, `normalize()`
    - `assemble()`:
        - given: a list of parts
          when: assemble() is called
          then: returns the parts combined into a widget
        - given: an empty part list
          when: assemble() is called
          then: returns an empty widget
    - `normalize()`:
        - given: mixed-case input
          when: normalize() is called
          then: returns lowercase result
        - given: input with leading and trailing spaces
          when: normalize() is called
          then: returns trimmed result
- [ ] RU03 · `WidgetUtils` · test: `WidgetUtilsTest` · covers: `toRest()`
    - `toRest()`:
        - given: a fully populated domain object
          when: toRest() is called
          then: all fields are mapped correctly
        - given: a domain object with a null optional field
          when: toRest() is called
          then: null is preserved in the response
        - update: `whenGadgetIsMapped_thenResponseCarriesItsFields()` — the response record gained `value`, so
          assert it alongside the fields the test already checks; without this the test compiles and passes while
          asserting nothing about the new field

#### TDD Integration Red Phase

- [ ] RI01 · `WidgetRepositoryAdapter` · test: `WidgetRepositoryAdapterTest` · covers: `findById()`,
  `save()` · scenarios: A4, A5
    - `findById()`:
        - given: an existing widget
          when: findById() is called
          then: returns the widget
        - given: an unknown widget id
          when: findById() is called
          then: throws ResourceNotFoundException
    - `save()`:
        - given: a valid widget
          when: save() is called
          then: the widget is persisted
        - given: an unknown parent id
          when: save() is called
          then: throws ResourceNotFoundException
        - given: a widget whose name is already taken under the same parent
          when: save() is called
          then: throws DuplicateResourceException
- [ ] RI02 · `WidgetController` · test: `WidgetControllerTest` · covers: `POST /widgets` · mocks: `CreateWidgetPort` · scenarios: A2, A3
    - Happy Path:
        - given: the mocked port returns a created widget
          when: request is made with a valid payload
          then: the port is called with the mapped command and 200 is returned with the widget response
    - Error Mapping:
        - given: the mocked port throws ResourceNotFoundException
          when: request is made
          then: return 404
        - given: the mocked port throws DuplicateResourceException
          when: request is made
          then: return 409
        - given: the mocked port throws PersistenceFailedException
          when: request is made
          then: return 503
    - Validation: `name` — blank, null, exceeds max length

#### TDD System Test Red Phase

- [ ] RS01 · `CreateWidgetTest` · covers: `POST /widgets` · scenarios: A1, A3
    - Happy Path:
        - given: a valid parent resource
          when: request is made with a valid payload
          then: return 200 with the created widget
    - Unhappy Path:
        - given: an unknown parent id
          when: create request is made
          then: return 404

### Green Phase

#### TDD Unit Green Phase

- [ ] GU01 · `CreateWidgetUseCase` · test: `CreateWidgetUseCaseTest`
- [ ] GU02 · `WidgetAssembler` · test: `WidgetAssemblerTest`
- [ ] GU03 · `WidgetUtils` · test: `WidgetUtilsTest`

#### TDD Integration Green Phase

- [ ] GI01 · `WidgetRepositoryAdapter` · test: `WidgetRepositoryAdapterTest`
- [ ] GI02 · `WidgetController` · test: `WidgetControllerTest` · covers: `POST /widgets` · mocks: `CreateWidgetPort` ·
  after: GU03

#### TDD System Test Green Phase

- [ ] GS01 · `CreateWidgetTest` · covers: `POST /widgets`

### Post-Implementation Steps

#### Manual Request Files

- [ ] P01 · Update `.http` files to reflect the new request shape

## Open Questions / Blockers

- **Q1:** `module-a`'s integration tests need a containerized database; the CI runner has no container runtime
  configured, so `RI01` cannot run there until it does. Run it locally, or configure the runner first?
  - A:

## Review Findings

- **F1:** `RI02`'s error-mapping scenarios cover 404 and 409, but `WidgetControllerTest` must also assert the 503
  the design maps `PersistenceFailedException` to — no scenario covers it at any layer.
  - Resolution: mechanical
  - Action: applied — added the scenario to `RI02`.
