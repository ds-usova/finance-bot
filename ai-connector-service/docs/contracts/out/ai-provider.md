# AI provider — recording spending (chat completions over HTTPS)

The service hands a user's message to a language model, with the ledger's tools attached. The model records what
was spent and asks for the summaries the message asks about.

**Sent:** two messages — [what they carry](#what-the-two-messages-carry) — and the ledger's tool schemas.

**Returned:** a sequence of tool calls, each answered with that tool's result, until the model answers with text.

- **Counterpart:** an OpenAI-compatible chat-completions API — the address, the model and the credential are
  [configuration](../../configuration.md)
- **Transport:** HTTPS, one exchange per turn — as many requests as the model asks for tool calls
- **Schema:** none — the provider owns the chat-completions request and response format

## Operations

| Operation          | Purpose                                                                             | Used by                                                                         |
|--------------------|-------------------------------------------------------------------------------------|---------------------------------------------------------------------------------|
| Act on the message | calls the ledger's tools — one recording call per expense, one summary per question | [Record the spending a user's message names](../../usecases/extract-intents.md) |

## A turn is a loop, not a request

- The model answers with a tool call. That call's result goes back to it as the answer.
- The loop ends when the model answers with text instead.
- Nothing caps it. The turn runs for as long as the model keeps asking for tool calls.
- The instructions ask for one retry per refused recording call, and put no limit on category lookups.
- Nothing is cached. Nothing is retried by this service. No conversation is kept between turns.

## What the two messages carry

| Message               | Contents                                                                     |
|-----------------------|------------------------------------------------------------------------------|
| Standing instructions | the task, the order, the retry policy. Never changes, never mentions a user. |
| Per-turn message      | the text, the groupings, the catch-all, the day the turn runs on            |

- Both travel together in the same request, so one user's groupings can never reach another's turn.
- The instructions name no tool, no argument and no format.
- The lookup tool and the summary tool are each named once, in the per-turn message.
- Every argument and format the model uses comes from the schemas the ledger publishes.

## What the model is told to do

- Pick a grouping, ask the ledger which categories it holds, and file the expense under one of those.
- Send that grouping alongside the category it chose.
- Use the named fall-back grouping when none fits.
- Send a stated amount exactly as the message writes it, in the currency's main unit. Never convert it. That
  rule comes from the tool's own argument schema, not from these instructions.
- Work a relative period out from the day the turn runs on, stated as a UTC calendar date. A week starts on
  Monday.
- Ask for one summary over that period's first and last day.
- Never state an amount of its own.

- The model's final answer is discarded. What a turn recorded is visible in the ledger alone.
- No category is ever sent. The groupings travel as bare names.

## What a weaker model costs

- A model that cannot call tools answers with text and records nothing. The turn still succeeds.
- A model that answers a question in prose, instead of asking for a summary, leaves the turn with nothing to
  report.

## Cost and latency

- Every tool call is a further round trip.
- An expense costs at least two: the lookup and the recording call.
- A question costs one.
- Cost grows with the number of groupings sent and the number of expenses the message names.
- What bounds a turn is the caller's deadline,
  `spring.grpc.client.channel.ai-connector.default.deadline`, on the ledger's side.

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

- The provider is interchangeable. Anything speaking the OpenAI chat-completions format, with tool calling, is
  reached by pointing the service at a different address. Nothing else moves with it.
- The tool schema is the ledger's. An argument added there reaches the model with no change here.
- Changing the model or the instructions changes what gets recorded without changing any schema. No build
  announces it, and only the tests that pin the recording behaviour will notice.
