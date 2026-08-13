# A consumer of the ledger's changes — the change stream (Redis)

Every row change the ledger makes to its own spending tables is republished onto one Redis stream, in the order
the database committed it. It is how something outside the service learns that spending was recorded, refiled or
discarded without asking the ledger for it.

Nothing consumes the stream yet. The entries are written and trimmed regardless.

- **Counterpart:** a stream consumer — none exists
- **Transport:** Redis, one stream, appended to with `XADD`
- **Schema:** none held in a file — the entry is the database's own change event, and its shape is below

## Operations

| Operation       | Purpose                                                     | Used by                                              |
|-----------------|-------------------------------------------------------------|------------------------------------------------------|
| Append an entry | publishes one committed row change                          | the ledger's change capture, on every captured change |
| Trim the stream | drops the oldest entries once the stream passes its cap     | the same append                                      |

The stream is named by `CDC_STREAM_KEY` and capped by `CDC_STREAM_MAX_LENGTH`, both
[configuration](../../configuration.md). The cap is approximate: the stream is trimmed to whole internal blocks,
so it holds at least the cap and a little more.

## What an entry carries

| Field        | Holds                                                                 | Present                              |
|--------------|-----------------------------------------------------------------------|--------------------------------------|
| `payload`    | the change event as JSON                                              | always                               |
| `enrichment` | the category and grouping names the change refers to                  | on a spending row, never on a category |

### What the payload names

| Path             | Means                                                                  |
|------------------|--------------------------------------------------------------------------|
| `op`             | `c` recorded, `u` changed, `d` removed, `r` read from an initial snapshot |
| `source.table`   | which table changed                                                     |
| `source.lsn`     | the log position the change was committed at                            |
| `source.txId`    | the transaction, which is what ties two rows changed together           |
| `source.ts_ms`   | when the database committed it                                          |
| `before`         | the whole row as it was                                                 |
| `after`          | the whole row as it is                                                  |

Both sides carry every column, on an update and a delete alike.

**A person is an internal user id on the wire and nothing else.** The Telegram identifier lives in a table that
is not captured, so it never reaches the stream. The same id is what a caller token's subject carries
([Authenticated user id](../../domain/authenticated-user-id.md)).

### The enrichment block

`enrichment` mirrors the payload's two sides, so a category's name is read off the spending entry itself.

| Path                     | Holds                                     |
|--------------------------|-------------------------------------------|
| `before.categoryName`    | the category the row was filed under      |
| `before.groupingName`    | the grouping that category sits in        |
| `after.categoryName`     | the category the row is filed under       |
| `after.groupingName`     | the grouping that category sits in        |

- A side is empty when the id resolves to no category.
- The only route to that is a deleted person: their categories and their spending go in one transaction, so all
  their delete entries land unnamed together.
- A name resolved from the service's own cache is the name as of the change.
- A name read fresh is the name as it is now. The two differ only between a rename and the cache noticing it.

## Which changes reach the stream

| Changed                              | Reaches the stream |
|--------------------------------------|--------------------|
| a recorded expense                   | yes                |
| an expense proposal                  | yes                |
| a category or a grouping             | yes                |
| a user, a spending query, a report   | no                 |
| the capture heartbeat                | no                 |

A proposal accepted is one transaction: the proposal removed and the expense recorded share a `source.txId`.

## Deduplicating

The key is `source.table`, the row's own key, and `source.lsn`, all three together. `source.lsn` alone is not a
key — every entry of an initial snapshot carries the same one.

## What is not promised

| Not promised                    | What a consumer must expect                                                                 |
|---------------------------------|---------------------------------------------------------------------------------------------|
| exactly-once delivery           | delivery is at-least-once — a restart between the append and the position being committed republishes the change |
| a redelivered copy is identical | the two copies can name a category differently, having been enriched at different moments   |
| every change eventually arrives | an outage long enough to invalidate the replication slot loses every change made during it, permanently — see [Rebuilding the slot](../in/operations.md#rebuilding-the-slot) |

## Failures

| Condition                                       | Signal                                                                      |
|-------------------------------------------------|-------------------------------------------------------------------------------|
| Redis refuses the write                         | nothing is appended, the log position stands, and the change is retried until it is accepted |
| the category lookup fails                       | the same — the entry is held back rather than published unnamed              |
| Redis stays unreachable                         | the database's retained log grows, and the health component reads down        |
| the retained log passes the database's bound    | the database invalidates the slot, the engine stops, and an operator rebuilds it |

Nothing is dropped to keep the pipeline moving; holding the log position is the whole mechanism
([ADR 0016](../../../../docs/adr/0016-an-embedded-engine-holds-the-log-position-until-redis-acknowledges-bounded-by-the-database.md)).

## Compatibility

The payload is the database's change event, so a column added to a captured table appears in `before` and `after`
with no change here. A consumer reading fields by name is unaffected. One reading the whole row is not.

`enrichment` is this service's own. A field added to it is additive.

Capture can be switched off entirely, which leaves the stream untouched and no longer appended to — see
[configuration](../../configuration.md).
