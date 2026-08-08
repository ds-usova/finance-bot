# [Conventions](../conventions.md) > Orientation

Where things are and what the module is built from.

## Tech Stack

Versions are pinned in `gradle.properties` / `build.gradle`, and runtime configuration lives in
`src/main/resources/application.yaml` — neither is repeated here. What matters at the conventions level:

**Language / framework**: Java 25, Spring Boot;
**Database**: PostgreSQL 18, Flyway, Spring Data JDBC;
**Messaging / event broker**: none;
**Caching**: none;
**External services consumed**: Telegram Bot API and Transcription Service over HTTP/REST, AI Connector Service
over **gRPC**; see the C4 diagrams referenced under [Documentation References](#documentation-references). The
Telegram Bot API is reached through the `com.github.pengrad:java-telegram-bot-api` client (version pinned in
`gradle.properties`), which the service drives in **long-polling** mode — it calls `getUpdates` outbound rather
than exposing a webhook, so every Telegram interaction is an outbound HTTP call and can be pointed at a stub
server in tests. The gRPC client comes from Spring Boot's own `spring-boot-starter-grpc-client`, which wraps
Spring gRPC;
**APIs exposed**: an MCP server endpoint over HTTP, built on Spring AI's MCP server and reachable only with a
token this service issues and validates itself — see
[Agent acting for a user — the expense proposal tool](../contracts/in/mcp.md); and an HTTP API under `/api/v1`
for the browser client, on a filter chain of its own, reachable with a cookie this service issues and validates
itself — a session API, see [A person signing in from a browser](../contracts/in/web-session-api.md), and the
reads behind it, see
[A person browsing their ledger from a browser](../contracts/in/web-browse-api.md);
**Contract-first codegen**: **yes**, on two schemas, both at the repository root and neither committed as
generated source. The `proto/` schema is the contract with the AI Connector Service, and the
`com.google.protobuf` Gradle plugin generates the message classes and client stubs into
`build/generated/sources/proto/main/`. The `openapi/` specification is the contract with the browser client,
shared with `web-app`, and the `org.openapi.generator` plugin generates one endpoint interface per tag into
`build/generated/sources/openapi/` (see [File Locations](architecture.md#file-locations)).

## Documentation References

Background reading before making changes. These documents provide context; where they disagree with the
conventions, the conventions win.

- Architecture / diagrams: [`ledger-service/README.md`](../../README.md) — a C4 **C3 Component** diagram
  (PlantUML) of one primary use case, not of every port and adapter. The repo-root `README.md` holds C1
  (System Context) and C2 (Container).
- Use cases: [`docs/usecases/`](../usecases/) — one page per use case, what it does and who it collaborates
  with, in the product's words, and a C3 of the components, ports and external systems that use case touches.
- Domain: [`docs/domain/`](../domain/) — one page per entity and value object, what it represents and the
  invariants under which it refuses to exist. A type's own rules live here, not in the use cases that apply them.
- Contracts: [`docs/contracts/`](../contracts/) — one page per boundary with a system outside the service,
  `in/` for what it receives, `out/` for what it calls.
- Configuration: [`docs/configuration.md`](../configuration.md) — the environment variables a deployment
  supplies, and what breaks without them.
- Manual requests: [`docs/requests/`](../requests/) — one `.http` file per HTTP boundary this service exposes,
  for driving an endpoint by hand against a running service.
- ADRs / design decisions: [`docs/adr/`](../adr/) — decisions the code cannot explain by itself, whose
  consequences stay inside this service; repo-root [`docs/adr/`](../../../docs/adr/) for those that also
  constrain another service or the repository. One number sequence spans both, so each tier carries gaps.
  Lifecycle rules: [`docs/conventions/adr.md`](../../../docs/conventions/adr.md). Repo-root `docs/implemented` —
  one directory per implemented task, holding its `design.md` and `plan.md`.
- Other: `infrastructure/docker-compose.yaml` (repo root) — local Postgres for running the service outside tests.

How every page above is written: [`docs/conventions/documentation.md`](../../../docs/conventions/documentation.md).
