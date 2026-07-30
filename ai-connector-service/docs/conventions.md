# Conventions — `ai-connector-service`

This is the **index** of the module's conventions — the single source of truth for how things are done in
`ai-connector-service`. The content lives in section files under [`conventions/`](conventions/).

Each section covers one concern, but changes rarely stay inside one: a new RPC touches architecture, code style,
and testing at once.

- [Orientation](conventions/orientation.md) — project structure, documentation references, tech stack.
- [Architecture & Layering](conventions/architecture.md) — package structure, dependency rules, diagram format.
- [Testing Conventions](conventions/testing.md) — test layers, test tooling, naming conventions, testing style.
- [Code Style](conventions/code-style.md) — production-code style, refactoring conventions.
- [Build](conventions/build.md) — build & test commands.
- [Agent Configuration](conventions/agent.md) — commit behavior, sub-agent models, parallelism, plan-file
  locations, post-implementation actions.
