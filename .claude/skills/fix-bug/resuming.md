# Resuming an interrupted run

Read this only where Phase 0 found a directory to resume. A run that starts from a bug report reads none of it.

## What counts as one

**A directory directly under `docs/` holding a `bug.md`**, where `fix.sh task <that directory>` reports
anything open.

**A `bug.md` carrying a `**Closed:**` header line is not one** — report it and leave it alone. Nothing under
`docs/implemented/` is one either.

**It is resumed only where it is the same bug.** Compare its `# Bug:` line and its `## What happens` against
what was asked. A different symptom means the argument is a new bug, and the interrupted one is named in the
report rather than continued. Where the two might be the same, ask.

**Ask before resuming a fix whose log says the chain of causes was disproved.** Picking it up and abandoning it
are both available, and which one is the user's answer.

## What the tree looks like, and what it does not mean

**Read everything before acting** — `bug.md`, every `fix.md`, and every `## Attempts` entry — so that what has
already been ruled out is not tried again.

A resumed run keeps the original `**Baseline:**`, and Phase 0's gates do not apply to what the run itself
produced. Expect all three of these, and none of them is a reason to stop:

| On disk                                    | Because                                             |
|--------------------------------------------|-----------------------------------------------------|
| a committed `red` test that fails          | that is the step working                            |
| tests disabled by a `stabilize` step       | the `red` step named in `disables:` has not run yet |
| uncommitted edits under the step in flight | the run was stopped inside it                       |

**An unanswered Open Question stops the resume here, not in Phase 3.** An agent that blocked wrote one into its
own file, and `fix.sh validate` refuses that file until it is answered. Ask it now, the way Phase 2 asks, and
write the answer in.

## Where to pick up

**The step to resume is the first unticked one**, which `fix.sh status` names.

**The `**In flight:**` line says what was being tried, and it is read for that alone. It never decides what to
revert.** It is written by hand around an operation that commits, so it goes stale exactly when a step lands,
and reverting on its word would undo work that is finished.

**Revert what is uncommitted under that step, and start it again from its own beginning.** Uncommitted work
anywhere else, and anything red no step accounts for, stops the resume and is reported.

**The revert belongs to this skill, not to an agent, and it happens before any agent is spawned**, while no file
has an owner.

Then continue at Phase 3.

## Where this hands back instead

**A directory that is not this bug's leaves Phase 0 exactly where it started.** Report it and go on with the
reproduce-and-baseline path: the tree is checked clean, the bug is reproduced, the baseline is measured, and a
new directory is numbered in Phase 1. Nothing about the interrupted one carries over.
