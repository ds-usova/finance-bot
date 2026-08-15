# Applying one step

What each kind of step edits, what it runs, and when it refuses. Read beside the step itself, which
`fix.sh show <ID>` prints. The sequence around a step is the skill's: the validate gate, the order the kinds run
in, the commit, and what is never done.

| Kind        | Edit                                                        | Then run                                                           |
|-------------|-------------------------------------------------------------|--------------------------------------------------------------------|
| `stabilize` | carry each broken call site back to compiling, nothing more | the module's whole suite · the architecture check                 |
| `red`       | only `test-files:`, and no production file at all           | `runs:` — **it must fail, with the symptom `reproduces:` names** |
| `green`     | only `files:`, and no test file at all                      | `runs:` — it passes · then the module's whole suite             |

## The red step is the one that can lie

A `red` step has three ways to look finished and be worthless, and each is caught by a different check.

- **It passes.** Then it does not reach the bug. The step is not done; the test is wrong, or the bug is not where
  the diagnosis says it is. Write the attempt and go back to the diagnosis.
- **It fails on something else** — a missing fixture, an unconfigured property, a null nobody claimed was
  involved. A failure is not a reproduction. Compare the actual output against `reproduces:` and fix the test
  until they match.
- **It fails for the right reason but only here.** A test that reproduces the bug by asserting on a stack trace,
  a wall-clock time, or the order of an unordered collection will pass tomorrow for a reason nobody chose. Assert
  the symptom the user reported.

**Record the failure output verbatim.** It is the step's proof, it goes in the report, and it is what the `green`
step is measured against.

## The green step

**It changes production code until `runs:` passes, and touches no test.** A `green` step that cannot pass without
editing the test refuses: the reproduction was wrong. Revert, write the attempt, and put it back to the user.

**Then the module's whole suite.** A fix that greens its own test and reds another is not finished. Do not
weaken the other test and do not edit it. Report both failures and what the other test was asserting.

**A test asserting the old, wrong behaviour is the `red` step's to rewrite, not this one's.** Where that was
foreseen, the fix file already says so and the test is in a `red` step's `test-files:`. Where it was not, this
step stops and the file is amended.

**A symptom that survives a step you believe is correct is a second cause.** Stop, and **do not revert**: the
step is right and unfinished. Only the level that owns the fix file may add the pair of steps that finishes it.
Write the attempt, saying which cause is now gone.

**The fix is the smallest one that makes the symptom impossible**, not the largest one the diagnosis permits. A
guard clause that hides the bad value is not a fix where the bad value is the bug.

## The stabilize step

**It exists so the red step can be written at all** — an interface the test needs, a signature the fix requires,
a contract between two services the bug spans.

**It changes no behaviour that anything already asks for.** A changed signature keeps its logic and gains a
`TODO`, a new method gets a stub with an intent comment, a test that cannot compile is disabled rather than
removed, in the form the module's conventions give for a disabled test. Whatever it disables, `disables:` names,
and a `red` step clears it.

**A schema change is a `stabilize` step.** A migration adding a column, a constraint or an index changes what
the store will accept, and the reproduction cannot be written until it has. What makes it a `stabilize` step is that no code path behaves differently yet: the column
is unread, the constraint refuses only what nothing writes. A migration that changes what an existing path does
is a `green` step, and it needs a reproduction like any other.

**A migration that has run is not undone by reverting its file.** Say so in the report, and say what putting the
store back would take. The abandonment path in the skill owes the same.

**A `stabilize` that finds a file its `files:`, `test-files:` or `disables:` does not name widens that line and
says so in the report.** Widening a boundary the step already owns is the one edit to a
step's own text its agent may make. Adding a step, removing one, or changing what a step does is not, and goes
back to the level that owns the file. It never widens into a behaviour change.

## Where a step refuses

- **A `red` step that passes before any production code is touched** reproduces nothing. Where `reproduces:`
  carries a rate, it takes the run count the skill's rule for an intermittent bug gives, and only that count
  refuses it.
- **A `green` step that needs the test edited** rests on a reproduction that was wrong.
- **A `green` step whose suite goes red elsewhere** is reported with both failures, not made green by editing the
  other test.
- **A `stabilize` step that has to change what something does** is a `green` step in disguise, and the fix file
  is corrected before it is applied.

Each of these reverts the step, writes the attempt, and puts it back to the user.

**A symptom surviving a correct `green` step is the one case that does not revert**, and it is not in that list:
the step is right and unfinished, and undoing it would lose a correct fix.

## One thing looks like a refusal and is not

**A `green` step that failed twice and landed on the third approach** is a normal step with two attempts logged.
The log is where those go, and the report says how many. The count at which a step stops instead is
`attempts.md`'s.
