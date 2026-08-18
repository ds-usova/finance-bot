# Ledger Service — the ledger's facts (Redis)

Every fact the ledger publishes about a piece of spending reaches this service over one Redis stream. Each entry
carries the whole entry the fact happened to, so this service looks nothing up and applies each fact on its own.

- **Counterpart:** [the ledger's change stream](../../../../ledger-service/docs/contracts/out/change-stream.md)
  — what an entry carries, the catalogue of facts, and what is not promised
- **Transport:** Redis, one stream, read as a consumer group
- **Schema:** none held here. An entry's shape is the counterpart's.

## Operations

| Operation             | Purpose                                                                                     | Used by                                                                             |
|-----------------------|---------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------|
| Join the group        | claims a place in group `ai-connector`, creating the stream and the group if neither exists | [Learn what the ledger did with a message](../../usecases/learn-message-outcome.md) |
| Read entries          | takes the group's entries for this consumer                                                 | the same                                                                            |
| Claim a stalled entry | takes over an entry another consumer has left pending longer than a fixed idle bound        | the same                                                                            |
| Acknowledge an entry  | ends its delivery                                                                           | the same                                                                            |

The stream is named by `CDC_STREAM_KEY`, [configuration](../../configuration.md).

## What this service does

- Joins as one consumer per instance, named after the host it runs on.
- Starts at the stream's end: what was published before the group existed is never read.
- Reads claimed entries first, then its own unacknowledged ones, then new ones.
- Applies the six spending facts of the ledger's catalogue.
- Acknowledges an entry once its fact is applied, found to hold nothing to learn, or dropped.
- Leaves an entry unacknowledged when the store refuses its fact, and backs off before reading again.
- Holds a fresh entry's successors back while it is still waiting on its first attempt.

## What this service ignores

| Ignored                                                                    | Acknowledged |
|----------------------------------------------------------------------------|--------------|
| a type outside the ledger's six spending facts                             | yes          |
| an entry naming no message, or one this service does not keep              | yes          |

## Redelivery and order

- A claimed or swept entry can arrive after entries published later
  ([what the ledger promises](../../../../ledger-service/docs/contracts/out/change-stream.md#what-is-not-promised)).
- The event's own id is neither stored nor checked, so a republished fact is applied again rather than skipped.
- Which fact stands when entries arrive out of order is
  [the use case's](../../usecases/learn-message-outcome.md).

## Failures

| Condition                                              | Signal                                                                                                     |
|--------------------------------------------------------|--------------------------------------------------------------------------------------------------------------|
| Redis cannot be reached at startup                     | logged at `WARN`; the service starts and keeps trying                                                        |
| Redis becomes unreachable                              | logged at `WARN`; reading backs off and resumes at the group's own position; the health endpoint reads down  |
| An entry's body cannot be read as a fact               | logged at `WARN` naming the entry; acknowledged, never applied                                               |
| Applying the fact fails in a way nothing anticipated   | logged at `ERROR`; the entry stays pending and the consumer keeps reading                                    |
| Entries are trimmed before they are read               | gone; the facts they carried are never learned                                                               |

What each of those means for what the service remembers is
[the use case's](../../usecases/learn-message-outcome.md).

## Compatibility

| Change on the ledger's side          | Effect here                                                        |
|--------------------------------------|--------------------------------------------------------------------|
| A field is added to a body           | none — a body is read field by field                               |
| A fact joins the catalogue           | ignored until this service is written against it                   |
| A body's field is renamed or dropped | entries of that type stop being read, and are acknowledged unread   |
| The stream is renamed                | nothing is read until `CDC_STREAM_KEY` here names it too            |
| Capture is switched off              | the consumer idles on a stream nothing appends to                   |

A second instance of this service joins the same group and shares the entries with the first.
