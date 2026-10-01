# [Conventions](../conventions.md) > Build

What is specific to this module. The wrapper, how to read a run, queueing, and dependency inspection are
[Building a Java Module](../../../docs/conventions/java-build.md).

| Name                     | Value                                            |
|--------------------------|--------------------------------------------------|
| `--module`               | `ledger-service`                                 |
| Test package root        | `bot.finance`                                    |
| Architecture-enforcement | `bot.finance.architecture.CleanArchitectureTest` |
| Coverage minimum         | `0.85` instructions                              |

## Module Tasks

- Architecture check: `tools/agent-test/agent-test.sh --module ledger-service --tests "bot.finance.architecture.CleanArchitectureTest"`
- Coverage guardrail: `tools/agent-test/agent-test.sh --module ledger-service --coverage`
- Coverage report (JaCoCo): `ledger-service/gradlew -p ledger-service jacocoTestReport`, after a `--coverage` run
- Reformat to style (Spotless): `ledger-service/gradlew -p ledger-service spotlessApply`
- Check formatting (Spotless): `ledger-service/gradlew -p ledger-service spotlessCheck` — runs as part of `check`
- Contract codegen: `ledger-service/gradlew -p ledger-service openApiGenerate`, reading `openapi/ledger-api.yaml`
  — runs automatically before `compileJava`.

A schema named in `components/schemas` and reached from inside another schema generates under its own name —
`Expense`, `RenderedMoney`, `DayTotal`, `ExpenseStatus`. An operation's **top-level** response schema does not:
the generator derives that name from the operation and status code, because every operation is reached through
an externally-`$ref`ed file under `openapi/paths/`. So `ExpensePage` is generated but unreferenced, and the
endpoint answers `ListExpenses200Response`. The same holds for `Category`, `Grouping`, `Session`, `Problem`,
`AcceptanceRequest`, `Acceptance`, `Preferences` and `PreferencesUpdate`.

Reordering the paths renames those types and breaks this module's build; no caller notices.

## Test JVM

`build.gradle` gives the test JVM a 1 GB heap and strips `TELEGRAM_BOT_TOKEN` from its environment.

## CI

`.github/workflows/build.yml` runs `./gradlew build` on every pull request to `main`. The coverage guardrail is not
part of it.

## Dependencies

What is shared with every Java module is [Building a Java Module](../../../docs/conventions/java-build.md#dependencies).

**Pinned on purpose:**

| Property              | Held at | Because                                                                                                                       |
|-----------------------|---------|-------------------------------------------------------------------------------------------------------------------------------|
| `kafkaConnectVersion` | `3.9.0` | Debezium 3.1.1 is built against it; the newer `kafka-clients` the Spring Boot BOM would pull dropped a call its engine makes |

## Docker

Container-based tests need Docker running; without it they skip silently, and the summary says so when a whole
run was skipped.
