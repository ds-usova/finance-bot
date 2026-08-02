# Conventions > Building a Java Module

How every Gradle/JVM module in this repository is compiled, tested, and inspected. A module's own
`docs/conventions/build.md` names what is specific to it — its package root, its architecture test, and any task
only it has.

A module on another stack gets its own file beside this one; nothing here is meant to generalize past the JVM.

## Build & Test Commands

Compiling and testing go through `tools/agent-test/agent-test.sh`, a wrapper around the Gradle wrapper. It is run
with bash from the **repository root**, takes the same JUnit patterns Gradle does, and names its target with
`--module`, since this repository holds more than one Gradle build:

```
tools/agent-test/agent-test.sh --module <module> --compile
tools/agent-test/agent-test.sh --module <module> --tests "<package>.<TestClassName>"
tools/agent-test/agent-test.sh --module <module> --all
```

Name a test class in full. The architecture tests ignore Gradle's filter, so a wildcard pattern would pull them
into a run meant for one class elsewhere; the wrapper leaves them out of any filtered run that does not ask for
them by name.

## Reading a Run

The wrapper reports a run rather than a build log: a `PASS` / `FAIL` / `COMPILE ERROR` / `NO TESTS RAN` verdict,
the test counts, and each failure with its message and the stack frames inside the module's own packages. Read
that summary — do not grep the console output, and do not open the JUnit XML or the HTML report to reconstruct
what it already says. Its exit code is 0 only when everything passed.

Run it unpiped. Its output *is* the summary, so a `| head` or `| tail` only truncates it — and truncates the
saved output of a backgrounded run, where the failure list is the reason to read it at all. When the per-class
table is the part in the way, `--brief` drops it and keeps the verdict, the totals and every failure. Reaching
for a pipe means the flag was not looked up: `inspect-jar.sh` has `--head <n>` for the same reason.

Each run gets a directory of its own, `<module>/build/agent-runs/<label>-<timestamp>-<pid>/`, holding
`summary.txt`, the full `console.log`, the JUnit XML and the HTML report. The name is unique per run, so two runs
of the same class never share one. Anything the summary omits — a full stack trace, printed application logs — is
in that directory's `console.log`. The wrapper prints the path it used.

## Waiting and Queueing

Concurrent runs of the **same module** share one build directory, so the wrapper queues them: a second waits for
the first rather than racing it, and compiling queues too. The queue is per-module, so a run in one module and a
run in a sibling do not wait on each other — but they share this machine's memory and one Gradle daemon pool, so
two large suites at once is still slower than one after the other. Waiting is normal and needs no action. Give
the command a generous timeout, and let a run finish instead of interrupting and retrying it.

The wrapper's remaining options, its exit codes, and the limits of what queueing can protect are in
[`tools/agent-test/README.md`](../../tools/agent-test/README.md).

## Formatting

Every Java module formats with Spotless and `palantirJavaFormat`, on one version across the repository, over
`src/main/java` and `src/test/java`, with unused imports removed and imports ordered. `spotlessCheck` runs as
part of `check`, so a build fails on unformatted code rather than leaving it to review. The `spotlessApply`
command is in each module's own [Build](../../ledger-service/docs/conventions/build.md) page.

Palantir's parser predates the unnamed variable: a lambda parameter written `_` fails the format task rather
than being reformatted. Name it.

## Running Gradle Directly

The Gradle wrapper still works and is the way to run a task the script does not cover. It runs from the
repository root as `<module>/gradlew …`, so no command needs a `cd`. Two of its invocations at once clobber each
other's results in `build/test-results/test/`, which is exactly what the script exists to prevent.

## Inspecting a Dependency

What a jar on the classpath contains — its classes, a class's signatures, the code a generator emits — comes from
`tools/inspect-jar/inspect-jar.sh`, documented in
[`tools/inspect-jar/README.md`](../../tools/inspect-jar/README.md). It resolves the jar from the module's own
dependency set, so nothing has to be located, unpacked, or decompiled by hand.
