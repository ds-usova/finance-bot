# [Conventions](../conventions.md) > Build

How to compile, test, and check the module.

## Build & Test Commands

Compiling and testing go through `tools/agent-test.sh`, a wrapper around the Gradle wrapper. It is run with bash,
from the **repository root**, and takes the same JUnit patterns Gradle does:

- Compile / type-check: `tools/agent-test.sh --module ledger-service --compile`
- Run a single test class: `tools/agent-test.sh --module ledger-service --tests "bot.finance.<package>.<TestClassName>"`
- Run the module's full test suite: `tools/agent-test.sh --module ledger-service --all`
- Run the architecture-enforcement test: `tools/agent-test.sh --module ledger-service --tests "bot.finance.architecture.CleanArchitectureTest"`

Name the class in full. The architecture tests ignore Gradle's filter, so a wildcard pattern would pull them into
a run that was meant for one class elsewhere; the wrapper leaves them out of any filtered run that does not ask
for them by name.
- Coverage report (JaCoCo): `./gradlew test jacocoTestReport` from `ledger-service/`
- Run contract codegen: n/a — no codegen is wired

The wrapper reports a run rather than a build log: a `PASS` / `FAIL` / `COMPILE ERROR` / `NO TESTS RAN`
verdict, test counts, and each failure with its message and the stack frames inside `bot.finance`. Read that
summary — do not grep the console output or open the JUnit XML to reconstruct what it already says. Exit code is
0 only when everything passed.

Each run gets its own directory, `ledger-service/build/agent-runs/<label>-<timestamp>-<pid>/`, holding
`summary.txt`, the full `console.log`, the JUnit XML and the HTML report. Anything the summary omits — a full
stack trace, printed application logs — is in `console.log`. The wrapper prints the path it used.

Runs queue: a second waits for the first rather than racing it, and compiling queues too. Waiting is normal.
Give the command a generous timeout and let a run finish instead of interrupting it.

Docker must be running for container-based tests; without it they skip silently, and the summary says so when a
whole run was skipped.

The Gradle wrapper itself still works (`./gradlew …` from `ledger-service/`) and is the way to run a task the
script does not cover. Two of its invocations at once will clobber each other's results in
`build/test-results/test/`, which is exactly what the script exists to prevent.

The wrapper's remaining options, its exit codes, and the limits of what queueing can protect are written up in
[`tools/README.md`](../../../tools/README.md).
