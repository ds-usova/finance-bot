# Review: the capture tests that assert an outcome stop booting the application

**Nothing open.** Every invariant under **What must stay true** was kept, both `measures:` claims held, and every
`survives:` scenario was found running again by name.

R3 of [task 23](../../implemented/23-broadcast-ledger-changes-to-redis/review/findings.md) stays open where it
was raised. It asks for the three `adapter/cdc` classes to run on the database slice with Redis mocked; this
rework moved the scenarios that never needed the wide context out of it, and all three classes still carry
`@CdcCaptureTest` for what remains.

One assertion was found dropped while its scenario name survived — a failed drop followed by a second call that
finishes — and was restored inside the step, as
`ChangeStreamRecoveryOutcomeTest#whenDropThatFailedIsRetriedAndSucceeds_thenSecondCallRebuildsTheSlot`.
