# The AI connector filing a turn's results — the turn operations (gRPC)

A turn's writes, filed as the person the turn is for and under the message it is about.

- **Counterpart:** the AI Connector Service, filing a turn's results itself. Nothing in this repository calls these
  operations yet.
- **Transport:** gRPC, plaintext, on `LEDGER_GRPC_PORT`, a port of its own beside the service's HTTP port
- **Schema:** [`proto/ledger_turn.proto`](../../../../proto/ledger_turn.proto), shared at the repository root

## Operations

| Operation              | Purpose                                                   | Used by                                                                    |
|------------------------|-----------------------------------------------------------|----------------------------------------------------------------------------|
| `FileExpenseProposal`  | files one expense the turn read from the message, pending | [Create an expense proposal](../../usecases/create-an-expense-proposal.md) |
| `RecordSpendingPeriod` | records a period the message asked about                  | [Summarize spending over a period](../../usecases/summarize-spending.md)   |
| Health check           | whether the server is serving                             | anyone reaching the port                                                   |
| Reflection             | which services the port serves                            | anyone reaching the port                                                   |

## Who may call

- The credential is the one the ledger mints per turn for [its tools](mcp.md#how-a-caller-authenticates).
- It is checked exactly as the tools check it, against the ledger's own key, with no key-set fetch.
- It is checked before the request is read.
- The person and the message it names are the operation's caller. The request names neither.
- The port is for the services' own network, and is published to no host.

Why the operations sit beside the tools, behind their credential:
[ADR 0020](../../../../docs/adr/0020-the-ledger-serves-a-turns-writes-as-grpc-operations-on-a-port-of-their-own-beside-its-mcp-tools-behind-the-tools-own-credential.md).

## What an answer holds

- `FileExpenseProposal` answers the grouping and the category the request named.
- Its `amount` is in the currency's main unit, written as a plain decimal.
- `RecordSpendingPeriod` answers the period as it was stored.

## What a repeated call leaves behind

| Operation              | Repeating it                                                                   |
|------------------------|--------------------------------------------------------------------------------|
| `FileExpenseProposal`  | not idempotent: two calls store two proposals                                  |
| `RecordSpendingPeriod` | idempotent in what the user reads: two rows, and the turn uses the period once |

Every call that passes the credential check writes [the turn call line](operations.md#the-turn-call-line), and is
timed by [the server's call meter](operations.md#meters).

## Failures

| Condition                                                                                                  | Signal                                                            |
|------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------|
| No credential, one expired past the skew, a wrong issuer or audience, a lifetime too long, a session token | `UNAUTHENTICATED`, and the operation never runs                   |
| The credential names no message, or a blank one                                                            | `UNAUTHENTICATED`, and the operation never runs                   |
| `amount` is malformed, or has more decimals than its currency records                                      | `INVALID_ARGUMENT`, naming an invalid request and `amount`        |
| `currency_code` is not three letters, or names a currency no amount is recorded in                         | `INVALID_ARGUMENT`, naming an invalid request and `currency_code` |
| `grouping` is empty or blank, or `description` is invalid                                                  | `INVALID_ARGUMENT`, naming an invalid request and the field       |
| The grouping is not one of the person's                                                                    | `INVALID_ARGUMENT`, repeating it                                  |
| The grouping holds no category of that name                                                                | `INVALID_ARGUMENT`, naming both                                   |
| A day is blank or not written `YYYY-MM-DD`, or the last day is before the first                            | `INVALID_ARGUMENT`, the tool's reason                             |
| The credential's subject is not a stored user's id at all                                                  | `INVALID_ARGUMENT`, naming an invalid request                     |
| The credential's subject names no stored user                                                              | `NOT_FOUND`, saying the user is unknown                           |
| The proposal or the period cannot be stored                                                                | `UNAVAILABLE`, the tool's reason                                  |
| Anything else                                                                                              | `INTERNAL`, the tool's reason                                     |

- Every reason is the one [the matching tool](mcp.md#failures) gives for the same input.
- No description names a table, a constraint or a stack frame.
- Every refusal is logged with its kind, and with neither the request nor the credential.

## Compatibility

- Both sides build from the one schema. A change to it reaches the build, not the runtime.
- The credential check guards the service by its full name, `bot.finance.ledger.v1.LedgerTurnService`. Renaming
  the package or the service leaves every operation unguarded.
- Moving `LEDGER_GRPC_PORT` changes where the connector points, and nothing else.
