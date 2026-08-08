# [Conventions](../conventions.md) > Architecture & Layering

Dependency rules between layers and the package structure.

## Package Structure

Code is organized by Clean Architecture layer, under `bot.finance`:

```
src/main
├── java/bot/finance
│   ├── domain          # enterprise business rules
│   │   ├── model       # entities with identity, e.g. Expense, User
│   │   ├── value       # value objects
│   │   └── exception
│   ├── application     # application business rules
│   │   ├── usecase
│   │   ├── port        # inbound/outbound port interfaces
│   │   └── dto
│   └── adapter         # interface adapters
│       ├── config      # use-case bean wiring only
│       ├── logging     # SLF4J-backed Logger/LoggerFactory
│       ├── telegram    # everything fronting the Telegram Bot API, inbound and outbound
│       ├── aiconnector # everything fronting the AI Connector Service
│       ├── mcp         # the MCP server's tools and their wire types
│       ├── security    # the filter chain, the token decoder, the minter and the JWKS endpoint
│       ├── web
│       └── persistence
└── resources
    └── db
        └── migration   # Flyway migrations
```

`model` holds entities, equal by identity. `value` holds value objects, equal by attributes.

Dependencies point inward: `adapter` → `application` → `domain`, never the reverse. `domain` and `application`
depend on nothing outside the JDK — no Spring, no `jakarta.*`, no external logging API. Hence:

- use cases are plain classes, declared as beans from the adapter layer;
- transaction boundaries live in adapters, never in `domain`/`application`;
- logging goes through `Logger`/`LoggerFactory` in `application/port`, implemented in `adapter/logging`.

Bean declaration — Java configuration is for classes that cannot be annotated, everything else is annotated:

- core classes and third-party classes (a library client, e.g. the Telegram Bot API client) get `@Bean` methods;
- the module's own adapters are `@Component`s, component-scanned, never listed as `@Bean` methods. Conditional
  registration goes on the class as `@ConditionalOnProperty`.

Configuration placement: adapter-specific config lives in the adapter subpackage it configures — persistence
config in `adapter/persistence`, web config in `adapter/web`. Use-case wiring is the exception, living in
`adapter/config`, which holds nothing else.

External services get one adapter subpackage each, holding everything that fronts that system — outbound
clients *and* any inbound adapter it drives. `adapter/telegram` holds the long-polling listener and, in time,
the file fetch and notification clients; `adapter/aiconnector` holds the gRPC client, and every generated proto
type stays inside it; `adapter/transcription` follows. `adapter/web` is for HTTP endpoints this service exposes,
not for every inbound adapter.

## Naming Across the Layer Boundary

So a second messenger, transcriber or data store can be added without touching the core:

- **No type in `domain`/`application` carries an external-system or transport name.** The adapter names its
  system, the core names the capability: `HandleIncomingMessagePort` driven by `TelegramUpdateListener`, never
  `HandleTelegramMessagePort`.
- **No type in `domain`/`application` carries a transport-shaped field.** `HandleIncomingMessageCommand` identifies
  a conversation with a `String conversationId`; a `long chatId` would be a Telegram fact leaking inward.

The first is enforced below; the second by review. How a command is named is enforced below too.

## File Locations

- Migrations: `src/main/resources/db/migration/V<NNN>__<snake_case_description>.sql`.
- Protocol Buffers schema: repo-root `proto/<snake_case_name>.proto`, shared by every module that speaks the
  contract and added to this build as an extra proto source directory. Generated Java lands in
  `build/generated/sources/proto/main/`, never edited or committed.
- API schema: repo-root `openapi/ledger-api.yaml`, layered under `openapi/paths/` and `openapi/components/`.
  Generated Java lands in `build/generated/sources/openapi/`, never edited or committed.
- Manual `.http` request files: `ledger-service/docs/requests/`, one file per endpoint — `expenses.http`,
  `categories.http`, `groupings.http` and `session.http`.

## Architecture Enforcement

- Tool: ArchUnit (JUnit 5 integration).
- Test class: `bot.finance.architecture.CleanArchitectureTest` (run command in
  [Build](build.md)).
- Rules:
  - the layer-dependency rules;
  - `org.springframework..`, `jakarta..`, `org.slf4j..`, `com.pengrad..`, `io.grpc..`, `com.google.protobuf..`,
    `bot.finance.ai..` — the generated gRPC schema's own package — `bot.finance.api..` — the generated OpenAPI
    schema's own package — and `io.modelcontextprotocol..` banned from `domain`/`application`; each new
    external-service library joins the list as its adapter lands;
  - `coreTypesCarryNoExternalSystemName` — no simple name in `domain`/`application` containing `Telegram`,
    `Whisper`, `Postgres`, `AiConnector`, `Grpc`, `Proto`, `Mcp` or `Jwt`; the list grows the same way;
  - `everyDomainModelClassIsAnEntity` — every class in `domain/model` is assignable to `Entity`;
  - `inboundPortCommandsAreNamedAfterTheirUseCase` — every `application/port` interface implemented by an
    `application/usecase` class names its `application/dto` parameters `<UseCase>Command`;
  - `authenticatedUserIdIsConstructedOnlyBySecurityAdapter` — no class outside `bot.finance.adapter.security`
    constructs `AuthenticatedUserId`.

## Diagram Format

Repo-root [`docs/conventions/diagrams.md`](../../../docs/conventions/diagrams.md), unchanged for this module.
