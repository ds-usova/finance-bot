# ADR 0002: The intent extraction schema lives at the repository root

- **Status:** Accepted
- **Date:** 2026-07-27
- **Source:** [Initialize the AI Connector Service](../implemented/2-plan-init-ai-connector-service.md)

## Context

The intent extraction contract is a Protocol Buffers schema, and both sides of it generate code from that file:
the AI Connector Service its server stubs, the Ledger Service its client stubs once it starts calling. Each
service is its own independent Gradle build, so the natural place for a schema is inside the module that serves
it — which leaves the caller either reaching across a module boundary or keeping a copy. A copy compiles
cleanly while it disagrees, and the disagreement surfaces as a wire error in an environment where both are
deployed.

## Decision

`proto/` at the repository root holds the schema, and no module holds a second copy. A module's build adds that
directory as a proto source root; the module's own `src/main/proto` does not exist, so there is one place a
schema can be edited.

The service container is therefore built from a repository-root context rather than a module directory: its
image needs the module and the schema, and only the root sees both.

See [the intent extraction contract](../../ai-connector-service/docs/contracts/in/intent-extraction.md) for
what the schema promises beyond what it can state.

## Consequences

A field added or renamed reaches both builds at once, so an incompatible change fails to compile rather than
failing in production. Neither service can evolve the contract privately, and a change that suits one caller is
a change every caller regenerates against.

Building the service image copies more of the repository than the module, and a compose file or CI job that
sets a module directory as the build context fails until it is widened.

What must stay true: a module never gains a proto source root of its own, and a new consumer reads the root
directory rather than vendoring a copy.
