# Conventions — `ledger-service`

This is the **index** of the module's conventions — the single source of truth for how things are done in
`ledger-service`. The content lives in section files under [`conventions/`](conventions/).

Each section covers one concern, but changes rarely stay inside one: a new endpoint touches architecture,
code style, and testing at once.

- [Orientation](conventions/orientation.md) — project structure, documentation references, tech stack.
- [Architecture & Layering](conventions/architecture.md) — package structure, dependency rules, diagram format.
- [Testing Conventions](conventions/testing.md) — test layers, test tooling, naming conventions, testing style.
- [Code Style](conventions/code-style.md) — production-code style, refactoring conventions.
- [Build](conventions/build.md) — build & test commands.
- [Agent Configuration](conventions/agent.md) — commit behavior, sub-agent models, parallelism, plan-file
  locations, post-implementation actions and the plan sections that authorize them.

The [repository-wide conventions](../../docs/conventions.md) — how documentation is written, how diagrams are
drawn, how an ADR lives — bind this module too, and these sections extend them.