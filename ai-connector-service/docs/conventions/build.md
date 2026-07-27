# [Conventions](../conventions.md) > Build

How to compile, test, and check the module.

## Build & Test Commands

Compiling and testing go through `tools/agent-test/agent-test.sh`, a wrapper around the Gradle wrapper. It is run with
bash, from the **repository root**, and takes the same JUnit patterns Gradle does. This repository holds more
than one Gradle build, so every invocation names the module it targets with `--module`:

- Compile / type-check: `tools/agent-test/agent-test.sh --module ai-connector-service --compile`
- Run a single test class:
  `tools/agent-test/agent-test.sh --module ai-connector-service --tests "bot.finance.ai.<package>.<TestClassName>"`
- Run the module's full test suite: `tools/agent-test/agent-test.sh --module ai-connector-service --all`
- Run the architecture-enforcement test:
  `tools/agent-test/agent-test.sh --module ai-connector-service --tests "bot.finance.ai.architecture.CleanArchitectureTest"`

Name the class in full. The architecture tests ignore Gradle's filter, so a wildcard pattern would pull them
into a run that was meant for one class elsewhere; the wrapper leaves them out of any filtered run that does
not ask for them by name.

- Coverage report (JaCoCo): `./gradlew test jacocoTestReport` from `ai-connector-service/`
- Run contract codegen: `./gradlew generateProto` from `ai-connector-service/` — though it is rarely needed on
  its own, since `compileJava` depends on it and a `.proto` edit regenerates on the next compile.

The wrapper reports a run rather than printing a build log: a `PASS` / `FAIL` / `COMPILE ERROR` /
`NO TESTS RAN` verdict, the test counts, and each failure with its message and the stack frames inside
`bot.finance`. Read that summary — do not grep the console output, and do not open the JUnit XML or the HTML
report to reconstruct what it already says. Its exit code is 0 only when everything passed.

Each run gets a directory of its own, `ai-connector-service/build/agent-runs/<label>-<timestamp>-<pid>/`,
holding `summary.txt`, the full `console.log`, the JUnit XML, and the HTML report. The name is unique per run,
so two runs of the same class never share one. Anything the summary omits — a full stack trace, printed
application logs — is in `console.log` of that directory. The wrapper prints the path it used.

Because concurrent runs of the **same module** share one build directory, the wrapper queues them: a second run
waits for the first to finish rather than racing it. Compiling queues too, since it writes to the same
`build/classes`. The queue is per-module, so a run here and a run in a sibling service do not wait on each
other — but they do share this machine's memory and one Gradle daemon pool, so two large suites at once is
still slower than one after the other. Waiting is normal and needs no action. Give the command a generous
timeout, and let a run finish instead of interrupting and retrying it.

The suite runs entirely in-process — the one external system is stubbed by WireMock — so every failure it
reports is a real one.

The Gradle wrapper itself still works (`./gradlew …` from `ai-connector-service/`) and is the way to run a task
the script does not cover. Two of its invocations at once will clobber each other's results in
`build/test-results/test/`, which is exactly what the script exists to prevent.

The wrapper's remaining options, its exit codes, and the limits of what queueing can protect are written up in
[`tools/agent-test/README.md`](../../../tools/agent-test/README.md).
