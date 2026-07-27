---
description: Review an existing plan file against the real codebase (mechanical lint, boundary audit, test-scenario audit) and append findings to its Review Findings section. Runs as a fresh-context subagent — automatically as plan-task's last step, or standalone against any plan file.
argument-hint: [ plan file path ]
---

# Review Plan

Independently verify the claims in an existing plan file against the real codebase. Do not trust the plan's own
assumptions at face value — this skill runs in a fresh context precisely so it isn't biased by whatever reasoning
produced the plan. It has full read access to the repository (test files, production code, conventions files), not
just the plan file text; the test-scenario audit in particular depends on reading the real code, not the plan's
description of it.

## 1. Locate the Plan and Its Modules

Read the plan file at the given path in full. Read `<module>/docs/conventions.md` for every module listed in
**Affected Modules** (and the repo-root `docs/conventions.md` if present) — the boundary audit and test-scenario
audit both depend on knowing the module's real layer mapping, naming conventions, and architecture-enforcement test.

## 2. Checklist

Work through the checklist in this exact order — later steps require more judgment and build on the earlier ones
holding.

### 2.1 Mechanical Lint

Run `plan.sh validate` (at `scripts/plan/plan.sh` under the plugin root — `${CLAUDE_PLUGIN_ROOT}` installed,
`.claude/` in a plain checkout) before working through this list, and treat what it prints as already known.
Duplicate IDs, items with no ID, `after:` naming an ID nothing defines, dependency cycles, placeholder
given/when/then values, and `update:` bullets naming a test method that exists nowhere in the repository are its
job — do not re-derive them by hand and do not report them again as findings.

- Confirm every section required by `plan-task.md`'s **3. Plan Structure** is present, and in the fixed order.
- Confirm the **Step-by-Step Implementation Map** nests correctly: the four `### <Group>` headings — Stabilization,
  Red Phase, Green Phase, Post-Implementation Steps — appear in that fixed order, and every `#### <Section>`
  heading sits under the group `plan-task.md` assigns it to (e.g. no `TDD Unit Red Phase` section floating outside
  the Red Phase group, no stabilization section appearing under Green Phase).
- Confirm every step under **TDD Unit Red Phase**, **TDD Integration Red Phase**, and **TDD System Test Red Phase**
  follows its mandated step format exactly (`<TargetClass>` · test: `<TestClass>` · covers: line — plus `mocks:`
  for inbound-adapter integration steps — given/when/then sub-bullets, and optional `update:` sub-bullets for
  existing tests).
- Confirm every Green-phase step corresponds 1-to-1 with a Red-phase step (same target class, same test class) — no
  Green step without a matching Red step, and no Red step left without a Green step.
- Confirm every `after:` reference on a Green-phase step names a class that is itself a Green-phase target in the
  plan, and that the `after:` graph contains no cycles.
- Confirm every class stubbed in **Interface-First / Build Stabilization** appears as a target in some Red phase, or
  is validly excluded under the simple-delegation rule (a one-line pass-through with no logic of its own).
- Confirm every shared fixture, builder, or base-class capability a Red Phase step's scenarios rely on (beyond what
  that single step needs) is listed under stabilization's **Shared Test Infrastructure** sub-group — a step that
  quietly assumes a shared helper exists without that sub-group creating it will duplicate or block at
  implementation time.

### 2.2 Boundary Audit

- Confirm every new/changed class is placed in the layer its module's conventions file maps it to (domain,
  application/usecase, adapter).
- Confirm every **TDD Unit Red Phase** step fakes/mocks only outbound ports — no real infrastructure, no
  application-framework context.
- Confirm every **TDD Integration Red Phase** outbound-adapter step calls only the adapter-under-test's own public
  methods — never through a usecase, a port default method, or the full application context.
- Confirm every **TDD Integration Red Phase** inbound-adapter step mocks only the adapter's inbound port / usecase
  beans and uses no real infrastructure — entered through the protocol via the framework's slice mechanism, never
  by direct method calls, and never with real usecases or the full application context.
- Confirm every **TDD System Test Red Phase** step enters only through an inbound port, the way production does
  (HTTP via the API-level test client, or a framework-fired trigger induced as in production — never a direct
  inbound-port method call), and stays a thin slice — per entry point, a happy path and a representative error path;
  flag any field-validation matrix listed at system level (it belongs to the inbound adapter's integration step).
- Confirm Green-phase `after:` markers match the real collaborator graph: a green step whose tests exercise
  another green target as a **real, unmocked collaborator** (a domain entity/value object in a unit test, an
  unmocked mapper or domain object on an adapter's execution path in an integration test) must carry an `after:`
  naming it; flag a missing marker, and flag an `after:` on a collaborator the tests actually mock (a false
  dependency that needlessly serializes the schedule).

Treat this as a dry run, at plan level, of the module's architecture-enforcement test — flag anything that test
would reject if the code existed today.

### 2.3 Test-Scenario Audit

Read the actual production code and schema the plan describes changing — not just the plan's prose — before judging
this section.

- For every request/entity field the plan touches, confirm there is a corresponding validation scenario; flag any
  field with no validation coverage.
- Check for missing boundary values relevant to the field's type: `null`, empty, max-length, unknown-id, and similar
  edges.
- Confirm every error path the plan introduces has a matching unhappy-path scenario; flag any error path with only
  happy-path coverage.
- Flag any scenario tested at the wrong layer — the default home for request validation, binding, and the
  status-code contract is the inbound adapter's integration step (e.g. field validation listed at system level, or
  the same scenario duplicated across two layers when one would suffice).
- Verify the plan's **coverage balance rule** claims: open the actual `<TestClass>` files the plan references and
  confirm the scenarios listed as "new coverage" are not already covered by an existing test.
- Verify the plan's **existing-test updates rule** the other way around: in those same test files, flag any existing
  test whose assertions the planned change would leave incomplete (a new field it omits, a grown enum/case set an
  exhaustive test iterates) that has no corresponding `update:` sub-bullet in the plan — step sub-agents implement
  only what is listed, so a missing `update:` bullet means the update never happens.

## 3. Report Findings — Never Edit

Report only. Do not modify production code, test code, or the plan's structure or sections — the only edit this
skill makes to the plan file is writing its **Review Findings** section.

Append one entry per finding, in this exact format:

```
- **F1:** [what's wrong or missing, with file/class/scenario reference]
- Resolution: mechanical | decision
- Action:
```

Findings are numbered `F1`, `F2`, … continuing past the highest number already in the section; a number is
assigned once and never renumbered. Leave `Action:` empty — it records how the finding was resolved, and is
written by whoever resolves it, never by this skill.

### Classifying a finding

`Resolution:` decides **who** resolves the finding: the orchestrator applies the settled ones and puts only the
open ones in front of the user. Eleven findings in one review is an unreadable inbox when nine of them have one
possible answer. Classify by a single test:

- **`mechanical`** — the fix is fully determined by something already written down: a rule in `plan-task.md`, a
  module's conventions file, or the code as it exists. One correct outcome, no taste involved.
- **`decision`** — resolving it means choosing between outcomes that are each defensible. What the system should
  do, what a value object should permit, which of two acceptable designs to take: a decision, however obvious the
  answer looks from here.

Classify by the **fix**, not by severity. A blocker whose fix a written rule dictates is `mechanical`; a small
matter of taste is a `decision`.

**Escalate to `decision` regardless of that test** when the fix would:

- add or remove a checklist item, or change a step's target class or test class;
- change a contract artifact — an API schema, a proto file, a migration;
- contradict an answer already recorded under **Open Questions / Blockers**.

Those reshape the plan rather than correct it, and reshaping is the user's call.

State the correct fix in the finding text either way. A `mechanical` finding whose text does not say what to
change cannot be applied without guessing, which lands it back in front of the user for the wrong reason.

If nothing is wrong, still write the **Review Findings** section (or replace its placeholder, if invoked via the
`plan-task` hook) with a single line stating no issues were found — its presence must be consistent across every
plan, clean or not.

## 4. Re-Reviews

A plan is re-reviewed whenever it is materially edited after its first review (the `implement-plan` skill's
plan-readiness gate triggers this). On a re-review — recognizable because **Review Findings** already contains
entries:

- **Never modify or delete existing findings, their `Resolution:` lines, or their `Action:` lines** — they record
  what was decided and what was already applied, and stay part of the plan's history even when the finding is now
  resolved or obsolete.
- Append a marker line `Re-review (<date>):` after the existing entries, then the new findings beneath it in the
  same exact format, each carrying its own `Resolution:`. Judge the plan **as it now stands** — a previously
  reported finding that still applies and was answered with a decision stands as decided; do not re-report it. A
  `mechanical` finding whose `Action:` says it was applied is likewise settled: report only what the applied fix
  got wrong, not the original finding again.
- If the re-review finds nothing new, append `Re-review (<date>): no new issues.` instead — a re-review that
  leaves no trace is indistinguishable from one that never ran.
