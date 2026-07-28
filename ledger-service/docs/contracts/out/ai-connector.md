# AI Connector Service — intent extraction (gRPC)

The service asks the AI Connector what actions a user's message asks for. What crosses out is the user's text,
the categories that user already has and — optionally — the currency to assume; what comes back is one entry per
action found.

*Nothing in this service calls it yet: the boundary exists ahead of its first caller.*

- **Counterpart:** the AI Connector Service — its address is [configuration](../../configuration.md)
- **Transport:** gRPC, one call per extraction
- **Schema:** [`proto/intent_extraction.proto`](../../../../proto/intent_extraction.proto), shared with the
  counterpart at the repository root

## Operations

| Operation                     | Purpose                                              | Used by                                                                                                              |
|-------------------------------|------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------|
| Extract intents               | the actions a message asks for                       | no use case yet; answered by [Extract the intents in a user's message](../../../../ai-connector-service/docs/usecases/extract-intents.md) |
| Check the connector is serving | whether the connector is answering at all           | this service's health endpoint                                                                                       |

## Semantics

**Sent:** the user's text · that user's categories · the currency to assume (optional).

What the connector promises about the answer is [its side of this boundary](../../../../ai-connector-service/docs/contracts/in/intent-extraction.md#semantics).
What this side adds:

- A request is fixed once made: blank text, no categories, or a blank one among them is refused where the
  request is built, so it never crosses.
- An absent request is refused before the connector is reached.
- The answer is read entry by entry, in the order it arrives.
- An entry this side cannot make sense of becomes an unknown intent carrying the reason, never a failed call.
- That covers an action this service does not know, an entry carrying no payload, a currency ISO 4217 does not
  know, and any shape the ledger's own rules reject.
- A negative amount is not an amount this side accepts.
- Nothing is retried and nothing is cached: the same text sent twice is two calls.
- A call has ten seconds to answer, which has to cover a model round trip on the connector's side.
- The connector's own serving status is polled and reported in this service's health endpoint, so a target
  pointing nowhere shows there rather than at the first extraction call.
- The check asks about the connector's server as a whole, not one service on it.

## Failures

| Condition                                                       | Signal                                                                      |
|-----------------------------------------------------------------|-------------------------------------------------------------------------------|
| The request is absent                                           | rejected as invalid; the connector is never reached                          |
| The call fails, times out, or the connector is unreachable      | the extraction fails, naming the status it came back with                    |
| The answer holds no entries                                     | the extraction fails — a never-empty answer is what the connector promises   |
| One entry cannot be made sense of                               | none — that entry alone becomes unknown, carrying the reason                 |
| The health check fails, or reports anything but serving         | the health endpoint reports down, carrying what came back                    |

## Compatibility

A wider schema costs this side nothing: an action or an entry kind it does not recognize already arrives as an
unknown intent.

Removing what the connector promises breaks it. An answer that could legitimately be empty is the sharpest one —
this side reads emptiness as a failure — followed by an order that is not the user's, and an unknown arriving as
a failed call.

Pointing the service at a different connector is an address change and nothing else.
