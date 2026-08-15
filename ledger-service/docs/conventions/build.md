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

- Coverage guardrail: `tools/agent-test/agent-test.sh --module ledger-service --coverage`
- Coverage report (JaCoCo): `ledger-service/gradlew -p ledger-service test jacocoTestReport`
- Reformat to style (Spotless): `ledger-service/gradlew -p ledger-service spotlessApply`
- Check formatting (Spotless): `ledger-service/gradlew -p ledger-service spotlessCheck` — runs as part of `check`
- Contract codegen: `ledger-service/gradlew -p ledger-service openApiGenerate`, reading `openapi/ledger-api.yaml`
  — runs automatically before `compileJava`.

A schema named in `components/schemas` and reached from inside another schema generates under its own name —
`Expense`, `RenderedMoney`, `DayTotal`, `ExpenseStatus`. An operation's **top-level** response schema does not:
the generator derives that name from the operation and status code, because every operation is reached through
an externally-`$ref`ed file under `openapi/paths/`. So `ExpensePage` is generated but unreferenced, and the
endpoint answers `ListExpenses200Response`; the same holds for `Category`, `Grouping`, `Session` and `Problem`.

Reordering the paths renames those five and breaks this module's build; no caller notices.

## Dependencies

What is shared with every Java module is [Building a Java Module](../../../docs/conventions/java-build.md#dependencies).

**Pinned on purpose:**

| Property              | Held at | Because                                                                                                                       |
|-----------------------|---------|-------------------------------------------------------------------------------------------------------------------------------|
| `kafkaConnectVersion` | `3.9.0` | Debezium 3.1.1 is built against it; the newer `kafka-clients` the Spring Boot BOM would pull dropped a call its engine makes |

## Docker

Container-based tests need Docker running; without it they skip silently, and the summary says so when a whole
run was skipped.
