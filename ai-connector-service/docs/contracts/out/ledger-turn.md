# Ledger Service — the turn operations (gRPC)

In the classifier mode, the service files each expense and records each period itself, as the turn's caller.

- **Counterpart:** [the ledger's turn operations](../../../../ledger-service/docs/contracts/in/turn-operations.md) —
  what each takes, answers and refuses
- **Transport:** gRPC, at `LEDGER_TURN_TARGET` — [configuration](../../configuration.md)
- **Schema:** [`proto/ledger_turn.proto`](../../../../proto/ledger_turn.proto), shared at the repository root

## Operations

| Operation              | Used by                                                                         | On the ledger's side                                                                                 |
|------------------------|---------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------|
| `FileExpenseProposal`  | [Record the spending a user's message names](../../usecases/extract-intents.md) | [Create an expense proposal](../../../../ledger-service/docs/usecases/create-an-expense-proposal.md) |
| `RecordSpendingPeriod` | [Record the spending a user's message names](../../usecases/extract-intents.md) | [Summarize spending over a period](../../../../ledger-service/docs/usecases/summarize-spending.md)   |

## What this service sends

- One `FileExpenseProposal` per usable expense, in the user's order, then one `RecordSpendingPeriod` per period.
- One call at a time. The next starts once the last has answered.
- The token the turn arrived with, forwarded verbatim, scheme included.
- The grouping and the category the turn settled on.
- A merchant only when one was read.
- The answer is not read.
- No call is retried.

## Deadlines

Each call ends at `LEDGER_TURN_TIMEOUT`, or at the deadline of the extraction call the turn runs inside, whichever
comes first.

## Failures

| Condition                     | What this side does                                                     |
|-------------------------------|-------------------------------------------------------------------------|
| `INVALID_ARGUMENT`            | that expense or period is left unrecorded; one `WARN`; the turn goes on |
| Any other status              | the turn ends; nothing further is sent                                  |
| No answer before the deadline | the same                                                                |
| No token held for the turn    | the same, before anything is sent                                       |

What the caller is told is [the turn's Outcomes](../../usecases/extract-intents.md#outcomes).

## Compatibility

- Both sides build from the one schema. A change to it reaches the build, not the runtime.
- A move of the ledger's port is a change to `LEDGER_TURN_TARGET`.
- A refusal the ledger moves into or out of `INVALID_ARGUMENT` changes whether it ends the turn.
