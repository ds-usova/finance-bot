# Rework: An entry is refiled by its id alone

**Affected Modules:** `ledger-service`, `web-app`
**Source:** docs/implemented/34-one-expense-table-with-a-status/review/findings.md, R3 (backlog C5)
**Baseline:** e6a05f8a

## The fix

`PATCH /api/v1/expenses/{id}` names the entry by its id, and the status travels outward from the row instead of
inward from the path. Since ADR 0018 an id is unique across both statuses, so `id` and `user_id` name the row on
their own; the `{status}` segment only reaches `AND status = :status` in `ExpenseEntityRepository.refile`, a
compare-and-set against the status the browser last listed. Everything the status still decides after the write —
the fact's type (`ProposalRefiled` or `ExpenseRefiled`) and the `status` field of the answered entry — is on the
`RETURNING *` row already, so the adapter reads it there.

## What changes

**Move the signature from the path down to the SQL, in one step**

| #   | Kind        | What changes                                                                        | Touches                                                |
|-----|-------------|-------------------------------------------------------------------------------------|--------------------------------------------------------|
| R01 | `stabilize` | drop `{status}` from the path, the controller, the mapper, the command, the use case, the port, the adapter and the query; regenerate both clients | `openapi/`, `bot.finance` (web → persistence), `web-app/src/api` |

**Give every test the segment carried its id-alone shape**

| #   | Kind    | What changes                                                                           | Touches                        |
|-----|---------|----------------------------------------------------------------------------------------|--------------------------------|
| R02 | `tests` | retarget the ledger tests R01 disabled to the id, and merge the PENDING/RECORDED pairs  | `bot.finance` test classes     |
| R03 | `tests` | merge the two web-app path tests into one asserting `/api/v1/expenses/<id>`            | `web-app/src/api/expenses.test.ts` |

**Not changed:** the document (`replace /categoryId`), the answered entry's shape, the `operationId`, the fact
types the change stream carries, `changeCategory(entry, categoryId)`'s parameters in the web app, and every
other statement in `ExpenseEntityRepository`.

## What the code does now

| What                                             | Where                                             | What is wrong with it                                                                                          |
|--------------------------------------------------|---------------------------------------------------|----------------------------------------------------------------------------------------------------------------|
| `{status}` path parameter                        | `openapi/paths/expense-category.yaml:11`          | "Guards the entry's current status" — a guard against a race whose losing outcome was harmless and wanted      |
| `AND status = :status`                           | `ExpenseEntityRepository.java:160`                | the only reader of the segment; a Confirm landing between the list and the PATCH turns a legal refile into a 404 |
| `ExpenseStatus.valueOf(status)` and its 400      | `ExpenseWebMapper.java:79`                        | parses and refuses a value nothing needs                                                                       |
| `status` on `ChangeExpenseCategoryCommand`       | `ChangeExpenseCategoryCommand.java:8`             | threaded through the command, the use case and the port to reach one predicate                                 |
| `toExpenseEntry(ExpenseStatus)`                  | `SpendingRowProjection.java:25`                   | takes the status as an argument while carrying it as a field                                                   |
| `LedgerEventType.refiled(status)` from the input | `ExpenseRepositoryAdapter.java:209`               | the fact's type is chosen from what the caller said rather than from the row that was written                  |
| `${EXPENSES_PATH}/${entry.status}/${entry.id}`   | `web-app/src/api/expenses.ts:62`                  | the one client, in this repository, sending the segment                                                        |

## What must stay true

- The fact a refile publishes is still `ProposalRefiled` for a pending row and `ExpenseRefiled` for a recorded
  one. `ExpenseRepositoryAdapterEventsTest.Refile` asserts the type; that the consumer still applies it is
  `ai-connector-service`'s suite, which this rework does not run.
- `web-app/src/api/generated/ledger-api.d.ts` is regenerated from the spec, never edited. A stale copy would
  type-check against a path that no longer exists, and `WebSessionSystemTest`'s browse-and-refile flow is the one
  test that would notice at runtime.
- Another person's entry is never refiled: `user_id = :userId` stays in the predicate.
  `ExpenseRepositoryAdapterTest.Refile.whenCalledForAnotherPersonsExpense_...` asserts it.

## Steps

The steps live in [`shared/steps.md`](shared/steps.md), [`ledger-service/steps.md`](ledger-service/steps.md)
and [`web-app/steps.md`](web-app/steps.md).

## Open Questions

- **Q1:** Dropping the guard changes one observable answer: an id patched under the other status answered 404 and
  now answers 200, refiling the entry. The finding calls the blocked operation harmless and wanted, and the design
  of task 34 deferred it deliberately. Is that within this rework's scope, or does it go back through
  `design-task`?
  - A: Yes, in scope. The finding states the outcome change, and the row was filed as a candidate knowing it.
- **Q2:** R01 disables ten ledger tests and two web-app tests whose assertions the segment's removal invalidates.
  R02 and R03 retarget each to the id-alone shape, and where a PENDING case was a copy of its RECORDED sibling
  differing only in the segment — the controller, the mapper, the use case and the web-app client each have one —
  the pair becomes one test. The `status is absent` command test has no subject left and is deleted. Agreed?
  - A: Yes, merge and delete as written.
