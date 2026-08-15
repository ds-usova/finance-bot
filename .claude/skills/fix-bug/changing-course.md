# When the fix has to change

Read this only where a run already under way turns out to be wrong. A fix whose diagnosis holds reads none of
it.

**The diagnosis is a hypothesis, and applying the steps is what tests it.**

## Amending an approved fix

Four things routinely disprove it mid-run, and none of them is a reason to end the run with a failing test
committed:

| What the run found                                             | What changes                                             |
|----------------------------------------------------------------|----------------------------------------------------------|
| the symptom survives a correct `green` step — a second cause | a new `red` and `green` pair, written into the same file |
| the cause is in another module                                 | the pair moves whole, to that module's file              |
| a step's kind was wrong                                        | the step is re-classified                                |
| a test asserting the old behaviour surfaced after the red wave | a new `red` and `green` pair, naming that test           |

**A pair moves whole or not at all.** A reproduction and the step that fixes it are one unit, and `fixes:`
cannot cross a file, so striking out one half leaves the other paired with nothing and the file will not
validate. Moving a cause to another module strikes out both steps and writes both again in the other file.
Where the reproduction belongs where it is and only the fix moves, the bug was two bugs, and it goes back to
the user as two.

**Only the skill amends a file, never an agent it spawned, and only with the user's answer.** An agent that finds one of these
returns and says so. Put it to the user as a numbered Open Question in the file it belongs to, write the answer
in, re-run `fix.sh validate`, and re-spawn the module's agent, which starts at the first unticked step.

**A file is amended only once its own agent has returned**, since that agent is writing it. Module A returning
with "the cause is in module B" almost always arrives while B's agent is still running. **Wait for it.** Where
it cannot be waited for, say so and stop it first.

**A step that turns out to be unnecessary is struck from the checklist**, and its row in the **Steps** table
stays, with `abandoned — <why>` in place of what proved it. Its ID is retired and never reused, so no other
step renumbers. **A struck step that had already landed is reverted first**, on the terms below.

**Striking a step out is not free, and `validate` says why.** Removing a `green` step leaves its `red` step
paired with nothing, and the file will not validate. Either both go, or the reproduction stays and something
fixes it.

## Abandoning a fix

A fix the user calls off, or one whose diagnosis is disproved with nothing to replace it, does not simply stop.

**This is the other half of resuming.** An interrupted directory is picked up or abandoned, and which one is the
user's answer when the diagnosis is what failed.

Then, in the skill itself, never inside an agent:

1. **Revert every step that landed**, newest first — the `green` edits, the `red` test, and each `stabilize`
   step, so nothing in `disables:` is left off. A skipped count above the baseline blocks the next fix in that
   module, and a disabled test naming a step that will never run is never noticed again.

   **A revert that conflicts stops here.** Work that landed after a `stabilize` step may be built on the
   signature it moved, and undoing it would break code this fix never touched. Report what conflicts and leave
   the tree as it is: the choice between reverting further and keeping the stabilization is the user's.
2. **Run every affected module's suite** and confirm the baseline's figures are back.
3. **Keep the directory**, with every attempt log intact, and say in the report that the fix was abandoned and
   what the log rules out.
4. **Close `bug.md` with its `**Closed:**` header line**, so the next run's resume scan reports it rather than
   picking it up.
5. **The directory is not archived**, and nothing in `docs/implemented/` refers to it.

**Where the conventions commit, the revert is committed too**, on the same terms as the steps were.
