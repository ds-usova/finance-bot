# [Conventions](../conventions.md) > Agent Configuration

How the coding agent works this module, and what it cannot exercise here.

## Version Control

Repository-wide, since every module shares one history: [Version Control](../../../docs/conventions/version-control.md).

## Permissions

Every command in this module is an npm invocation, and the project's permission hook refuses what
`.claude/settings.json` does not name. `permissions.allow` therefore carries:

```
Bash(npm ci:*)
Bash(npm install:*)
Bash(npm run:*)
Bash(npx tsc:*)
Edit(web-app/**)
```

Without them nothing in this module can be installed, built, tested, or verified.

## Parallelism

What the machine allows across every module at once is repository-wide:
[Parallelism](../../../docs/conventions/parallelism.md). Below is this module only.

- Max concurrent implementation agents on this module's plan: **4**.
- Concurrent test runs: **1**. Two runs share `coverage/` and `dist/`, so a coverage run and a test run are not
  started at the same time.

Nothing else here is contended: there is no shared build directory of the kind that forces a queue on a Gradle
module, and the suite runs under jsdom without starting a container.

## Follow-Up Work in a Plan

What runs once a change is complete, and what it earns, is [Follow-Up Work](follow-up.md).

A plan carries one of those kinds in its **Post-Implementation Steps** group:

- **ADRs** — one item per approved decision, as `Write ADR: <the decision, stated as a fact>`.

The approval is a numbered open question in the plan, and only an answered `yes` becomes an item.

## Reaching for a Tunnel

Never, to run the tests. [Orientation](orientation.md#what-cannot-be-exercised-locally) says what a local run
cannot exercise and why the suite does not need it: the tests sign their own payload with a test bot token.
