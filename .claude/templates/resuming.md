# Resuming a run from its file

Read this only where the argument is the path of an existing `rework.md` or `upgrade.md`. It replaces the
baseline and writing phases of the skill that owns the file. A bug has its own rule in `fix-bug/resuming.md`.

**A file under `docs/implemented/`, or one carrying a `**Closed:**` line, is not resumed.** Say so; where the
user wants it reopened, the `**Closed:**` line goes and the run continues from here.

## Read before acting

The main file, every steps file beside it, and — where the file has them — every `## Attempts` entry and every
`## Kept back` row. **Nothing an attempt's `ruled-out:` line or a kept-back row settles is tried again.**

The original `**Baseline:**` stands. On disk, these are the run working, not a reason to stop:

| On disk                                    | Because                                                    |
|--------------------------------------------|------------------------------------------------------------|
| tests disabled by a `stabilize` step       | the step named in `disables:` that clears them has not run |
| uncommitted edits under the step in flight | the run was stopped inside it                              |
| a version moved by a ticked step           | that is the step's work, committed or not by the policy    |

Uncommitted work anywhere else, and anything red no step accounts for, stops the resume and is reported.

## Where to pick up

| The files say                              | Do                                                                                                                                             |
|--------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------|
| steps are open and nothing is blocked      | revert what is uncommitted under the first unticked step (the script's `status` names it), continue at the apply phase from that step          |
| a step failed three times, or is abandoned | the plan is what needs work: amend the files, log what was tried, stop for approval as the skill's own stop phase says                          |
| an Open Question is unanswered             | ask it now, write the answer in, then continue                                                                                                 |
| every step is ticked                       | continue at the finish phase — its closing gates ran nowhere yet                                                                                |

**The revert belongs to the skill, never to an agent**, and happens before any agent is spawned. Attempt numbers
continue from where each file's log stopped; nothing is renumbered.
