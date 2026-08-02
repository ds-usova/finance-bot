# Ledger Service — the expense proposal tool (MCP over HTTP)

Every expense a user's message says was paid crosses this boundary as one tool call, made by the model reading
that message. It is recorded against the person whose token arrived with the extraction request, never against
anyone this service names.

- **Counterpart:** [the Ledger Service's expense proposal tool](../../../../ledger-service/docs/contracts/in/mcp.md)
- **Transport:** MCP over Streamable HTTP, one long-lived client for the whole process — the address is
  [configuration](../../configuration.md)
- **Schema:** none held in a file — the ledger publishes the tool's argument schema over the protocol itself

## Operations

| Operation                 | Purpose                                         | Used by                                                                                                                                                                                                             |
|---------------------------|-------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| List the tools            | reads what the ledger offers and what it takes  | here, [Record the spending a user's message names](../../usecases/extract-intents.md)                                                                                                                               |
| `create_expense_proposal` | records one expense the user's message asks for | here, [Record the spending a user's message names](../../usecases/extract-intents.md) · on the ledger's side, [Create an expense proposal](../../../../ledger-service/docs/usecases/create-an-expense-proposal.md) |

### What is sent

The model fills every argument, from the message and from what the tool says each argument takes. This service
assembles none of them.

`merchant` is sent when the message names who the expense was paid to. The other arguments, and which are
required, are [the tool's own declaration](../../../../ledger-service/docs/contracts/in/mcp.md#what-the-tool-takes).

## Semantics

One client serves the whole process. The tools are listed once, on the first turn that needs them, and that list
is kept for the life of the process.

Every request carries the token that arrived on the extraction call, verbatim and scheme included, read fresh
for each turn on the thread running it. Two turns running at once share the client and never the identity. The
token is never parsed, never logged, never stored.

A turn holding no token sends no request at all.

The expenses of one message are sent in the order the user said them.

Nothing is read out of the answer here. A stored proposal's id, its timestamp, and everything else the ledger
returns go back to the model as that call's result.

A tool result flagged as an error is a refusal, and it reaches the model as that call's answer. The model
corrects the call and tries the same expense once more; refused again, that expense is left unrecorded and the
rest of the message is still sent. A refusal never ends the turn.

An argument the protocol cannot bind is the same: the failure reaches the model, which corrects the call it just
made.

A failure of the transport itself ends the turn — no further expense is sent, and what was recorded stands.

Nothing is deduplicated. The same message handled twice records two proposals, and a model that repeats a call
within one turn records two.

## Failures

| Condition                                                     | Signal                                                                  |
|---------------------------------------------------------------|-------------------------------------------------------------------------|
| The tool answers an error result                              | none — the model reads the refusal and retries the expense once       |
| An argument's value cannot be bound to its declared type      | none — the model reads the failure and corrects the call              |
| The ledger cannot be reached, times out, or refuses the token | the turn fails; the caller is told the service is unavailable           |
| The tools cannot be listed                                    | the same, before any expense is attempted                               |
| No caller token is held for the turn                          | the same; nothing is sent to the ledger and the model is never prompted |

## Compatibility

The tool is chosen by the model from the list the ledger publishes, and its arguments are filled from the same
list. Renaming either changes what the model is offered rather than breaking a call assembled here.

An optional argument added on the ledger's side costs nothing. Making one required, or removing one, changes
what the model is told to send.

An argument description is where the ledger tells the model how to fill it — the minor-units conversion is read
there, not from this service's instructions.

The ledger can switch this endpoint off, which makes every turn fail as unavailable.
