# [Conventions](../conventions.md) > Code Style

The idioms production code follows, and what a cleanup pass may touch.

## Production-Code Style

- Dependency-injection style: `<e.g. constructor injection only — never field injection>`
- Null-handling policy: `<e.g. never return null — use Optional or throw a domain exception>`
- Error / exception conventions: `<e.g. a domain exception per error case, mapped to a response by a global handler>`
- Logging: `<e.g. a structured logger per class, debug level for infrastructure operations; or "none yet">`
- Imports / qualified names: `<e.g. import types directly; never fully qualified names inside code bodies>`
- Method decomposition: `<e.g. extract a private helper when a method exceeds one screen>`
- Domain ↔ persistence mapping: `<e.g. toDomain()/fromDomain() on the record class — never private helpers in the
  adapter; or a dedicated mapper class per adapter>`
- Outbound adapter idioms: `<e.g. repository methods over hand-written SQL; transactions on write methods;
  declarative HTTP client interfaces>`
- Inbound binding / validation / error mapping: `<the framework mechanism the inbound adapter uses>`

Add a subsection per layer where the layers differ enough that one list would flatten them.

## Refactoring Conventions

What a cleanup pass prioritizes, where extracted code goes, and what it must leave alone. Without this section a
default checklist applies — cross-class duplication, duplicated test fixtures, idiom inconsistency, leftover
scaffolding, needless complexity, import hygiene.

- Priorities: `<what to tackle first — e.g. deduplicating mapping logic, collapsing needless conditionals>`
- Extraction targets: `<where extracted shared code goes — e.g. a mapper class per adapter package, shared test
  builders into a test-fixtures source set>`
- Shared helpers that may be extended: `<existing helpers a consolidation may add to; or "none">`
- Leave-alone list: `<what must never be touched even when it looks duplicated — e.g. generated code, DTOs
  mirroring the API schema, a package mid-migration; or "nothing">`
- Thresholds: `<when extraction is worth it — e.g. only when logic repeats in 2+ classes; or "use your judgment">`
