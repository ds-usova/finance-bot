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
    ├── boot                  # what a test starts, and how
    │   ├── AbstractSystemTest    # full-application base class
    │   ├── PersistenceAdapterTest # composed annotation — persistence-adapter tests
    │   ├── AiConnectorAdapterTest # composed annotation — AI connector gRPC adapter tests
    │   ├── McpAdapterTest        # composed annotation — MCP tool adapter tests
    │   ├── WebAdapterTest        # composed annotation — @WebMvcTest slice tests over adapter/web
    │   └── SigningKeysConfiguration # the signing key pair a MockMvc slice does not component-scan
    ├── containers            # Testcontainers / WireMock / in-JVM gRPC stub server lifecycle
    ├── rows                  # seeds a table's rows and reads them back, one class per table
    │   ├── CategoryRowUtils      # reads back a user's stored category rows, and stores a grouping or a category under one
    │   ├── ExpenseRowUtils       # reads back a user's stored expense rows, and stores one directly
    │   ├── ExpenseProposalRowUtils # reads back a user's stored expense proposal rows, and stores one directly
    │   ├── SpendingQueryRowUtils # reads back a user's stored spending query rows, and stores one directly
    │   └── UserRowUtils          # stores a user row and returns its generated id
    ├── fixtures              # payloads a test sends, and the loader for the ones kept on disk
    │   ├── JsonUtils             # loads JSON fixtures from src/test/resources
    │   ├── McpRequests           # JSON-RPC request bodies posted to /mcp
    │   ├── McpTokens             # tokens minted through the application's own AccessTokenMinter
    │   ├── SessionTokens         # browser session tokens, and the configuration they are minted under
    │   ├── SigningKeys           # the keystore configuration the test profile runs with, and the key pair it resolves to
    │   ├── TelegramFixtures      # Bot API JSON bodies
    │   └── TelegramLoginPayloads # Login Widget payloads, signed the way Telegram signs them
    ├── stubs                 # the external systems' fakes, and what they recorded
    │   ├── WireMockStubs         # stub registration, one static method per endpoint
    │   └── TelegramTestBot       # Telegram client wiring, bot tokens, poll verification, Bot API method recording
    └── LogCapture            # Logback appender, for asserting on log output
```

A new helper joins the subpackage its role names, and is listed above. `LogCapture` sits at the root because it
belongs to none of them — a bucket of one is worth less than the honesty of leaving it where it is.

## Test Layers

- **Unit** — `domain/`, `application/usecase/`, self-validating `application/dto` records, and the stateless
  helper classes in an adapter package — the renderers, mappers and their kind
  ([Code Style](code-style.md#general)). Plain JUnit, outbound ports mocked, no Spring context.
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
- **A test owed a rework is `@Disabled`, never commented out.** `@Disabled("RU08: …")` names the step that owns
  it, and the runner reports it as *skipped* — so what is owed is visible in every summary and clears itself when
  the step lands. A method commented out disappears from the count instead, and a total that still balances hides
  it. When the method cannot compile against a changed signature, keep the method and comment out the lines
  inside it rather than the method itself.
- **A skipped test is otherwise a Docker-shaped answer, not a choice.** `@Testcontainers(disabledWithoutDocker
  = true)` skips the container-backed classes when Docker is down, so a run's skipped count is only meaningful
  against the count the same machine produced before the change.

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
  `WireMockStubs`. Test helpers are the one place `*Utils` is kept — production code names a helper for its role
  ([Code Style](code-style.md#general)) — because a test helper genuinely is a bag of conveniences keyed to a
  fixture rather than a thing with one job.
- New shared builders and factories go in the `bot.finance.common` subpackage their role names — `boot`,
  `containers`, `rows`, `fixtures` or `stubs` — and get listed in
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
- Every test method carries `@DisplayName` as `"when [condition] - then [outcome]"`: **one condition, one
  outcome, under 120 characters.** The name says what the test proves, never what it asserts — the assertions are
  in the body, and a name that lists them has to be re-read every time one of them changes. A name that will not
  fit is the signal, not the problem: the test is proving several things at once, so either split it or name the
  one behaviour they add up to. It never cites a plan step or a design decision by number either — the scenario a
  step agent works from carries those, and they name nothing once the plan is archived.
- Verify a mocked port's call and its key arguments; avoid full object-equality interaction assertions.
- **Assert the invariant, not the mechanism.** Where an outcome depends on how a dependency routes a call
  internally, assert what must hold whichever route it takes — *no proposal is stored at the wrong scale* — never
  which route was taken. A test pinning the mechanism fails when the dependency turns out to work differently,
  and says nothing about whether the product broke. The opposite error is as bad: a disjunction covering every
  possible outcome asserts nothing. Assert what a user would notice going wrong.
- **Do not assert on a log message** — `info`, `debug` or `error` — when the outcome can be observed any other
  way. Assert the outcome itself: the port that was called, the row that was written, the message that was sent.
  A log line is a diagnostic, not a contract; wording drifts with every edit to the class, and a test bound to it
  fails for a change that broke nothing. Reserve a `LogCapture` assertion for the case where the behaviour leaves
  no other trace — a failure that is swallowed on purpose, a path whose whole point is that nothing else happens.
- **A system test signposts its phases with `// then:` comments.** One per thing the flow proves — the batch was
  confirmed, the tool was called, the reply went back — written in the same words the display name uses. A system
  test is a long sequence of awaits and assertions against a stack the reader cannot see, and the phases are what
  a reader scans for; nothing else in the method says where one ends and the next begins. This is the one place a
  comment restating the code earns its place, and it does not license them elsewhere: a unit or adapter test
  short enough to read at once gets none ([Code Style](code-style.md#general)).
- Text blocks for long literals. Move a payload shared by more than one test to `src/test/resources` +
  `JsonUtils` — except a parameterized one, since `JsonUtils` performs no substitution, which is why
  `TelegramFixtures` stays a set of text-block builders. Move any body past roughly fifteen lines to a file.
- Log responses in system tests.
