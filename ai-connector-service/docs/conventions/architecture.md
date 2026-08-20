# [Conventions](../conventions.md) > Architecture & Layering

Dependency rules between layers and the package structure.

## Package Structure

Code is organized by Clean Architecture layer, under `bot.finance.ai`:

```
src/main
├── java/bot/finance/ai
│   ├── domain          # enterprise business rules
│   │   ├── model       # entities with identity — empty in this module
│   │   ├── value       # value objects, e.g. CurrencyCode
│   │   └── exception
│   ├── application     # application business rules
│   │   ├── usecase
│   │   ├── port        # inbound/outbound port interfaces
│   │   └── dto
│   └── adapter         # interface adapters
│       ├── config      # use-case bean wiring only
│       ├── logging     # SLF4J-backed Logger/LoggerFactory
│       ├── grpc        # gRPC service implementation, proto mapping, status mapping
│       ├── metrics     # the module's own meters, the sole point Micrometer is touched
│       ├── ai          # everything fronting the AI provider
│       ├── ledger      # everything fronting the ledger's MCP tools
│       ├── persistence # everything fronting the connector's own database
│       ├── redis       # the consumer of the ledger's change stream
│       ├── security    # the caller token, read against the ledger's published key set
│       └── scheduling  # the timer the purge runs on, and the memory's settings
└── resources
    └── prompts         # prompt templates
```

The Protocol Buffers contract lives in the repo-root `proto/` directory, not here — see
[File Locations](#file-locations).

`model` holds entities, equal by identity. `value` holds value objects, equal by attributes.

Dependencies point inward: `adapter` → `application` → `domain`, never the reverse. `domain` and `application`
depend on nothing outside the JDK — no Spring, no `jakarta.*`, no logging API, no gRPC or Protocol Buffers
types. Hence:

- use cases are plain classes, declared as beans from the adapter layer;
- generated protobuf classes are adapter types; translation happens in `adapter/grpc` and nowhere else;
- logging goes through `Logger`/`LoggerFactory` in `application/port`, implemented in `adapter/logging`.

Bean declaration — Java configuration is for classes that cannot be annotated, everything else is annotated:

- core classes and third-party classes get `@Bean` methods;
- the module's own adapters are annotated and component-scanned, never listed as `@Bean` methods. The gRPC
  service carries `@GrpcService` (`org.springframework.grpc.server.service.GrpcService`), which is what
  registers it; other adapters carry `@Component`. Conditional registration goes on the class as
  `@ConditionalOnProperty`.

Configuration placement: adapter-specific config lives in the adapter subpackage it configures. Use-case wiring
is the exception, living in `adapter/config`, which holds nothing else. `ledger.change-stream.*` binds in
`adapter/redis`. `memory.*` binds in `adapter/scheduling` — unconditionally, so the recall and backfill use
cases, beans of every context, can read their bounds with the memory off — and `adapter/config` reads from it to
build the outcome, recall and backfill use cases, and `adapter/ai` reads its embedding and backfill timeouts from
there too. `memory.enabled` gates `adapter/persistence`, `adapter/redis`, `adapter/security` and the rest of
`adapter/scheduling` alike: with it off, none of those registers a bean — except the one bean that only binds
the settings, which still registers so the rest of the module can read them.

External services get one adapter subpackage each, holding everything that fronts that system —
`adapter/ai` for the AI provider, `adapter/redis` for the ledger's change stream. `adapter/grpc` is named for
its transport instead, being the module's own front door rather than a client of anything. `adapter/scheduling`
fronts no external system either: it holds a timer that fires an inbound port, the way `adapter/config` and
`adapter/logging` front nothing outside.

## Naming Across the Layer Boundary

So a second model provider or transport can be added without touching the core:

- **No type in `domain`/`application` carries an external-system or transport name.** The adapter names its
  system, the core names the capability: `IntentInferencePort` implemented by `AiIntentInferenceAdapter`, never
  `OpenAiExtractionPort`.
- **No type in `domain`/`application` carries a transport-shaped field.** An amount crosses the outbound port
  as a decimal `String` and becomes a `Money`; the wire's `int64 minor_units` stays in `adapter/grpc`.

The first is enforced below; the second by review.

## File Locations

- Protocol Buffers schema: repo-root `proto/<snake_case_name>.proto`, shared by every module that speaks the
  contract and added to this build as an extra proto source directory. Generated Java lands in
  `build/generated/sources/proto/main/`, never edited or committed.
- Prompt templates: `src/main/resources/prompts/<kebab-case-name>.st`.
- Manual requests: `grpcurl` against the server reflection service, enabled by default.

## Architecture Enforcement

- Tool: ArchUnit (JUnit 5 integration).
- Test class: `bot.finance.ai.architecture.CleanArchitectureTest` (run command in
  [Build](build.md)).
- Rules:
  - the layer-dependency rules;
  - `org.springframework..`, `jakarta..`, `org.slf4j..`, `io.grpc..`, `com.google.protobuf..`,
    `io.modelcontextprotocol..`, `org.springframework.data..`, `org.springframework.jdbc..`,
    `org.springframework.security..`, `com.nimbusds..`, `org.flywaydb..`,
    `org.springframework.data.redis..` and `io.micrometer..` banned from `domain`/`application`; each new
    external-service library joins the list as its adapter lands;
  - `coreTypesCarryNoExternalSystemName` — no simple name in `domain`/`application` containing `OpenAi`,
    `Grpc`, `Proto`, `Mcp`, `Jdbc`, `Jwt`, `Jwks` or `Redis`; the list grows the same way;
  - `adaptersReachUseCasesThroughPorts` — no class in `adapter..` may depend on `application.usecase..`,
    `adapter/config..` exempt. The layer rule permits `adapter` → `application` wholesale, so without this an
    inbound adapter can inject a use-case class instead of its port and still compile;
  - `inboundPortCommandsAreNamedAfterTheirUseCase` — every `application/port` interface implemented by an
    `application/usecase` class names its `application/dto` parameters `<UseCase>Command`.

## Diagram Format

Repo-root [`docs/conventions/diagrams.md`](../../../docs/conventions/diagrams.md), unchanged for this module.
