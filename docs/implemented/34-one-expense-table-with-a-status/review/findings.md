# Review: One Expense Table, With a Status

**1 bug open, 1 refactoring candidate open. No critical defect, no manual check.**

## Bug

Found while measuring this task, but not caused by it: the timeout predates the change, and nothing in the merge
touches slot recovery.

**`ledger-service` — slot recovery answers 503 under an instrumented test run**

- **Given** the coverage run — `tools/agent-test/agent-test.sh --module ledger-service --coverage`, which is what
  `plan-evidence.sh` invokes — with JaCoCo instrumenting the JVM
- **When** `RecoverSlotSystemTest$HappyPath` posts `/actuator/cdc` against a slot it has invalidated
- **Then** the operation rebuilds the slot and answers 200
- **Actual** it answers 503. `ChangeStreamRecovery` waits `ENGINE_STOP_TIMEOUT` = 10s for the Debezium engine to
  stop, gives up, and returns `ENGINE_DID_NOT_STOP`, which `CdcRecoveryEndpoint` maps to 503. Instrumentation
  pushes engine shutdown past the deadline. The same suite passes uninstrumented: this task's `--all` runs were
  green at every stage, and the class passes alone in 23.6s. Task 33's evidence run failed one test on a
  1164-test tree that `--all` passed clean, so this is the second occurrence; that run records no test name, so
  the two are the same shape rather than confirmed the same test
- **Fix** unverified. Raising `ENGINE_STOP_TIMEOUT`, or making it a configurable property, would settle the test.
  Whether it should be settled that way is a product question this run did not answer: the same 10s applies in
  production on a loaded host, where an operator gets a 503 and retries — which may be the wanted behaviour, in
  which case the test is what should change · `ChangeStreamRecovery.java:21`, `CdcRecoveryEndpoint.java:34`

## Refactoring candidate

Both are in `ledger-service`, both raised by the refactor pass over the finished diff, and neither changes what
the service does.

| #  | Status | What                                                                                                 | Why it is a candidate and not a quibble                                                                                                                       |
|----|--------|--------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|
| R1 | done · directly | `ResolveProposalsUseCaseTest.Resolve` proves "already accepted" twice, once for a count of 2 and once for 3 | The module's Testing Style forbids exactly this — a behaviour spanning several values is one `@ParameterizedTest`, never a case duplicated as a one-off. The count is threaded from the stub straight to the assertion, so the second number exercises no boundary and no branch. Merging them keeps the older test's `verify` of `countByMessageReference`, which the newer one omits. `whenDiscardResolvesNothingAndExpensesAlreadyStored_...` is a different operation and stays its own case |
| R2 | open   | `ck_expense_pending_has_message` is proved in two places, one of which pays for a container to do it   | `ColumnLimitsSchemaTest.StatusColumn` and `ExpenseRepositoryAdapterTest.Create`'s `whenPendingRowCarriesNoMessageId_...` seed the same row and assert the same violation. The schema guard belongs to the schema test; the adapter copy spends a container round trip proving a constraint rather than adapter behaviour |

## What was settled during the run, and is not inherited

Recorded here so a reader does not go looking for them in the plan's blockers:

- **B1** — a defect in the plan, not in the code: ST03 dropped `expense_proposal` while ST07 deliberately left
  the `UNION` against it to GI01, so those statements could not run in between. Paid for during stabilization
  with a disable set wider than ST17 named, and cleared at GI01. The plan should have moved the query rewrite
  into stabilization; it is written up here because the next plan against this module can avoid it, not because
  anything is left to do.
- **B2, B3** — two tests that no step owned: `WebSessionSystemTest`'s browse-and-refile, and
  `CountMatching.whenCalledWithCategoryIdBelongingToAnotherUser_thenAnswerIsZero`. Both assigned to GI01 and
  cleared there.
- **B4** — RS01 asserted a nanosecond-precision `created_at` the microsecond column cannot hold. Fixed by
  truncating the seed rather than loosening the assertion.
- **B5** — five findings from the refactor pass. The one real defect, `Expense`'s constructor raising
  `NullPointerException` instead of `InvalidExpenseException` for a bare `null` message id, was fixed under
  RU11 and GU11 on the user's decision. Two stale test-method names still naming the deleted
  `InvalidExpenseProposalException` were renamed while this file was written. `testing.md`'s Package Structure
  tree, still listing the deleted `ExpenseProposalRowUtils`, is the follow-up documentation pass's. The
  remaining two are R1 and R2 above.
