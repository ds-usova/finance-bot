# Change capture — the write-ahead log this service reads (logical replication)

The other half of the same database. The service reads its own committed row changes back out of Postgres's
write-ahead log, so it can republish them onto [the change stream](change-stream.md). Nothing here reads or
writes a row: what the tables hold is [the database's own contract](database.md).

- **Counterpart:** the same PostgreSQL database the rows live in — [Database](database.md)
- **Transport:** logical replication through the `pgoutput` plugin, consumed by an engine embedded in this service
- **Schema:** the publication and the replica identity, declared in
  `src/main/resources/db/migration/V009__publish_ledger_changes.sql`

## What is captured

| Table                                       | Reaches the log | Reaches [the stream](change-stream.md) |
|---------------------------------------------|-----------------|----------------------------------------|
| `expense`, `expense_proposal`, `category`   | yes             | yes                                    |
| `cdc_heartbeat`                             | yes             | no                                     |
| every other table                           | no              | no                                     |

The three captured tables run under `REPLICA IDENTITY FULL`, so a change to any of them logs the whole row —
both sides of an update, and the row a delete removed. Under the default identity a delete would log its primary
key alone, and a discarded proposal would reach the stream as an id and nothing else.

The publication `finance_ledger_cdc` covers those three plus `cdc_heartbeat`, and is restricted to inserts,
updates and deletes. A truncate is not published: it arrives carrying neither a before nor an after image, and
no operation on the wire describes it.

`cdc_heartbeat` is in the publication but not on the stream. Its single row is advanced on a timer, which is
what moves the replication slot forward while only uncaptured tables are being written — retained log is
cluster-wide, so without it a healthy pipeline would pin the log through any period of ordinary traffic.

## The slot and the stored position

Neither is declared by a migration, and both are created on first start:

- **The replication slot.** Creating one cannot run inside a transaction, and every migration is wrapped in one.
  Its name is [configuration](../../configuration.md).
- **`debezium_offset_storage`.** The engine's own offset store creates its table, and owns its shape.

The slot is what makes the log durable across a restart: Postgres retains every segment after the position the
slot has confirmed, so a stopped service loses nothing, and a service that never reads again retains
everything.

## What the database owes

| Requirement                        | Without it                                                                   |
|------------------------------------|-------------------------------------------------------------------------------|
| `wal_level = logical`              | capture reports down and stops retrying; the rest of the service serves normally |
| `REPLICATION` on the service's account | the slot cannot be opened                                                  |
| the publication present            | capture reports down and takes no slot                                         |
| a bound on what a slot may retain  | an unread slot retains log until the disk is gone                             |

The bound is the database's own setting rather than the service's, and what happens when a slot passes it — the
slot is invalidated, and only an operator can rebuild it — is [the operator's boundary](../in/operations.md).

## Compatibility

The captured set is declared in a migration rather than created by the engine, so adding or removing a table
from it is a migration and shows in a diff.

Adding a table to the publication puts its changes on the stream from that point on, and never retrospectively:
a consumer sees nothing about rows that existed before. Removing one silently stops a consumer being told about
it, which is the change most likely to go unnoticed on the far side.
