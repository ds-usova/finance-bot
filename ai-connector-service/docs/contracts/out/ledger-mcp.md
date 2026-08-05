# Ledger Service — the ledger's tools (MCP over HTTP)

Three things cross this boundary as tool calls made by the model reading a user's message: which categories one
of the user's groupings holds, every expense that message says was paid, and the period the message asks what
was spent over. All are made against the person whose token arrived with the extraction request, never against
anyone this service names.

- **Counterpart:** [the Ledger Service's tool endpoint](../../../../ledger-service/docs/contracts/in/mcp.md)
- **Transport:** MCP over Streamable HTTP, one long-lived client for the whole process — the address is
  [configuration](../../configuration.md)
- **Schema:** none held in a file — the ledger publishes each tool's argument schema over the protocol itself

## Operations

| Operation                 | Purpose                                                          | Used by                                                                                                                                                                                                             |
|---------------------------|------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| List the tools            | reads what the ledger offers and what each of them takes         | here, [Record the spending a user's message names](../../usecases/extract-intents.md)                                                                                                                               |
| `list_categories`         | answers the categories filed under one of the caller's groupings | here, [Record the spending a user's message names](../../usecases/extract-intents.md) · on the ledger's side, [List a grouping's categories](../../../../ledger-service/docs/usecases/list-categories.md)          |
| `create_expense_proposal` | records one expense the user's message asks for                  | here, [Record the spending a user's message names](../../usecases/extract-intents.md) · on the ledger's side, [Create an expense proposal](../../../../ledger-service/docs/usecases/create-an-expense-proposal.md) |
| `summarize_spending`      | answers what the caller spent over a period                      | here, [Record the spending a user's message names](../../usecases/extract-intents.md) · on the ledger's side, [Summarize spending over a period](../../../../ledger-service/docs/usecases/summarize-spending.md)   |

### What is sent

The model fills every argument, from the message, from what the lookup answers, and from what each tool says its
arguments take. This service assembles none of them.

A lookup carries one grouping name, taken from the groupings that arrived on the extraction request. A recording
call carries the category the lookup answered and the grouping it was asked for, both required. `merchant` is
sent when the message names who the expense was paid to. A summary call carries a first and a last day, both
required, both inclusive, both worked out by the model from the day the turn runs on. The arguments, and which
are required, are [the tools' own declarations](../../../../ledger-service/docs/contracts/in/mcp.md).

## Semantics

One client serves the whole process. The tools are listed once, on the first turn that needs them, and that list
is kept for the life of the process. Every tool on it is offered to the model as published — nothing here names
a tool or registers one.

Every request carries the token that arrived on the extraction call, verbatim and scheme included, read fresh
for each turn on the thread running it. Two turns running at once share the client and never the identity. The
token is never parsed, never logged, never stored.

Forwarding it untouched is what carries the ledger's own claims across: the reference tying a turn's proposals
to the message that produced them rides the token, and this service neither sends it nor knows it.

A turn holding no token sends no request at all.

The expenses of one message are sent in the order the user said them. A grouping is looked up before an expense
is filed under it, and nothing forbids reusing a listing already made in the same turn.

Nothing is read out of the answer here. A grouping's categories, a stored proposal's id, its timestamp, and
everything else the ledger returns go back to the model as that call's result.

A summary call answers the period the ledger accepted, and no amount. What was spent is put in front of the user
by the ledger, so no total ever enters the model's context.

A tool result flagged as an error is a refusal, and it reaches the model as that call's answer. A refusal never
ends the turn.

A refused recording call is corrected by the model and the same expense tried once more; refused again, that
expense is left unrecorded and the rest of the message is still sent. A refused lookup costs the expense
nothing — the model corrects the grouping's name and asks again, and the expense keeps its one recording retry.

An argument whose value is not the type the published schema declares is refused before the tool runs, so
nothing is recorded. The refusal reaches the model, which corrects the call it just made.

A failure of the transport itself ends the turn — no further call is sent, and what was recorded stands.

A lookup stores nothing, so repeating one changes nothing. Recording is not deduplicated: the same message
handled twice records two proposals, and a model that repeats a recording call within one turn records two.

## Failures

| Condition                                                     | Signal                                                                  |
|---------------------------------------------------------------|-------------------------------------------------------------------------|
| A recording call answers an error result                      | none — the model reads the refusal and retries that expense once      |
| A lookup call answers an error result                         | none — the model corrects the grouping's name and asks again          |
| An argument is not the type the published schema declares     | none — refused before the tool runs; the model corrects the call      |
| The ledger cannot be reached, times out, or refuses the token | the turn fails; the caller is told the service is unavailable           |
| The tools cannot be listed                                    | the same, before any expense is attempted                               |
| No caller token is held for the turn                          | the same; nothing is sent to the ledger and the model is never prompted |

## Compatibility

A tool is chosen by the model from the list the ledger publishes, and its arguments are filled from the same
list. Adding a tool, or renaming one, changes what the model is offered rather than breaking a call assembled
here. Nothing is wired for a new tool: it reaches the model as soon as this process has read the published list.

That makes a newly published tool a matter of restart order, not of a compatibility window. The ledger must be
serving the tool before this service starts; a process that read the list without it goes on offering what it
read, and a message asking for that tool is answered by whatever the model can do with the rest.

An optional argument added on the ledger's side costs nothing. Making one required, or removing one, changes
what the model is told to send.

How an argument is filled is read from the description the ledger publishes beside it, never from this service's
instructions. The amount's description is what asks for it as the message writes it, in the currency's main
unit; the grouping's is what ties a recording call to the lookup that answered its category.

A change to the published list reaches this service only when it restarts. A process still holding a list
without the lookup holds only grouping names, so it sends a grouping as the category and no grouping alongside
it; the ledger refuses that as an invalid request, and nothing the model holds lets it correct the call. Those
expenses go unrecorded until the process restarts.

An argument renamed on the ledger's side lands the same way. A process that started against the older ledger
keeps filling the name it was offered, for its lifetime. Every call carrying the old name is refused as an
invalid request, and the model cannot correct to a name it was never told about. The recovery is a restart of
this service, after which the renamed argument is picked up and the calls work again.

The ledger can switch this endpoint off, which makes every turn fail as unavailable.
