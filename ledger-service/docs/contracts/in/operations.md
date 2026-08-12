# An operator running the service — health, meters and slot recovery (HTTP)

The boundary a deployment is watched and repaired through: everything an operator needs to see how the service
is doing, and the one operation they can run against it by hand.

It is served on its own port, `MANAGEMENT_PORT`, which is never published alongside the service's own. Nothing
the product is used with is reachable here, and nothing here is reachable there.

- **Counterpart:** an operator, or the monitoring that stands in for one
- **Transport:** HTTP on the management port
- **Schema:** none held in a file

## Operations

| Operation           | Purpose                                                             | Used by                                     |
|---------------------|-----------------------------------------------------------------------|---------------------------------------------|
| Read the health     | says whether the service and each of its parts is up                 | a liveness and readiness probe              |
| Scrape the meters   | publishes every meter in the Prometheus text format                  | the metrics collector                       |
| Rebuild the slot    | replaces an invalidated replication slot and restarts change capture | an operator, after a long outage            |

Health and the meters answer to anyone reaching the port. Rebuilding the slot demands a shared secret.

## What the health says about change capture

A `changeStream` component appears whenever capture is switched on, carrying a `state` detail.

| State       | Means                                                                                         | Component reads |
|-------------|-----------------------------------------------------------------------------------------------|-----------------|
| `STREAMING` | this instance holds the replication slot and is reading the log                               | up              |
| `STANDBY`   | another instance holds the slot, and this one is waiting for it                               | up              |
| `DOWN`      | this instance is not publishing — it never started, or it is stalled retrying a refused write | down            |

`STANDBY` is up on purpose: in a scaled deployment every instance but one is a standby, so alarming on it would
alarm permanently.

With capture switched off the component is absent altogether, and the aggregate health is unaffected by it.

## The meters

| Meter                                       | Kind    | Says                                                                     |
|---------------------------------------------|---------|--------------------------------------------------------------------------|
| `ledger_cdc_events_published_total`         | counter | changes appended to the stream, tagged by `table` and `op`               |
| `ledger_cdc_publish_failures_total`         | counter | appends the stream refused                                               |
| `ledger_cdc_category_lookups_total`         | counter | category name lookups, tagged `result` `hit` or `miss`                   |
| `ledger_cdc_category_lookup_failures_total` | counter | category name lookups the database refused                               |
| `ledger_cdc_event_lag_seconds`              | gauge   | now, less the commit time of the last change appended                    |
| `ledger_cdc_state`                          | gauge   | the capture state, as an ordinal                                         |
| `ledger_cdc_slot_retained_bytes`            | gauge   | how much log the replication slot is holding — the one to alarm on       |
| `ledger_cdc_slot_wal_status`                | gauge   | what the database says about that slot, as an ordinal                    |

The two slot gauges are read on a timer whether or not capture is on, so a slot left behind by switching capture
off is still visible. `ledger_cdc_slot_retained_bytes` is what a bound is watched against — see
[configuration](../../configuration.md).

### `ledger_cdc_state`

| Value | State       |
|-------|-------------|
| `0`   | `STREAMING` |
| `1`   | `STANDBY`   |
| `2`   | `DOWN`      |

### `ledger_cdc_slot_wal_status`

| Value | Slot                                                                  |
|-------|-------------------------------------------------------------------------|
| `0`   | reserved — the log it needs is kept                                    |
| `1`   | extended — it has reached beyond the checkpoint, still kept            |
| `2`   | unreserved — the log it needs may be removed at any moment             |
| `3`   | lost — the log it needs is gone, and the slot is dead                  |
| `4`   | absent — no slot of that name exists                                   |

A slot the database cannot classify reads as lost.

## Rebuilding the slot

The operation is the only repair for a slot the database has invalidated. It stops the engine, deletes the
stored read position, drops the dead slot, and starts a fresh one at the current end of the log.

**It never replays the gap.** Every change made while the slot was dead is gone from the stream permanently. The
position it abandoned and the moment it did so are written to the service's log, at error, once. Nothing else
records which hours are missing.

| Request          | What it carries                                                  |
|------------------|--------------------------------------------------------------------|
| `POST /actuator/cdc` | the `X-Cdc-Recovery-Secret` header, matching `CDC_RECOVERY_SECRET` |

The answer names the position abandoned and the position resumed from. It carries neither on a refusal.

Only one instance runs the sequence at a time — a second is refused rather than queued.

## Failures

| Condition                                                       | Signal |
|-----------------------------------------------------------------|--------|
| the rebuild carries no secret header, or one that does not match | 401, before the operation is reached |
| no secret is configured at all                                   | 401, the same way |
| the slot exists and the database has not invalidated it          | 409, with the slot untouched and the engine still running |
| another rebuild is already running, here or on another instance  | 409, the same way |
| the engine will not stop within its bound                        | 503, with nothing deleted and nothing dropped |
| the stored position will not delete                              | 503, with the engine stopped and the slot untouched |
| the slot will not drop                                           | 503, with the engine stopped; a second call resumes from there |

A 503 leaves the service running with capture stopped. Repeating the call is safe.

## Compatibility

A meter renamed or a tag added is a change to whatever alerts on it. Nothing in this repository reads these
meters, so a dashboard outside it is the only thing a rename reaches.

Moving the management port changes where a probe and a collector point, and nothing else. Publishing it beside
the service's own port would put the rebuild operation on a reachable address, guarded by the shared secret
alone.
