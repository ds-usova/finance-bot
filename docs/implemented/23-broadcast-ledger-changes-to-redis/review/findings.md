# Review: Broadcast Ledger Changes to Redis

**2 bugs, 2 refactoring candidates, 5 manual checks. Nothing critical.** Every entry is `ledger-service`.

## Bug

**A publication the database does not have looks exactly like a healthy idle pipeline**

- **Given** the service is deployed with `CDC_ENABLED=true` and the `finance_ledger_cdc` publication is absent —
  a migration that did not run, a database restored without it, or a renamed publication
- **When** the engine starts and captured rows change
- **Then** the operator learns the pipeline is not working
- **Actual** `pgoutput` resolves a named publication permissively, so the engine starts, reports `STREAMING`,
  holds the slot and publishes nothing, forever. `/actuator/health` reads healthy and
  `ledger_cdc_events_published_total` simply stays at zero, which is indistinguishable from a quiet ledger. The
  only symptom is `ledger_cdc_slot_retained_bytes` climbing, and that is the alarm for a different failure.
- **Fix** check the publication exists at start-up and report the component `DOWN` when it does not, the way a
  non-logical `wal_level` already is · `ChangeStreamReader`

**`ChangeStreamReaderTest`'s stop and Redis-unavailable cases fail intermittently**

- **Given** the full suite running against a real engine
- **When** `ChangeStreamReaderTest$Stop` or `$TheOfferLoop$RedisUnavailable` runs
- **Then** it passes as it does in isolation
- **Actual** it fails occasionally on its own await bounds. Confirmed against a baseline taken before the
  boot-race fix, so it predates that change and is not a regression from it. A run that trips it should be
  re-run before it is read as one.
- **Fix** widen or re-anchor the awaits against observable engine state rather than elapsed time ·
  `ChangeStreamReaderTest`

## Refactoring candidate

| What                                                                                 | Why the task left it                                                                                                                                                                                                                             |
|--------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| One Spring context per system test class, now ~20 booted applications in one JVM      | Already agreed as its own task. The suite needed `maxHeapSize = "2g"` and `max_connections=300` to run at all; both are symptoms. `testing.md` documents context-per-class as a benefit, which is the convention to change — a distinct property should be the exception, not the isolation mechanism. |
| `CategoryNameResolver.evict(id)` clears the whole cache rather than one entry          | `CategoryNames` carries two names and no ids, and `CategoryNameReader.findNames(long)` answers only that record, so no cached entry knows its parent and no grouping-to-categories index can be built. A rename therefore empties a 50 000-entry cache, which is more than D16 and D17 assumed. Widening the projection and the record to carry the parent id is the fix, and it is a design-level change rather than a green step's. |

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
