# ADR 0002: The intent extraction schema lives at the repository root

- **Status:** Accepted
- **Date:** 2026-07-27
- **Source:** [Initialize the AI Connector Service](../implemented/2-init-ai-connector-service/2-plan-init-ai-connector-service.md)

## Context

Both sides of the intent extraction contract generate code from the same Protocol Buffers schema, and each
service is its own Gradle build. Putting the schema inside the module that serves it leaves the caller either
reaching across a module boundary or keeping a copy — and a copy compiles cleanly while it disagrees, surfacing
as a wire error only where both are deployed.

## Decision

`proto/` at the repository root holds the schema, and no module holds a second copy. A module's build adds that
directory as a proto source root and has none of its own.

See [the intent extraction contract](../../ai-connector-service/docs/contracts/in/intent-extraction.md) for what
the schema promises beyond what it can state.

## Consequences

- An incompatible change fails to compile rather than failing in production, since both builds regenerate from
  one file.
- Neither service can evolve the contract privately.
- Service images build from a repository-root context, not a module directory; a compose file or CI job scoped
  to the module fails until widened.
- Must stay true: no module gains a proto source root of its own, and a new consumer reads the root directory
  rather than vendoring a copy.
