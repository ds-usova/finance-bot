# Bug: slot recovery answers 503 while the connector is retrying a slot it cannot open

**Affected Modules:** `ledger-service`
**Source:** [task 34, review findings, the open bug](../34-one-expense-table-with-a-status/review/findings.md#bug)
**Baseline:** `bc75b44` · `ledger-service` — 1124 tests, 1 skipped: the reproduction `ChangeStreamReaderTest$Stop#whenStoppedWhileConnectorStillTryingToOpenHeldSlot_thenStopsWithinTenSeconds`, disabled under `R01`; container-backed classes skip without Docker. The run (`suite-20260818-141921-1762`) was red on one thing only, `DisplayNameConventionsTest` refusing the reproduction's own 121-character display name; the name was shortened and that class re-run green (`DisplayNameConventionsTest-20260818-142259-220`)
**Attempts:** bug.md · A1–A2, fix.md · —

## What happens

- **Given** a slot the engine cannot open — lost, or held by another connection — and a Debezium connector
  that is between attempts to open it
- **When** the recovery operation stops the reader, or anything else calls `ChangeStreamReader.stop` with the
  recovery's ten-second bound
- **Then** the engine stops within the bound and the recovery goes on to rebuild the slot
- **Actual** `stop` waits the full ten seconds and answers `false`; `ChangeStreamRecovery` returns
  `ENGINE_DID_NOT_STOP` and `POST /actuator/cdc` answers 503

## How it reproduces

The report's own reproduction is the coverage run, `tools/agent-test/agent-test.sh --module ledger-service
--coverage`, where `RecoverSlotSystemTest$HappyPath` fails one run in three or so — the timing that puts the
connector between attempts is a window of a few dozen milliseconds. Its run of 2026-08-18 00:41 records:

```
- RecoverSlotSystemTest$HappyPath > when an invalidated slot is recovered - then 200 carries both positions and streaming resumes
    type: java.lang.AssertionError
    message: java.lang.AssertionError: 1 expectation failed. Expected status code <200> but was <503>.
```

and its application log, on the request thread:

```
00:44:23.141 [tomcat-handler-38] INFO  i.d.e.async.AsyncEmbeddedEngine - Engine state has changed from 'POLLING_TASKS' to 'STOPPING'
00:44:32.056 [...change-event-source-coordinator] WARN  i.d.c.p.c.PostgresReplicationConnection - Failed to start replication stream at LSN{0/6346128}, waiting for PT10S ms and retrying, attempt number 2 over 6
...
00:45:22.074 [tomcat-handler-38] INFO  i.d.e.async.AsyncEmbeddedEngine - Stopped task #1 out of 1 tasks (it took 58933 ms to stop the task).
```

The same state is reached on purpose, and every time, by holding the slot from a second connection so the
connector's every attempt to open it fails: `ChangeStreamReaderTest$Stop`'s
`whenStoppedWhileConnectorStillTryingToOpenHeldSlot_thenStopsWithinTenSeconds`, disabled in the tree until
`R01` enables it. Run twice, failed twice:

```
- ChangeStreamReaderTest$Stop > when stopped while its connector is still trying to open a slot held elsewhere - then the reader stops within ten seconds
    type: org.opentest4j.AssertionFailedError
    message: org.opentest4j.AssertionFailedError:  Expecting value to be true but was false
    at bot.finance.adapter.cdc.ChangeStreamReaderTest$Stop.whenStoppedWhileConnectorStillTryingToOpenHeldSlot_thenStopsWithinTenSeconds(ChangeStreamReaderTest.java:529)
```

```
14:14:45.968 [Test worker] INFO  i.d.e.async.AsyncEmbeddedEngine - Engine state has changed from 'POLLING_TASKS' to 'STOPPING'
14:14:55.967 [...change-event-source-coordinator] WARN  i.d.c.p.c.PostgresReplicationConnection - Failed to start replication stream at LSN{0/1C65CE0}, waiting for PT10S ms and retrying, attempt number 2 over 6
org.postgresql.util.PSQLException: ERROR: replication slot "change_stream_reader_test" is active for PID 164
...
14:15:46.006 [Test worker] INFO  i.d.e.async.AsyncEmbeddedEngine - Stopped task #1 out of 1 tasks (it took 60035 ms to stop the task).
```

## Why it happens

1. `ChangeStreamRecovery.recover` calls `changeStreamReader.stop(ENGINE_STOP_TIMEOUT)` with ten seconds
   (`ChangeStreamRecovery.java:21`, `:55`); `false` becomes `ENGINE_DID_NOT_STOP`, which
   `CdcRecoveryEndpoint.java:34` maps to 503.
2. `ChangeStreamReader.stop` calls the engine's `close()` and then waits on the completion latch for whatever is
   left of the bound (`ChangeStreamReader.java:142-147`). The latch is counted down by Debezium's completion
   callback, which fires only once the engine's own `close()` has returned.
3. `AsyncEmbeddedEngine.close()` stops the task synchronously: `stopSourceTasks` submits `task.stop()` and waits
   for it (`task.management.timeout.ms`, 180 s by default) — the two log lines above are its "Stopping down
   connector" and its "Stopped task #1 ... (it took N ms)".
4. `BaseSourceTask.stop` calls `ChangeEventSourceCoordinator.stop`, which does `executor.shutdown()` — no
   interrupt — and then `awaitTermination` for `SHUTDOWN_WAIT_TIMEOUT` (90 s) on the coordinator's single
   thread.
5. That thread is inside `PostgresReplicationConnection.startStreaming`'s retry loop: an attempt to open the slot
   fails, and the loop sleeps `slot.retry.delay.ms` (10 s) and tries again, up to `slot.max.retries` (6) times,
   before it throws. Nothing in that loop reads the coordinator's `running` flag. Once the loop gives up, the
   thread ends, the coordinator's wait returns, and the task is stopped — 58.9 s after the stop was asked for in
   the coverage run, 60.0 s in the reproduction. Neither Debezium setting is set in
   `ChangeStreamConfiguration.java:50-79`, so both stand at their defaults.
6. Where the connector was instead between restarts — its previous attempt already thrown, the task in
   `RESTARTING` — the same stop blocks on `BaseSourceTask.stateLock`, which the polling thread holds while it
   parks for `retriable.restart.connector.wait.ms` (10 s) in `startIfNeededAndPossible`. That wait ignores the
   interrupt the engine sends. Read from the sources, not observed in a run: it is the other window in the same
   cycle, and it is as long as the bound.

So the bound is a race against Debezium's own waits, and one of those waits alone is six times the bound.

## What the fix must not break

- A stop of a streaming engine still returns `true` and leaves the slot in place — `ChangeStreamReaderTest$Stop`'s
  first test.
- The recovery still refuses a slot that is merely behind (`SLOT_NOT_LOST`), and still rebuilds a lost one under a
  stopped reader — `ChangeStreamRecoveryTest`, `RecoverSlotSystemTest`.
- A slot held by another connection is still retried, never given up on: Debezium's connector-level retry
  (`errors.max.retries`, unlimited) restarts the task after each failed cycle, as it does today. The cycle only
  gets shorter.
- Nothing about what is streamed, published or committed changes.

## Open Questions

- **Q1:** The fix agreed before the diagnosis was a configurable `cdc.engine-stop-timeout` (10 s in
  production, 30 s in the test profile) plus an explicit `offset.flush.timeout.ms`. The diagnosis found the
  wait is up to 60 s and comes from three Debezium settings, so the fix caps those instead. Add the
  configurable stop timeout as well? Recommended: no — it did not cause the failure and would not have fixed it,
  and the fix stays inside the cause.
  - A: no. Apply as written; the three Debezium settings only.

## Attempts

- **A1** · diagnosis · Reproduced the state at the adapter layer by starting a reader against a slot the test
  had first invalidated, expecting the connector to enter the slot-open retry that the coverage run's log shows.
  - why: a lost slot is what the report's own test creates, and `ChangeStreamRecoveryTest` already invalidates
    one on purpose.
  - result: failed — the connector never reached that retry. A reader started against a slot whose
    `restart_lsn` the invalidation has cleared loops elsewhere first: `PostgresConnection.readReplicationSlotInfo`
    re-reads the slot every two seconds, up to 900 times, waiting for a valid position, and the engine sits in
    task start until then.
  - evidence:
    ```
    14:06:33.024 [pool-10-thread-1] WARN  i.d.c.p.c.PostgresConnection - Cannot obtain valid replication slot 'change_stream_recovery_test' for plugin 'pgoutput' and database 'ledger_db' [during attempt 1 out of 900, concurrent tx probably blocks taking snapshot.
    14:06:35.026 [pool-10-thread-1] WARN  i.d.c.p.c.PostgresConnection - Cannot obtain valid replication slot 'change_stream_recovery_test' for plugin 'pgoutput' and database 'ledger_db' [during attempt 2 out of 900, concurrent tx probably blocks taking snapshot.
    ```
  - ruled-out: a slot lost before the reader starts is not the way into the reported wait. It is a defect of its
    own — see the report — and its 30-minute loop is not configurable.
- **A2** · diagnosis · Reproduced the state by invalidating the slot underneath a reader that was already
  streaming, with Redis cut so the reader could not confirm positions, expecting the walsender's death to put the
  connector into the slot-open retry.
  - why: that is the shape `RecoverSlotSystemTest` builds, and the coverage run failed inside it.
  - result: failed — within sixty seconds the connector went the other way: streaming ended, the task restarted
    after its ten-second backoff, and the restart landed in the same 900-attempt slot read as A1. The coverage
    run's connector hit the slot-open retry only because the invalidation landed in the few milliseconds between
    its position search and its `START_REPLICATION`, a window a test cannot aim for.
  - evidence:
    ```
    14:09:41.560 [...change-event-source-coordinator] INFO  i.d.p.ChangeEventSourceCoordinator - Finished streaming
    14:09:41.648 [pool-3-thread-1] WARN  i.d.connector.common.BaseSourceTask - Going to restart connector after 10 sec. after a retriable exception
    14:09:51.955 [pool-3-thread-1] INFO  i.d.connector.common.BaseSourceTask - Attempting to restart task.
    14:09:52.017 [pool-3-thread-1] WARN  i.d.c.p.c.PostgresConnection - Cannot obtain valid replication slot 'change_stream_recovery_test' for plugin 'pgoutput' and database 'ledger_db' [during attempt 1 out of 900, concurrent tx probably blocks taking snapshot.
    ```
  - ruled-out: a lost slot cannot reproduce the reported wait on demand. A slot held by another connection can:
    every `START_REPLICATION` against it fails at once with `is active for PID`, which is what the reproduction
    now does.
