# [Conventions](../conventions.md) > Build

How to compile, test, and check the module.

## Build & Test Commands

All commands run from the **module root** (`ledger-service/`). On Windows use `gradlew.bat`, on POSIX shells
`./gradlew` — the arguments are identical.

- Compile / type-check: `./gradlew compileJava compileTestJava`
- Run a single test class: `./gradlew test --tests "bot.finance.<package>.<TestClassName>"`
- Run the module's full test suite: `./gradlew test`
- Run the architecture-enforcement test: `./gradlew test --tests "bot.finance.architecture.*"`
- Coverage report (JaCoCo): `./gradlew test jacocoTestReport`

Docker must be running for container-based tests. Every test class that uses the containerized infrastructure
carries `@Testcontainers(disabledWithoutDocker = true)` (directly or via `AbstractSystemTest`), so without Docker
those tests are skipped silently — a suspiciously fast green suite usually means Docker was down.
