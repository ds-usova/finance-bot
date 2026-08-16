# Applying one step

What each kind edits, what it runs, and when it refuses. Read beside the step itself, which `fix.sh show <ID>`
prints.

| Kind        | Edit                                                        | Then run                                                           |
|-------------|-------------------------------------------------------------|--------------------------------------------------------------------|
| `stabilize` | carry each broken call site back to compiling, nothing more | the module's whole suite · the architecture check                 |
| `red`       | only `test-files:`, and no production file at all           | `runs:` — **it must fail, with the symptom `reproduces:` names** |
| `green`     | only `files:`, and no test file at all                      | `runs:` — it passes · then the module's whole suite             |

## The stabilize step

**It exists so the `red` step can be written** — an interface the test needs, a signature the fix requires, a
contract the bug spans, a schema change no code path reads yet. How each edit is made — the stub, the `TODO` on
a changed signature, the disabled test — is `stabilizing.md` in the `templates` directory beside the skills, the
one statement of it for every workflow that stabilizes. What is a fix step's own: every test it disables is
named in `disables:`, and a migration that has run is not undone by reverting its file — say so in the report.

**A `stabilize` that finds a file its boundary does not name widens that line and says so** — the one edit to a
step's text its agent may make. It never widens into a behaviour change.

## The red step

**Where Phase 0 left the reproduction test disabled in the tree, the step's work is to enable it** — and to
sharpen it only where its failure does not match `reproduces:`. Otherwise it writes the test from
`## How it reproduces`.

Three ways it can look finished and be worthless:

- **It passes.** It does not reach the bug. Write the attempt; the diagnosis is what needs work.
- **It fails on something else** — a missing fixture, an unconfigured property. Compare the output against
  `reproduces:` and fix the test until they match.
- **It fails for the right reason but only here** — a stack trace, a wall-clock time, an unordered collection.
  Assert the symptom the user reported.

**Record the failure output verbatim.** It is the step's proof and what the `green` step is measured against.
Where `reproduces:` carries a rate, run the counts `SKILL.md` gives for an intermittent bug.

## The green step

**It changes production code until `runs:` passes, and touches no test.** Then the module's whole suite. **The
fix is the smallest one that makes the symptom impossible** — a guard clause that hides the bad value is not a
fix where the bad value is the bug.

**A symptom that survives a step you believe is correct is a second cause.** Stop, do not revert, write the
attempt saying which cause is now gone, and return.

## Where a step refuses

Each of these reverts the step, writes the attempt, and returns to the level above:

- a `red` step that passes before any production code is touched;
- a `green` step that cannot pass without editing a test — the reproduction was wrong;
- a `green` step whose suite goes red elsewhere — reported with both failures, never made green by editing the
  other test;
- a `stabilize` step that has to change what something does — a `green` step in disguise;
- a test asserting the old behaviour that no `red` step names.

**A step that failed twice and landed on the third approach is a normal step with two attempts logged.** The
third failure is where it stops and returns.
