# [Conventions](../conventions.md) > Testing Conventions

What each test type targets, and how a test in this module is written.

## Test Types

There are three, and what separates them is what is real and what is faked: a **unit** test mocks every
dependency, an **integration** test uses the real thing the class talks to, and a **system** test mocks nothing
at all.

**This section maps the module's own structure onto those three.** Nothing outside this file knows what this
module's parts are called, so a plan cannot place a class in a test type without the mapping below. Name real
packages, folders or roles — whatever this module actually organizes code by.

- **Unit-test targets:** `<the parts holding logic worth testing in isolation — e.g. domain/, application/usecase/,
  or src/lib/>`
- **Integration-test targets, against infrastructure:** `<the parts that talk to a database, cache, object store,
  broker or external API, and which real dependency each one gets — e.g. adapter/persistence/ against a
  containerized Postgres, adapter/httpclient/ against a stub server>`
- **Integration-test targets, against the framework:** `<the parts the framework itself calls — routing, binding,
  serialization, validation — and the entry-point kind each covers, e.g. adapter/web/ (REST),
  adapter/messaging/ (listeners); or "none — the module has no framework entry points">`
- **System-test entry points:** `<what a system test enters through, end to end with nothing mocked; or "same as
  the framework targets above">`

A module that leaves this mapping blank cannot be planned: the planner would have to guess a test type from a
package name, and that guess is wrong exactly where the module differs from the last one.

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
