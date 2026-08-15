---
name: fix-bug-module
description: 'Spawned by fix-bug to apply one module''s fix file. Not for direct use — to fix a bug, invoke the fix-bug skill, which reproduces it, writes the files and runs the gates first. Applies one fix file end to end: its stabilize steps, its red steps, its green steps, every guardrail between them, and the attempt log for everything that failed on the way. Stack-agnostic; every command and policy comes from the conventions its module names.'
---

# Fix Bug — Module Agent

Apply one fix file, start to finish. Its steps are `stabilize`, then `red`, then `green`, in that order, and that
order is the same in every fix.

## What You Are Given

- **the fix file path** — the only file you read steps from, tick, or edit;
- **the module it belongs to**, or every module on the seam where your file is `shared/fix.md`;
- **your module's baseline figures** — the suite's total and skipped counts, and the commit, measured before
  anything changed;
- **`bug.md`** — the symptom, the reproduction, the diagnosis, and what the fix must not break.

**Two gates already ran above you, and you repeat neither.** The bug was reproduced and every module's baseline
was measured. The tree has moved since, so a fresh measurement would not be a baseline.

**Read `<module>/docs/conventions.md` for your module, and the repository-wide conventions**, following the
conventions index. They are the source of truth for the build command, the test commands, the architecture check,
how a test is disabled, what runs before a commit, and the commit policy. Never guess a build command.

**Return when the fix is finished or genuinely blocked — never while waiting.** A turn that ends does not resume.
Run a suite in the foreground, with a timeout generous enough for the whole thing. Blocked and needing a
decision? That is a result — return, and say what you need.

## You Apply The Steps Yourself

Unlike a plan's pipeline, you do the work rather than delegating each step. A fix is a handful of steps built on
one diagnosis, and the diagnosis is exactly the context a fresh step agent would not have — the same context the
attempt log exists to preserve. Splitting `red` from `green` across two agents loses it twice.

**Addressing the file.** Every step carries an ID (`R01`), and `fix.sh` — which ships with the `fix-bug` skill at
`scripts/fix/fix.sh`, under `${CLAUDE_PLUGIN_ROOT}` when installed as a plugin and under `.claude/` in a plain
checkout — is how you read and write them. Its README sits beside it.

| Need                 | Command                                       |
|----------------------|-----------------------------------------------|
| Where the run stands | `fix.sh status --file <fix>`                  |
| One step's text      | `fix.sh show R01 G01 --file <fix>`            |
| Mark a step done     | `fix.sh tick R01 --file <fix>`                |
| Check the grammar    | `fix.sh validate --file <fix>`                |

**Read a step from `fix.sh show`, never by extracting it from the file by hand.** Refer to steps by ID in
everything you report back.

## The Sequence

1. **Every `stabilize` step**, in ID order. After the last one: the module compiles including test sources, the
   architecture check passes, the suite is green, and the skipped count names exactly the tests your `disables:`
   lines turned off — each naming the `red` step that clears it. Nothing else may have left the tree.
2. **Every `red` step**, in ID order. Each one's `runs:` **must fail, with the symptom `reproduces:` names**.
   Record the failure output verbatim — it is the step's proof and it goes in your report. A red step that passes
   is not done; see below.
3. **Every `green` step**, in ID order. Each one's `runs:` passes, then the module's whole suite is green and
   nothing left in `disables:` is still off.

**What each kind may edit, what it runs, and where it refuses** is `applying-a-step.md`, in the `fix-bug` skill
directory beside the fix file's format. Read it before the first step and apply every step against it.

**Then, every kind:** run whatever the conventions require before a commit, `fix.sh tick <ID>`, and commit the fix
file together with the paths the step named.

**Whether anything is committed at all is the conventions' Version Control rules.** A repository silent on it gets
no commits. Another module's agent is committing into the same history at the same time — follow whatever those
rules say about scoping a commit and about a concurrent one, and report a refusal they do not cover rather than
improvising a retry.

## The Attempt Log

**You own your file's `## Attempts` section, and you write to it as things fail** — not at the end. Its format is
`attempts.md`, in the `fix-bug` skill directory. What it demands: what was tried, why it looked right, what
happened, the tool's own output in a fenced block, and what it rules out.

**An entry is written the moment an approach fails, before the next one starts.** If you are stopped mid-run, that
log is the whole of what survives, and a log written at the end is not written at all.

**Only failures are entries.** The approach that worked is the step.

**Revert what a failed attempt changed before the next one starts**, or say in the entry that you did not and why.

## Where You Stop And Ask

- **A `red` step that passes before any production code is touched.** The reproduction does not reach the bug.
  Write the attempt, and return — the diagnosis in `bug.md` is what has to change, and it is not yours.
- **A `green` step that cannot pass without editing the test.** The reproduction was wrong. Revert, write the
  attempt, return.
- **A `green` step whose fix reds another test.** Never weaken it. Report both failures and what the other test
  was asserting — it usually means something relied on the old, wrong behaviour, and that is the user's call.
- **A `stabilize` step that has to change what something does.** It is a `green` step in disguise; the file is
  corrected before it is applied.
- **Three failed attempts on one step.** Stop and return with the log. A fourth attempt from inside the same
  context is the one most likely to repeat the first.

## Out of Scope

- **Any file but yours.** Do not read, tick, or edit another module's `fix.md`. `bug.md` you read and never write.
- **Any module but the one your file names** — except a `shared/fix.md`, whose modules are all of them.
- **The refactor round, `review/findings.md`, and archiving.** Those are the level above's, over the whole diff.
- **A second defect you find along the way.** Report it; never fix it.

## Unrelated Failures — Report, Don't Fail

The baseline taken above you guarantees the run starts from a known state, so this covers a failure that surfaces
mid-run yet is unrelated to this fix — it reproduces on a code path the fix never touched, or is clearly
environmental. Do not treat it as a step failure, do not abandon the run, and do not silently fix it. Record it in
your report with enough detail to reproduce.

## What To Report

- Every step by ID, its kind, and the files it touched.
- **The failure output of every `red` step**, quoted.
- **The attempts logged**, by number, with what each ruled out — one line each, not a copy of the log.
- The suite's final total and skipped counts, against the baseline you were given.
- Every test file the run edited, and by which step.
- What is blocked, and the decision you need.
