# Conventions — `<module-name>`

This is the **index** of the module's conventions — the single source of truth for how things are done in
`<module-name>`. The content lives in section files under [`conventions/`](conventions/).

Each section covers one concern, but changes rarely stay inside one: a new endpoint touches architecture, code
style, and testing at once.

- [Orientation](conventions/orientation.md) — tech stack, documentation references.
- [Architecture & Layering](conventions/architecture.md) — package structure, dependency rules, file locations,
  diagram format.
- [Testing Conventions](conventions/testing.md) — which parts fall into which test type, test tooling, naming
  conventions, testing style.
- [Code Style](conventions/code-style.md) — production-code style, refactoring conventions.
- [Build](conventions/build.md) — the commands that compile, test, and check this module.
- [Follow-Up Work](conventions/follow-up.md) — what runs once a change is complete, and what it earns.
- [Agent Configuration](conventions/agent.md) — commit behaviour, sub-agent models, parallelism.

`<if the repository has a tier above: The [repository-wide conventions](../../docs/conventions.md) bind this
module too, and these sections extend them.>`
