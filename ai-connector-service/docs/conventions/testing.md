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
    ├── IntentFixtures         # domain Intent / Money builders
    └── RequestFixtures        # valid ExtractIntentsRequest builders
```

## Test Layers

- **Unit** — `domain/`, `application/usecase/`, self-validating `application/dto` records, and pure `*Utils`
  mappers in an adapter package. Plain JUnit, outbound ports mocked, no Spring context. A proto mapper is a
  unit target: building a message needs no server and no channel.
- **Integration, outbound** — `adapter/ai/` via `@AiAdapterTest`. Wire only the adapter under test, call its
  public methods directly, mock nothing. Owns the request Spring AI sends, the schema it derives from the
  target record, and how a stubbed response, a provider error and a malformed body map onto the port's result
  or exception.
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
- `@AiAdapterTest` — boots the adapter under test, its `ChatClient` configuration and Spring AI's OpenAI
  autoconfiguration, with `base-url` on the stub server. A real client over a stubbed transport is what these
  tests exist for; a mocked `ChatClient` would exercise none of it.
- **Reach WireMock through `WireMockSupport.SERVER`, never the static DSL.** `WireMock.stubFor(...)`,
  `verify(...)` and `findAll(...)` address `localhost:8080` and fail with a connection error before any
  assertion runs. Only the pure builders — `post`, `urlPathEqualTo`, `okJson`, `postRequestedFor`, `equalTo`,
  `matchingJsonPath` — are safe to static-import.
- `ChatCompletionFixtures` embeds the extracted JSON **as a string inside `choices[0].message.content`**.
  Returning the payload directly yields a stub Spring AI cannot parse, and a failure that reads like a mapping
  bug.
- `LogCapture` requires the real `Slf4jLoggerFactory`: it keys on the logger named after the class, a name a
  mocked factory never produces.

## Naming Conventions

- Test methods: `when<Condition>_then<Result>()`.
- Test classes: `<ClassUnderTest>Test`; `<Flow>SystemTest` for system tests.
- Helpers: static `*Utils` classes with a private constructor.
- New shared builders and factories go in `bot.finance.ai.common` and get listed in
  [Package Structure](#package-structure), so later tests reuse them instead of recreating them.

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
