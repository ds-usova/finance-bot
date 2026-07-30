---
description: Reflect on the session that just ran and write a numbered, untracked retro file proposing concrete changes to the permission settings, skills, conventions, scripts, or agent memory. Use when the user asks for a retro, a reflection, or what could have gone faster.
argument-hint: [ optional focus, e.g. "permissions" or "the red phase" ]
---

# Retro

Look back over the session that just ran and write down what would make the next one faster and less interrupted.
The output is a file of proposals; this skill changes nothing else.

If an argument is given, it narrows the focus to that part of the session. Without one, the whole session is in
scope.

## 1. Gather Evidence

Findings come from what the session actually did, not from a general sense of how it went. Before writing anything,
collect:

- **Every approval the user was asked for**, and the command behind it. A prompt is a design defect somewhere —
  in the permission rules, or in reaching for the shell where a tool would do.
- **Every correction the user made**, including the ones phrased as a question ("is that still going to work
  if…?"). These are the most valuable input in the file; a correction that had to be made twice is the most
  valuable of all.
- **Commands that failed and were re-run** — wrong flag, wrong path, wrong platform, wrong assumption about output.
- **Work that was redone**: a re-delegated sub-agent step, a file edited and then reverted, a decision reversed.
- **`.claude/settings.local.json`** — entries added during the session are the literal record of what prompted.
- **`git log`** for the session's commits, and the working tree, for churn a summary would smooth over.
- If a plan ran: its `Open Questions / Blockers` and `Review Findings` — a blocker recorded mid-run is a question
  the planning stage should have asked.

## 2. What Counts as a Finding

A finding must be **recurring, or expensive once**. A single wrong turn that cost half a minute is noise.

Every finding **names the artifact to change** and what to put in it:

| Layer                          | Owns                                                                   |
|--------------------------------|------------------------------------------------------------------------|
| `.claude/settings.json`        | What may run without asking, what must ask, what is denied.            |
| `.claude/commands/<skill>.md`  | Workflow — stage order, guardrails, what a sub-agent is told.          |
| `.claude/agents/<agent>.md`    | What one sub-agent does with the context it is handed.                 |
| `<module>/docs/conventions.md` | Project rules the workflow reads: build commands, models, parallelism. |
| `.claude/scripts/`             | Mechanics worth doing the same way every time.                         |
| Agent memory                   | Habits of the agent that no repo file should have to carry.            |

A proposal that resolves to "be more careful" belongs to none of these layers and is not a finding — it is the
class of suggestion that never takes effect. Rewrite it as a change to one of the five, or drop it.

Two more rules:

- **Include the agent's own mistakes**, not only tooling friction. A misread instruction propagated into three
  files is a finding, and its fix is usually a memory entry or a sharper sentence in a skill file.
- **Do not re-file what the user has already decided against.** A rejected option is settled, not pending.

## 3. Write the File

Create `docs/retro/<n>-retro-<slug>.md`, where `<n>` is one more than the highest number already used by a
`<number>-retro-*.md` file in that directory (starting at 1), and `<slug>` names what the session was about —
`3-retro-ai-connector-green-phase.md`.

**The directory stays out of version control.** Before writing, confirm `docs/retro/` is excluded — check with
`git check-ignore -q docs/retro`, and if it is not, add the line `docs/retro/` to `.git/info/exclude`. That file
is per-clone and never committed, so retros stay local without the repository carrying an ignore rule for them.

Structure:

```
# Retro: <what ran>

<One line: what the session did, and what this file covers.>

## Where the time actually went

<Ranked list, worst first. Cost, not chronology.>

## 1. <Finding>

**What happened.** The evidence — the command, the message, how many times.

**Change.** The file to edit and what it should say.

...

## What worked

<Named, so a later change does not undo it by accident.>

## Adoption order

<Cheapest first, and what each unblocks.>
```

Follow the repository's rules for writing docs. Rank findings by what they cost, not by how easy they are to fix,
and keep the ranking honest — a permission prompt that fired twenty times outranks an elegant refactor of a script
that works.

## 4. Where This Stops

The retro **proposes**. It does not edit settings, skills, conventions, scripts, or memory, and it does not commit.
End by listing the findings and asking which to apply — the answer is often "some of them", and which ones is the
user's call.

If the user then asks for a change to be applied, record it in the retro file as done rather than deleting the
finding: the file is the record of what was tried, and a finding struck through is more useful than one that
vanished.
