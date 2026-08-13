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
│       ├── async       # background work dispatched off the request thread, fronting no external system
│       ├── config      # use-case bean wiring only
│       ├── logging     # SLF4J-backed Logger/LoggerFactory
│       ├── telegram    # everything fronting the Telegram Bot API, inbound and outbound
│       ├── aiconnector # everything fronting the AI Connector Service
│       ├── mcp         # the MCP server's tools and their wire types
│       ├── security    # the filter chain, the token decoder, the minter and the JWKS endpoint
│       ├── cdc         # the embedded Debezium engine, its recovery operation and its meters
│       ├── redis       # the change stream writer
│       ├── web
│       └── persistence
└── resources
    └── db
        └── migration   # Flyway migrations
```

Entities are equal by identity, values by attributes.

Dependencies point inward: `adapter` → `application` → `domain`, never the reverse. `domain` and `application`
depend on nothing outside the JDK. Hence:

- use cases are plain classes, declared as beans from the adapter layer;
- transaction boundaries live in adapters, never in `domain`/`application`;
- logging goes through `Logger`/`LoggerFactory` in `application/port`, implemented in `adapter/logging`.

Bean declaration — Java configuration is for classes that cannot be annotated, everything else is annotated:

- core classes and third-party classes get `@Bean` methods;
- the module's own adapters are `@Component`s, component-scanned, never listed as `@Bean` methods. Conditional
  registration goes on the class as `@ConditionalOnProperty`.

Adapter-specific config lives in the adapter subpackage it configures. Use-case wiring is the exception, living
in `adapter/config`, which holds nothing else.

External services get one adapter subpackage each, holding everything that fronts that system — outbound clients
*and* any inbound adapter it drives, generated types included. `adapter/web` is for the HTTP endpoints this
service exposes, and no other inbound adapter.

## Naming Across the Layer Boundary

So a second messenger, transcriber or data store can be added without touching the core:

- **No type in `domain`/`application` carries an external-system or transport name.** The adapter names its
  system, the core names the capability: `HandleIncomingMessagePort` driven by `TelegramUpdateListener`, never
  `HandleTelegramMessagePort`.
- **No type in `domain`/`application` carries a transport-shaped field.** `HandleIncomingMessageCommand` identifies
  a conversation with a `String conversationId`; a `long chatId` would be a Telegram fact leaking inward.

The first is enforced below, along with how a command is named. The second by review.

## File Locations

- Migrations: `src/main/resources/db/migration/V<NNN>__<snake_case_description>.sql`.
- Protocol Buffers schema: repo-root `proto/<snake_case_name>.proto`, shared by every module that speaks the
  contract and added to this build as an extra proto source directory. Generated Java lands in
  `build/generated/sources/proto/main/`, never edited or committed.
- API schema: repo-root `openapi/ledger-api.yaml`, whose `components/schemas` holds every schema. Paths,
  parameters and responses stay layered under `openapi/paths/` and `openapi/components/`. Generated Java lands in
  `build/generated/sources/openapi/`, never edited or committed.
- Manual `.http` request files: `ledger-service/docs/requests/<tag>/<operation>.http` — one file per endpoint,
  in a directory per tag the API schema declares, named after the operation. An endpoint outside the schema goes
  under `management/`.

## Architecture Enforcement

- Tool: ArchUnit (JUnit 5 integration).
- Test class: `bot.finance.architecture.CleanArchitectureTest` (run command in [Build](build.md)). It holds the
  current list behind every rule below; a list repeated here drifts.
- Rules:
  - the layer-dependency rules;
  - every framework and external-service library banned from `domain`/`application` — Spring, `jakarta`, gRPC
    and their kind, and the repository's own generated schema packages `bot.finance.ai..` and
    `bot.finance.api..`. A library joins as its adapter lands;
  - `coreTypesCarryNoExternalSystemName` — no simple name in `domain`/`application` carries an external
    system's, such as `Telegram`, `Postgres` or `Mcp`;
  - `everyDomainModelClassIsAnEntity` — every class in `domain/model` is assignable to `Entity`;
  - `inboundPortCommandsAreNamedAfterTheirUseCase` — every `application/port` interface implemented by an
    `application/usecase` class names its `application/dto` parameters `<UseCase>Command`;
  - `authenticatedUserIdIsConstructedOnlyBySecurityAdapter` — no class outside `bot.finance.adapter.security`
    constructs `AuthenticatedUserId`.

## Diagram Format

Repo-root [`docs/conventions/diagrams.md`](../../../docs/conventions/diagrams.md), unchanged for this module.
