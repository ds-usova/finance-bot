# The Evidence Writer

`tools/plan-evidence/plan-evidence.sh` measures a finished plan and writes `evidence.md` and `evidence.json`
next to it — the record that the suite was green, coverage was met and the code was formatted at a named commit.

## Why it exists

"Everything passes" is the one claim about a finished plan that nobody can check by reading it. A report written
by whoever did the work restates their own conclusion, in prose that differs every time, about a tree that has
since moved on. The evidence file replaces that: every number in it comes from a run this script performed, on
the commit it names, in a format two plans share.

The file is committed alongside the plan, so a plan archived six months ago still carries what its suite looked
like the day it closed, long after the build directory that produced those numbers was cleaned.

## Usage

Run it with bash, from the **repository root** (on Windows, a Git Bash prompt):

```
tools/plan-evidence/plan-evidence.sh --plan docs/implemented/15-a-task/plan.md
tools/plan-evidence/plan-evidence.sh --plan docs/15-a-task/plan.md --module ledger-service
tools/plan-evidence/plan-evidence.sh --plan docs/implemented/15-a-task/plan.md --verify
```

| Option             | Meaning                                                                              |
|--------------------|--------------------------------------------------------------------------------------|
| `--plan <path>`    | Required. The plan file; the evidence is written into its directory.                 |
| `--module <name>`  | Module to measure; repeatable. Default: every module in the repository.              |
| `--verify`         | Measure nothing; report whether the evidence beside the plan still describes `HEAD`. |
| `--wait <seconds>` | Passed to the test runner's queue. Default 900.                                      |

Exit codes: **0** verified, **1** not verified — a failure, coverage below the minimum, unformatted code, an
unclean tree, or (with `--verify`) evidence that has gone stale — **2** the run never started.

Every module is measured by default because *the plan is finished* is a claim about the whole tree. A plan that
touched one module still passes or fails on what it did to the other.

`--verify` does not demand that `HEAD` be the exact commit recorded. Committing the evidence moves `HEAD` by
itself, and so does every documentation commit after it, so a run whose commits since have touched only `docs/`
is still current — anything outside `docs/` is stale.

## What the file says

- **The verdict**, on its own line: `VERIFIED`, `VERIFIED WITH SKIPPED TESTS`, `NOT VERIFIED`, or
  `UNVERIFIED (uncommitted changes)`.
- **The commit** it was measured on, the branch, and whether the working tree was clean. `evidence.md` and
  `evidence.json` themselves are left out of that check — a previous run leaves them uncommitted, and counting
  them would mean a re-measure could never come back verified without a commit in between.
- **A row per module**: the runner's verdict, tests total/passed/failed/skipped, line and branch coverage, the
  module's minimum, and whether the module is formatted — `clean`, `unformatted`, or `n/a` for a module with no
  formatting check. Formatting is measured because it is enforced by a gate this script never runs: it runs the
  tests plus the coverage task, so without this column a plan could be archived over unformatted code and still
  read `VERIFIED`.
- **The least-covered classes**, fully covered ones omitted — where the next test would go. For an npm module
  the row names a source file rather than a class.

**Both stacks are measured the same way**, each through its own tooling:

| Where the number comes from | Gradle                                          | npm                                          |
|-----------------------------|-------------------------------------------------|----------------------------------------------|
| The suite                   | the test runner, via `./gradlew test`           | the test runner, via the module's npm scripts |
| Coverage                    | `jacocoTestReport.csv`                          | `coverage/coverage-summary.json`              |
| The minimum                 | `coverageMinimum` in `gradle.properties`        | `test.coverage.thresholds.lines` in the vite config |
| Formatting                  | `spotlessCheck`                                 | `npm run format:check`                        |

`evidence.json` carries the same fields for anything that would rather not parse a table.

The run directories are **not** in the file. They are `build/` paths: gone by the time anyone pulls the commit,
and pruned locally after twenty runs. The script prints them as it measures, which is when they are worth having.

It ends with a **Check this evidence** section holding the `--verify` and the re-measure command, each in its own
`shell` fence — IntelliJ IDEA puts a run action in the gutter of a fenced command, so checking the file is a click
from reading it. Both are spelled `bash tools/…`, which runs the same way on Windows as it does elsewhere.

### Skipped tests are called out, not hidden

A suite where Docker is not running skips its container-based classes and still reports every remaining test
green. That downgrades the verdict to `VERIFIED WITH SKIPPED TESTS` and prints a note, because a green line over
a skipped suite is the one way this file could mislead a reader who trusts it.

### Determinism

Two runs of the same commit produce the same file apart from the `Generated` timestamp: modules are sorted,
classes are sorted by coverage and then by name, and every number comes from a machine-readable report — JaCoCo's
CSV, or the coverage summary the npm runner writes — rather than from anything read off a console. `git diff` on
a re-run therefore shows what changed in the code, not in the formatting.

`coverage-summary.awk` beside this script is what reads the npm one. It walks the JSON by counting braces rather
than parsing JSON in general, and emits rows in the same shape the JaCoCo branch produces, so one sort and one
table render both stacks.

## On Windows

Git Bash, from the repository root. The script derives the repository root from its own location rather than
from `git rev-parse --show-toplevel`, which answers `C:/…` where the rest of the tooling uses `/c/…`; mixing the
two spellings breaks the path arithmetic. `.gitattributes` pins `*.sh` to LF.

## Where it stops

**It is evidence, not proof.** What the file gives a reader is a commit SHA and a command: `--verify` says
whether it still describes `HEAD`, and re-running without it reproduces every number from scratch. A file that
cannot survive either check is not evidence of anything.

The agent's permissions narrow the ways it could be written by something other than this script — `Edit` and
`Write` on `evidence.md` and `evidence.json` are denied outright, and a `PreToolUse` hook
(`.claude/scripts/hooks/deny-evidence-write.ps1`) rejects a shell command that names one, unless the command is a
`git` verb that only stages, commits or reads it. That closes the routes an agent would actually take. It is not a
security boundary: the file is in the repository, and anyone with an editor can change it. The SHA is what makes
the change detectable.

**It measures the tree, not the plan.** It cannot tell that the tests it ran are the tests the plan asked for,
or that a passing suite covers the behaviour the plan describes. Whether the right tests exist is what the plan's
own scenarios and the review pass are for.

**Coverage is a floor, not a goal.** The percentage says how much of the code some test executed, never whether
anything was asserted about it.
