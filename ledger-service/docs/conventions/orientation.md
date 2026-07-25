# [Conventions](../conventions.md) > Orientation

Where things are and what the module is built from.

## Tech Stack

Versions are pinned in `gradle.properties` / `build.gradle`, and runtime configuration lives in
`src/main/resources/application.yaml` — neither is repeated here. What matters at the conventions level:

**Language / framework**: Java 25, Spring Boot;
**Database**: PostgreSQL 18, Flyway, Spring Data JDBC;
**Messaging / event broker**: none;
**Caching**: none;
**External services consumed**: Telegram Bot API, Transcription Service, AI Connector Service — all over
HTTP/REST; see the C4 diagrams referenced under [Documentation References](#documentation-references). The
Telegram Bot API is reached through the `com.github.pengrad:java-telegram-bot-api` client (version pinned in
`gradle.properties`), which the service drives in **long-polling** mode — it calls `getUpdates` outbound rather
than exposing a webhook, so every Telegram interaction is an outbound HTTP call and can be pointed at a stub
server in tests;
**Contract-first codegen**: none yet — to be decided together with the first API schema.

## Documentation References

Background reading before making changes. These documents provide context; where they disagree with the
conventions, the conventions win.

- Architecture / diagrams: [`ledger-service/README.md`](../../README.md) — a C4 **C3 Component** diagram
  (PlantUML) of every port and adapter and the external system each fronts. The repo-root `README.md` holds C1
  (System Context) and C2 (Container).
- ADRs / design decisions: repo-root `docs/implemented` — implemented plans.
- Other: `infrastructure/docker-compose.yaml` (repo root) — local Postgres for running the service outside tests.
