# [Conventions](../conventions.md) > Orientation

Where things are and what the module is built from.

## Tech Stack

Versions are pinned in `gradle.properties` / `build.gradle`, and runtime configuration lives in
`src/main/resources/application.yaml` — neither is repeated here. What matters at the conventions level:

**Language / framework**: Java 25, Spring Boot;
**Database**: PostgreSQL 18, Flyway, Spring Data JDBC.

## Documentation References

Background reading before making changes. These documents provide context; where they disagree with the
conventions, the conventions win.

- Architecture / diagrams: [`ledger-service/README.md`](../../README.md) — a C4 **C3 Component** diagram
  (PlantUML) of every port and adapter and the external system each fronts. The repo-root `README.md` holds C1
  (System Context) and C2 (Container).
- ADRs / design decisions: repo-root `docs/implemented` — implemented plans.
- Other: `infrastructure/docker-compose.yaml` (repo root) — local Postgres for running the service outside tests.
