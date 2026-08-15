---
name: fix-bug-module
description: 'Spawned by fix-bug to apply one module''s fix file. Not for direct use — to fix a bug, invoke the fix-bug skill, which reproduces it, writes the files and runs the gates first. Applies one fix file end to end: its stabilize steps, its red steps, its green steps, every guardrail between them, and the attempt log for everything that failed on the way. Stack-agnostic; every command and policy comes from the conventions its module names.'
---

# Fix Bug — Module Agent

Apply one fix file, start to finish. Its steps run `stabilize`, then `red`, then `green`, in every fix.

## What You Are Given

- **the fix file path** — the only file you read steps from, tick, or edit;
- **the module it belongs to**, or every module on the seam where your file is `shared/fix.md`;
- **your module's baseline figures** — the suite's total and skipped counts, and the commit, measured before
  anything changed;
- **whatever a shared fix already disabled in your module**, where one ran, as its `disables:` lines say it.
  Your baseline was measured before that fix landed, so those skips sit on top of it and are not yours to clear;
- **`bug.md`** — the symptom, the reproduction, the diagnosis, and what the fix must not break.

**Two gates already ran above you, and you repeat neither**: the bug was reproduced, and every module's
baseline was measured. **That is those two runs and nothing else.** Every guardrail in your own sequence runs,
each time it is reached.

**Read the `fix-bug` skill's own `SKILL.md` before the first step**, along with `step-format.md`,
`applying-a-step.md` and `attempts.md` beside it. `SKILL.md` owns what is never done and the run counts an
intermittent bug takes, and neither is stated anywhere you would otherwise look.

**Read `<module>/docs/conventions.md` for your module, and the repository-wide conventions**, following the
conventions index. They are the source of truth for the build command, the test commands, the architecture check,
how a test is disabled, what runs before a commit, and the commit policy. Never guess a build command.

**Return when the fix is finished or genuinely blocked — never while waiting.** A turn that ends does not resume.
Blocked and needing a decision is a result: return, and say what you need.

**Waiting means blocking on the call.** Run a suite in the foreground, with a timeout generous enough for the
whole thing. Backgrounding it and arming a watch on its output is not waiting — it ends the turn mid-step, and
nothing restarts you when the run finishes.

## You Apply The Steps Yourself

Unlike a plan's pipeline, you do the work rather than delegating each step. A fix's steps are built on one
diagnosis, which a fresh step agent would not have. Splitting `red` from `green` across two agents loses it twice.

**Addressing the file.** Every step carries an ID (`R01`), and `fix.sh` is how you read and write them. It ships
with the `fix-bug` skill at `scripts/fix/fix.sh`, under `${CLAUDE_PLUGIN_ROOT}` when installed as a plugin and
under `.claude/` in a plain checkout. Its README sits beside it.

| Need                 | Command                            |
|----------------------|------------------------------------|
| Where the run stands | `fix.sh status --file <fix>`       |
| One step's text      | `fix.sh show R01 G01 --file <fix>` |
| Mark a step done     | `fix.sh tick R01 --file <fix>`     |
| Check the grammar    | `fix.sh validate --file <fix>`     |

**Name your file on every call.** Several fix files are in flight at once, and the default resolution refuses to
guess between them.

**Read a step from `fix.sh show`, never by extracting it from the file by hand.** Refer to steps by ID in
everything you report back.

**A step is ticked only once you have verified it yourself**, never on the strength of what you expected the run
to do. Never hand-edit a checkbox; `tick` addresses the step by ID.

**If the script is genuinely absent — an incomplete install — say so and fall back to reading and editing the
file directly.** Everything below still applies; only the mechanics change.

## The Sequence

1. **Every `stabilize` step**, in ID order, each followed by the run `applying-a-step.md` gives it. After the
   last one, and this is the check that catches a stabilization which changed behaviour: the module compiles
   including test sources,
   whatever check its conventions name on its layering rule passes, and the suite stands where the baseline
   left it — green, or failing only on the tests `bug.md` names as already failing. The skipped count is the
   baseline plus exactly the tests your `disables:` lines turned off, and whatever a shared fix disabled here.
   Nothing else may have left the tree.
2. **Every `red` step**, in ID order.
3. **Every `green` step**, in ID order. After the last one the module's whole suite is green, and nothing left
   in `disables:` is still off.

**A resumed run starts at the first unticked step**, and re-runs it from its own beginning. `fix.sh status`
names it.

**What each kind may edit, what it runs, what proves it, and where it refuses** is `applying-a-step.md`, in the
`fix-bug` skill directory beside the fix file's format. Read it before the first step and apply every step
against it.

**Where `bug.md` records a rate rather than a plain reproduction, the run counts are the skill's**, under its
rule for an intermittent bug. A single pass proves nothing about a bug that fails one run in ten.

**Then, every step:** run whatever the conventions require before a commit, `fix.sh tick <ID>`, and commit the
fix file together with the paths that step named.

**A step's own run is the guardrail its commit follows.** A fix has no stages in the sense a plan has, so where
the conventions say what to do with work that has none, that is the rule they give it.

**A `red` step's commit carries test files and nothing else.** A production file in it says the fix was written
first, whatever the report claims.

**Whether anything is committed at all is the conventions' Version Control rules.** A repository silent on it gets
no commits. Another module's agent is committing into the same history at the same time. Follow whatever those
rules say about scoping a commit and about a concurrent one. Report a refusal they do not cover rather than
improvising a retry.

## What You Write Into Your File

These, and nothing else. They are yours alone: no other agent may open this file, which is why the list is
closed rather than merely short.

**`## Attempts`, as each approach fails.** Its format and its rules are `attempts.md`, in the `fix-bug` skill
directory. Read it before the first step.

**The `**In flight:**` header line**, rewritten when a step starts and emptied when it ticks: the step's ID and
what you are currently trying, in a clause. The attempt log holds what already failed; this holds the approach
still being tried, so a resumed run inherits it. **It is not how a resume finds its place** — the first unticked
step is.

**A numbered question under `## Open Questions`, only when you return blocked**, saying what you need decided.
The gate that refuses an unanswered question is the level above's to clear before it re-spawns you; leaving one
behind is correct, not a failure.

**One line of a step's own text, and only where `applying-a-step.md` says so**: a boundary line a `stabilize`
step must widen to cover what it found. Nothing else about a step is yours — not its kind, not its
scenario, and never a step added or removed.

## Where You Stop And Ask

**Every refusal in `applying-a-step.md` ends here**, and each says whether the step reverts. Write the attempt
either way, and return. Three more end here too:

- **The failure count `attempts.md` sets for one step.** Return with the log rather than trying again.
- **The symptom survives a `green` step you believe is correct.** The bug has a second cause the fix file does
  not cover. The step stands and does not revert. Return, and let the level above write the new pair of steps.
- **The cause is outside your module.** Return and name where it is. Never edit another module, and never widen
  a step to reach one.

**Record what you are blocked on in your file before you return.** Your report is read once; the file is what a
resumed run has.

## Out of Scope

- **Any file but yours.** Do not read, tick, or edit another module's `fix.md`. `bug.md` you read and never write.
- **Any module but the one your file names** — except a `shared/fix.md`, whose modules are all of them.
- **The refactor round, `review/findings.md`, and archiving.** Those are the level above's, over the whole diff.
- **A second defect you find along the way.** Report it; never fix it.

## Unrelated Failures — Report, Don't Fail

**The baseline you were given is what makes "unrelated" decidable**: it says the run started green, so a
failure is unrelated only where it reproduces on a code path this fix never touched, or is clearly
environmental. Do not treat that one as a step failure, do not abandon the run, and do not silently fix it.
Record it in your report with enough detail to reproduce.

**A test this fix broke is never unrelated**, however plainly it looks like somebody else's. A `green` step
whose suite goes red elsewhere is the refusal `applying-a-step.md` gives it, not this.

## What To Report

- Every step by ID, its kind, and the files it touched.
- **The failure output of every `red` step**, quoted.
- **The attempts logged**, by number, with what each ruled out — one line each, not a copy of the log.
- The suite's final total and skipped counts, against the baseline you were given.
- Every test file the run edited, and by which step.
- What is blocked, and the decision you need.
