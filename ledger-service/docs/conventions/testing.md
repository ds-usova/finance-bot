# [Conventions](../conventions.md) > Testing Conventions

How tests are built, named, and styled.

## Package Structure

Test classes mirror the production package of the class under test (e.g. a test for `bot.finance.adapter.web.X`
lives in `bot.finance.adapter.web`). Two test-only packages exist alongside the mirrored ones:

```
bot.finance
├── architecture    # ArchUnit dependency-rule tests — see Architecture & Layering
└── common          # shared test infrastructure — see Test Tooling below
    ├── containers  # Testcontainers / WireMock singleton lifecycle
    ├── AbstractSystemTest
    ├── WireMockStubs
    └── JsonUtils
```

## Test Tooling

- Test framework: JUnit 5.
- Shared test infrastructure lives in `bot.finance.common` — the classes and their javadoc are the reference for
  what each one provides; listed here is only how they are meant to be used:
  - `containers/` (`PostgresContainers`, `WireMockSupport`, `Network`) — JVM-wide singletons for the
    containerized Postgres database and the WireMock stub server. Tests never manage their lifecycle (no
    start/stop, no per-test containers). Every test class that touches this containerized infrastructure carries
    `@Testcontainers(disabledWithoutDocker = true)` — directly, or inherited from `AbstractSystemTest` — so the
    suite skips (never errors) when Docker is unavailable.
  - `AbstractSystemTest` — the full-application base class. **System tests extend it; outbound-adapter and
    slice tests do not.** Subclasses wire the containerized database by declaring a `@DynamicPropertySource`
    method that delegates to the inherited `setProperties(registry)`.
  - `WireMockStubs` — the single home for stub registration: one static helper method per external endpoint.
    Raw stubbing inlined in test classes is against convention.
  - `JsonUtils` — loads JSON payload fixtures from `src/test/resources`.
- API-level test client: **RestAssured** (with `json-path`) against the booted application's random port.
  **Awaitility** is available for asynchronous assertions.
- Inbound-adapter slice testing: `@WebMvcTest(<ControllerClass>.class)` + `MockMvc`, with the controller's
  inbound-port/use-case beans mocked via `@MockitoBean`. No containers, no full context.
- Firing non-HTTP entry points in system tests: none exist yet. The mechanism will be decided alongside the
  Telegram update listener's transport (see [Architecture & Layering](architecture.md)). One rule is already
  settled: a system test makes the framework fire the trigger the way production does — it never calls the
  inbound-port method directly.

## Test Layers

Which packages map to which test layer.

- **Unit tests:** `domain/` (model, value, exception behaviour) and `application/usecase/` — all pure logic,
  tested with plain JUnit + mocks for outbound ports, no Spring context.
- **Integration tests (outbound adapters):** `adapter/persistence/` and future outbound HTTP adapters (one
  subpackage per external system — see [Architecture & Layering](architecture.md#package-structure)). Wire only
  the adapter under test and call its public methods directly against real test infrastructure (see Test Tooling
  below); nothing is mocked.
- **Integration tests (inbound adapters):** `adapter/web/` (REST controllers). Driven through the web-slice
  mechanism (see Test Tooling below) with the inbound-port/use-case beans mocked. Covers validation, binding,
  delegation, and error-to-response mapping; never business logic or real infrastructure.
- **System tests:** the same entry points as the inbound adapters above, entered end-to-end against the fully
  wired application — one happy path plus a representative error path per entry point. The Telegram update
  listener becomes an additional entry point when it lands; its transport (webhook vs long-polling) is still
  undecided, and how system tests fire it will be defined with that decision.

## Naming Conventions

- Test method naming pattern: `when<Condition>_then<Result>()`, e.g. `whenExpenseIsValid_thenPersistsAndReturnsId()`.
- Test class naming: `<ClassUnderTest>Test` for unit and integration tests; `<Flow>SystemTest` for system tests
  (e.g. `RecordExpenseSystemTest`), placed per [Package Structure](#package-structure) above.
- Inbound-adapter test class naming: follows the general pattern — `<ControllerClass>Test`.
- Mapper/helper class conventions: static `*Utils` classes with a private constructor (see `JsonUtils`); stub
  helpers as static methods on `WireMockStubs`.
- Existing shared test builders/factories: none yet. New builders/factories belong in `bot.finance.common` and
  get listed here, so later tests reuse them instead of recreating them.

## Testing Style

- Parameterized/table-driven tests: prefer `@ParameterizedTest` when the same behaviour is exercised across
  several values (enum cases, validation matrices, null-handling); never duplicate a case as both a parameterized
  entry and a one-off test.
- Assertion library / style: AssertJ `assertThat(...)` only — never JUnit `assertEquals`/`assertTrue`. RestAssured
  response specs (`then().statusCode(...)`) are fine for HTTP-level assertions.
- Test description annotations: every test method carries `@DisplayName` in the format
  `"when [condition] - then [outcome]"`.
- Log responses in the system tests; the logs are useful for debugging.
- Imports / qualified names: import types and static methods directly; never use fully qualified class names
  inside code bodies.
- Mocked-port verification depth: verify the call and its key arguments on the mocked port (`verify(port).x(...)`
  with targeted argument assertions or captors); avoid full object-equality interaction assertions.
- Long string literals (JSON bodies, SQL): use Java text blocks (`"""…"""`), never concatenation. Prefer a
  `src/test/resources` file + `JsonUtils` once a payload is shared by more than one test.
