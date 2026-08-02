# [Conventions](../conventions.md) > Testing Conventions

How tests are built, named, and styled.

## Package Structure

Unit and integration tests mirror the production package of the class under test. A system test covers a flow
rather than a class, so system tests live together in `bot.finance.ai.system`, one class per flow.

```
bot.finance.ai
├── architecture    # ArchUnit dependency-rule tests
├── system          # system tests — one class per end-to-end flow
└── common          # shared test infrastructure
    ├── WireMockSupport        # JVM-wide stub-server singleton
    ├── AbstractSystemTest     # full-application base class
    ├── GrpcAdapterTest        # composed annotation — inbound-adapter tests
    ├── AiAdapterTest          # composed annotation — outbound-adapter tests
    ├── WireMockStubs          # stub registration, one static method per endpoint
    ├── JsonUtils              # loads JSON fixtures from src/test/resources
    ├── LogCapture             # Logback appender, for asserting on log output
    ├── ChatCompletionFixtures # provider response bodies
    ├── RequestFixtures        # valid ExtractIntentsRequest builders
    ├── AuthorizedStubs        # attaches an authorization header to a generated stub
    ├── McpLedgerStubs         # stubs the ledger's /mcp endpoint, one static method per outcome
    └── CapturedRequestUtils   # reads back the requests WireMock recorded, and their JSON bodies
```

## Test Layers

- **Unit** — `domain/`, `application/usecase/`, self-validating `application/dto` records, and pure `*Utils`
  mappers in an adapter package. Plain JUnit, outbound ports mocked, no Spring context. A proto mapper is a
  unit target: building a message needs no server and no channel. The same goes for an adapter-layer class
  doing something non-trivial — branching logic with no infrastructure of its own, like
  `LedgerToolFailureProcessor` or `CallerTokenMcpRequestCustomizer`; a class whose behaviour is trivial is left
  to its adapter's integration test instead.
- **Integration, outbound** — `adapter/ai/` via `@AiAdapterTest`. Wire only the adapter under test, call its
  public methods directly, mock nothing. Owns the request Spring AI sends, the tool calls it makes against a
  stubbed ledger, and how a stubbed response, a tool refusal, a transport failure and a malformed body map onto
  the port's result or exception.
- **Integration, inbound** — `adapter/grpc/` via `@GrpcAdapterTest`, entered through a generated blocking stub
  with the inbound port mocked. Owns request binding, delegation, proto mapping, and the RPC's validation
  matrix and status-code contract.
- **System** — the fully wired application with the provider stubbed, entered over a **real Netty channel** on
  the port the server actually bound (`@LocalGrpcServerPort`), not the in-process transport. The transport is
  production behaviour: an in-process run replaces the server factory, so it proves nothing about the service
  binding its port or serving a real channel. One happy path and one representative error path per RPC;
  several scenarios may share a class. Actuator's HTTP surface is covered the same way, through its own port.

## Test Tooling

- JUnit 5, AssertJ, Mockito, WireMock, Awaitility. Every gRPC test enters through a generated blocking stub;
  RestAssured covers Actuator's HTTP endpoints.
- `AbstractSystemTest` boots the application on a **random real gRPC port**, exposes a blocking stub built
  against that port and the Actuator port for HTTP assertions, points `spring.ai.openai.base-url` at the stub
  server, and resets stubs after each test; subclasses declare nothing. System tests extend it, adapter tests
  do not.
- Building a stub is `AbstractSystemTest`'s and `@GrpcAdapterTest`'s job, never a test class's. A test that
  hand-builds a channel picks a transport by accident, which is exactly the distinction these two exist to
  hold apart.
- `@GrpcAdapterTest` — `@SpringBootTest` + `@AutoConfigureTestGrpcTransport` + the test profile. Isolation
  comes from `@MockitoBean` on the inbound port, not from a framework slice.
- `@AiAdapterTest` — boots the adapter under test, its `ChatClient` configuration, the `adapter/ledger` MCP
  classes and Spring AI's OpenAI, MCP-client, transport and tool-callback autoconfigurations, with both
  `base-url` and the ledger connection's `url` on the stub server. A real client over a stubbed transport is what
  these tests exist for; a mocked `ChatClient` would exercise none of it.
- **Reach WireMock through `WireMockSupport.SERVER`, never the static DSL.** `WireMock.stubFor(...)`,
  `verify(...)` and `findAll(...)` address `localhost:8080` and fail with a connection error before any
  assertion runs. Only the pure builders — `post`, `urlPathEqualTo`, `okJson`, `postRequestedFor`, `equalTo`,
  `matchingJsonPath` — are safe to static-import.
- `ChatCompletionFixtures` builds a **whole chat-completion body**, served verbatim. A tool-calling turn needs
  the `tool_calls` array on `choices[0].message`, a shape a payload escaped into `message.content` cannot carry.
  A turn is also at least two provider round trips, so it is stubbed as a sequence, not a single response.
- `LogCapture` requires the real `Slf4jLoggerFactory`: it keys on the logger named after the class, a name a
  mocked factory never produces.
- **Shared test infrastructure that only proves itself at runtime ships with a test that boots it.** A composed
  annotation, a stub server: compiling says nothing about whether the context loads. A throwaway class carrying
  the annotation, autowiring one bean and asserting nothing is enough. Without it the first real test to use the
  infrastructure is where a missing autoconfiguration surfaces, and it surfaces as that test's failure rather
  than as its own.
- **A stub standing in for a protocol ships with a test that completes one exchange through it**, not one that
  loads a context. A protocol has a preamble and correlation rules of its own — an MCP client sends `initialize`
  and `notifications/initialized` before any tool call, and matches each response by the id it generated — so a
  stub that answers only the interesting message is green on its own and unusable by the adapter it exists for.

## Naming Conventions

- Test methods: `when<Condition>_then<Result>()`.
- Test classes: `<ClassUnderTest>Test`; `<Flow>SystemTest` for system tests.
- Helpers: static `*Utils` classes with a private constructor.
- New shared builders and factories go in `bot.finance.ai.common` and get listed in
  [Package Structure](#package-structure), so later tests reuse them instead of recreating them. The exception
  is a helper needing package-private access to the class it fronts: it stays in that class's package and is
  listed there — `adapter/grpc/CallerTokenTestSupport` runs a body inside a context holding a caller token,
  which `bot.finance.ai.common` could not reach.

## Testing Style

- Group tests into `@Nested` classes, never a flat list. Unit and outbound-adapter tests group by the method
  under test (`infer()` → `@Nested class Infer`); inbound-adapter and system tests group by scenario kind
  (`HappyPath`, `UnhappyPath`, `ErrorMapping`, `Validation`). Each nested class carries a prose `@DisplayName`;
  only the outer class is suffixed `Test`. A class whose tests form one group still uses a nested class.
- A nested class named after the type under test shadows its import — `@Nested class Money` inside `MoneyTest`
  makes `Money.of(...)` resolve to the test class. Name it for the role instead: `MoneyFactory`.
- `@ParameterizedTest` when one behaviour spans several values (currency scales, enum cases, validation
  matrices). Never duplicate a case as both a parameterized entry and a one-off test.
- AssertJ only. Compare a `BigDecimal` with `isEqualByComparingTo(...)`, so a scale difference does not fail an
  assertion about value. Assert a gRPC failure on `StatusRuntimeException` and its status code, never its
  message.
- Every test method carries `@DisplayName` as `"when [condition] - then [outcome]"`.
- Verify a mocked port's call and its key arguments; avoid full object-equality interaction assertions.
- Text blocks for long literals. Move a payload shared by more than one test to `src/test/resources` +
  `JsonUtils` — except a parameterized one, since `JsonUtils` performs no substitution — and move any body past
  roughly fifteen lines to a file.
- Log responses in system tests.
