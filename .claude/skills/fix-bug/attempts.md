# The attempt log

What was tried and did not work, written down as it happens. `## Attempts` is a section of `bug.md` and of every
`fix.md`, and `fix.sh validate` reads it.

A bug whose fix is obvious produces an empty log. A bug that is not produces the section this skill exists for:
the next session — or the same one after the user stops it — starts from what has already been ruled out instead
of trying it again.

## The entry

````
- **A1** · diagnosis · Rewrote the read so it could not return a row twice, to find out whether the duplication
  came from the query.
  - why: the count doubled exactly when a record had two active children.
  - result: failed — the duplicates survived the rewrite.
  - evidence:
    ```
    expected: 1 but was: 2
      at <the assertion that failed>
    ```
  - ruled-out: the query is not the source. The duplication is upstream of it.
````

| Line         | Holds                                                                      |
|--------------|----------------------------------------------------------------------------|
| the header   | `A<n>`, the phase, and what was tried, in a sentence                       |
| `why:`       | what made it look like it would work — the observation, not the hunch    |
| `result:`    | `failed — <what happened instead>`                                       |
| `evidence:`  | a fenced block of the runner's, compiler's or process's **own output**     |
| `ruled-out:` | what the next person no longer has to try, and why this attempt settles it |

**A fence inside `evidence:` needs a longer fence around the entry**, as above, where the output being pasted
carries a fence of its own. `validate` refuses a block that never closes.

**The phase is `diagnosis` or a step ID.** `diagnosis` for an attempt made while working out what is wrong;
those live in `bug.md`. A step ID for an approach that failed while applying that step; those live in that
module's `fix.md`. `validate` refuses a phase that is neither.

**Every entry goes in the `## Attempts` section, whatever its phase names.** The step ID is how an entry says
which step it belongs to. An entry written under the step's own checklist bullet is one `validate` refuses.

**Numbers are `A1` upward, per file, assigned once and never renumbered.** A withdrawn attempt keeps its number.
Since each file numbers its own, anything outside the file cites both: `module-a/fix.md · A3`.

## The rules

**An attempt is written the moment it fails, before the next one starts.**

**Only failures are entries.** The approach that worked is the step.

**An approach that was right and insufficient is a failure for this purpose.** A `green` step that removes one
of two causes leaves the symptom, so it gets an entry: `result: failed — the symptom survived`, and a
`ruled-out:` saying which cause is now gone. That entry is what turns one step into two.

**A probe that made the bug observable is not an entry either.** What becomes of it is the skill's Phase 1.

**Evidence is pasted, never described.** The stack trace, the assertion diff, the compiler error, the exit
status — whatever the tool actually printed. Trim it to the frames that carry the failure; never rewrite them.
An attempt whose failure produced no output says so in `evidence:` and quotes what it did produce: the query plan,
the log line, the response body. `validate` refuses an `evidence:` with no fenced block under it, because an
attempt without its output is a rumour the next session has to reproduce.

**`ruled-out:` is the value of the entry.** An attempt that rules nothing out says so, and names what it would
take to settle the question.

**Two failed attempts on one step is what the section expects.** The third failure is where the step stops and
goes back to the user, because a fourth approach from inside the same context is the one most likely to repeat
the first.

**An attempt whose failure changed the tree is reverted before the next one starts.** Where it was not — a schema
left migrated, a dependency left added — the entry says so.

**The approach still being tried is not an entry.** It belongs on the fix file's `**In flight:**` line, whose
rules are the skill's.
