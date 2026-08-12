# [Conventions](../conventions.md) > Orientation

Where things are and what the module is built from.

## Tech Stack

Versions are pinned in `gradle.properties` / `build.gradle`, and runtime configuration lives in
`src/main/resources/application.yaml` — neither is repeated here. What matters at the conventions level:

| What                   | This module                                                                                  |
|------------------------|----------------------------------------------------------------------------------------------|
| Language / framework   | Java 25, Spring Boot                                                                         |
| Database               | PostgreSQL 18, Flyway, Spring Data JDBC                                                      |
| Messaging, caching     | Redis, holding one capped stream the service writes to and nothing else                     |
| Services consumed      | Telegram Bot API and the Transcription Service over HTTP, the AI Connector Service over gRPC |
| APIs exposed           | [MCP](../contracts/in/mcp.md), and HTTP under `/api/v1` for the browser client               |
| Contract-first codegen | `proto/` for the AI connector, `openapi/` for the browser client                             |

Three things the table cannot carry:

- **Telegram is polled, not pushed.** The `com.github.pengrad:java-telegram-bot-api` client runs in long-polling
  mode, calling `getUpdates` outbound rather than exposing a webhook. Every Telegram interaction is therefore an
  outbound call, and a test points it at a stub server.
- **The browser API sits on a filter chain of its own**, admitted by a cookie this service issues and validates —
  [the session API](../contracts/in/web-session-api.md), and
  [the reads behind it](../contracts/in/web-browse-api.md). The MCP endpoint has its own token, issued and
  validated here too.
- **Both schemas live at the repository root**, and neither is committed as generated source. The commands are in
  [Build](build.md), the output paths in [File Locations](architecture.md#file-locations).

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
