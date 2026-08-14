# Review: Broadcast Ledger Changes to Redis

**1 of 4 refactoring candidates still open, 5 manual checks. No open bugs.** Every entry is `ledger-service`.

## Refactoring candidate

| #  | Status    | What                                                                             | Why the task left it                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
|----|-----------|----------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| R1 | open      | One Spring context per system test class, now ~20 booted applications in one JVM | Already agreed as its own task. The suite needed `maxHeapSize = "2g"` and `max_connections=300` to run at all; both are symptoms. `testing.md` documents context-per-class as a benefit, which is the convention to change — a distinct property should be the exception, not the isolation mechanism.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| R2 | done · 24 | `CategoryNameResolver.evict(id)` clears the whole cache rather than one entry    | `CategoryNames` carries two names and no ids, and `CategoryNameReader.findNames(long)` answers only that record, so no cached entry knows its parent and no grouping-to-categories index can be built. A rename therefore empties a 50 000-entry cache, which is more than D16 and D17 assumed. Widening the projection and the record to carry the parent id is the fix, and it is a design-level change rather than a green step's.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| R3 | done · 28 | The three `adapter/cdc` tests boot the whole application rather than a slice     | `ChangeStreamReaderTest`, `ChangeStreamRecoveryTest` and `ReplicationSlotMonitorTest` carry `@CdcCaptureTest`, which is `@SpringBootTest`, while this plan classified all three as integration-outbound — the type [Testing](../../../../ledger-service/docs/conventions/testing.md#test-layers) wires only the adapter under test for. One annotation was made to serve them and the four system tests, and the system tests set the width: each adapter test also starts a Telegram poll loop, the web layer, the MCP server, the gRPC client and both security chains, none of which it touches. Target shape — the database slice with Redis mocked for the three; the system tests keep the whole context; `RedisChangeStreamWriterTest` needs nothing, already being a narrow test against a real Redis.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| R4 | done · 26 | Change capture carries its own SQL, so two actors change one class               | `ChangeStreamRecovery`, `ReplicationSlotMonitor`, `ChangeStreamReader` and `ChangeStreamConfiguration` each issue their own statements, so a Postgres upgrade and a change to the capture process both open the same file. `ChangeStreamRecovery` shows it plainest: one public `recover()` over six private statements — `pg_try_advisory_lock`, `pg_advisory_unlock`, `pg_replication_slots`, `DELETE FROM debezium_offset_storage`, `pg_drop_replication_slot`, `pg_current_wal_lsn` — plus a raw `DataSource`, its own `Connection` lifecycle, and Debezium's offset-table name, which is the storage engine's schema detail rather than the sequence's business. What the class is actually for names no database: take the lock or refuse, gate on `lost`, stop the engine, delete the stored position *before* dropping the slot so a crash between them heals as a first start, log what was abandoned. Target — the statements move to a collaborator in `adapter/persistence`, `adapter/cdc` orchestrates it, and an ArchUnit rule keeps `org.springframework.jdbc..`, `org.springframework.data.jdbc..`, `javax.sql..` and `com.zaxxer.hikari..` out of every package but `adapter/persistence`. `ChangeStreamConfiguration`'s `(HikariDataSource)` cast goes with it: persistence hands over the connection details rather than the pipeline reaching into the pool. Two dividends — most of `ChangeStreamRecovery`'s eight integration scenarios become unit tests over a mocked collaborator, leaving only those needing a real slot; and with one seam to Postgres and another to Redis, `adapter/cdc` is settled as a sibling package rather than a child of either. |

Verdict: both are valid finding, and both will be tackled with a separate PR:

- the context-per-class change should define different telegram scenarios rather than isolating the each system test;
- the cache eviction is not what I thought it's gonna look like, it should be redesigned

## Manual test

**[ ] A second instance meets a slot the first already holds**

- **Given** one instance streaming and holding `finance_ledger_cdc`
- **When** a second instance starts against the same database
- **Then** its capture component reads `STANDBY` and it serves every endpoint normally

**[ ] The instance holding the slot dies**

- **Given** two instances, one streaming and one standby
- **When** the streaming one is killed
- **Then** the standby takes the slot once Postgres releases it and resumes from the last committed position

> Both of the above are A9 and A10, and no automated test reaches them. Four arrangements were tried inside one
> JVM — a raw pgjdbc replication stream, the same pumped on its own thread with the holder confirmed through
> `active_pid`, a second `ChangeStreamReader` sharing the beans, and that reader given its own engine identity —
> and every one ended with the contender `STREAMING`, because the slot read as unheld at the moment it started.
> The obstacle is structural: these are about two *instances* electing a streamer, and one JVM against one shared
> container keeps recreating the slot underneath the arrangement. What is proven is the permanent half, a
> non-logical `wal_level` reaching `DOWN` and stopping, and the classification logic itself.

**[ ] The recovery operation refuses a slot that is merely behind**

- **Given** a slot whose `wal_status` is `extended` or `unreserved`
- **When** `POST /actuator/cdc` is posted with the secret
- **Then** it answers 409 and the slot is untouched

> `RI05` proves this for `reserved` only. Landing exactly on `extended` or `unreserved` depends on checkpoint
> timing against `max_slot_wal_keep_size` and cannot be forced from a test. The refusal is one branch keyed on
> "not `LOST`", and `RU10` covers all four states' mapping, so the gap is in the integration proof rather than
> in the behaviour.

**[ ] `max_slot_wal_keep_size` cannot be widened without a restart**

- **Given** the deployment shape `infrastructure/docker-compose.yaml` writes, which passes the bound as
  `postgres -c max_slot_wal_keep_size=1GB`
- **When** an operator raises it during a Redis outage to buy time, via `ALTER SYSTEM` and `pg_reload_conf()`
- **Then** they learn whether the new value takes effect

> A command-line value outranks `ALTER SYSTEM`, so it does not — the container must restart. The design assumed
> the setting was reloadable in every shape. `docs/configuration.md` states the restart cost; this check is
> whether that cost is acceptable to whoever operates it, since it lands during exactly the incident the bound
> exists for.

**[ ] The engine reports `DOWN` promptly when Redis stops accepting writes**

- **Given** a streaming engine and Redis made unreachable
- **When** `/actuator/health` is read
- **Then** the capture component reads `DOWN` while the outage lasts

> `GS04` dropped this as an intermediate assertion: an already-established Lettuce connection does not fail the
> instant the connection is cut, so the await raced its timeout. That the component reads `DOWN` for a stalled
> engine is covered by `ChangeStreamHealthIndicatorTest`; how quickly it gets there against a real outage is not.
