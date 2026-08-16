# Applying one step

What each kind of step edits, what it runs, and when it refuses. Read beside the step itself, which
`rework.sh show <ID>` prints. The sequence around a step — the validate gate, the commit, what is never done — is
the skill's.

| Kind        | Edit                                                                               | Then run                                                                                                  |
|-------------|------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| `inline`    | reshape or relocate only what `files:` names; in a test, only a mechanical edit    | `runs:` — they pass                                                                                       |
| `extract`   | break the body in place first, then move it without rewriting, and wire the caller | `frozen:` goes red, then unedited and green · write `cover:` and **mutate** each · the architecture check |
| `tests`     | move or reshape test code, with no `files:` at all                                 | the module's whole suite · then find every scenario in `survives:` running again                          |
| `pin`       | add, tighten, or drop the check or setting, and nothing else                       | the module's whole suite · then **mutate**, where anything was added or tightened                         |
| `stabilize` | carry each broken call site back to compiling, nothing more                        | the module's whole suite · the architecture check                                                         |

## Mutation

**To mutate is to break the target deliberately and confirm the failure.** Restore it, and run again before the
commit.

- **One mutation per test, and each targets what that test asserts.**
- **A `pin` mutates by undoing what it just did**: putting back the dependency or the layer violation its check
  forbids, or restoring the value its setting replaced. `proves:` records that, and the failure it produced. A
  `pin` that **drops** something has nothing to undo — its `proves:` says the suite is green without it.
- **A run that executed no test is neither a pass nor a failure**, so read the runner's own verdict before
  believing a red or a green. A test that could not compile means the step is waiting on a `stabilize` one.

## What each kind owes

**Each scenario in `survives:` is found again by name, in the test its line already names.** Where the run puts a
scenario somewhere else, the line is corrected to say where.

**A mechanical test edit is a call this step renamed, re-shaped or re-imported** — a constructor argument, an
import, a package, a method name. An assertion, a fixture value, and the removal of a test are not mechanical.

**`frozen:` must bite before it can vouch.** Break the body where it stands today and confirm `frozen:` goes
red; restore, and only then move it. A `frozen:` that passes whatever the extraction did is not a net, and the
step is refused. This asks only whether the tests reach the body, so one breakage answers it.

**`frozen:` is verified against the step's own start.** Where the run commits per step, that start is the last
commit and version control answers it exactly. Where the conventions commit nothing there is no anchor, so copy
each `frozen:` file before editing anything and compare against the copy.

**`extract`, and an `inline` that relocates a file, run whatever the module's conventions name as the check on
its layering rule.**

**A `stabilize` step disables the least it can**, in the form the module's conventions give for a disabled test.
How each of its edits is made — the stub, the `TODO` on a changed signature, the disabled test — is
`stabilizing.md` in the `templates` directory beside the skills, the one statement of it for every workflow that
stabilizes.

## Where a step refuses

- **`inline` needing more than a mechanical test edit** is not `inline`.
- **`extract` whose body cannot move unrewritten** is not `extract`. A connection, a transaction or a lock the
  original held across its statements travels with the body, passed in rather than acquired again, and the step
  stays an `extract`. Where even that is impossible, stop and put it to the user: nothing about the behaviour
  changed, so no other kind fits. A resource re-acquired inside the new class is the one case `frozen:` cannot
  catch.
- **`tests` with a scenario it cannot find running again** dropped it, and it is restored before anything else.
- **`pin` that survives its own mutation** pins nothing. A `pin` that only drops something has no mutation, and
  this does not apply to it.

Each of these reverts the step and puts it back to the user, re-classified or repaired.

## Three things look like refusals and are not

- **A step whose run is red for a reason other than its own claim** is waiting on what its `needs:` names. It is
  not finished, it does not commit, and it is not put back to the user either.
- **A `stabilize` that finds a call site its `files:` does not name** widens the line in its steps file and says
  so in the report. It never widens into a behaviour change: the new site keeps its logic and takes a `TODO`.
- **A `pin` whose new check reds a file the step does not name** widens the line the same way and reports it.
