# [Conventions](../conventions.md) > Orientation

What the module is built from, and what to read before changing it.

## Tech Stack

Versions live in the files that pin them and are not repeated here.

- Language / framework: `<e.g. Java 21 / Spring Boot, Python / FastAPI, TypeScript / NestJS>`
- Database: `<e.g. PostgreSQL with Flyway migrations; or "none — module is stateless">`
- Persistence framework / ORM: `<e.g. Spring Data JPA, a hand-rolled JDBC repository, Prisma; or "n/a">`
- Messaging / event broker: `<e.g. RabbitMQ, Kafka; or "none">`
- Caching: `<e.g. Redis; or "none">`
- External services consumed: `<what this module calls in production and over what protocol; or "none">`
- APIs exposed: `<what other systems call, and over what protocol; or "none">`
- Contract-first codegen: `<what is generated from which schema, and what the generator owns — e.g. request/response
  models are generated, controllers are hand-written against generated interfaces; or "none — all hand-written">`

## Documentation References

Background reading before making changes. These provide context; where they disagree with the conventions, the
conventions win.

- Architecture / diagrams: `<e.g. <module>/README.md — a component diagram of one primary use case; or "none">`
- Use cases: `<path — one page per use case, what it does and who it collaborates with; or "none">`
- Domain: `<path — one page per entity and value object, and the invariants it holds; or "none">`
- Contracts: `<path — one page per boundary with a system outside the module; or "none">`
- Configuration: `<path — the environment variables a deployment supplies, and what breaks without them>`
- Decision records: `<path, and where cross-module ones live if the repository splits them; or "none">`
- Runbooks / operational docs: `<path; or "none">`
- Other: `<a domain glossary, an external system's docs, a local compose file; or "none">`
