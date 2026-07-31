# [Conventions](../conventions.md) > Build

How to compile, test, and check the module.

## Build & Test Commands

Compiling and testing go through `tools/agent-test/agent-test.sh`, a wrapper around the Gradle wrapper. It is run with bash,
from the **repository root**, and takes the same JUnit patterns Gradle does:

- Compile / type-check: `tools/agent-test/agent-test.sh --module ledger-service --compile`
- Run a single test class: `tools/agent-test/agent-test.sh --module ledger-service --tests "bot.finance.<package>.<TestClassName>"`
- Run the module's full test suite: `tools/agent-test/agent-test.sh --module ledger-service --all`
- Run the architecture-enforcement test: `tools/agent-test/agent-test.sh --module ledger-service --tests "bot.finance.architecture.CleanArchitectureTest"`

Name the class in full. The architecture tests ignore Gradle's filter, so a wildcard pattern would pull them into
a run that was meant for one class elsewhere; the wrapper leaves them out of any filtered run that does not ask
for them by name.
- Coverage report (JaCoCo): `ledger-service/gradlew test jacocoTestReport`
- Reformat to style (Spotless): `ledger-service/gradlew spotlessApply`
- Check formatting (Spotless): `ledger-service/gradlew spotlessCheck` — runs as part of `check`
- Run contract codegen: n/a — no codegen is wired

The wrapper reports a run rather than a build log: a `PASS` / `FAIL` / `COMPILE ERROR` / `NO TESTS RAN`
verdict, test counts, and each failure with its message and the stack frames inside `bot.finance`. Read that
summary — do not grep the console output or open the JUnit XML to reconstruct what it already says. Exit code is
0 only when everything passed.

Run it unpiped. Its output *is* the summary, so a `| head` or `| tail` only truncates it — and truncates the
saved output of a backgrounded run, where the failure list is the reason to read it at all.

Each run gets its own directory, `ledger-service/build/agent-runs/<label>-<timestamp>-<pid>/`, holding
`summary.txt`, the full `console.log`, the JUnit XML and the HTML report. Anything the summary omits — a full
stack trace, printed application logs — is in `console.log`. The wrapper prints the path it used.

Runs queue: a second waits for the first rather than racing it, and compiling queues too. Waiting is normal.
Give the command a generous timeout and let a run finish instead of interrupting it.

Docker must be running for container-based tests; without it they skip silently, and the summary says so when a
whole run was skipped.

The Gradle wrapper itself still works and is the way to run a task the script does not cover. It runs from the
repository root as `ledger-service/gradlew …`, so no command here needs a `cd`. Two of its invocations at once
will clobber each other's results in
`build/test-results/test/`, which is exactly what the script exists to prevent.

The wrapper's remaining options, its exit codes, and the limits of what queueing can protect are written up in
[`tools/agent-test/README.md`](../../../tools/agent-test/README.md).

## Inspecting a Dependency

What a jar on the classpath contains — its classes, a class's signatures, the code a generator emits — comes from
`tools/inspect-jar/inspect-jar.sh`, documented in
[`tools/inspect-jar/README.md`](../../../tools/inspect-jar/README.md). It resolves the jar from the module's own
dependency set, so nothing has to be located, unpacked, or decompiled by hand.
