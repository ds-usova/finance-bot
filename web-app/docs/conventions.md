# Conventions — `web-app`

This is the **index** of the module's conventions — the single source of truth for how things are done in
`web-app`. The content lives in section files under [`conventions/`](conventions/).

Each section covers one concern, but changes rarely stay inside one: a new page touches architecture, code
style, and testing at once.

- [Orientation](conventions/orientation.md) — project structure, documentation references, tech stack, and what
  a local run cannot exercise.
- [Architecture & Layering](conventions/architecture.md) — directory structure, dependency rules, diagram format.
- [Testing Conventions](conventions/testing.md) — test layers, test tooling, naming conventions, testing style.
- [Code Style](conventions/code-style.md) — production-code style, refactoring conventions.
- [Build](conventions/build.md) — this module's commands, coverage minimum and container build.
- [Follow-Up Work](conventions/follow-up.md) — what runs once a change is complete, and what it earns.
- [Agent Configuration](conventions/agent.md) — commit behavior, parallelism, and the permissions this module's
  commands need.

The [repository-wide conventions](../../docs/conventions.md) — how documentation is written, how diagrams are
drawn, how an ADR lives, how a Node module is built — bind this module too, and these sections extend them.
