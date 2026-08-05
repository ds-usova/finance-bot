# AI provider — recording spending (chat completions over HTTPS)

The service hands a user's message to a language model together with the ledger's tools, and lets the model
record what the message says was spent and ask for a summary of what it asks about. What crosses out is standing
instructions, the user's text, the groupings that user's categories are filed under, the grouping to fall back
on, the day the turn runs on, and the ledger's tool schemas; what comes back is a sequence of tool calls, each
answered with the tool's result, until the model answers with text.

- **Counterpart:** an OpenAI-compatible chat-completions API — the address, the model and the credential are
  [configuration](../../configuration.md)
- **Transport:** HTTPS, one exchange per turn — as many requests as the model asks for tool calls
- **Schema:** none — the provider owns the chat-completions request and response format

## Operations

| Operation          | Purpose                                                                             | Used by                                                                         |
|--------------------|-------------------------------------------------------------------------------------|---------------------------------------------------------------------------------|
| Act on the message | calls the ledger's tools — one recording call per expense, one summary per question | [Record the spending a user's message names](../../usecases/extract-intents.md) |

## Semantics

A turn is a loop, not a request. The model answers with a tool call, the call's result goes back to it as that
call's answer, and the loop ends when the model answers with text instead.

Nothing caps the loop. The instructions ask for one retry per refused recording call, and put no limit on how
often a grouping's categories may be asked for; beyond that the turn runs for as long as the model keeps asking
for tool calls.

Nothing is cached, nothing is retried by this service, and no conversation is kept between turns.

The standing instructions never change and never mention a user. The groupings, the catch-all, the day the turn
runs on and the text vary per turn and travel together in the same message, so one user's groupings can never
reach another's turn.

The day the turn runs on is stated as a UTC calendar date, together with the rule that a week starts on Monday.
A message asking what was spent over a period is answered by working that period out from the day and asking
for one summary over its first and last day.

No category is sent. The groupings travel as bare names, and the model is told to pick a grouping, ask the
ledger which categories it holds, and file the expense under one of those — sending that grouping alongside
the category it chose. The grouping to use when none fits is named in the same message.

The model sends a stated amount exactly as the message writes it, in the currency's main unit, and never
converts it. The rule it reads that from is the tool's own argument schema, not these instructions.

The standing instructions describe the task, the order and the retry policy, and name no tool, no argument and
no format. The lookup tool and the summary tool are each named once, in the per-turn message that carries the
groupings; every argument and format the model uses comes from the schemas the ledger publishes.

The instructions forbid the model stating an amount of its own. Nothing rests on that alone: the model's final
answer is discarded, so what a turn recorded and what a period totals are visible in the ledger, not in
anything the provider says.

A model that cannot call tools answers with text and records nothing, and the turn still succeeds. A model that
answers a question in prose instead of asking for a summary leaves the turn with nothing to report.

Cost and latency grow with the number of groupings sent and with the number of expenses the message names,
since every tool call is a further round trip. An expense costs at least two round trips — the lookup and the
recording call; a question costs one. What bounds a turn is the caller's deadline, set by
`spring.grpc.client.channel.ai-connector.default.deadline` on the ledger's side.

## Failures

| Condition                                                | Signal                                                                          |
|----------------------------------------------------------|---------------------------------------------------------------------------------|
| The provider is unreachable, refuses the call, or errors | the turn fails and the caller is told the service is unavailable                |
| The model answers with no tool call                      | none — the turn succeeds having recorded and asked nothing                      |
| The model sends an argument the tool cannot read         | none — the failure goes back as that call's answer for the model to correct     |
| The ledger refuses a recording call                      | none — the refusal goes back as that call's answer, and the model retries once  |
| The ledger refuses a category lookup                     | none — the refusal goes back as that call's answer; the expense keeps its retry |
| The ledger cannot be reached under a tool call           | the turn fails and the caller is told the service is unavailable                |
| The turn outlives the caller's deadline                  | the caller abandons it; the turn runs on and what it recorded stands            |

## Compatibility

The provider is interchangeable: anything speaking the OpenAI chat-completions format, with tool calling, is
reached by pointing the service at a different address. Nothing else in the service moves with it.

Changing the model or the instructions changes what gets recorded without changing any schema, so no build
announces it and only the tests that pin the recording behaviour will notice.

The tool schema is the ledger's. An argument added there reaches the model without a change here.
