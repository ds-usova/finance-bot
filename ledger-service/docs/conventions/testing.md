# [Conventions](../conventions.md) > Testing Conventions

How tests are built, named, and styled.

## Package Structure

Unit and integration tests mirror the production package of the class under test. A system test covers a flow
rather than a class, so system tests live together in `bot.finance.system`, one class per flow.

```
bot.finance
├── architecture    # ArchUnit dependency-rule tests
├── system          # system tests — one class per end-to-end flow
└── common          # shared test infrastructure
    ├── containers            # Testcontainers / WireMock / in-JVM gRPC stub server lifecycle
    ├── AbstractSystemTest    # full-application base class
    ├── PersistenceAdapterTest # composed annotation — persistence-adapter tests
    ├── AiConnectorAdapterTest # composed annotation — AI connector gRPC adapter tests
    ├── McpAdapterTest        # composed annotation — MCP tool adapter tests
    ├── CategoryRowUtils      # reads back a user's stored category rows, and stores one or one under a parent
    ├── ExpenseRowUtils       # reads back a user's stored expense rows
    ├── ExpenseProposalRowUtils # reads back a user's stored expense proposal rows
    ├── UserRowUtils          # stores a user row and returns its generated id
    ├── WireMockStubs         # stub registration, one static method per endpoint
    ├── JsonUtils             # loads JSON fixtures from src/test/resources
    ├── LogCapture            # Logback appender, for asserting on log output
    ├── McpRequests           # JSON-RPC request bodies posted to /mcp
    ├── McpTokens             # tokens minted through the application's own AccessTokenMinter
    ├── TelegramFixtures      # Bot API JSON bodies
    └── TelegramTestBot       # Telegram client wiring, bot tokens, poll verification
```

## Test Layers

- **Unit** — `domain/`, `application/usecase/`, self-validating `application/dto` records, and pure
  mapper/`*Utils` classes in an adapter package. Plain JUnit, outbound ports mocked, no Spring context.
- **Integration, outbound** — `adapter/persistence/` and future outbound HTTP adapters, one subpackage per
  external system. Wire only the adapter under test and call its public methods directly against real test
  infrastructure; nothing is mocked. Persistence adapters use `@PersistenceAdapterTest`.
- **Integration, inbound** — `adapter/web/` through `@WebMvcTest` with the inbound-port beans mocked. Owns
  validation, binding, delegation and error-to-response mapping; never business logic or real infrastructure.

  An inbound adapter whose protocol is not HTTP has no slice, so it is entered through its own protocol:
  `adapter/telegram/TelegramUpdateListener` is driven by a real Telegram client polling WireMock, with the
  inbound port mocked.
- **System** — the same entry points end-to-end against the fully wired application; one happy path plus a
  representative error path each. For the long-polling listener see the isolation rules below.
- **Architecture** — a rule that holds for *every* type in a package is asserted once in `bot.finance.architecture`
  rather than repeated in each type's test class. A rule about one type stays with that type.

## Test Tooling

- JUnit 5, AssertJ, Mockito, WireMock, Awaitility. API-level client: **RestAssured** (with `json-path`) against
  the booted application's random port.
- `containers/` (`PostgresContainers`, `WireMockSupport`, `Network`, `GrpcStubServer`) — JVM-wide singletons for
  the containerized Postgres, the WireMock stub server, and a real in-JVM gRPC server on a dynamic port
  fronting the AI connector's contract. Tests never manage their lifecycle. `@Testcontainers(disabledWithoutDocker
  = true)` applies to the container-backed singletons only, directly or via `AbstractSystemTest`, so the suite
  skips rather than errors without Docker; `GrpcStubServer` needs no Docker and carries no such annotation.
- `AbstractSystemTest` — the full-application base class, wiring the containerized database via
  `@ImportTestcontainers(PostgresContainers.class)` and `@ServiceConnection`; subclasses declare nothing.
  System tests extend it; outbound-adapter and slice tests do not.
- `PersistenceAdapterTest` — boots the `@DataJdbcTest` slice against the containerized Postgres, never the full
  context. The test class adds `@Import(<AdapterUnderTest>.class)` and calls the adapter directly. Each test
  runs in a rolled-back transaction; use `@Commit` plus explicit cleanup only when committed state matters.
- **Reach WireMock through `WireMockSupport.SERVER`, never the static DSL.** `WireMock.stubFor(...)`,
  `verify(...)` and `findAll(...)` address `localhost:8080` and fail with a connection error before any
  assertion runs. Only the pure builders — `post`, `urlPathEqualTo`, `okJson`, `postRequestedFor`, `equalTo`,
  `absent` — are safe to static-import. `withoutFormParam` exists only on the verification side; a stub
  expresses the same thing as `withFormParam(name, absent())`.
- `TelegramFixtures` — Bot API JSON in two shapes that must not be conflated: bare `Update` objects (what
  `parseUpdate` deserializes) and `getUpdates` envelopes (what the stub server serves).
- `LogCapture` requires the real `Slf4jLoggerFactory`: it keys on the logger named after the class, a name a
  mocked factory never produces.
- **Shared test infrastructure that only proves itself at runtime ships with a test that boots it.** A composed
  annotation, a container singleton, a stub server: compiling says nothing about whether the context loads. A
  throwaway class carrying the annotation, autowiring one bean and asserting nothing is enough. Without it the
  first real test to use the infrastructure is where a missing autoconfiguration surfaces, and it surfaces as
  that test's failure rather than as its own.

### Isolating the long-polling listener

The Telegram listener's poll loop starts with the application context and runs continuously, so a system test
stubs the Bot API and waits for the outcome rather than calling the inbound port.

Each such system test class declares its own bot token via
`@TestPropertySource(properties = "telegram.bot.token=…")`. A differing property gives the class its own entry
in Spring's context cache — a fresh loop from a clean offset — and, since the token forms part of the request
path, a stub path no other class can reach. Consequence: **one triggered scenario per class**, since a second
would inherit the first's advanced state. Give each new class a token constant in `TelegramTestBot`.

## Naming Conventions

- Test methods: `when<Condition>_then<Result>()`.
- Test classes: `<ClassUnderTest>Test`; `<Flow>SystemTest` for system tests.
- Helpers: static `*Utils` classes with a private constructor; stub helpers as static methods on
  `WireMockStubs`.
- New shared builders and factories go in `bot.finance.common` and get listed in
  [Package Structure](#package-structure), so later tests reuse them instead of recreating them.

## Testing Style

- Group tests into `@Nested` classes, never a flat list. Unit and outbound-adapter tests group by the method
  under test (`toHandleIncomingMessageCommand()` → `@Nested class ToHandleIncomingMessageCommand`);
  inbound-adapter and system tests group by scenario kind (`HappyPath`, `UnhappyPath`, `ErrorMapping`,
  `Validation`). Each nested class carries a prose `@DisplayName`; only the outer class is suffixed `Test`. A
  class whose tests form one group still uses a nested class.
- A nested class named after the type under test shadows its import, so `@Nested class Widget` inside
  `WidgetTest` would make `new Widget(...)` resolve to the test class. Name it for the role instead:
  `WidgetConstructor`.
- `@ParameterizedTest` when one behaviour spans several values (enum cases, validation matrices, null-handling).
  Never duplicate a case as both a parameterized entry and a one-off test.
- AssertJ only — never JUnit `assertEquals`/`assertTrue`. RestAssured response specs are fine for HTTP-level
  assertions.
- Every test method carries `@DisplayName` as `"when [condition] - then [outcome]"`.
- Verify a mocked port's call and its key arguments; avoid full object-equality interaction assertions.
- **Do not assert on a log message** — `info`, `debug` or `error` — when the outcome can be observed any other
  way. Assert the outcome itself: the port that was called, the row that was written, the message that was sent.
  A log line is a diagnostic, not a contract; wording drifts with every edit to the class, and a test bound to it
  fails for a change that broke nothing. Reserve a `LogCapture` assertion for the case where the behaviour leaves
  no other trace — a failure that is swallowed on purpose, a path whose whole point is that nothing else happens.
- Text blocks for long literals. Move a payload shared by more than one test to `src/test/resources` +
  `JsonUtils` — except a parameterized one, since `JsonUtils` performs no substitution, which is why
  `TelegramFixtures` stays a set of text-block builders. Move any body past roughly fifteen lines to a file.
- Log responses in system tests.
