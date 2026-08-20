# [Conventions](../conventions.md) > Agent Configuration

How a coding agent commits, parallelizes work, and which model does what. Every agent-only fact belongs here;
the other section files describe the module and never mention agents.

**The test for this file:** a fact that stops being true when nobody uses an agent belongs here. A fact that
stays true either way — what the module is built from, how it is tested, what runs when a change is finished —
belongs in the section file that owns it.

## Version Control

If this section is silent, nothing is committed automatically.

**Which tier answers this is the repository's call.** A repository has one history however many modules it has,
so where several modules share it these answers usually belong to the root tier — in which case this section
says so and links there, rather than being deleted or answered twice. A single-module repository, or one whose
modules genuinely commit differently, answers here.

- Commit incrementally: `<yes/no — e.g. "yes, after each passed stage guardrail", or "no — the developer commits">`
- Granularity: `<e.g. one commit per stage; one commit per logical unit of work>`
- Branch policy: `<e.g. the developer checks out the branch beforehand; the agent commits to the current branch and
  never creates, switches, or deletes one>`
- Message format: `<the format this repository's history already uses — e.g. Conventional Commits (feat:, fix:),
  or "<Prefix>: <description>" with the prefixes listed>`
- Message body: `<e.g. usually none — the subject carries the change and the diff carries the detail; never a file
  list or a test count>`
- Squash before merging: `<e.g. "no — keep the full history">`
- Concurrent commits: `<what a commit covers when another module may be mid-flight in the same tree, and what to
  do when one is refused because of it — e.g. "name the module's own paths; retry once if the index is locked">`

## Sub-Agent Models

Which model each kind of delegated work runs on. Naming none means everything runs on the session's own model.

- Deciding work — `<model>`: `<what counts — e.g. planning, plan review, the refactor pass over a finished diff>`
- Executing work — `<model>`: `<what counts — e.g. stabilization, and every red- and green-phase step agent>`
- Everything else: the session's model.

## Parallelism

How many agents and test runs may run at once **in this module**. Concurrent runs share build caches, a container
runtime, and this machine's memory; too many at once produces failures that look like broken tests and are
resource contention. A silent section means no known cap.

**What the machine allows across every module at once belongs to the root tier.** One machine runs them all, and
a module file cannot state a limit it has no way to see. Where the repository has such a file, this section links
there and states only its own numbers.

- Max concurrent implementation agents on this module's plan: `<e.g. 4>`
- Max concurrent test runs in this module: `<e.g. 1 — a queue serializes them; or "unlimited">`
- Notes: `<e.g. treat an unexplained container-startup failure as memory pressure and rerun before debugging it;
  or "none">`

Parallel agents share one working tree: an agent stays inside the files its step owns, and never draws conclusions
from a file another agent is writing.

## Follow-Up Work in a Plan

What runs once a change is complete, and what documents it earns, is [Follow-Up Work](follow-up.md) — true of
this module however the work was done, so it is not an agent-only fact and does not live here.

This section says only how a **plan** carries those two things:

- Which kinds from that file a plan lists under its **Post-Implementation Steps** group, and what one item looks
  like: `<e.g. a decision record's placeholder, as `Place ADR: <the decision, stated as a fact>`; or "none —
  nothing there is written per plan">`
- Where an artifact needs the developer's consent, the plan raises it as a numbered question and only an answered
  yes becomes an item. Nothing downstream writes one that has no item.

