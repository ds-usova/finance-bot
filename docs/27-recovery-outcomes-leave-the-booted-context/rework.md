# Rework: the capture tests that assert an outcome stop booting the application

**Affected Modules:** `ledger-service`
**Source:** R3 of [task 23's findings](../implemented/23-broadcast-ledger-changes-to-redis/review/findings.md),
and the dividend R4's row named that
[rework 26](../implemented/26-persistence-owns-the-replication-sql/rework.md) unlocked without taking
**Baseline:** `477e3b0`

## What the code does now

| What | Where | What is wrong with it |
|------|-------|-----------------------|
| Eight scenarios on `@CdcCaptureTest`, four of which only ever assert a `SlotRecoveryOutcome.Status` | `ChangeStreamRecoveryTest` | The status is decided by `runSequence` from what its collaborators answer. Booting the application, the Telegram poll loop, the web layer, the MCP server and both security chains to read it back proves nothing the collaborators could not answer directly. |
| Six calls to `invalidateSlot()` | `ChangeStreamRecoveryTest` | Each writes 25 MB of WAL and polls `wal_status` for up to 60 seconds, to reach a state four of the scenarios never assert on. |
| Two disjunctions | `ChangeStreamRecoveryTest` | `isIn(ENGINE_DID_NOT_STOP, REBUILT)` and `isIn(SLOT_NOT_DROPPED, REBUILT)` pass whichever way the race falls, so neither says what the code does. |
| No scenario at all for `POSITION_NOT_DELETED` | `ChangeStreamRecoveryTest` | One of six statuses is unreachable from a real database on demand, so it was never covered. |
| Four scenarios on `@CdcCaptureTest`, two of which read only what the catalogue answered | `ReplicationSlotMonitorTest` | The monitor maps a retention row onto two gauges. Two scenarios assert that mapping and nothing else. |

`ReplicationCatalogue` and `SlotRebuildSession` are what make this reachable: before rework 26 the statements
were private methods on the classes under test, so there was nothing to answer differently.

## What must stay true

- A scenario that asserts against a really-invalidated slot keeps a real one. Nothing in this module can set
  `wal_status`, so a mocked `lost` proves the mapping and not the rebuild.
- The delete-before-drop ordering stays proven end to end. A unit scenario asserting `POSITION_NOT_DELETED`
  says what the status is, never that the order is safe to crash inside.
- Every status in `SlotRecoveryOutcome.Status` has a scenario after this rework. Today `POSITION_NOT_DELETED`
  has none.

## Steps

- [x] R01 · tests · the recovery's outcome scenarios move out of the booted context
  - test-files:
    - `ledger-service/src/test/java/bot/finance/adapter/cdc/ChangeStreamRecoveryTest.java`
    - `ledger-service/src/test/java/bot/finance/adapter/cdc/ChangeStreamRecoveryOutcomeTest.java`
  - survives: another connection already holds the slot's lock, so the rebuild is refused · a mocked
    `ReplicationCatalogue` answering empty, where it ran against a second real connection holding the lock ·
    `ChangeStreamRecoveryOutcomeTest#whenAnotherConnectionAlreadyHoldsTheSlotsLock_thenTheRebuildIsRefused`
  - survives: the engine will not stop inside the wait, so nothing is deleted or dropped · a mocked
    `ChangeStreamReader` answering `false`, where it ran against a real engine held down by a lost slot ·
    `ChangeStreamRecoveryOutcomeTest#whenEngineWillNotStopInsideTheWait_thenNothingIsDeletedOrDropped`
  - survives: the slot cannot be dropped, so the rebuild reports it and a later call still finishes · a mocked
    `SlotRebuildSession` answering `false`, where it ran against a real slot held open on another connection ·
    `ChangeStreamRecoveryOutcomeTest#whenSlotCannotBeDropped_thenRebuildReportsItAndEngineIsNotStarted` and
    `#whenDropThatFailedIsRetriedAndSucceeds_thenSecondCallRebuildsTheSlot`
  - survives: the abandoned position and its wall-clock time are logged at error · a mocked session answering a
    lost slot with a confirmed position, where it ran against a really-invalidated slot ·
    `ChangeStreamRecoveryOutcomeTest#whenLostSlotIsRebuilt_thenAbandonedPositionAndItsTimeAreLoggedAtError`
  - survives: the stored position cannot be deleted, so the slot is left alone · a mocked session answering
    `false`, which nothing ran against before ·
    `ChangeStreamRecoveryOutcomeTest#whenStoredPositionCannotBeDeleted_thenTheSlotIsLeftAlone`
  - measures: scenarios booting the application to assert a recovery outcome 8 -> 4
  - measures: calls to `invalidateSlot()`, each burning 25 MB of WAL 6 -> 2

- [x] R02 · tests · the slot monitor's metering scenarios move out of the booted context
  - test-files:
    - `ledger-service/src/test/java/bot/finance/adapter/cdc/ReplicationSlotMonitorTest.java`
    - `ledger-service/src/test/java/bot/finance/adapter/cdc/ReplicationSlotMeteringTest.java`
  - survives: a slot holding log behind it is recorded as its retained bytes and its `wal_status` · a mocked
    `ReplicationCatalogue` answering a retention row, where it ran against a real slot with emitted WAL — that
    a real slot answers a positive number is `ReplicationCatalogueTest`'s now ·
    `ReplicationSlotMeteringTest#whenSlotHoldsLogBehindIt_thenRetainedBytesAndWalStatusAreRecorded`
  - survives: a slot of that name existing nowhere is recorded as zero bytes and `ABSENT` · a mocked catalogue
    answering empty, where it ran against a database with no such slot ·
    `ReplicationSlotMeteringTest#whenNoSlotOfThatNameExists_thenRetainedBytesIsZeroAndWalStatusIsAbsent`
  - measures: scenarios booting the application to assert a gauge 4 -> 2

## Open Questions

- **Q1:** `ReplicationSlotMonitorTest`'s *"when no engine in this JVM holds the slot — then the same two numbers
  are still recorded"* proves the monitor needs no engine. Against a mocked catalogue it asserts the same thing
  as the scenario above it, since no engine is involved either way. Keep it in the booted class as the one
  scenario that still proves it, or drop it as covered by the monitor never touching `ChangeStreamReader`?
  - A: Keep it booted, against a real slot with no engine running.
- **Q2:** `ReplicationSlotMonitorTest`'s *"when a healthy slot is kept moving by the heartbeat — then retained
  bytes stays near zero after an idle period"* needs a real slot and a real heartbeat, so it stays booted.
  `ChangeStreamReaderTest` already proves the heartbeat advances the slot while only uncaptured tables are
  written. Keep both, or is the monitor's version the same fact measured twice?
  - A: Keep both. The reader's proves the slot advances, the monitor's proves the gauge an operator watches.
- **Q3:** The two new classes are named for the aspect they cover — `ChangeStreamRecoveryOutcomeTest` and
  `ReplicationSlotMeteringTest` — following `UserRepositoryAdapterConcurrencyTest`, the module's one existing
  second-class-for-one-production-class. Is that the shape, or should the unit scenarios stay inside the
  existing classes and give up the context saving?
  - A: New sibling classes. A context is cached per class, so nothing else stops the boot.
