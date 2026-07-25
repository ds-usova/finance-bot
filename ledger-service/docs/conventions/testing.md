# [Conventions](../conventions.md) > Testing Conventions

How tests are built, named, and styled.

## Package Structure

Unit and integration test classes mirror the production package of the class under test (e.g. a test for
`bot.finance.adapter.web.X` lives in `bot.finance.adapter.web`).

**System tests do not**, because they are a different kind of thing: a system test covers a flow through the whole
wired stack, not a class, so there is no production package for it to mirror and no single class it belongs to.
They all live together in `bot.finance.system`, one class per flow. Three test-only packages exist alongside the
mirrored ones:

```
bot.finance
├── architecture    # ArchUnit dependency-rule tests — see Architecture & Layering
├── system          # system tests — one class per end-to-end flow
└── common          # shared test infrastructure — see Test Tooling below
    ├── containers  # Testcontainers / WireMock singleton lifecycle
    ├── AbstractSystemTest
    ├── PersistenceAdapterTest  # composed annotation for persistence-adapter tests
    ├── WireMockStubs
    ├── JsonUtils
    ├── LogCapture      # attaches a Logback appender to a logger, for asserting on log output
    ├── TelegramFixtures # Telegram Bot API JSON bodies
    └── TelegramTestBot  # Telegram client wiring against WireMock, plus poll verification
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
    slice tests do not.** The containerized database is wired automatically via
    `@ImportTestcontainers(PostgresContainers.class)` together with `@ServiceConnection` on the containerized
    Postgres singleton; subclasses declare nothing.
  - `PersistenceAdapterTest` — the composed annotation for outbound persistence-adapter tests: it boots the
    `@DataJdbcTest` slice (Spring Data JDBC repositories, `JdbcTemplate`, transaction manager, Flyway migrations)
    against the containerized Postgres, never the full application context. The test class adds
    `@Import(<AdapterUnderTest>.class)` and calls the adapter's public methods directly. Each test runs in a
    rolled-back transaction (slice default) — use `@Commit` plus explicit cleanup only when committed state must
    be verified.
  - `WireMockStubs` — the single home for stub registration: one static helper method per external endpoint.
    Raw stubbing inlined in test classes is against convention.
  - `JsonUtils` — loads JSON payload fixtures from `src/test/resources`.
  - `LogCapture` — attaches a Logback `ListAppender` to a named logger and exposes the formatted messages, for
    the cases where log output *is* the observable outcome. Requires the real `Slf4jLoggerFactory`: it keys on the
    logger named after the class, a name a mocked `LoggerFactory` never produces.
  - `TelegramFixtures` — Telegram Bot API JSON in two shapes that must not be conflated: bare `Update` objects
    (what the client's `parseUpdate` deserializes) and `getUpdates` envelopes (what the stub server serves).
  - `TelegramTestBot` — builds a Telegram client pointed at the WireMock singleton for a given bot token, owns
    the per-test-class token constants, and exposes the poll-verification helpers.

- **WireMock is reached through the instance, never the static DSL.** `WireMock.stubFor(...)`, `verify(...)` and
  `findAll(...)` address `WireMock.defaultInstance`, which defaults to `localhost:8080`; `WireMockServer` never
  reconfigures it, and the singleton binds a dynamic port. A static call therefore cannot reach this module's
  server and fails with a connection error before any assertion runs. Always go through
  `WireMockSupport.SERVER`. Only the pure builders — `post`, `urlPathEqualTo`, `okJson`, `postRequestedFor`,
  `equalTo`, `absent` — are safe to static-import. Note also that `withoutFormParam` exists only on the
  verification side; a stub expresses the same thing as `withFormParam(name, absent())`.
- API-level test client: **RestAssured** (with `json-path`) against the booted application's random port.
  **Awaitility** is available for asynchronous assertions.
- Inbound-adapter slice testing: `@WebMvcTest(<ControllerClass>.class)` + `MockMvc`, with the controller's
  inbound-port/use-case beans mocked via `@MockitoBean`. No containers, no full context.
- Firing non-HTTP entry points in system tests: the trigger runs with the application context and the test waits
  for the observable outcome — it never calls the inbound-port method directly, because the trigger wiring is
  production behaviour too. The Telegram listener is the worked example: its long-polling loop starts with the
  context, so a system test stubs the Bot API and waits.

  Such a trigger runs **continuously** for the life of the context, which makes isolation the hard part. Each
  system test class declares its own bot token via
  `@TestPropertySource(properties = "telegram.bot.token=…")`. A differing property gives the class its own entry
  in Spring's context cache — hence a fresh loop starting from a clean offset — and, since the token forms part
  of the request path, a stub path no other class can reach. The consequence is **one triggered scenario per
  class**: a second scenario in the same class would inherit the first one's advanced state. Give each new class
  a token constant in `TelegramTestBot`.

## Test Layers

Which packages map to which test layer.

- **Unit tests:** `domain/` (model, value, exception behaviour) and `application/usecase/` — all pure logic,
  tested with plain JUnit + mocks for outbound ports, no Spring context. Two kinds of class outside those packages
  map here too, because they are pure logic and an integration test would add infrastructure to assert a field
  mapping: **self-validating `application/dto` command records**, and **pure mapper/`*Utils` classes in an adapter
  package** (e.g. `adapter/telegram/TelegramUpdateUtils`) that only translate between types.
- **Integration tests (outbound adapters):** `adapter/persistence/` and future outbound HTTP adapters (one
  subpackage per external system — see [Architecture & Layering](architecture.md#package-structure)). Wire only
  the adapter under test and call its public methods directly against real test infrastructure (see Test Tooling
  below); nothing is mocked. The wiring mechanism for persistence adapters is `@PersistenceAdapterTest` (see Test
  Tooling).
- **Integration tests (inbound adapters):** `adapter/web/` (REST controllers) driven through the web-slice
  mechanism (see Test Tooling below) with the inbound-port/use-case beans mocked. Covers validation, binding,
  delegation, and error-to-response mapping; never business logic or real infrastructure.

  An inbound adapter whose protocol is **not** HTTP has no slice to boot, so it is entered through its own
  protocol instead: the test wires a real client against the stub server and lets the adapter be driven the way
  production drives it, never by calling its method directly. `adapter/telegram/TelegramUpdateListener` is the
  worked example — a real Telegram client polling WireMock, with the inbound port mocked. Same principle as the
  web slice, different transport.
- **System tests:** the same entry points as the inbound adapters above, entered end-to-end against the fully
  wired application — one happy path plus a representative error path per entry point. They live in
  `bot.finance.system`, not beside the adapter they enter through (see [Package Structure](#package-structure)).
  For an entry point the framework fires rather than a caller (the Telegram long-polling listener), see the
  isolation rules under Test Tooling: one scenario per class, each class scoped by its own bot token.

## Naming Conventions

- Test method naming pattern: `when<Condition>_then<Result>()`, e.g. `whenExpenseIsValid_thenPersistsAndReturnsId()`.
- Test class naming: `<ClassUnderTest>Test` for unit and integration tests; `<Flow>SystemTest` for system tests
  (e.g. `RecordExpenseSystemTest`), placed per [Package Structure](#package-structure) above.
- Inbound-adapter test class naming: follows the general pattern — `<ControllerClass>Test`.
- Mapper/helper class conventions: static `*Utils` classes with a private constructor (see `JsonUtils`); stub
  helpers as static methods on `WireMockStubs`.
- Existing shared test builders/factories: `TelegramFixtures` (Bot API JSON bodies) and `TelegramTestBot` (client
  wiring, bot-token constants, poll verification), alongside the general-purpose `LogCapture` and `JsonUtils`. New
  builders/factories belong in `bot.finance.common` and get listed here, so later tests reuse them instead of
  recreating them.

## Testing Style

- Test class structure: group the tests inside a class into `@Nested` inner classes — one per group, never a flat
  list of test methods. What a group *is* depends on the test:
  - unit tests and outbound-adapter tests group by the **method under test**, one nested class per method, named
    after it in PascalCase (`toIncomingMessage()` → `@Nested class ToIncomingMessage`);
  - inbound-adapter and system tests group by the **kind of scenario** — `HappyPath`, `UnhappyPath`,
    `ErrorMapping`, `Validation` — since a single entry point is exercised throughout.

  Each nested class carries a `@DisplayName` naming its group in prose (`@DisplayName("happy path")`). Nested
  classes are not suffixed `Test`; only the outer class is. A class whose tests genuinely form one group still
  uses a single nested class, so the structure reads the same everywhere.

  When the method under test is a **constructor**, the nested class cannot simply take the type's name — a member
  type shadows a single-type import of the same name throughout the enclosing class, so `@Nested class Widget`
  inside `WidgetTest` would make `new Widget(...)` resolve to the test class. Name it after what it constructs
  plus the role: `@Nested class WidgetConstructor` with `@DisplayName("constructing a widget")`.
- Parameterized/table-driven tests: prefer `@ParameterizedTest` when the same behaviour is exercised across
  several values (enum cases, validation matrices, null-handling); never duplicate a case as both a parameterized
  entry and a one-off test. A parameterized test lives in the nested class of the group it belongs to.
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
  `src/test/resources` file + `JsonUtils` once a payload is shared by more than one test — **except** when the
  payload is parameterized, since `JsonUtils` performs no substitution. `TelegramFixtures` is the standing example:
  every body varies by update id, chat id or text, so it stays a set of text-block builders. Move a body to a
  resource file if it outgrows roughly fifteen lines.
