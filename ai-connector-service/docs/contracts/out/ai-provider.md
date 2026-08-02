# AI provider — recording spending (chat completions over HTTPS)

The service hands a user's message to a language model together with the ledger's tool, and lets the model
record what the message says was spent. What crosses out is standing recording instructions, the user's text,
the categories that user has and the ledger's tool schema; what comes back is a sequence of tool calls, each
answered with the tool's result, until the model answers with text.

- **Counterpart:** an OpenAI-compatible chat-completions API — the address, the model and the credential are
  [configuration](../../configuration.md)
- **Transport:** HTTPS, one exchange per turn — as many requests as the model asks for tool calls
- **Schema:** none — the provider owns the chat-completions request and response format

## Operations

| Operation                     | Purpose                                                       | Used by                                                                         |
|-------------------------------|---------------------------------------------------------------|---------------------------------------------------------------------------------|
| Record the message's spending | calls the ledger's tool once per expense, in the user's order | [Record the spending a user's message names](../../usecases/extract-intents.md) |

## Semantics

A turn is a loop, not a request. The model answers with a tool call, the call's result goes back to it as that
call's answer, and the loop ends when the model answers with text instead.

Nothing caps the loop. The instructions ask for one retry per refused expense; beyond that the turn runs for as
long as the model keeps asking for tool calls.

Nothing is cached, nothing is retried by this service, and no conversation is kept between turns.

The standing instructions never change and never mention a user. The categories and the text vary per turn and
travel together in the same message, so one user's categories can never reach another's turn.

Each category is rendered as a `Grouping > Category` label, so two categories sharing a name can be told apart.
The model splits a label into the tool's two category arguments — what follows the separator is the category,
what precedes it is the parent.

The model converts a stated amount into the currency's minor units. The rule it reads that from is the tool's
own argument schema, not these instructions.

The instructions describe the task, the order, the retry policy and the closed set of categories. They name no
tool, no argument and no format — the model reads those from the schema the ledger publishes.

The model's final answer is discarded. What a turn recorded is visible in the ledger, not in anything the
provider says.

A model that cannot call tools answers with text and records nothing, and the turn still succeeds.

Cost and latency grow with the number of categories sent and with the number of expenses the message names,
since every tool call is a further round trip.

## Failures

| Condition                                                | Signal                                                                           |
|----------------------------------------------------------|----------------------------------------------------------------------------------|
| The provider is unreachable, refuses the call, or errors | the turn fails and the caller is told the service is unavailable                 |
| The model answers with no tool call                      | none — the turn succeeds having recorded nothing                               |
| The model sends an argument the tool cannot read         | none — the failure goes back as that call's answer for the model to correct    |
| The ledger refuses a call                                | none — the refusal goes back as that call's answer, and the model retries once |
| The ledger cannot be reached under a tool call           | the turn fails and the caller is told the service is unavailable                 |
| The turn outlives the caller's deadline                  | the caller abandons it; the turn runs on and what it recorded stands             |

## Compatibility

The provider is interchangeable: anything speaking the OpenAI chat-completions format, with tool calling, is
reached by pointing the service at a different address. Nothing else in the service moves with it.

Changing the model or the instructions changes what gets recorded without changing any schema, so no build
announces it and only the tests that pin the recording behaviour will notice.

The tool schema is the ledger's. An argument added there reaches the model without a change here.
