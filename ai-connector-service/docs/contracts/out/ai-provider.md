# AI provider — intent inference (chat completions over HTTPS)

The service asks a language model what actions a user's message asks for. What crosses out is standing
extraction instructions, the user's text and the categories that user has; what comes back is one unvalidated
answer per action the model found.

- **Counterpart:** an OpenAI-compatible chat-completions API — the address, the model and the credential are
  [configuration](../../configuration.md)
- **Transport:** HTTPS, one request per extraction
- **Schema:** none — the provider owns the chat-completions request and response format

## Operations

| Operation                        | Purpose                                                        | Used by                                                                 |
|----------------------------------|-----------------------------------------------------------------|-------------------------------------------------------------------------|
| Ask what the message asks for    | returns one entry per action found, in the order they were said | [Extract the intents in a user's message](../../usecases/extract-intents.md) |

## Semantics

One call per extraction. Nothing is cached, nothing is retried, and no conversation is kept: the provider is
told everything it needs each time.

The standing instructions never change and never mention a user. The categories and the text vary per call and
travel together in the same message, so one user's categories can never reach another's extraction.

The answer is asked for as a list of entries, one per action the message asks for, in the order the user said
them, and as a one-entry list when the message asks for a single action. Every field of an entry is asked for
as text — an amount as decimal digits, a currency as an ISO 4217 code — so no amount is ever a number in this
exchange. The provider is told to leave a field out rather than guess at it, and to answer with an empty list
when the message asks for nothing actionable.

Each entry carries a target, an operation, a category name, a new category name, an amount, a currency and a
description, exactly as they came back. None of them is required: a bad or partial answer from the model is an
ordinary outcome on this boundary, never an error.

Nothing the provider says is trusted. Every field is checked afterwards against
[what the caller promised](../in/intent-extraction.md#semantics): an invented category, an unusable amount, an
action that does not exist and a missing mandatory piece are all caught on this side. An empty answer is a
legitimate answer, not a failure.

Cost and latency grow with the number of categories sent, since every one of them is part of the message.

## Failures

| Condition                                                     | Signal                                                                     |
|---------------------------------------------------------------|-----------------------------------------------------------------------------|
| The provider is unreachable, refuses the call, or errors      | the extraction fails and the caller is told the service is unavailable      |
| The answer cannot be read as the expected list of entries     | the same — an unreadable answer is a provider failure, not an unknown intent |
| The answer holds no entries                                   | none — it becomes the single unknown entry the caller is promised           |
| An entry is missing a field or carries an unusable one        | none — that entry alone becomes unknown, saying why                        |

## Compatibility

The provider is interchangeable: anything speaking the OpenAI chat-completions format is reached by pointing
the service at a different address. Nothing else in the service moves with it.

Changing the model or the instructions changes what gets extracted without changing any schema, so no build
announces it and only the tests that pin the extraction behaviour will notice.
