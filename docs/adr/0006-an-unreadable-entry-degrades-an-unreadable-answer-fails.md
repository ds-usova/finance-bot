# ADR 0006: An unreadable entry degrades, an unreadable answer fails

- **Status:** Accepted
- **Date:** 2026-07-28
- **Source:** [Call the AI Connector from the Ledger Service](../implemented/4-plan-ledger-ai-connector-integration.md)

## Context

The connector's answer is a list of independent entries, and it may grow entry kinds the ledger has never seen.
Failing a whole answer because one entry was unreadable would make every widening of the schema a breaking
change for the caller.

## Decision

Trouble with **one entry** degrades: it becomes an unknown intent carrying the reason, and the rest of the
answer stands. Trouble with the **whole answer** — a gRPC failure, or an answer holding no entries at all —
fails the call. The connector promises the answer is never empty, so an empty one means something is wrong
rather than that there was nothing to find.

## Consequences

- The connector can add operations and entry kinds without a coordinated release; the ledger already reads them
  as unknown.
- A caller must handle unknown entries as a normal outcome, not an error.
- Should the connector ever want an empty answer to mean "nothing to extract", that is a breaking change for
  this caller and needs a new decision here.
- A malformed entry is invisible from the call's outcome; it is only in the entry's reason.
