# Ledger Service — the ledger's changes (Redis)

Every row change the ledger makes to its spending tables reaches this service over one Redis stream. It is how
the service learns what became of a message it kept, without asking the ledger anything.

- **Counterpart:** [the ledger's change stream](../../../../ledger-service/docs/contracts/out/change-stream.md)
  — what an entry carries, which changes reach it, and what is not promised
- **Transport:** Redis, one stream, read as a consumer group
- **Schema:** none held here. An entry's shape is the counterpart's.

## Operations

| Operation             | Purpose                                                                        | Used by                                                                        |
|-----------------------|--------------------------------------------------------------------------------|----------------------------------------------------------------------------------|
| Join the group        | claims a place in group `ai-connector`, creating the stream and the group if neither exists | [Learn what the ledger did with a message](../../usecases/learn-message-outcome.md) |
| Read entries          | takes this consumer's unacknowledged entries, then entries nobody has read yet | the same                                                                        |
| Claim a stalled entry | takes over an entry another consumer has left pending longer than a fixed idle bound | the same                                                                  |
| Acknowledge an entry  | ends its delivery                                                              | the same                                                                        |

The stream is named by `CDC_STREAM_KEY`, [configuration](../../configuration.md).

## What this service does

- Joins as one consumer per instance, named after the host it runs on.
- Starts at the stream's end: what was published before the group existed is never read.
- Reads claimed entries first, then its own unacknowledged ones, then new ones.
- Acknowledges an entry once its change is applied, found to hold nothing to learn, or dropped.
- Leaves an entry unacknowledged when the store refuses its change, and backs off before reading again.
- Holds a fresh entry's successors back while it is still waiting on its first attempt.

## What this service ignores

| Ignored                                                    | Acknowledged |
|------------------------------------------------------------|--------------|
| a change to a table nothing here learns from                | yes          |
| an operation other than a record, a change or a removal     | yes          |

## Failures

| Condition                                                        | Signal                                                                                          |
|------------------------------------------------------------------|---------------------------------------------------------------------------------------------------|
| Redis cannot be reached at startup                               | logged at `WARN`; the service starts and keeps trying                                             |
| Redis becomes unreachable                                        | logged at `WARN`; reading backs off and resumes at the group's own position; the health endpoint reads down |
| An entry's body cannot be read as a change                       | logged at `WARN` naming the entry; acknowledged, never applied                                    |
| Applying the change fails in a way nothing anticipated           | logged at `ERROR`; the entry stays pending and the consumer keeps reading                         |
| Entries are trimmed before they are read                         | gone; the outcomes they carried are never learned                                                 |

What each of those means for what the service remembers is
[the use case's](../../usecases/learn-message-outcome.md).

## Compatibility

| Change on the ledger's side                    | Effect here                                                          |
|------------------------------------------------|------------------------------------------------------------------------|
| A column is added to a captured table          | none — an entry is read field by field                                 |
| A field is added to the enrichment             | none                                                                   |
| A captured table is renamed                    | its changes stop being learned, silently                               |
| The stream is renamed                          | nothing is read until `CDC_STREAM_KEY` here names it too               |
| Capture is switched off                        | the consumer idles on a stream nothing appends to                      |

A second instance of this service joins the same group and shares the entries with the first.
