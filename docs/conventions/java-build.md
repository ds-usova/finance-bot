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
tools/agent-test/agent-test.sh --module <module> --coverage
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

**Never run `gradlew test` directly.** The wrapper's queue is the only thing keeping two runs out of each other's
`build/test-results/test/`, and a raw invocation joins no queue. The symptom is not a compile error but a
`NoSuchFileException` on `build/test-results/test/binary` — someone else's run deleting the directory this one is
writing into, which reads like a Windows quirk and is not. `gradlew` stays the way to run a task the wrapper does
not wrap, such as `spotlessApply` or `jacocoTestReport`; running tests is not one of them.

## Waiting and Queueing

Concurrent runs of the **same module** share one build directory, so the wrapper queues them: a second waits for
the first rather than racing it, and compiling queues too. The queue is per-module, so a run in one module and a
run in a sibling do not wait on each other — but they share this machine's memory and one Gradle daemon pool, so
two large suites at once is still slower than one after the other. Waiting is normal and needs no action. Give
the command a generous timeout, and let a run finish instead of interrupting and retrying it.

The wrapper's remaining options, its exit codes, and the limits of what queueing can protect are in
[`tools/agent-test/README.md`](../../tools/agent-test/README.md).

## Test Coverage

Every Java module carries a coverage guardrail: `jacocoTestCoverageVerification` fails the build when
instruction coverage is below the `coverageMinimum` in the module's `gradle.properties`, currently `0.85` in
both. Generated protobuf and gRPC stubs and the `*Application` class are outside the measured set.

The guardrail belongs to neither `test` nor `check`. It runs only when `--coverage` names it, over the whole
suite — a run filtered to one class, and a plan whose later steps have not been written yet, are both expected
to be short of the threshold and are never failed for it. The verdict for a green suite under the minimum is
`COVERAGE BELOW MINIMUM`, and the summary's `== Coverage ==` section names each violated rule.

Where it is worth running: at the end of a change, once every step of it is implemented.

**Not where [`plan-evidence.sh`](#evidence-for-a-finished-plan) is about to run.** It runs `--coverage` over every
module itself, so a guardrail run of the same commit minutes earlier measures what the evidence is about to
measure, and this module's suite starts a container to do it. Let the evidence be the guardrail, and read its
per-module verdict.

## Evidence for a Finished Plan

A finished task carries `evidence.md` and `evidence.json` in its `review/` folder, written by
[`tools/plan-evidence/plan-evidence.sh`](../../tools/plan-evidence/README.md) — the verdict, the commit it was
measured on, and a row per module with its test counts, its coverage against the minimum, and whether it is
formatted:

```
tools/plan-evidence/plan-evidence.sh --plan docs/implemented/<n>-<task>/plan.md
```

It runs **after the task directory is archived and committed**, so the commit it names is the one that finished
the work and the tree it measures is clean. It measures every module, not only the ones the plan touched, and it
runs once for the task however many plans the task holds. Its own output is then committed with
`Documentation: <task> implementation evidence`.

An exit code other than 0 means the plan is not finished after all: the evidence says which module, and whether
it was a failing test, coverage below the minimum, or a suite that skipped.

`--verify` re-checks an existing evidence file against the current `HEAD` without measuring anything, which is
how a reader asks whether an archived plan's numbers still describe the code.

## Formatting

Every Java module formats with Spotless and `palantirJavaFormat`, on one version across the repository, over
`src/main/java` and `src/test/java`, with unused imports removed and imports ordered. `spotlessCheck` runs as
part of `check`, so a build fails on unformatted code rather than leaving it to review. The `spotlessApply`
command is in each module's own [Build](../../ledger-service/docs/conventions/build.md) page.

**Apply it on every change to Java sources, before the change is committed.**

**Formatting is never a reason to run the suite again.** Spotless reorders imports and whitespace; it cannot
change a test result. Run the suite once — before the format or after it, whichever the work needs — and never
on both sides of it. This module's suite takes minutes and starts a container, so a second run of an unchanged
tree is that time spent twice for an answer already known.

Palantir's parser predates the unnamed variable: a lambda parameter written `_` fails the format task rather
than being reformatted. Name it.

## Running Gradle Directly

The Gradle wrapper still works and is the way to run a task the script does not cover. It runs from the
repository root as `<module>/gradlew -p <module> <task>`, so no command needs a `cd`. Two of its invocations at
once clobber each other's results in `build/test-results/test/`, which is exactly what the script exists to
prevent.

**`-p <module>` is not optional.** The wrapper takes its project directory from the working directory, not from
where the script sits, and this repository's root holds no Gradle build — so `<module>/gradlew <task>` fails with
`does not contain a Gradle build` before running anything. A command that appears to do nothing is this one.

## Dependencies

| Fact              | Value                                                                                                          |
|-------------------|----------------------------------------------------------------------------------------------------------------|
| Manifest          | `<module>/gradle.properties` declares every version as a property; `<module>/build.gradle` reads them          |
| Managed versions  | a library declared without a version takes it from the Spring Boot BOM, so `springBootVersion` moves them all  |
| Lock file         | none                                                                                                           |
| Outdated versions | no plugin; each property is read against Maven Central                                                         |
| Vulnerabilities   | no scanner                                                                                                     |
| Routine upgrades  | patch and minor; a major is its own task, run for that dependency by name                                     |
| Custom flow       | none                                                                                                           |

A version held back on purpose is named in the module's own [Build](../../ledger-service/docs/conventions/build.md)
page, with the reason beside it.

## Inspecting a Dependency

What a jar on the classpath contains — its classes, a class's signatures, the code a generator emits — comes from
`tools/inspect-jar/inspect-jar.sh`, documented in
[`tools/inspect-jar/README.md`](../../tools/inspect-jar/README.md). It resolves the jar from the module's own
dependency set, so nothing has to be located, unpacked, or decompiled by hand.
