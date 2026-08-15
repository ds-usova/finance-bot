# The attempt log

What was tried and did not work, written down as it happens. `## Attempts` is a section of `bug.md` and of every
`fix.md`, and `fix.sh validate` reads it.

A bug whose fix is obvious produces an empty log. A bug that is not produces the section this skill exists for:
the next session — or the same one after the user stops it — starts from what has already been ruled out instead
of trying it again.

## The entry

```
- **A1** · diagnosis · Swapped the `LEFT JOIN` for a correlated subquery, to see whether the duplicate rows
  came from the join.
  - why: the row count doubled exactly when a user had two active budgets.
  - result: failed — the duplicates survived the rewrite.
  - evidence:
    ```
    expected: 1 but was: 2
      at ExpenseQueryTest.listsOneRowPerExpense(ExpenseQueryTest.java:88)
    ```
  - ruled-out: the join is not the source. The duplication is upstream of the query.
```

| Line         | Holds                                                                                  |
|--------------|----------------------------------------------------------------------------------------|
| the header   | `A<n>`, the phase, and what was tried, in a sentence                                   |
| `why:`       | what made it look like it would work — the observation, not the hunch                  |
| `result:`    | `failed — <what happened instead>`                                                     |
| `evidence:`  | a fenced block of the runner's, compiler's or process's **own output**                 |
| `ruled-out:` | what the next person no longer has to try, and why this attempt settles it             |

**The phase is `diagnosis` or a step ID.** `diagnosis` for an attempt made while working out what is wrong —
those live in `bug.md`. A step ID for an approach that failed while applying that step — those live in that
module's `fix.md`, under the step that was being applied. `validate` refuses a phase that is neither.

**Numbers are `A1` upward, per file, assigned once and never renumbered.** A withdrawn attempt keeps its number.

## The rules

**An attempt is written the moment it fails, before the next one starts.** A log written at the end is a summary,
and a summary is what a stopped run does not have.

**Only failures are entries.** The approach that worked is the step. A log with the successful approach in it is
a diary, and a reader looking for what to avoid has to work out which is which.

**Evidence is pasted, never described.** The stack trace, the assertion diff, the compiler error, the exit
status — whatever the tool actually printed. Trim it to the frames that carry the failure; never rewrite them.
An attempt whose failure produced no output says so in `evidence:` and quotes what it did produce: the query plan,
the log line, the response body. `validate` refuses an `evidence:` with no fenced block under it, because an
attempt without its output is a rumour the next session has to reproduce.

**`ruled-out:` is the value of the entry.** Everything above it says what happened; this line says what it means.
An attempt that rules nothing out says so, and names what it would take to settle the question.

**An attempt is not a defeat.** Three of them in a row on a hard bug is what the section expects. What the section
refuses is the fourth one repeating the first.

**An attempt whose failure changed the tree is reverted before the next one starts**, and the entry says so where
it was not — a schema left migrated, a dependency left added. The tree the next attempt runs against has to be
the one the log describes.
