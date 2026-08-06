# Ledger Service — the ledger's tools (MCP over HTTP)

The model calls the ledger's tools itself. This service carries the calls and forwards the caller's token. It
assembles no argument and reads no answer.

- **Counterpart:** [the ledger's tool endpoint](../../../../ledger-service/docs/contracts/in/mcp.md) — what each
  tool takes, answers, and refuses
- **Transport:** MCP over Streamable HTTP. The address is [configuration](../../configuration.md).
- **Schema:** none held here. The ledger publishes each tool's arguments over the protocol.

## Operations

| Tool                      | Why the model calls it                | On the ledger's side                                                                                     |
|---------------------------|---------------------------------------|----------------------------------------------------------------------------------------------------------|
| List the tools            | to learn what it may call             | —                                                                                                        |
| `list_categories`         | to find where an expense is filed     | [List a grouping's categories](../../../../ledger-service/docs/usecases/list-categories.md)              |
| `create_expense_proposal` | to record one expense                 | [Create an expense proposal](../../../../ledger-service/docs/usecases/create-an-expense-proposal.md)     |
| `summarize_spending`      | to answer what the caller spent       | [Summarize spending over a period](../../../../ledger-service/docs/usecases/summarize-spending.md)       |

Every one is driven by [Record the spending a user's message names](../../usecases/extract-intents.md).

## What this service does

- Forwards the caller's token on every request, verbatim, scheme included.
- Reads that token fresh per turn, on the thread running it. Two turns share the client, never the identity.
- Keeps one client for the whole process.
- Reads the tool list once, on the first turn that needs it, and keeps it for the life of the process.
- Offers every published tool to the model as published.

The token is never parsed, never logged, never stored. Forwarding it untouched is what carries the ledger's own
claims across — the reference tying a turn's proposals to its message rides the token, and this service never
sees it.

## What this service does not do

- Name a tool, register one, or assemble an argument. The model fills every call.
- Read anything out of an answer. Results go back to the model as that call's result.
- Send anything at all when the turn holds no token.

A summary answers the period the ledger accepted, never an amount. Totals reach the user from the ledger, so no
total enters the model's context.

## How a turn behaves

- Expenses are sent in the order the user said them.
- A grouping is looked up before an expense is filed under it. Reusing a listing from the same turn is allowed.
- A lookup stores nothing, so repeating one changes nothing.
- Recording is not deduplicated. The same message handled twice records two proposals.

A refusal is a tool result flagged as an error. It reaches the model as that call's answer and never ends the
turn:

| Refused call        | What follows                                                                   |
|---------------------|---------------------------------------------------------------------------------|
| A recording call    | the model corrects it and tries that expense once more, then leaves it and goes on |
| A lookup            | the model corrects the grouping's name and asks again; the expense keeps its retry |
| An argument's type  | refused before the tool runs, so nothing is recorded; the model corrects the call  |

## Failures

| Condition                                                     | Signal                                                                  |
|---------------------------------------------------------------|-------------------------------------------------------------------------|
| The ledger cannot be reached, times out, or refuses the token | the turn fails; the caller is told the service is unavailable           |
| The tools cannot be listed                                    | the same, before any expense is attempted                               |
| No caller token is held for the turn                          | the same; nothing is sent and the model is never prompted               |

A transport failure ends the turn. What was already recorded stands.

## Compatibility

The model picks a tool from the published list and fills it from the same list. Nothing here is wired per tool.

| Change on the ledger's side | Effect here                                                                  |
|-----------------------------|-------------------------------------------------------------------------------|
| A tool is added             | offered to the model once this process restarts and reads the new list       |
| An optional argument added  | none                                                                          |
| An argument made required   | changes what the model is told to send                                        |
| An argument renamed         | calls carrying the old name are refused until this process restarts          |
| The endpoint is switched off | every turn fails as unavailable                                              |

A published change reaches this service only on restart. That makes a new tool a matter of **restart order**, not
a compatibility window: the ledger must serve it before this service starts. Until then the model is never
offered it, and the message is answered with whatever else the model can do.

How an argument is filled comes from the description the ledger publishes beside it, never from this service's
instructions.
