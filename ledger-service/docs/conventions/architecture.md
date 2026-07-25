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
│       ├── config      # use-case bean wiring (@Configuration classes only — no adapter-specific config)
│       ├── logging     # SLF4J-backed implementation of the core's Logger/LoggerFactory abstraction
│       ├── telegram    # everything fronting the Telegram Bot API, inbound and outbound
│       ├── web
│       └── persistence
└── resources
    └── db
        └── migration   # Flyway migrations
```

`model` vs `value` — both live under `domain` but hold different kinds of object:

- **`model`** — entities with an identity that persists across changes (e.g. `Expense`, `User`). Two instances
  are equal if their identity (e.g. an ID) matches, even if every other field differs; the same entity can
  change state over its lifetime and still be "the same" `Expense`.
- **`value`** — value objects with no identity of their own (e.g. an amount, a currency, a time period). Two
  instances are equal if all their attributes match.

Dependencies point inward: `adapter` depends on `application`, which depends on `domain` — never the reverse.
`domain` and `application` stay framework-agnostic: no Spring, no `jakarta.*`, and no external logging API
anywhere in either package — the core depends on nothing outside the JDK. Consequences of that rule:

- Use cases are plain classes, wired as beans from configuration classes in the adapter layer, not annotated
  themselves.
- Transaction boundaries live in adapters (see
  [Production-Code Style](code-style.md#production-code-style)), never in `domain`/`application`.
- Logging goes through the application's own `Logger`/`LoggerFactory` interfaces in `application/port` — the
  core is forced to by this rule, and the other layers follow the same pattern by convention (see
  [Production-Code Style](code-style.md#production-code-style)); the SLF4J-backed implementation lives in
  `adapter/logging`.

Bean declaration style — **Java configuration is for classes that cannot be annotated; everything else is
annotated**:

- **core classes** (use cases and anything else in `domain`/`application`) are declared with `@Bean` methods in
  `@Configuration` classes, because the rule above forbids them from carrying Spring annotations themselves;
- **third-party classes** (a client object from a library, e.g. the Telegram Bot API client) likewise need a
  `@Bean` method — there is nowhere to put an annotation;
- **the module's own adapter classes are `@Component`s**, found by component scanning, never listed as `@Bean`
  methods. An adapter lives in the framework's world already, so a configuration class that does nothing but
  call its constructor is indirection with no benefit. Conditional registration goes on the class as
  `@ConditionalOnProperty`, not on a factory method.

Configuration placement:

- adapter-specific framework config lives in the adapter subpackage it configures — persistence config
  (e.g. custom Spring Data JDBC converters) in `adapter/persistence`, web config (e.g. the global exception
  handler, MVC settings) in `adapter/web`, and so on for future adapter subpackages;
- use-case bean wiring is the one exception: use cases belong to no single adapter, so their `@Configuration`
  classes live in `adapter/config` — which holds use-case wiring only, never adapter-specific config.

Adapters for external services get **one adapter subpackage per external system**, holding everything that fronts
that system — outbound clients *and* any inbound adapter it drives. `adapter/telegram` exists and contains both
the long-polling update listener (inbound) and, in time, the file fetch and notification clients (outbound);
`adapter/transcription` and `adapter/aiconnector` follow when they land. `adapter/web` is the home for HTTP
endpoints this service exposes, not for every inbound adapter — a non-HTTP inbound adapter belongs to its
external system's subpackage.

## Naming Across the Layer Boundary

The core must not know **which** external system it is talking to, so that a second messenger, transcriber, or
data store can be added without touching it. Two rules follow:

- **No type in `domain`/`application` carries an external-system or transport name.** The adapter names its
  external system; the core names the capability. So `HandleIncomingMessagePort` in `application/port`, driven by
  `TelegramUpdateListener` in `adapter/telegram` — never `HandleTelegramMessagePort`.
- **No type in `domain`/`application` carries a transport-shaped field.** `IncomingMessage` identifies a
  conversation with a `String conversationId`, and the Telegram adapter renders the numeric chat id into it. A
  `long chatId` in the core would be a Telegram fact leaking inward.

Both are enforced (see [Architecture Enforcement](#architecture-enforcement)); the second only by review.

## File Locations

- Migration folder + naming scheme: `src/main/resources/db/migration/V<NNN>__<snake_case_description>.sql`
  (e.g. `V001__create_expense_table.sql`).
- API schema file: none yet — intended location `src/main/resources/schemas/api.yaml`, to be confirmed when the
  first contract is authored.
- Manual/`.http` request files: none yet — intended location `ledger-service/docs/requests/`, to be confirmed
  when the first one is written.

## Architecture Enforcement

- Tool: ArchUnit (JUnit 5 integration).
- Test class: `bot.finance.architecture.CleanArchitectureTest` (run command in
  [Build & Test Commands](build.md#build--test-commands)).
- Scope:
  - the layer-dependency rules;
  - the framework-agnostic core — `org.springframework..`, `jakarta..`, `org.slf4j..` and `com.pengrad..` are
    banned from `domain`/`application`. Each new external-service library joins this list as its adapter lands;
  - `coreTypesCarryNoExternalSystemName` — no type in `domain`/`application` may have a simple name containing an
    external-system name (`Telegram`, `Whisper`, `Postgres`), per
    [Naming Across the Layer Boundary](#naming-across-the-layer-boundary). This list grows the same way.
