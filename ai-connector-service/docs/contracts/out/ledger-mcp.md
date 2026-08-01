# Ledger Service — the expense proposal tool (MCP over HTTP)

Every expense a user's message asks to record crosses this boundary as one tool call. It is recorded against the
person whose token arrived with the extraction request, never against anyone this service names.

- **Counterpart:** [the Ledger Service's expense proposal tool](../../../../ledger-service/docs/contracts/in/mcp.md)
- **Transport:** MCP over Streamable HTTP, one session per proposal — the address is
  [configuration](../../configuration.md)
- **Schema:** none held in a file — the ledger publishes the tool's argument schema over the protocol itself

## Operations

| Operation                 | Purpose                                         | Used by                                                                                                                                                                                                        |
|---------------------------|-------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `create_expense_proposal` | records one expense the user's message asks for | here, [Act on the actions in a user's message](../../usecases/extract-intents.md) · on the ledger's side, [Create an expense proposal](../../../../ledger-service/docs/usecases/create-an-expense-proposal.md) |

### What is sent

| Argument           | Filled from                                         | Sent       |
|--------------------|-----------------------------------------------------|------------|
| `category`         | the name of the category the expense was matched to | always     |
| `parentCategory`   | that category's grouping                            | when known |
| `description`      | what the user said was bought                       | always     |
| `amountMinorUnits` | the extracted amount, in the currency's minor units | always     |
| `currencyCode`     | the extracted currency, ISO 4217                    | always     |

`merchant` is never sent — nothing is extracted for it.

A category the same message asked to create has no grouping, so its proposal carries no `parentCategory`.

## Semantics

One session per proposal: the client is opened, the tool is called once, and the client is closed. Nothing is
pooled and nothing is kept between proposals.

Every session carries the token that arrived on the extraction call, verbatim and scheme included. The token is
never parsed, never logged, never stored.

The proposals of one message are sent in the order the user said them.

Nothing is read out of the answer. A stored proposal's id, its timestamp, and everything else the ledger returns
is discarded.

A tool result flagged as an error is a refusal — a proposal the ledger will refuse again for the same call.
A refusal ends the turn: later expenses in the same message are never sent, and earlier ones stand.

Nothing is retried and nothing is deduplicated. The same message handled twice records two proposals.

## Failures

| Condition                                                       | Signal                                                                            |
|-----------------------------------------------------------------|-----------------------------------------------------------------------------------|
| The tool answers an error result                                | the proposal failed as a refusal; the caller is told the precondition failed      |
| The ledger cannot be reached, times out, or refuses the session | the proposal failed as unreachable; the caller is told the service is unavailable |
| No caller token is held for the turn                            | the same unreachable failure; the ledger is never called                          |

## Compatibility

The tool is called by name, with arguments named as the ledger publishes them. Renaming either breaks every
proposal at runtime, since nothing here is generated from a schema.

An optional argument added on the ledger's side costs nothing. Making one required, or removing one this service
sends, breaks every proposal.

The ledger can switch this endpoint off, which makes every proposal fail as unreachable.
