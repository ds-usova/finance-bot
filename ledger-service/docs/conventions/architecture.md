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
`domain` and `application` stay framework-agnostic: no Spring annotations (`@Service`, `@Component`,
`@Transactional`, …) anywhere in either package. Consequences of that rule:

- Use cases are plain classes, wired as beans from configuration classes in the adapter layer, not annotated
  themselves.
- Transaction boundaries live in adapters (see
  [Production-Code Style](code-style.md#production-code-style)), never in `domain`/`application`.

Configuration placement:

- adapter-specific framework config lives in the adapter subpackage it configures — persistence config
  (e.g. custom Spring Data JDBC converters) in `adapter/persistence`, web config (e.g. the global exception
  handler, MVC settings) in `adapter/web`, and so on for future adapter subpackages;
- use-case bean wiring is the one exception: use cases belong to no single adapter, so their `@Configuration`
  classes live in `adapter/config` — which holds use-case wiring only, never adapter-specific config.

Outbound adapters for external services (Telegram file fetch / notification, Transcription client, AI Connector
client) get **one adapter subpackage per external system** when they land, e.g. `adapter/telegram`,
`adapter/transcription`, `adapter/aiconnector` (exact names to be settled when the first one is implemented).

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
