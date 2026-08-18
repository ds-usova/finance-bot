# Fix: bound the connector's own waits below the reader's stop bound

**Affected Module:** `ledger-service`
**Bug:** [slot recovery answers 503 while the connector is retrying a slot it cannot open](bug.md)
**In flight:**

## Steps

| #   | Kind  | What changes                                                                        | Touches                     |
|-----|-------|-------------------------------------------------------------------------------------|-----------------------------|
| R01 | red   | enable the test that stops a reader whose connector is retrying a held slot         | `ChangeStreamReaderTest`    |
| G01 | green | cap the connector's slot-open retry and restart backoff below the ten-second bound  | `ChangeStreamConfiguration` |

- [x] R01 · red · the reader is stopped while its connector is still trying to open a slot held elsewhere, and
  must stop within ten seconds
  - test-files:
    - `ledger-service/src/test/java/bot/finance/adapter/cdc/ChangeStreamReaderTest.java`
    - `ledger-service/src/test/java/bot/finance/common/ReplicationSlots.java`
  - reproduces: `stop(Duration.ofSeconds(10))` answers `false` — the engine is still stopping when the bound
    runs out, because its connector sleeps between attempts to open the slot; the recovery turns that into 503
  - runs: `ChangeStreamReaderTest$Stop#whenStoppedWhileConnectorStillTryingToOpenHeldSlot_thenStopsWithinTenSeconds`
  - docs: `ledger-service/docs/conventions/testing.md`

- [x] G01 · green · the engine configuration sets `slot.max.retries`, `slot.retry.delay.ms` and
  `retriable.restart.connector.wait.ms` so that no wait the connector takes between attempts outlasts the bound
  the reader is stopped with
  - files:
    - `ledger-service/src/main/java/bot/finance/adapter/cdc/ChangeStreamConfiguration.java`
  - fixes: R01
  - runs: `ChangeStreamReaderTest$Stop#whenStoppedWhileConnectorStillTryingToOpenHeldSlot_thenStopsWithinTenSeconds`

The test and the `ReplicationSlots.hold` helper it uses are already in the tree, uncommitted, from the
reproduction; the test is `@Disabled("R01: …")`. R01's work is to remove the annotation and see it fail; G01's
is the three settings — one retry after one second, then a two-second backoff before the task restarts — with
one comment saying what the three have in common. The `docs:` line on R01 is the Package Structure tree in
`testing.md`, whose entry for `ReplicationSlots` names what it does.

## Attempts
