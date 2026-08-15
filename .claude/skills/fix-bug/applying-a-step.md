# Applying one step

What each kind of step edits, what it runs, and when it refuses. Read beside the step itself, which
`fix.sh show <ID>` prints. The sequence around a step is the skill's: the validate gate, the order the kinds run
in, the commit, and what is never done.

| Kind        | Edit                                                       | Then run                                                            |
|-------------|------------------------------------------------------------|---------------------------------------------------------------------|
| `stabilize` | carry each broken call site back to compiling, nothing more | the module's whole suite · the architecture check                   |
| `red`       | only `test-files:`, and no production file at all          | `runs:` — **it must fail, with the symptom `reproduces:` names**    |
| `green`     | only `files:`, and no test file at all                     | `runs:` — it passes · then the module's whole suite                 |

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
editing the test refuses: the reproduction was wrong, and everything downstream of it rests on the reproduction.
Revert, write the attempt, and put it back to the user.

**Then the module's whole suite.** A fix that greens its own test and reds another is not a fix, and the second
failure is the more interesting of the two — it says the old behaviour was relied on. Do not weaken the other
test. Report both.

**The fix is the smallest one that makes the symptom impossible**, not the largest one the diagnosis permits. A
guard clause that hides the bad value is not a fix where the bad value is the bug.

## The stabilize step

**It exists so the red step can be written at all** — an interface the test needs, a signature the fix requires,
a contract between two services the bug spans. It is the precondition of whatever works against what it moved.

**It changes no behaviour.** A changed signature keeps its logic and gains a `TODO`, a new method gets a stub with
an intent comment, a test that cannot compile is disabled rather than removed, in the form the module's
conventions give for a disabled test. Whatever it disables, `disables:` names, and a `red` step clears it.

**A `stabilize` that finds a call site its `files:` does not name** widens the line in the fix file and says so in
the report. It never widens into a behaviour change.

## Where a step refuses

- **A `red` step that passes before any production code is touched** reproduces nothing.
- **A `green` step that needs the test edited** rests on a reproduction that was wrong.
- **A `green` step whose suite goes red elsewhere** is reported with both failures, not made green by editing the
  other test.
- **A `stabilize` step that has to change what something does** is a `green` step in disguise, and the fix file
  is corrected before it is applied.

Each of these reverts the step, writes the attempt, and puts it back to the user.

## Two things look like refusals and are not

- **A step whose run is red for a reason other than its own claim** is waiting on what its `needs:` names. It is
  not finished, it does not commit, and it is not put back to the user either.
- **A `green` step that took three approaches to land** is a normal step with three attempts logged. The log is
  where that goes; the report says how many.
