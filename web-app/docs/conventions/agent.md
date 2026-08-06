# [Conventions](../conventions.md) > Agent Configuration

How the coding agent works this module, and what it cannot exercise here.

## Version Control

Commit behavior, message format, branch policy and squash policy are the repository's, and are stated once in
[`ledger-service/docs/conventions/agent.md`](../../../ledger-service/docs/conventions/agent.md#version-control).
They apply here unchanged.

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

Two runs share `coverage/` and `dist/`, so a coverage run and a test run are not started at the same time.
Nothing else here is contended: there is no shared build directory of the kind that forces a queue on a Gradle
module.

## What Cannot Be Exercised Locally

**The Telegram Login Widget does not render on `localhost`.** BotFather's `/setdomain` refuses `localhost` and
bare IP addresses, and the widget checks the page's origin against the domain registered for the bot. A real
sign-in therefore cannot happen from a bare local run — it needs a tunnel, registered with BotFather, and the
steps are in the [README](../../README.md#signing-in-for-real).

Nothing else in the module needs one, and no agent should reach for a tunnel to run the tests: they build a
payload and sign it with a test bot token, the same way `ledger-service`'s fixtures do.

No sign-in bypass exists, and none is added: an authentication bypass that ships by accident costs more than the
inconvenience.
