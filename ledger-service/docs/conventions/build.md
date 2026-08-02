# [Conventions](../conventions.md) > Build

What is specific to this module. The wrapper, how to read a run, queueing, and dependency inspection are
[Building a Java Module](../../../docs/conventions/java-build.md).

| Name                     | Value                                            |
|--------------------------|--------------------------------------------------|
| `--module`               | `ledger-service`                                 |
| Test package root        | `bot.finance`                                    |
| Architecture-enforcement | `bot.finance.architecture.CleanArchitectureTest` |

## Module Tasks

- Coverage report (JaCoCo): `ledger-service/gradlew test jacocoTestReport`
- Reformat to style (Spotless): `ledger-service/gradlew spotlessApply`
- Check formatting (Spotless): `ledger-service/gradlew spotlessCheck` — runs as part of `check`
- Contract codegen: none is wired.

## Docker

Container-based tests need Docker running; without it they skip silently, and the summary says so when a whole
run was skipped.
