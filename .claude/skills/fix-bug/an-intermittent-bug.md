# An intermittent bug

Read this only where Phase 0 found the bug fails some runs and not others. A bug that fails every time reads
none of it, and neither does one that never failed.

**A single green run proves nothing about a bug that fails one run in ten.** Both gates are repeated instead of
run once.

## The rate

**Run it enough times to see it fail twice, and record both numbers** — failures and runs. That pair is the
rate, and `reproduces:` carries it as `<failures> in <runs>`.

**A rate nobody could establish is a bug that does not reproduce here.** It takes Phase 0's branch for a bug
that could reproduce but did not: stop, say what was run, and ask for the missing condition.

## The counts

Call `P` the number of runs Phase 0 took to see two failures.

- **The `red` step runs until it has failed twice, and stops at `3 × P` runs.** Failing twice is the step
  passing its own gate. Reaching `3 × P` without two failures refuses it: what Phase 0 saw is not what the test
  reproduces.
- **The `green` step runs three times the number of runs the `red` step actually took**, and passes every time.

**Phase 2 asks whether those counts are enough**, as a numbered Open Question. They are a floor derived from one
measurement, not a confidence bound.

**Whatever the answer settles is written into `reproduces:`**, beside the rate, in the words the step's agent
will read. An answer left in an Open Question reaches nobody: the agent is handed the step.

**The report says the counts both gates actually used.**
