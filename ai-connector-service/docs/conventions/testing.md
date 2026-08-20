# [Conventions](../conventions.md) > Testing Conventions

How tests are built, named, and styled, on top of the repository-wide
[Testing](../../../docs/conventions/testing.md).

## Package Structure

Unit and integration tests mirror the production package of the class under test. A system test covers a flow
rather than a class, so system tests live together in `bot.finance.ai.system`, one class per flow.

```
bot.finance.ai
├── architecture    # ArchUnit dependency-rule tests, and the source scans ArchUnit cannot do
├── system          # system tests — one class per end-to-end flow
└── common          # shared test infrastructure
    ├── boot                   # what a test starts, and how
    │   ├── AbstractSystemTest     # full-application base class
    │   ├── AbstractMemorySystemTest # the same with the memory on, against the containerized database and Redis
    │   ├── GrpcAdapterTest        # composed annotation — inbound-adapter tests
    │   ├── AiAdapterTest          # composed annotation — outbound-adapter tests
    │   ├── PersistenceAdapterTest # composed annotation — the persistence slice on the containerized database
    │   ├── SecurityAdapterTest    # composed annotation — the token reader on the stubbed key-set endpoint
    │   ├── RedisAdapterTest       # composed annotation — the change-stream consumer on the containerized Redis
    │   ├── MetricsAdapterTest     # composed annotation — the metrics adapter on the rendered actuator scrape
    │   ├── RedisPropertiesConfiguration # the Redis URL and a stream key of its own, shared by AbstractMemorySystemTest and RedisAdapterTest
    │   ├── WireMockUrlConfiguration # points the provider and the ledger at the stub server's runtime port
    │   └── InProcessGrpcTransportConfiguration # the in-process gRPC transport, one server name per context
    ├── containers             # stub-server and database lifecycle
    │   ├── WireMockSupport        # JVM-wide stub-server singleton
    │   ├── PostgresContainers     # JVM-wide Postgres container singleton, on the pgvector image
    │   ├── RedisContainers        # JVM-wide Redis container singleton, and the connection factories a test builds against it
    │   ├── Network                # the Testcontainers network Redis and Toxiproxy share
    │   └── ToxiproxyContainers    # fronts the Redis singleton, for a test that cuts and restores the connection
    ├── fixtures               # payloads a test sends, and the loader for the ones kept on disk
    │   ├── JsonUtils              # loads JSON fixtures from src/test/resources
    │   ├── ChatCompletionFixtures # provider response bodies
    │   ├── CallerTokens           # the test signing key, its key set, and the caller tokens it mints
    │   ├── RequestFixtures        # valid ExtractIntentsRequest builders
    │   ├── ChangeStreamEntryFixtures # change-stream entry bodies, in the shape the ledger's stream carries
    │   ├── SpendingFactFixtures   # SpendingRow, CategoryRef, RecordedStatus and StreamPosition builders
    │   └── EmbeddingFixtures      # a unit vector, one at a chosen similarity to it, and the provider's response
    ├── rows                   # what a test writes into and reads back from a table directly
    │   ├── IncomingMessageRowUtils # rows of incoming_message, by identity and by received_at
    │   ├── RecordedExpenseRowUtils # rows of recorded_expense, by expense id and by message
    │   └── StreamEntryFailureRowUtils # rows of stream_entry_failure, by entry id
    ├── stubs                  # the external systems' fakes, and what they recorded
    │   ├── WireMockStubs          # stub registration, one static method per endpoint
    │   ├── McpLedgerStubs         # stubs the ledger's /mcp endpoint, one static method per outcome
    │   ├── LedgerJwksStubs        # stubs the ledger's key-set endpoint, served, unreachable or slow
    │   ├── AuthorizedStubs        # attaches an authorization header to a generated stub
    │   ├── CapturedRequestUtils   # reads back the requests WireMock recorded, and their JSON bodies
    │   └── LedgerChangeStreamStubs # stands in for the ledger's writes to the change stream, and a second consumer
    ├── LogCapture             # Logback appender, for asserting on log output
    └── MockedLoggerUtils      # reads a mocked Logger's calls back as lines
```

A new helper joins the subpackage its role names, and is listed above. `LogCapture` and `MockedLoggerUtils` sit
at the root because they belong to none of them. The same names carry the same meanings in `ledger-service`, so a
helper is looked for in the same place in either module.

## Test Layers

- **Unit** — `domain/`, `application/usecase/`, self-validating `application/dto` records, and the stateless
  helper classes in an adapter package — the mappers, renderers and their kind
  ([Code Style](code-style.md#general)). Plain JUnit, outbound ports mocked, no Spring context. A proto mapper is
  a unit target. The same goes for an adapter-layer class doing something non-trivial — branching logic with no
  infrastructure of its own, like `LedgerToolFailureProcessor` or `CallerTokenMcpRequestCustomizer`; a class
  whose behaviour is trivial is left to its adapter's integration test instead.
- **Integration, outbound** — `adapter/ai/` via `@AiAdapterTest`, `adapter/persistence/` via
  `@PersistenceAdapterTest`, `adapter/security/` via `@SecurityAdapterTest`, `adapter/redis/`'s consumer,
  `ChangeStreamConsumer`, via `@RedisAdapterTest` against the containerized Redis with the inbound port
  (`LearnMessageOutcomePort`) mocked, and `adapter/metrics/` via `@MetricsAdapterTest`, which boots only the two
  Micrometer classes with autoconfiguration on and the prometheus actuator endpoint exposed, with
  `PendingEntryCountPort` mocked for the pending gauge to sample — the rendered meter names and values on the
  scrape are its own, asserted over RestAssured. Wire only the adapter under test, call its public methods
  directly, and mock nothing the container or the stub server can stand in for. `adapter/ai/` owns the request Spring AI
  sends, the tool calls it makes against a stubbed ledger, and how a stubbed response, a tool refusal, a
  transport failure and a malformed body map onto the port's result or exception. `adapter/persistence/` runs
  against the containerized database and owns what a statement writes, what it leaves untouched, and how a
  store failure becomes the port's exception. `adapter/security/` runs against the stubbed key-set endpoint and
  owns which caller tokens are read, which are refused, and what an unreachable or slow key set answers.
  `adapter/redis/`'s consumer owns reading the stream, claiming and acknowledging entries, and how the store's
  answer decides retry, acknowledgement or drop; its reader, `ChangeStreamEntryReader`, is a unit target instead
  — stateless parsing with no infrastructure of its own. `RedisPendingEntries` is driven directly against a
  `RedisContainers` template, with no Spring context of its own — the class carries
  `@Testcontainers(disabledWithoutDocker = true)` itself, so it skips rather than fails without Docker.
- **Integration, inbound** — `adapter/grpc/` via `@GrpcAdapterTest`, entered through a generated blocking stub
  with the inbound port mocked. Owns request binding, delegation, proto mapping, and the RPC's validation
  matrix and status-code contract.
- **System** — the fully wired application with the provider stubbed, entered over a **real Netty channel** on
  the port the server actually bound (`@LocalGrpcServerPort`), not the in-process transport. One happy path and
  one representative error path per RPC; several scenarios may share a class. Actuator's HTTP surface is covered
  the same way, through its own port.
  `adapter/scheduling/` is covered here and nowhere else: what is proven is that the timer fires and the purge
  happens.

## Test Tooling

- JUnit 5, AssertJ, Mockito, WireMock, Awaitility. Every gRPC test enters through a generated blocking stub;
  RestAssured covers Actuator's HTTP endpoints.
- `AbstractSystemTest` boots the application on a **random real gRPC port**, exposes a blocking stub built
  against that port and the Actuator port for HTTP assertions, and resets stubs after each test; subclasses
  declare nothing. System tests extend it, adapter tests do not.
- Building a stub is `AbstractSystemTest`'s and `@GrpcAdapterTest`'s job, never a test class's. A test that
  hand-builds a channel picks a transport by accident.
- `@GrpcAdapterTest` — `@SpringBootTest` + `InProcessGrpcTransportConfiguration` + the test profile. Isolation
  comes from `@MockitoBean` on the inbound port, not from a framework slice. The transport is imported rather
  than autoconfigured because Boot's test transport holds one in-process server name for the whole JVM, so a
  second context configuration cannot start while the first context is cached.
  `InProcessGrpcTransportConfiguration` gives each context a server name of its own.
- `@AiAdapterTest` — boots the adapter under test, its `ChatClient` configuration, the `adapter/ledger` MCP
  classes and Spring AI's OpenAI, MCP-client, transport and tool-callback autoconfigurations. A real client over
  a stubbed transport is what these tests exist for, never a mocked `ChatClient`.
- **Reach WireMock through `WireMockSupport.SERVER`, never the static DSL.** `WireMock.stubFor(...)`,
  `verify(...)` and `findAll(...)` address `localhost:8080` and fail with a connection error before any
  assertion runs. Only the pure builders — `post`, `urlPathEqualTo`, `okJson`, `postRequestedFor`, `equalTo`,
  `matchingJsonPath` — are safe to static-import.
- `ChatCompletionFixtures` builds a **whole chat-completion body**, served verbatim. A tool-calling turn needs
  the `tool_calls` array on `choices[0].message`. A turn is also at least two provider round trips, so it is
  stubbed as a sequence, not a single response.
- `LogCapture` requires the real `Slf4jLoggerFactory`: it keys on the logger named after the class.
- **Shared test infrastructure that only proves itself at runtime ships with a test that boots it.** A composed
  annotation or a stub server counts: compiling says nothing about whether the context loads. A throwaway class
  carrying the annotation, autowiring one bean and asserting nothing is enough.
- **A stub standing in for a protocol ships with a test that completes one exchange through it**, not one that
  loads a context. A protocol has a preamble and correlation rules of its own: an MCP client sends `initialize`
  and `notifications/initialized` before any tool call, and matches each response by the id it generated.
- **A test owed a rework is `@Disabled`, never commented out.** `@Disabled("RU08: …")` names the step that owns
  it, and the runner reports it as *skipped*. When the method cannot compile against a changed signature, keep
  the method and comment out the lines inside it rather than the method itself.

## Naming Conventions

- Test methods: `when<Condition>_then<Result>()`.
- Test classes: `<ClassUnderTest>Test`; `<Flow>SystemTest` for system tests.
- Helpers: static `*Utils` classes with a private constructor. Test helpers are the one place `*Utils` is kept —
  production code names a helper for its role ([Code Style](code-style.md#general)).
- New shared builders and factories go in the `bot.finance.ai.common` subpackage their role names — `boot`,
  `containers`, `fixtures` or `stubs` — and get listed in
  [Package Structure](#package-structure). The exception is a helper needing package-private access to the class
  it fronts: it stays in that class's package and is listed there, as `adapter/grpc/CallerTokenTestSupport` is.

## Testing Style

- Group tests into `@Nested` classes, never a flat list. Unit and outbound-adapter tests group by the method
  under test (`infer()` → `@Nested class Infer`); inbound-adapter and system tests group by scenario kind
  (`HappyPath`, `UnhappyPath`, `ErrorMapping`, `Validation`). Each nested class carries a prose `@DisplayName`;
  only the outer class is suffixed `Test`. A class whose tests form one group still uses a nested class.
- A nested class named after the type under test shadows its import — `@Nested class Money` inside `MoneyTest`
  makes `Money.of(...)` resolve to the test class. Name it for the role instead: `MoneyFactory`.
- `@ParameterizedTest` when one behaviour spans several values (currency scales, enum cases, validation
  matrices). Never duplicate a case as both a parameterized entry and a one-off test.
- **Assert the invariant, not the mechanism.** Where an outcome depends on how a dependency routes a call
  internally, assert what must hold whichever route it takes, never which route was taken. The opposite error is
  as bad: a disjunction covering every possible outcome asserts nothing. Assert what a user would notice going
  wrong.
- AssertJ only. Compare a `BigDecimal` with `isEqualByComparingTo(...)`. Assert a gRPC failure on
  `StatusRuntimeException` and its status code, never its message.
- Every test method carries `@DisplayName` as `"when [condition] - then [outcome]"`: **one condition, one
  outcome, under 120 characters.** The name says what the test proves, never what it asserts. A name that will
  not fit means the test proves several things at once: split it, or name the one behaviour they add up to. It
  never cites a plan step or a design decision by number either. `DisplayNameConventionsTest` in
  `bot.finance.ai.architecture` enforces both the shape and the length.
- Verify a mocked port's call and its key arguments; avoid full object-equality interaction assertions.
- Text blocks for long literals. Move a payload shared by more than one test to `src/test/resources` +
  `JsonUtils` — except a parameterized one, since `JsonUtils` performs no substitution. Move any body past
  roughly fifteen lines to a file.
- Log responses in system tests.
