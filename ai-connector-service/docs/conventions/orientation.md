# [Conventions](../conventions.md) > Orientation

Where things are and what the module is built from.

## Tech Stack

Versions are pinned in `gradle.properties` / `build.gradle`, and runtime configuration lives in
`src/main/resources/application.yaml` — neither is repeated here. What matters at the conventions level:

**Language / framework**: Java 25, Spring Boot;

**Database**: none — the service holds no state. 

**Messaging / event broker**: none;

**Caching**: none;

**Exposed interface**: **gRPC only**. The service contract is the Protocol Buffers schema (see
[File Locations](architecture.md#file-locations)); the gRPC server comes from Spring Boot's own
`spring-boot-starter-grpc-server`, which wraps Spring gRPC on a Netty transport. 

**External services consumed**: an OpenAI-compatible chat-completions API, reached through **Spring AI**'s
`ChatClient`. Spring AI owns the transport, the request/response shape, and the JSON-schema-based structured
output; the service supplies the model name, the prompt, and the target record. The provider is addressed
through `spring.ai.openai.base-url`, which is what lets tests point the whole client at a stub server;

**Contract-first codegen**: **yes** — the `.proto` schema is the contract, and the `com.google.protobuf` Gradle
plugin generates the message classes and the service base class into `build/generated/sources/proto/`. Generated
sources are never edited or committed; changing the contract means changing the `.proto`.

## Documentation References

Background reading before making changes. These documents provide context; where they disagree with the
conventions, the conventions win.

- Architecture / diagrams: [`ai-connector-service/README.md`](../../README.md) — a C4 **C3 Component** diagram
  (PlantUML) of one primary use case, not of every port and adapter. The repo-root `README.md` holds C1
  (System Context) and C2 (Container).
- Use cases: [`docs/usecases/`](../usecases/) — one page per use case, what it does and who it collaborates
  with, in the product's words, and a C3 of the components, ports and external systems that use case touches.
- Contracts: [`docs/contracts/`](../contracts/) — one page per boundary with a system outside the service,
  `in/` for what it receives, `out/` for what it calls.
- Configuration: [`docs/configuration.md`](../configuration.md) — the environment variables a deployment
  supplies, and what breaks without them.
- ADRs / design decisions: repo-root [`docs/adr/`](../../../docs/adr/) — decisions the code cannot explain by
  itself; repo-root `docs/implemented` — implemented plans.
- API reference: the Protocol Buffers schema itself (see
  [File Locations](architecture.md#file-locations)) — it is the contract, not a description of one.
- Other: `infrastructure/docker-compose.yaml` (repo root) — the local runtime for the service and its siblings.
