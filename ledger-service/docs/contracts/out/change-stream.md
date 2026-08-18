# A consumer of the ledger's changes — the change stream (Redis)

Every fact a spending write produces is published onto one Redis stream, in the order the database committed it.
An entry names what happened and carries the whole piece of spending it happened to, so a consumer looks nothing
up.

- **Counterpart:** [`ai-connector-service`](../../../../ai-connector-service/docs/contracts/out/change-stream.md), as consumer group `ai-connector`
- **Transport:** Redis, one stream, appended to with `XADD`
- **Schema:** none held in a file — the entry and its bodies are below

## Operations

| Operation       | Purpose                                                 | Used by                                               |
|-----------------|---------------------------------------------------------|-------------------------------------------------------|
| Append an entry | publishes one committed fact                            | the ledger's [change capture](change-capture.md), on every fact |
| Trim the stream | drops the oldest entries once the stream passes its cap | the same append                                       |

The stream is named by `CDC_STREAM_KEY` and capped by `CDC_STREAM_MAX_LENGTH`, both
[configuration](../../configuration.md). The cap is approximate: the stream holds at least the cap and a little
more.

## What an entry carries

| Field        | Holds                                            |
|--------------|--------------------------------------------------|
| `id`         | the event's own id, a UUID                       |
| `type`       | which fact it is, from the catalogue below       |
| `occurredAt` | when the write stamped it, an ISO-8601 instant   |
| `payload`    | the body, JSON, whose shape the type fixes       |

## The catalogue

| Type                | The fact                                        | Emitted by                                                                   |
|---------------------|-------------------------------------------------|------------------------------------------------------------------------------|
| `ProposalCreated`   | the model proposed an expense from a message    | [Create an expense proposal](../../usecases/create-an-expense-proposal.md)   |
| `ProposalRefiled`   | a pending entry was moved to another category   | [Change an entry's category](../../usecases/change-an-expense-category.md)   |
| `ProposalDiscarded` | the person threw the pending entry away         | [Resolve a reported proposal](../../usecases/resolve-a-reported-proposal.md) |
| `ProposalAccepted`  | the person confirmed it, and it is now recorded | [Resolve a reported proposal](../../usecases/resolve-a-reported-proposal.md), [Accept the proposals a person chose](../../usecases/accept-chosen-proposals.md) |
| `ExpenseRecorded`   | an expense was recorded with no proposal        | [Create an expense](../../usecases/create-an-expense.md)                     |
| `ExpenseRefiled`    | a recorded entry was moved to another category  | [Change an entry's category](../../usecases/change-an-expense-category.md)   |

## What a body carries

Every type carries the same fields. The type and `status` are what tell one fact from another.

| Field                     | Holds                                                                                       |
|---------------------------|---------------------------------------------------------------------------------------------|
| `userId`                  | the person, by [internal id](../../domain/authenticated-user-id.md)                         |
| `incomingMessageId`       | the [message](../../domain/incoming-message-id.md) the entry came from; `null` where there was none |
| `expenseId`               | the entry's id ([expense](../../domain/expense.md))                                          |
| `status`                  | the entry's [status](../../domain/expense-status.md) as it stands after the fact            |
| `description`, `merchant` | as stored; `merchant` may be `null`                                                         |
| `amount`                  | a decimal string in the currency's main unit ([ADR 0011](../../adr/0011-the-amount-is-scaled-to-minor-units-in-the-domain.md)) |
| `currencyCode`            | the ISO code that amount is in                                                              |
| `category`                | `id` and `name` — what the entry is filed under                                             |
| `grouping`                | `id` and `name` — the grouping that category sits in                                        |

A whole body, as `ProposalCreated` carries it:

```json
{
  "userId": 41,
  "incomingMessageId": "1187:4402",
  "expenseId": 9013,
  "status": "PENDING",
  "description": "flat white",
  "merchant": "Blue Bottle",
  "currencyCode": "USD",
  "amount": "4.50",
  "category": { "id": 77, "name": "Coffee" },
  "grouping": { "id": 12, "name": "Dining" }
}
```

## The names and the ids

- A name is a snapshot — what the category or grouping was called at that commit.
- An id survives a rename, and is what a consumer matches on.
- A refile names where the entry now sits, never where it sat before.

## Ordering and counting

- One event per changed row: a report accepting three proposals is three entries.
- Rows changed together commit together, so their entries arrive together and in order.
- A resolution or a refile matching no row publishes nothing.
- The stream position is the event's position; the ledger carries no sequence of its own.

## Deduplicating

A consumer may deduplicate on `id` alone, since a redelivered event is byte-identical. One that keeps a per-row
mark of the position it last applied guards on that instead, and re-applies a republished event rather than
skipping it.

## What is not promised

| Not promised                    | What a consumer must expect                                                                          |
|---------------------------------|--------------------------------------------------------------------------------------------------------|
| exactly-once delivery           | delivery is at-least-once — a restart between the append and the position being committed republishes the event |
| every fact eventually arrives   | an invalidated slot ends a run of facts permanently — see Failures below                             |

## Failures

| Condition                                    | Signal                                                                                       |
|----------------------------------------------|------------------------------------------------------------------------------------------------|
| Redis refuses the write                      | nothing is appended, the log position stands, and the event is retried until it is accepted   |
| Redis stays unreachable                      | the database's retained log grows, and the health component reads down                         |
| the slot is invalidated ([change capture](change-capture.md)) | everything it still held is lost permanently, and an operator [rebuilds the slot](../in/operations.md#rebuilding-the-slot) |

Nothing is dropped to keep the pipeline moving
([ADR 0016](../../../../docs/adr/0016-an-embedded-engine-holds-the-log-position-until-redis-acknowledges-bounded-by-the-database.md)).

## Compatibility

A field added to a body is additive, and a consumer reading fields by name is unaffected.

A new type joins the catalogue with the use case that first produces it, so a consumer meets types it was not
written against.

Capture can be switched off, leaving the stream untouched and no longer appended to — see
[configuration](../../configuration.md).
