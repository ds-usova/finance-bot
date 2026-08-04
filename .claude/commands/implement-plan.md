---
description: Orchestrate the implementation of an entire plan file end to end — stabilization first (compile + architecture guardrail), then parallel RED-phase subagents (test compiles and fails), then GREEN-phase subagents (unit + integration in dependency-aware parallel waves, system tests last), then a single behavior-preserving REFACTOR-phase subagent over the whole diff, finishing only when the whole module builds and every test passes.
argument-hint: [ plan file path ] [ optional section name ]
---

# Implement Plan

Use this skill when the user asks to implement a plan that was just discussed or is referenced by path — e.g.
"implement this plan", "implement docs/1-add-widget/plan.md". The default scope is the **whole plan**, from
stabilization through the final system-test implementation. If the user names a single section, run only the stage
that owns that section (using the same rules for it) and stop there.

## Role: Orchestrator Only

This skill coordinates; it does not write production or test code itself. All implementation work is delegated to
sub-agents via the **`Agent` tool**. The orchestrator's own jobs are:

- resolving the plan file and reading conventions,
- spawning sub-agents with the right instructions and step context,
- running the guardrail verifications between stages,
- ticking checkboxes in the plan file (**only the orchestrator edits the plan file** — never a sub-agent, since
  several run concurrently),
- recording blockers and unrelated failures in `### Open Questions / Blockers`.

**Model per sub-agent.** Every spawn passes the `model` parameter, taken from the module conventions'
**Sub-Agent Models** section: the step agents (stabilization, red, green) run on the model it names for execution
work, and the refactor agent on the one it names for deciding work. Only if the module has no such section does a
spawn fall back to the default model.

**Point a sub-agent at the rule; do not restate it.** A rule the repository writes down is passed as the file
that owns it, named so the agent reads it there — never as a remembered version of what that file says, which is
a second copy that can drift and drifts in the one place no review looks. The same applies to counts and
inventories drawn from the tree: read them, never recall them. A prompt carries the step's own context — its
target class, its scenarios, what it may not touch — and pointers for everything else.

## Input Resolution

1. Identify the plan file: use the provided path, else the plan referenced/attached in the conversation, else ask.
2. Read the plan file in full. The `## Step-by-Step Implementation Map` section nests two levels: four
   `### <Group>` headings — **Stabilization**, **Red Phase**, **Green Phase**, **Post-Implementation Steps**, in
   that fixed order — each containing its `#### <Section>` blocks. Collect every `#### <Section>` block, grouped by
   its parent `### <Group>`, and its unchecked `- [ ]` items. The four groups map directly onto this skill's own
   stages: Stabilization → Stage 1, Red Phase → Stage 2, Green Phase → Stage 3, Post-Implementation Steps → Stage 5.
3. Read `<module>/docs/conventions.md` for every module listed in **Affected Modules** (and the repo-root
   `docs/conventions.md` if present). The conventions file is the source of truth for the build command, the test
   commands per layer, the architecture-enforcement test, and file locations. Pass the relevant conventions along in
   every sub-agent prompt — sub-agents must not guess build commands.

**Addressing the plan.** Every checklist item carries an ID (`GU07`), and `plan.sh` — which ships with these
instructions at `scripts/plan/plan.sh`, under `${CLAUDE_PLUGIN_ROOT}` when installed as a plugin and under
`.claude/` in a plain checkout — is how this skill reads and writes them. Its README sits beside it.

**The plan file is a positional argument on every call, and it comes last.** There is no `--plan` flag. `tick` and
`show` take any number of IDs in one call and reject a shell loop around them, so batch the IDs rather than
iterating:

| Need                          | Command                                                                  |
|-------------------------------|--------------------------------------------------------------------------|
| Where the run stands          | `plan.sh status docs/<plan>.md`                                          |
| One item's text and scenarios | `plan.sh show GU07 GU08 docs/<plan>.md`                                  |
| What is spawnable right now   | `plan.sh next --group <group> docs/<plan>.md` (`--all` also shows waiting) |
| Mark an item done             | `plan.sh tick GU07 GU08 docs/<plan>.md`                                  |
| Leave it open, record why     | `plan.sh block GU07 "<reason>" docs/<plan>.md`                           |

**Always scope `next` to the stage you are running.** Unscoped, it advances to the next group the moment the
current one is fully ticked — so a run covering only the Red Phase would start handing back Green Phase items
instead of reporting that it is finished. `--group red` returns `every item in scope is ticked` instead, which is
the stage's completion signal. `--section` narrows further within a group and may be repeated. Both match any part
of the heading, case-insensitively; an ambiguous `--group` lists the candidates rather than guessing.

Refer to items by ID in every sub-agent prompt and ask for the ID back in the report, so a tick is never matched
against wording that may have changed mid-run.

**`plan.sh show <ID>` is also how a step's text reaches its sub-agent** — its target class, its test class, its
`covers:` list and its scenarios verbatim. Read it from there rather than extracting it from the plan file by
hand: the step context goes into the prompt exactly as written, and hand-extraction is both a permission prompt
and a chance to paraphrase a scenario the agent is supposed to implement literally.

A plan whose items have no IDs predates this format: `plan.sh validate` will say so item by item. Add the IDs
first (the orchestrator owns plan edits), then proceed.

If the script is genuinely absent — an incomplete install — say so and fall back to editing the checkboxes
directly. Everything below still applies; only the mechanics change.

## Version Control

Whether this run commits at all, and how, is the module's **Version Control** section — read it with the other
conventions in step 3 above. Where a stage below says "commit per the Version Control policy," that section is
what it means. Stage 0 is the exception: nothing has changed yet, so it needs no commit.

**A missing or silent section means no commits.** The orchestrator never invents a commit policy — an uninvited
commit is exactly the kind of change a user managing their own history does not want.

## Plan-Readiness Gate (before Stage 0)

A plan is ready for implementation only when the user has actually closed the loops the planning phase opened.
Before running anything, check the plan file itself:

- **The design file** the plan's `**Design:**` header links: `design.sh settled --file <design>` exits 0. An
  unsettled decision means step agents will each invent their own answer to the same question, in different layers.
  The script ships with the `design-task` skill at `scripts/design/design.sh`.
- **Open Questions / Blockers**: every `- Q:` has a non-empty `- A:`, and every blocker recorded by a previous
  (partial) run has a resolution noted. An unanswered question means a step agent downstream will hit exactly the
  ambiguity the planner already flagged.
- **Review Findings**: every `- **F<n>:**` has a non-empty `- Action:` (a deliberate "won't fix" or "accepted as
  is" counts — the point is that it was decided, not that every finding produced a change). A `mechanical` finding
  carries `Action: applied — …`, written by `plan-task` when it applied the fix; that satisfies the gate on its
  own. A `decision` finding, and anything marked `- Escalated:`, needs the user's answer. A plan whose review
  found nothing has its single "no issues found" line instead; that passes.
- If an `Action:` or `A:` prescribes a change to the plan's steps or scenarios, confirm the plan text was actually
  updated to match — a decision written next to a finding but never applied to the affected step is still
  unresolved.

This is a **hard gate**: if anything above is unresolved, stop — do not run the baseline and do not change any
file. List the unresolved items and ask the user to resolve them. If the user resolves them in the conversation,
record their answers in the plan file (the orchestrator owns plan edits), apply any resulting step changes, and
only then proceed.

**Review freshness — no automatic re-review.** A plan is reviewed once, by `plan-task`. Editing it afterwards
does **not** trigger another review pass: not when the user changes a step, not when an `Action:` is applied, not
when a mid-run blocker forces a change. Re-review rounds cost more time than they return once the first pass has
been actioned, so run one only when the user explicitly asks for it.

The gate above still applies in full to whatever findings exist — every `- Q:` needs an `- A:` and every
`- **F<n>:**` needs an `- Action:` — and a plan edit that resolves a gate item must still be reflected in the
affected step's text. What is gone is the "spawn `review-plan` again and repeat until clean" loop.

Defects the missing review would have caught surface instead at the stage guardrails, where they are cheaper to
diagnose against real compiler and test output. When a stage agent reports a plan defect, record it under
`### Open Questions / Blockers` and fix the plan text in place — do not spawn a review to confirm it.

## Stage 0 — Baseline (prerequisite, before any change)

Before touching a single file, run the affected module(s)' full build and entire test suite (including the
architecture-enforcement test), using the commands from the conventions file. This is a **hard gate**:

- **Everything green** (the expected case): proceed — from here on, any failure is attributable to this plan's
  changes.
- **Anything already red**: **stop immediately — do not start any stage and do not change any file.** Report the
  failures to the user (test name, error, suspected cause) and wait for their decision. Do not attempt to fix the
  failures yourself; they predate the plan and fixing them is not this plan's scope. Resume only when the user
  explicitly says how to proceed (typically after the baseline has been made green).

Starting on a red baseline would make it impossible for any later stage to tell whether a failure was caused by
the plan or was broken all along.

## Stage 1 — Stabilization

Covers the plan's **Stabilization** group — its **API Contract**, **Database**, and **Interface-First / Build
Stabilization** sections, in that order. Delegate them as one sub-agent task (they are small, sequential, and share
context), passing the plan's checklist items verbatim plus the module conventions.

**A test method never disappears from the run.** Stabilization changes signatures, and an existing test written
against the old one often cannot compile against the new. Whatever is done to it, it stays something the runner
still reports — **disabled**, by whatever mechanism the module's test framework provides, so it counts as
*skipped* rather than vanishing:

- **It compiles but would now fail** — disable it where it stands, body intact.
- **It cannot compile** — keep the method, disable it, and comment out only the lines inside it. The husk stays
  *within* the method rather than the method being commented out whole.

Either way the reason names the red-phase step that owns the rework (`RU08`), so the skip list is the list of what
is owed. The red agent then adapts a real scenario instead of writing one from nothing, and the plan's `update:`
bullets still name methods that exist.

**Stabilization disables; the Red Phase deletes.** A test that is obsolete rather than owed a rework is removed by
the step that owns it — a red step's `update: … — delete` bullet — not here. Stabilization removes only what a
checklist item names by path, as a file to `git rm`. Between them those two are the *only* authority for a test
leaving the tree; a stage that deletes on its own initiative is a defect wherever it happens.

This is what makes the guardrails below cheap. A commented-out method is an invisible subtraction, and a total
that balances hides it — one removal paying for another. A skipped one is loud, self-clearing, and cannot be
lost track of.

**Stabilization guardrail** — verify yourself before ticking the sections and moving on:

1. **Compile-green**: the affected module(s) compile, including test sources.
2. **Architecture test**: run the module's architecture-enforcement test named in its conventions file and confirm
   it passes — this catches new or moved files that break the layer rules before any test is written against them.
3. **Existing suite still green**: run the module's pre-existing test suite. Unless the plan explicitly calls for a
   breaking change, it must still pass.
4. **No test was lost**: against the Stage 0 baseline, the module's **total** may fall only where a checklist item
   names a deletion, and its **skipped** count names exactly the tests owed to a red-phase step. A green suite
   proves nothing when the tests that would have failed are gone; disabling rather than removing them is what
   keeps the two numbers readable. Read the skip list itself, not just its size — every entry must name a step
   in the plan.
5. **Intent comments present and consistent**: the intent comments inside the stubs are load-bearing — red agents
   derive their assertions from them and green agents implement against them, so a vague or wrong one poisons every
   downstream step and surfaces late, as confusing blockers or wrong-behavior implementations. For every stub
   method that a red-phase step covers (unit or integration — match the plan's `covers:` lists against the stubbed
   classes), open the stub and confirm its intent comment exists and is consistent with that step's
   given/when/then scenarios: the described behaviour, the error cases the scenarios expect, nothing contradicting
   the plan. You have both artifacts in hand — the plan and the stubs — so this is a read-through, not a build
   step. A missing, vague, or contradicting comment is a stabilization defect: fix it (or re-delegate to the
   stabilization sub-agent) before Stage 2 spawns a single red agent.

If any check fails for a reason caused by this plan's changes, fix (or re-delegate) until green. If it fails for a
reason **unrelated to the plan**, apply the [Unrelated Failures](#unrelated-failures--report-dont-fail) rule.

Once the guardrail holds, commit per the Version Control policy.

## Stage 2 — RED Phase (parallel)

Covers the plan's **Red Phase** group — its **TDD Unit Red Phase**, **TDD Integration Red Phase**, and **TDD
System Test Red Phase** sections. Writing tests has no cross-dependencies — the stubs they compile against all
exist after Stage 1 — so:

- Spawn **one sub-agent per unchecked checklist item**, across all three red sections — `plan.sh next --group red`
  lists them, and reports the stage finished rather than rolling into Green. Respect the module
  conventions' **Parallelism** section: if it sets a max parallel RED-phase sub-agents count, spawn no more than
  that many at once, launching the next queued item as each running one finishes, until the whole batch is done. If
  the section is missing or silent, spawn everything at once, uncapped — the step count in the plan is the batch
  size.
- Spawn each item on the agent matching its kind, passing the parsed step context (target class, test class,
  covered methods, and the given/when/then scenarios verbatim) and the module conventions:
    - unit steps → `tdd-unit-red-phase-step`
    - integration steps → `tdd-integration-red-phase-step`
    - system steps → `tdd-system-red-phase-step`

**Per-step guardrail** (the sub-agent verifies; the orchestrator trusts the reports — the stage guardrail below is
the systematic check): the test class it wrote **compiles cleanly and fails at runtime**. A red test that passes against
a stub is as much a defect as one
that doesn't compile — it means the test asserts nothing — with one exception: tests asserting the *absence* of
behaviour (e.g. "no exception is thrown") may legitimately pass against a no-op stub, and sub-agents list those as
expected passes in their reports rather than rework them. Production code must not be touched in this stage.

Tick each item as its sub-agent reports success. If one reports a blocker, leave the item unchecked, record the
blocker, and let the rest of the batch continue — one failed step does not stop the stage, but the stage is only
complete when every item is ticked or explicitly recorded as blocked.

**Stage guardrail — RED exit check** (run yourself once every item is ticked or recorded as blocked, before
Stage 3 starts): run the module's **full test suite** once and compare the results against the sub-agents' reports.
The suite must fail in **exactly the expected places**:

- every **pre-existing** test still passes — a pre-existing test now failing means a red agent's changes (an
  `update:` edit gone wrong, a broken shared fixture) caused a regression; fix or re-delegate to the owning step
  before proceeding;
- the **failing tests are exactly the new ones** the reports claim fail — a reported-red test that actually passes
  (and is not listed as a negative-assertion or inbound early-pass expected pass) asserts nothing real; re-delegate
  it to its step's agent as a defect;
- the reported **expected passes** pass, and nothing else about the new tests deviates from the reports;
- **the skipped count is back to the Stage 0 baseline** — every test stabilization disabled has been reworked by
  the step named in its reason. A test still skipped here is a step that silently skipped its own `update:`
  bullets, and it will never fail loudly enough to be noticed later. Compare against the baseline's number
  rather than against zero: a module whose infrastructure skips on its own (no container runtime, say) starts
  above zero and must return there, not below it;
- **nothing left the tree that no bullet authorized** — the total is the baseline, plus what the red steps added,
  less exactly the methods an `update: … — delete` bullet named. This is the stage where a deletion can still
  hide: stabilization no longer removes anything silently, so a total that does not reconcile here is a red agent
  that dropped a test instead of reworking it.

Green agents build directly on this stage's output — a false red report caught here costs one re-delegated step; the
same defect caught during Stage 3 costs a confused green agent and a plan-level untangling. Do not start Stage 3
until this check holds for every non-blocked item.

Once the check holds, commit per the Version Control policy.

## Stage 3 — GREEN Phase (unit + integration parallel, system last)

Covers the plan's **Green Phase** group — its **TDD Unit Green Phase**, **TDD Integration Green Phase**, and **TDD
System Test Green Phase** sections.

Ordering constraint: green steps run in parallel **except where the plan declares a dependency**. A green step
may carry `after:` naming other green target classes its tests exercise as real, unmocked collaborators (e.g. an
integration adapter whose execution path runs through a mapper implemented at unit level, or a usecase whose unit
tests use a real domain entity another unit step implements) — such a step cannot go green before those steps are
done. **System green depends on everything** (a system test drives the full stack — usecase logic *and* adapters
must exist), so it starts only after every unit and integration green item is ticked. The final production
implementation lands here.

1. Take the unchecked items of **TDD Unit Green Phase** and **TDD Integration Green Phase** as one batch and
   schedule it in **dependency waves**:

   ```
   plan.sh next --group green --section unit --section integration
   ```

   That is the scheduler: it lists exactly the items whose `after:` dependencies are all ticked, so re-running it
   after each tick is what reveals the next wave. Never spawn a step `next` does not list. If a dependency step
   reports a blocker, do not spawn its dependents — record them as blocked by that dependency rather than letting
   them fail for a confusing downstream reason.

   The two `--section` flags are what keep system green out of this batch. System-green items often carry no
   `after:` edges — they depend on everything, which plans express by ordering rather than by listing every ID —
   so without the sections they would come back eligible in the first wave, against the rule below that they run
   last.

   Respect the module conventions' **Parallelism** section: if it sets a max parallel GREEN-phase sub-agents per
   wave, never have more than that many running at once — when eligible items exceed the cap, spawn up to the cap
   and queue the rest, launching a queued one as soon as a running slot frees up. If the section is missing or
   silent, spawn every eligible item at once, uncapped.

   **When the cap forces a choice, take them in the order `next` gives.** It ranks by longest remaining dependency
   chain, and that chain — not the cap — is what sets the phase's wall time: deferring an item that heads a deep
   chain in favour of a leaf costs a whole wave for nothing. The ordering is only advice when every eligible item
   fits under the cap.

   Unit items run on `tdd-unit-green-phase-step` and integration items on `tdd-integration-green-phase-step`,
   each passed its step context and the module conventions.
    - **One class = one sub-agent**: the plan structure normally gives each target class exactly one green item, so
      no two parallel sub-agents ever edit the same production file. If two items do name the same target class,
      merge them into a single sub-agent task covering both — never hand the same class to multiple parallel agents.
2. Wait until every item in the unit + integration batch is ticked or recorded as blocked. Tick items as they
   succeed; run the module's unit and integration suites once the batch is done and confirm both are fully green
   before proceeding. Once green, commit per the Version Control policy (if its granularity commits per wave —
   otherwise this checkpoint is a no-op and the commit happens at stage end).
3. Only then run the **TDD System Test Green Phase** steps — **sequentially, one sub-agent at a time, in plan
   order**, on `tdd-system-green-phase-step`. These fix remaining production bugs until the
   system tests pass; they never modify test classes. System green steps are never parallelized: their fixes may
   land in any production layer, and two entry points routinely share a usecase or an outbound adapter — parallel
   agents would race on the same production files. The one-class-one-agent rule only protects steps whose write
   scope is one class; a system step's write scope is the whole stack. Spawn the next step only after the previous
   one's report is in and its item is ticked (or its blocker recorded), passing along which production classes
   earlier system steps already modified.

**Per-step guardrail**: every test in the step's test class passes.

Once every green item (unit, integration, and system) is ticked or recorded as blocked, commit per the Version
Control policy.

## Stage 4 — Refactor (single sub-agent, whole diff)

The green phases produce correct-but-minimal code, one class at a time; this stage completes the
red–green–**refactor** cycle by reviewing the plan's whole diff at once. Runs only when every unit, integration,
and system green item is ticked (blocked items excluded — a partially blocked plan still gets its completed part
refactored) and the module's full suite is green.

Spawn **one** `tdd-refactor-phase` sub-agent for the entire plan — never in parallel with anything — and pass it:

- the **diff scope**: every production and test file this plan created or modified, compiled from the plan's step
  targets plus the file lists in the step agents' reports (and a version-control diff against the pre-plan
  baseline, if one is available);
- the plan file path (read-only context);
- the module conventions, including the **Refactoring Conventions** section — a module without that section is
  fine (the agent falls back to its defaults plus the style sections); pass whatever style sections exist.

**Stage guardrail** — verify yourself after the agent reports: the full suite is green with the **same test count**
as before the stage (a changed count means a test was lost or duplicated), and the architecture-enforcement test
passes (extractions may have created or moved files). This stage changes no behavior and ticks no checkboxes — if
the agent reports blocker-level findings (a suspected bug the tests missed, an over-specified test), record them
under `### Open Questions / Blockers`.

Once the guardrail holds, commit per the Version Control policy.

## Stage 5 — Wrap-Up and Whole-Plan Guardrail

1. Implement the plan's **Post-Implementation Steps** group, in section order (e.g. **Manual Request Files**) —
   small enough to do directly or via one sub-agent.

   **An item an Open Question authorized carries that question's answer verbatim.** The checklist item is a
   summary written when the answer arrived; the answer is what the user actually asked for, and the two drift in
   exactly the direction that drops half of it. Quote the `- A:` text into the prompt, and before ticking the
   item, read what was produced against that text rather than against the item. The same holds for any
   `- Action:` on a Review Finding that prescribes content.
2. **Whole-plan guardrail** — run yourself, from the conventions' commands: the module(s) fully compile, the
   architecture-enforcement test passes, and **the entire test suite is green** — not just the classes this plan
   touched.
3. Only when the guardrail holds and **no `- [ ]` remains anywhere in the plan file**, move the plan's **whole
   task directory** — `docs/<n>-<task-name>/`, holding `plan.md`, the `design.md` its `**Design:**` header links,
   and anything else the task accumulated — into `docs/implemented/`. Moving the directory rather than the files
   keeps every link inside it working. If unchecked items or blockers remain, leave the directory in place and
   summarize what is open.
4. Commit per the Version Control policy — this is where its **squash-before-archiving** setting applies, if the
   plan was archived in step 3.
5. **Post-implementation actions.** Run what the module conventions' **Post-Implementation Actions** section
   lists, in its order, passing each the archived plan file — **only if step 3 archived it**; anything still
   open skips them all, and the summary says so. An action listed by several affected modules runs once. Each
   states its own commit behaviour.

## Unrelated Failures — Report, Don't Fail

The Stage 0 gate guarantees the run starts green, so this rule covers failures that surface **mid-run** yet turn
out to be **unrelated to this plan** (verify: it reproduces on a code path this plan never touched, or is clearly
environmental/flaky). In that case:

- do **not** treat it as a stage failure and do **not** abandon the run — continue with the plan's own work;
- do **not** silently fix it either — unrelated fixes don't belong to this plan's diff;
- record it under `### Open Questions / Blockers` with enough detail to reproduce (test name, error, suspected
  cause), and call it out in the final summary.

Ideally this never happens — the codebase is expected to be green at all times — but when it does, the plan's scope
wins and the bug gets reported, not chased.

## Tick Policy

- Tick with `plan.sh tick <ID>` and record blockers with `plan.sh block <ID> "<reason>"`; never hand-edit a
  checkbox. Both address the item by ID, so neither depends on the wording matching what it was when the run
  started.
- Completed and verified in this run → `- [x]`; not done or blocked → keep `- [ ]`.
- A checkbox with multiple sub-tasks is ticked only when all of them are done.
- Never tick on a sub-agent's claim alone if the stage guardrail later contradicts it — the guardrail wins.

## Response Style

- Brief, stage-by-stage progress updates: what was spawned, what came back, guardrail results.
- Final summary: sections completed, test-suite status, blockers and unrelated failures (if any), and whether the
  plan was archived.
