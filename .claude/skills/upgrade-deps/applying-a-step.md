# Applying one step

What each kind of step edits, what it runs, and when it refuses. The sequence around a step — the commit, what
is never done — is the skill's.

| Kind      | Edit                                                                                              | Then run                                                          |
|-----------|---------------------------------------------------------------------------------------------------|-------------------------------------------------------------------|
| `bump`    | the version line in the manifest, and the lock file through the stack's own command               | full build · the module's whole suite · the architecture check    |
| `migrate` | the version line and the lock file, then each `change:` in order, inside `files:` and `test-files:` | full build · the module's whole suite · the architecture check    |

**The lock file is regenerated, never edited.** `npm install`, `./gradlew dependencies --write-locks`,
`poetry lock`, whatever the module's conventions name; where they name nothing and the stack keeps no lock file,
the manifest is the whole edit.

## A `bump`

Move the line, regenerate the lock, build, run the suite. Green: the step is done. Anything else:

- **it does not compile, or the suite is red on a code path the dependency reaches** — the step was a `migrate`
  wearing the wrong kind. Revert, re-classify in the file, return to the user.
- **the suite is red somewhere the dependency does not reach** — environmental or pre-existing; report it with
  enough to reproduce, revert the step, return.

A `bump` never edits a source file. The moment one is needed the kind is wrong.

## A `migrate`

Move the version first and build. Then take the `change:` lines in order; each is one edit and one build. Once
every `change:` is made, run the whole suite.

**A `change:` that fails is attempted, up to three times**, each attempt an entry in `## Attempts` with the
compiler's or runner's own output. On the third failure the change is kept back:

1. **Undo that change only** — the version stays at the target, the other `change:` lines stay applied.
2. **Build and run the suite.** Green: the module runs on the new version with the old API for this one item;
   write the row in `## Kept back` and continue with the next `change:`. Red: the old API does not survive the
   new version either.
3. **Where step 2 is red**, the version goes back to where it was, every `change:` is undone, the step is
   `abandoned — <why>`, and its survey row says `blocked`. Return; the level above decides.

**A `change:` whose old form still compiles under the new version but is deprecated is a legitimate keep-back.**
The deprecation is written into `review/findings.md` by the level above; the step is still done.

**A `change:` never reaches past its own place.** The guide asks for a rename in one class; a second class the
rename breaks is a second `change:` line, added by the level above, not an edit made because it was nearby.

## What every step runs

- **The full build** — the manifest resolves, the lock file agrees, everything compiles.
- **The whole suite**, in the foreground, with the module's own command. A run that executed no test is neither
  a pass nor a failure; read the runner's own verdict.
- **The architecture check the conventions name**, where a `migrate` touched a source file.
- **Whatever the conventions require before a commit.**

## Where a step refuses

- **A `bump` that needs a source edit** — re-classified.
- **A test that asserts the old behaviour** — a serialization shape, a default, an error message the new
  version changed. Never edited to pass. The step reverts and returns; the user decides whether the assertion
  follows the version or the version waits.
- **A `change:` that lands outside the module** — another module's file, a shared catalog. Named and returned.
- **A guide that offers two ways** and the step names neither — returned as an Open Question.
- **A vulnerability whose fix requires a major the policy does not offer** — the row stays `not offered` and
  the report says so; the step is not written.
