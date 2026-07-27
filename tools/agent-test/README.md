# The Test Runner

`tools/agent-test/agent-test.sh` is the single entry point for compiling and testing a module — `ledger-service` or
`ai-connector-service`, selected with `--module`. It wraps the Gradle wrapper and turns a build into a
ready-made summary, so that reading the result of a run is not an ad-hoc parsing problem for whoever ran it.

## Why it exists

A raw `./gradlew test` produces two things: an exit code and a console log. Neither is a result you can read
directly. Anyone who wants to know *which* test failed and *why* has to reconstruct it — by grepping the console,
by parsing `build/test-results/test/*.xml`, or by opening the HTML report. Every reader invents a slightly
different reconstruction.

The second problem is concurrency. Several processes working in the same checkout share one `build/`
directory, one set of Gradle caches, one Docker daemon, and this machine's RAM. Two plain Gradle runs at once
overwrite each other's results in `build/test-results/test/`, so a run can read a green report that belonged to
somebody else.

The runner addresses both: it reports rather than logs, and it keeps concurrent runs from stepping on each other.

## Usage

Run it with bash, from the **repository root** (on Windows that means Git Bash — see below):

```
tools/agent-test/agent-test.sh --module ledger-service --compile
tools/agent-test/agent-test.sh --module ledger-service --tests "bot.finance.application.usecase.HandleIncomingMessageUseCaseTest"
tools/agent-test/agent-test.sh --module ai-connector-service --all
```

| Option              | Meaning                                                                     |
|---------------------|-----------------------------------------------------------------------------|
| `--module <name>`   | Required. The module directory at the repository root to build and test.    |
| `--tests <pattern>` | JUnit pattern to run; repeatable. Omit (or `--all`) to run the whole suite. |
| `--compile`         | Compile main and test sources only; run no tests.                           |
| `--all`             | Run the whole suite. The default when no `--tests` is given.                |
| `--label <name>`    | Prefix of the run directory. Defaults to the test class name.               |
| `--wait <seconds>`  | How long to wait for another run to finish before giving up. Default 540.   |
| `--no-lock`         | Start immediately even if another run is in progress.                       |
| `--keep <count>`    | Previous run directories to keep. Default 20.                               |

Exit codes: **0** everything passed, **1** tests failed or the build broke, **2** the run never started (bad
option, or the queue timed out).

### On Windows

Run it from a **Git Bash** prompt, at the repository root:

```
./tools/agent-test/agent-test.sh --module ledger-service --all
```

The runner relies on the GNU utilities that come with Git Bash — `stat -c`, `kill -0`, `date +%s` — and on its
translation of `/c/…` paths into Windows paths when they reach the JVM as `-DagentRunDir`. `.gitattributes` pins
`*.sh` to LF, because a CRLF checkout fails on the first line with a bare `$'\r': command not found`.

## What a run produces

The summary goes to stdout and to `summary.txt` inside a run directory of its own:

```
<module>/build/agent-runs/<label>-<timestamp>-<pid>/
    summary.txt      the verdict, the counts, and every failure
    console.log      the untouched Gradle output
    test-results/    the JUnit XML
    test-report/     the HTML report
```

The verdict is one of `PASS`, `FAIL`, `COMPILES`, `COMPILE ERROR`, `NO TESTS RAN`, or `NOT RUN`. Failures carry
their assertion message and the stack frames inside `bot.finance`; the framework frames are dropped, because they
never say what to fix. Anything the summary leaves out — a full stack trace, printed application logs — is in
`console.log` next to it.

The directory name is unique per invocation, so two runs that pick the same label still cannot delete each
other's results.

Nothing cleans these up on a schedule. Each run, as it starts and before it creates its own directory, deletes
every run directory beyond the `--keep` most recently modified — 20 by default, so roughly the last twenty runs
survive and the current one is never at risk. Pruning happens before the queue is joined, so a run that ends up
waiting has already done it. Beyond that, the directories live under the module's own `build/`, which means
`./gradlew clean` removes them all, as does deleting `build/` by hand; git ignores the whole tree.

When a build fails before any test runs, there is no JUnit XML to summarize. The runner then lifts the
compilation errors and Gradle's own failure block out of the console log, since that is the whole story.

## Concurrency

Runs queue on a directory lock at `<module>/build/agent-runs/.lock`. The lock is per module, so a
`ledger-service` run and an `ai-connector-service` run never wait on each other. A second run of the same
module waits and says so; waiting is normal. Give the command a generous timeout rather than interrupting it.

The lock covers `--compile` as well as tests, because compilation writes to the shared `build/classes` — a
compile landing in the middle of another run's test JVM is exactly the kind of interference the lock exists to
prevent.

A lock is considered stale, and is broken automatically, when either the process that holds it is gone or it is
older than 15 minutes. A lock younger than 30 seconds is always treated as live: `mkdir` stamps the directory
before its holder can write its PID into it, and without that grace period a waiter could break a lock that was
still being taken. Breaking is attempted at most three times before the run gives up with `NOT RUN`.

`--no-lock` skips the queue. The results still land in a separate directory, but the runs compete for
`build/classes`, Docker, and RAM, so the summary is annotated to say the run was unlocked.

### What the lock does not do

It serialises *builds*, not *edits*. Nothing stops a file from being changed while another run is compiling, so
in a shared checkout a run can still compile somebody else's half-finished work. Making that impossible needs a
worktree per writer, not a better lock.

## Test filtering and the architecture tests

Prefer a fully qualified class name in `--tests`. The ArchUnit engine does not apply JUnit's post-discovery
filters, so a pattern like `bot.finance.**.SomeTest` also runs every rule in `CleanArchitectureTest` — a filtered
run would report architecture failures the caller neither asked about nor caused.

The runner compensates: whenever `--tests` is given and no pattern mentions `architecture`, it excludes the
ArchUnit engine for that run. Name the architecture test explicitly to run it, and note that `--all` always
includes it.

## Docker

Docker must be running for the container-based tests. Every test class that uses containerized infrastructure
carries `@Testcontainers(disabledWithoutDocker = true)`, directly or through `AbstractSystemTest`, so without
Docker those tests are skipped silently. The summary calls it out when a whole run was skipped.

Container ports are not a source of contention: Postgres comes up on a random host port through Testcontainers,
WireMock uses `dynamicPort()`, and the application binds `@LocalServerPort`. What concurrent runs actually
compete for is memory — each one is a Gradle daemon, a test JVM, and a Postgres container.

## The pieces

| File                   | Role                                                                                                                                                                                                                                    |
|------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `agent-test.sh`        | Argument handling, the lock, the run directory, and the final report.                                                                                                                                                                   |
| `agent-reports.gradle` | A Gradle init script, applied with `-I`. Points each `Test` task's JUnit XML, HTML report, and binary results at the run directory, and drops the ArchUnit engine when the run is filtered. Does nothing unless `-DagentRunDir` is set. |
| `junit-summary.awk`    | Turns the JUnit XML of one run into the plain-text summary.                                                                                                                                                                             |

## Scale

For `ledger-service` as it stands, the whole suite takes about 28 seconds and a single test class about 6. At
that size, queueing costs almost nothing, and per-run isolation matters far more than throughput: a wrong
answer is expensive, half a minute of waiting is not.

That trade-off is worth revisiting, per module, when its suite passes roughly three minutes — the growth will
come from the container-based system tests, which already dominate the wall time. At that point a worktree per
writer starts paying for its cold build cache, and it removes the shared-source-tree problem the lock cannot
reach.

Raw `./gradlew …` from a module's own directory still works and remains the way to run a task the runner does
not wrap, such as `jacocoTestReport`. Two raw invocations at once will clobber each other's results in
`build/test-results/test/`, which is what the runner exists to prevent.
