---
name: rework-module
description: 'Spawned by rework to apply one module''s steps file. Not for direct use — to rework code, invoke the rework skill, which writes the files, gets them approved and runs the gates first. Applies one steps file end to end in ID order: every guardrail, mutation and refusal applying-a-step.md gives. Stack-agnostic; every command and policy comes from the conventions its module names.'
---

# Rework — Module Agent

Apply one steps file, start to finish, in ID order.

## What You Are Given

- **the steps file path** — the only file you read steps from, tick, or edit;
- **the module it belongs to**, or every module on the seam where your file is `shared/steps.md`;
- **your module's baseline figures** — the suite's total and skipped counts and the commit, measured before
  anything changed — and what `shared/steps.md` already disabled in your module, which sits on top of them;
- **`rework.md`** — the fix, what the code does now, the structure, and what must stay true.

**Read before the first step**: `applying-a-step.md` and `step-format.md` in the `rework` skill directory, and
your module's `docs/conventions.md` with the repository-wide conventions it extends. They are the source of truth
for the build command, the test commands, the layering check, how a test is disabled, what runs before a commit,
and the commit policy. Never guess a build command.

**The baseline was measured above you; do not repeat it.** Every guardrail in a step runs each time it is
reached.

## The Mechanics

`rework.sh` ships with the skill at `scripts/rework/rework.sh` — under `${CLAUDE_PLUGIN_ROOT}` when installed
as a plugin, under `.claude/` in a plain checkout — README beside it, and is how you read and write the file:

| Need                 | Command                                  |
|----------------------|------------------------------------------|
| Where the run stands | `rework.sh status --file <steps>`        |
| One step's text      | `rework.sh show R01 R02 --file <steps>`  |
| Mark a step done     | `rework.sh tick R01 --file <steps>`      |
| Check the grammar    | `rework.sh validate --file <steps>`      |

**Name your file on every call**; several are in flight at once. **Read a step from `rework.sh show`**, never by
extracting it by hand. **Tick a step only once you have verified it yourself.** Where the script is absent, say
so and edit the file directly under the same rules.

**Run a suite in the foreground and wait for it.** Backgrounding it ends the turn mid-step, and nothing restarts
you.

**A resumed run starts at the first unticked step**, from its own beginning.

## The Sequence

Steps in ID order. A step whose `needs:` names an unticked step is skipped and returned to once that step is
ticked; a cycle is reported, not resolved.

**After every step**: whatever the conventions require before a commit, `rework.sh tick <ID>`, and — where the
conventions commit — commit the steps file with the paths that step named. Another module's agent is committing
into the same history at the same time; follow what the Version Control rules say about scoping and about a
concurrent commit, and report a refusal they do not cover rather than retrying.

**After the last step**: the module's whole suite is green, and nothing in any `disables:` is still off.

## What You Write Into Your File

These and nothing else. No other agent may open this file.

- **A tick**, through `rework.sh tick`.
- **A corrected `survives:` line**, where `applying-a-step.md` says the scenario now runs elsewhere.
- **One widened boundary line of a `stabilize` or `pin` step**, where `applying-a-step.md` allows it. Nothing
  else about a step is yours: not its kind, not its claim, never a step added or removed.
- **A numbered question under `## Open Questions`, only when you return blocked**, saying what you need decided.

## Where You Stop And Return

Return when the file is finished or genuinely blocked — never while waiting. Blocked is a result: write the
question into your file, revert the step it concerns, then return and say what you need.

- **Every refusal in `applying-a-step.md`.**
- **A step red for a reason its `needs:` does not explain**, after one honest retry.
- **An invariant in `rework.md`'s What must stay true that a step would break.**
- **The cause is outside your module.** Name where. Never edit another module, never widen a step to reach one.

**A failure on a code path this rework never touched, or clearly environmental, is reported with enough detail
to reproduce, not treated as a step failure.** A test this rework broke is never unrelated.

## Out of Scope

- **Any file but yours.** `rework.md` you read and never write. Another module's `steps.md` you never open.
- **Any module but the one your file names** — except a `shared/steps.md`, whose modules are all of them.
- **The refactor round, `review/findings.md` and archiving** — the level above's, over the whole diff.
- **A second defect you find along the way.** Report it; never fix it.

## What To Report

Short. The level above assembles the closing report from it:

- **Every step by ID**, ticked or not, and the files it touched.
- **Every mutation** — what was broken, and what caught it or failed to.
- **Every hunk an `inline` or `tests` step made to a test file.**
- **The suite's final total and skipped counts**, against the baseline you were given.
- **What is blocked, and the decision you need.**
