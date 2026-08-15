---
description: Reflect on the session that just ran and write a numbered, untracked retro file proposing concrete changes to the permission settings, skills, conventions, scripts, or agent memory. Use when the user asks for a retro, a reflection, or what could have gone faster.
argument-hint: [ optional focus, e.g. "permissions" or "the red phase" ]
---

# Retro

Look back over the session that just ran and write down what would make the next one faster and less interrupted.

An argument narrows the focus to that part of the session. Without one, the whole session is in scope.

## 1. Gather Evidence

Findings come from what the session actually did, not from a general sense of how it went. Before writing
anything, collect:

- **`docs/retro/DECISIONS.md`**, the ledger of what earlier retros settled, declined and left open. Read it
  first, and read no other retro file. A subject with a line there is not filed again. It moves up a rung
  because this session is new evidence, or it is dropped and its `Retros` column gains this session's number.
  Say in the finding what is new.
- **The previous retro's applied changes**, and whether this session exercised each one. A fix that shipped and
  did not change the behaviour it was written for is the first finding to write up. Its next attempt starts a
  rung above the one that failed.
- **Every approval the user was asked for**, and the command behind it. A prompt is a defect in the permission
  rules, or in reaching for the shell where a tool would do.
- **Every correction the user made**, including the ones phrased as a question ("is that still going to work
  if…?"). These outrank every other kind of evidence, and one that had to be made twice outranks those.
- **Commands that failed and were re-run**: wrong flag, wrong path, wrong platform, wrong assumption about output.
- **Work that was redone**: a re-delegated sub-agent step, a file edited and then reverted, a decision reversed.
- **`.claude/settings.local.json`**. Entries added during the session are the literal record of what prompted.
- **`git log`** for the session's commits, and the working tree, for churn a summary would smooth over.
- **The task's `review/findings.md`**, if a task ran, for what the implementation left open. Each plan's
  **Open Questions / Blockers** holds what the run settled instead, and a blocker settled mid-run is a question
  the planning stage should have asked.

## 2. What Counts as a Finding

A finding must be **recurring, or expensive once**. A single wrong turn that cost half a minute is noise.

**Choose the kind of fix before the file.** Take the highest rung that fits:

1. **Remove the reason.** A tool is missing the option that makes the wrong form tempting. A documented command
   cannot work as written. A permitted command is not in the allowlist. Fix that, and the rule has nothing left
   to forbid.
2. **Enforce it.** A hook, a deny rule, a check inside a script the workflow already runs.
3. **Write it down.** A rule, placed by the test below.
4. **Agent memory.** A habit of the agent that no repository file should carry.

Rungs 2 and 4 divide by who pays. A mistake that costs only this operator time can live in memory. A mistake that
can leave the repository broken for the next person belongs in a hook.

**A rule that already exists and was ignored may not be restated.** It moves up a rung, or the finding is dropped.

**Verify a finding against the file it blames.** Quote the line, or say the line is absent after reading it. An
approval log says what was permitted, not what the rules lacked. Two observations that fit a theory are not
evidence for it. Where the cause is not established, say so.

Every finding **names the artifact to change** and what to put in it:

| Layer                          | Owns                                                                   |
|--------------------------------|------------------------------------------------------------------------|
| `.claude/settings.json`        | What may run without asking, what must ask, what is denied.            |
| `.claude/scripts/hooks/`       | A refusal that cannot be read and skipped.                             |
| The repository's own tooling   | An option or a command that removes the reason to do it the wrong way. |
| `.claude/skills/<skill>/`      | Workflow — stage order, guardrails, what a sub-agent is told.          |
| `.claude/agents/<agent>.md`    | What one sub-agent does with the context it is handed.                 |
| Repository-wide conventions    | Facts true of the whole repository, not of one module.                 |
| `<module>/docs/conventions.md` | Project rules the workflow reads: build commands, models, parallelism. |
| `.claude/scripts/`             | Mechanics worth doing the same way every time.                         |
| `.claude/templates/`           | Reference material a skill points at rather than carries.              |
| Agent memory                   | Habits of the agent that no repo file should have to carry.            |

**Not a finding.** Each of these looks like one and takes effect nowhere:

- **It resolves to "be more careful."**
- **The rule already has an owner.**
- **It moves a label, not an outcome.** Ask what would have been different had the fix been in place.
- **A safety net caught it, at its intended cost.**

Rewrite such a proposal as a change to one of the layers above, or drop it.

**Every skill the session used is in scope**, including one invoked from a plugin or a marketplace. A skill whose
file cannot be edited here still earns a finding: write what should change, and where it would have to be raised.

**Include the agent's own mistakes**, not only tooling friction.

**On rung 3, apply the placement test before naming the file.**

- Does it change the **sequence** of work, or a guardrail between stages? → the skill.
- Does it change what **one agent** does with the context handed to it? → that agent's file.
- Is it a fact about **this repository**, its stack, its tests, its build? → module conventions, or the
  repository-wide ones where it holds for every module.
- Is it a **habit of the agent** that no repository file should carry? → memory.

Take the lowest row that fits. A skill edit is the last resort.

**Additions to a long skill are paid for with extractions.** Past roughly 250 lines, a skill takes no new rule
until something is extracted or merged. Format specifications, glossaries and worked examples extract to a file
the skill points at. The sequence, the guardrails and the handoffs stay.

## 3. Write the File

Create `docs/retro/<n>-retro-<slug>.md`. `<n>` is one more than the highest number prefixing any file in that
directory, starting at 1. Count every numbered file, not only the ones spelled `-retro-`, or two files end up
claiming one number. `<slug>` names what the session was about: `3-retro-green-phase-rework.md`.

**The directory stays out of version control.** Before writing, check with `git check-ignore -q docs/retro`. If it
is not ignored, add the line `docs/retro/` to `.git/info/exclude`, which is per-clone and never committed.

Structure:

```
# Retro: <what ran>

<One line: what the session did, and what this file covers.>

## Where the time actually went

<Ranked list, worst first. Cost, not chronology.>

## 1. <Finding> · <STATUS, once it is settled>

**What happened.** The evidence — the command, the message, how many times.

**Change.** The file to edit and what it should say.

...

## What worked

<Named, so a later change does not undo it by accident.>

## Outcome

<One row per finding: number, finding, layer, outcome — applied, declined, dropped or open.>
```

The status word belongs in the heading as well as the table.

Follow the repository's rules for writing docs.

**Findings appear in the order they are to be applied.** The file is read from the top, one finding at a time,
and each is answered where it stands — so the sequence is the proposal, and there is no separate list of it at
the end. Cheapest first, then whatever each one unblocks; a finding that depends on another comes after it, and
one being dropped comes last. Where the position needs a reason — it is free once the finding above lands, it
cannot start until that one does — the finding says so in one clause. Ranking by severity instead makes a reader
hold the whole file in their head before they can answer the first question.

## 4. Propose, Then Apply

The retro runs in two phases and never commits.

**Propose.** Write the file. Change no settings, skill, convention, script or memory entry. End by listing the
findings and asking which to apply.

**Apply**, on request. Make the change, then stamp the finding: the status word into its heading, the outcome
into the table. Never delete the finding or edit the problem statement above it.

A finding that is settled, applied or declined, is written to `docs/retro/DECISIONS.md` in the same edit, if a
later retro could plausibly file it again. One line, keyed by subject rather than by finding. A one-shot fix
needs no line.
