# [Conventions](../conventions.md) > Testing Conventions

What each test layer targets, and how a test in this module is written.

## Test Layers

Which packages/folders each test layer covers, so it is clear where a new test belongs.

- **Unit-test targets:** `<packages holding pure logic — e.g. domain/, application/usecase/>`
- **Integration-test targets (outbound adapters):** `<e.g. adapter/persistence/, adapter/httpclient/ — exercised
  against real test infrastructure>`
- **Integration-test targets (inbound adapters):** `<the inbound adapters tested as a framework slice with their
  ports mocked, and the entry-point kind each covers — e.g. adapter/web/ (REST), adapter/messaging/ (listeners)>`
- **System-test entry points:** `<by default the inbound adapters above, entered end to end; list only the
  exceptions, or "same as the inbound adapters above">`

## Test Tooling

- Test framework: `<e.g. JUnit 5, pytest, Jest>`
- Container-based dependencies: `<e.g. Testcontainers running Postgres; or "none">`
- HTTP stubbing for outbound calls: `<e.g. WireMock, nock, responses; or "none">`
- API-level test client: `<e.g. RestAssured, httpx test client, supertest>`
- Inbound-adapter slice testing: `<the framework mechanism for booting one inbound adapter with its ports mocked —
  e.g. @WebMvcTest with MockMvc and @MockitoBean>`
- Firing non-HTTP entry points in system tests: `<how a test makes the framework fire a scheduled or message-driven
  entry point as in production; or "none — the module has no non-HTTP entry points">`

## Naming Conventions

- Test method naming: `<e.g. when<Condition>_then<Result>()>`
- Test class naming: `<e.g. <ClassUnderTest>Test for unit and integration, <ScenarioName>Test for system tests>`
- Test base classes and what they provide: `<e.g. AbstractIntegrationTest boots the context, wires the test database
  and stub server, and resets stub state after each test; or "none">`
- Shared test builders/factories and what they provide: `<list every reusable builder so a new test reuses it
  instead of recreating it; or "none yet">`

## Testing Style

- Parameterized / table-driven tests: `<when they are preferred over repeated one-off tests>`
- Assertion library / style: `<e.g. AssertJ assertThat(...) only — never JUnit assertEquals/assertTrue>`
- Test description annotations: `<e.g. @DisplayName in the format "when [condition] - then [outcome]"; or "none">`
- Imports / qualified names: `<e.g. import types directly; never fully qualified names inside code bodies>`
- Mocked-port verification depth: `<e.g. verify the call and its key arguments; avoid full object-equality
  interaction assertions>`
