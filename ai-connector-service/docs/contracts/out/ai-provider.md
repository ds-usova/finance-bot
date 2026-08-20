# AI provider — reading and embedding a message (HTTPS)

The service hands a user's message to a language model, with the ledger's tools attached. It also has the
provider turn a message into the vector by which messages are compared for meaning.

**Sent:** for a turn, two messages — [what they carry](#what-the-two-messages-carry) — and the ledger's tool
schemas. For an embedding, the text of one or more messages.

**Returned:** for a turn, a sequence of tool calls, each answered with that tool's result, until the model
answers with text. For an embedding, one vector per text, in the order the texts were sent.

- **Counterpart:** an OpenAI-compatible API — the address, the credential and the two models are
  [configuration](../../configuration.md). Both operations use the same address and the same credential.
- **Transport:** HTTPS. One exchange per turn — as many requests as the model asks for tool calls — and one
  request per embedding call.
- **Schema:** none — the provider owns the request and response format of both operations

## Operations

| Operation           | Purpose                                                                             | Used by                                                                             |
|---------------------|-------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------|
| Act on the message  | calls the ledger's tools — one recording call per expense, one summary per question | [Record the spending a user's message names](../../usecases/extract-intents.md)     |
| Embed one message   | the vector a turn is given its own examples by                                       | [Recall the person's own worked examples](../../usecases/recall-examples.md)        |
| Embed a batch       | the vectors for messages no turn managed to embed                                    | [Embed the messages nothing has embedded yet](../../usecases/backfill-embeddings.md) |

## A turn is a loop, not a request

- The model answers with a tool call. That call's result goes back to it as the answer.
- The loop ends when the model answers with text instead.
- Nothing caps the loop.
- The instructions ask for one retry per refused recording call, and put no limit on category lookups.
- Nothing is cached. Nothing is retried by this service. No conversation is kept between turns.

An embedding call is never part of that loop.

## What the two messages carry

| Message               | Contents                                                                                                |
|-----------------------|-----------------------------------------------------------------------------------------------------------|
| Standing instructions | the task, the order, the retry policy, what an example is. Never changes, never mentions a user.        |
| Per-turn message      | the text, the person's own worked examples, the groupings, the catch-all, the day the turn runs on      |

- Both travel together in the same request, so one user's groupings can never reach another's turn.
- The examples are that same person's own earlier messages, and travel in the same request for the same reason.
- The instructions name no tool, no argument and no format.
- The lookup tool and the summary tool are each named once, in the per-turn message.
- Every argument and format the model uses comes from the schemas the ledger publishes.

## What a person's history may leave over this boundary

- Only when the memory is on and the turn's message was registered. Otherwise the per-turn message carries no
  examples section at all.
- The bound on how much and how far back is
  [the recall use case's](../../usecases/recall-examples.md), by way of
  [configuration](../../configuration.md).
- An example carries the earlier message's text and its decided expenses — never a date, never an amount this
  service computed, never another person's anything.
- An embedding call carries one message's text and nothing else about the person.

## What the model is told to do

- Pick a grouping, ask the ledger which categories it holds, and file the expense under one of those.
- Send that grouping alongside the category it chose.
- Use the named fall-back grouping when none fits.
- Read an example as this person's own filing habit, never as an answer that outranks what the tools say.
- Send a stated amount exactly as the message writes it, in the currency's main unit. Never convert it. That
  rule comes from the tool's own argument schema, not from these instructions.
- Work a relative period out from the day the turn runs on, stated as a UTC calendar date. A week starts on
  Monday.
- Ask for one summary over that period's first and last day.
- Never state an amount of its own.

- The model's final answer is discarded. What a turn recorded is visible in the ledger alone.
- No category is ever sent. The groupings travel as bare names.

## What a weaker model costs

- A model that answers a question in prose, instead of asking for a summary, leaves the turn with nothing to
  report.

## Cost and latency

- Every tool call is a further round trip.
- An expense costs at least two: the lookup and the recording call.
- A question costs one.
- Cost grows with the number of groupings sent, the number of examples sent, and the number of expenses the
  message names.
- A turn makes at most one embedding call, when the recall needs one.
- What bounds a turn is the caller's deadline,
  `spring.grpc.client.channel.ai-connector.default.deadline`, on the ledger's side.
- What bounds a turn's embedding call is `MEMORY_EMBEDDING_TIMEOUT`, and the backfill's batch call
  `MEMORY_BACKFILL_TIMEOUT`. Neither delays a turn beyond its own bound.

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
| An embedding call is refused or errors                   | none — a failed attempt is counted and the turn goes on with no examples        |
| An embedding call outlives its own timeout               | the same, once that timeout passes and no later                                 |
| Fewer vectors come back than texts were sent             | the same; nothing of that call is kept                                          |

## Compatibility

- Anything speaking the OpenAI chat-completions and embeddings formats, with tool calling, is reached by
  pointing the service at a different address. Nothing else moves with it.
- The tool schema is the ledger's. An argument added there reaches the model with no change here.
- Changing the model or the instructions changes what gets recorded without changing any schema. No build
  announces it, and only the tests that pin the recording behaviour will notice.
- The chat model and the embedding model are chosen separately, and either may change without the other.
- A vector is only comparable with vectors of the same embedding model, and only fits the width
  [the store's column](database.md) holds. Changing the embedding model therefore takes a migration, and the
  vectors already stored stop meaning anything.
