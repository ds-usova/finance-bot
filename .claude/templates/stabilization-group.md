# A Plan's Stabilization Group

Which items the group holds and in what order. Written by [`plan-task`](../skills/plan-task/SKILL.md), read by
[`review-plan`](../agents/review-plan.md) and by whoever applies the group.

How each item is *carried out* — the stub's intent comment, the `TODO` on a changed signature, what a comment may
never name, how a broken test is disabled — is [`stabilizing.md`](stabilizing.md), the one statement of it for
every workflow that stabilizes. An item here says *what* is created or changed; that file says *how*. The worked
stub shape is in [`example-plan.md`](example-plan.md).

Sections appear in this order; use only the ones the task needs.

## API Contract

API schema and path changes, in the module's schema format and location (see conventions file). First within the
group when contract changes are involved.

## Database

Migrations and schema changes, in the module's migration format (see conventions file).

Contract artifacts — the API schema and migrations — are created entirely in these two sections. A red-phase test
must fail on an assertion, never on a missing table or schema constraint, and TDD step agents never create or edit
a contract artifact. A gap found later is a blocker back to the plan.

## Interface-First / Build Stabilization

Update interfaces and signatures first, add temporary stubs for new methods, add or update configuration, add or
extend the shared test infrastructure the upcoming Red Phase steps will need, and resolve build errors
(compile/type-check per the module's stack) before implementing full logic.

Always present, and always last within the group. Its items group under these labelled sub-groups — bold labels,
not headings — in this order. Omit a sub-group entirely rather than leaving it empty.

**Interface & Signature Sync**

- sync all affected API/interface contracts and method signatures,
- for **new** methods and fields: an item per stub, its intent stated,
- for **existing** methods whose signature changes: an item naming the change and the call sites it breaks, test
  tree included, until the module builds green.

**Configuration**

- add or update any configuration this task's design requires — an outbound client's address, a schedule
  expression, a pool setting, a new environment variable and its default. Configuration belongs here, never inside
  a step agent's scope: a red-phase test fails on an assertion, never on a missing property.

**Shared Test Infrastructure**

- add or extend any test fixture, builder or base-class capability more than one upcoming Red Phase step will
  need. Every red-phase step agent is scoped to add no shared fixture beyond what its own step needs, so shared
  test infrastructure has exactly one owner and gets written once. Listing it here is what stops two parallel
  steps duplicating it or blocking on each other.

## Closing item

After stabilization, confirm the module's architecture-enforcement test still passes — per the conventions file,
where the module has one.
