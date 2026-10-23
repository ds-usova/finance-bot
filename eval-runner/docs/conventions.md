# Conventions — `eval-runner`

This is the **index** of the module's conventions. The content lives in section files under
[`conventions/`](conventions/).

`eval-runner` is a plain Java program that runs the evaluation corpus against a running ledger and AI Connector
and writes one JSON file per run. It is described in its [README](../README.md).

- [Orientation](conventions/orientation.md) — tech stack, and what to read before changing anything.
- [Architecture](conventions/architecture.md) — package layout, the one composition point, file locations.
- [Testing Conventions](conventions/testing.md) — the test-type mapping, tooling, naming and style.
- [Code Style](conventions/code-style.md) — production-code idioms, refactoring priorities.
- [Build](conventions/build.md) — this module's name, package root, tasks and what it lacks.
- [Follow-Up Work](conventions/follow-up.md) — what runs once a change is complete.
- [Agent Configuration](conventions/agent.md) — commits, sub-agent models, parallelism, bundles.

The [repository-wide conventions](../../docs/conventions.md) bind this module too, and these sections extend them.
