# AI Connector Service — intent extraction (gRPC)

The service hands a user's turn to the AI Connector: the text, the groupings that user's categories are filed
under, and a credential to act as them. The connector acts on whatever the message asks for and answers only
that the turn completed — no action crosses back.

- **Counterpart:** the AI Connector Service — its address is [configuration](../../configuration.md)
- **Transport:** gRPC, one call per message
- **Schema:** [`proto/intent_extraction.proto`](../../../../proto/intent_extraction.proto), shared with the
  counterpart at the repository root

## Operations

| Operation                      | Purpose                                   | Used by                                                                                                                                                                                           |
|--------------------------------|-------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Extract intents                | act on what a message asks for            | [Act on a user's message](../../usecases/handle-incoming-message.md); answered by [Record the spending a user's message names](../../../../ai-connector-service/docs/usecases/extract-intents.md) |
| Check the connector is serving | whether the connector is answering at all | this service's health endpoint                                                                                                                                                                    |

## Semantics

**Sent:** the user's text · the names of the groupings their categories are filed under · which of those
groupings is the catch-all · the currency to assume (optional) · a credential naming the user, carried on the
call rather than in the payload.

**Answered:** an acknowledgement carrying nothing. Success means the turn was acted on — no count, no per-action
outcome, no text for the user. What the turn actually recorded is read back out of this service's own store,
under the message the credential named.

What the connector promises is [its side of this boundary](../../../../ai-connector-service/docs/contracts/in/intent-extraction.md#semantics).
What this side adds:

- A request is fixed once made: blank text, no groupings, or a blank name among them is refused where the
  request is built, so it never crosses.
- Only grouping names are sent; no category name crosses.
- A category is reached from the other side, through [the tool the connector calls back on](../in/mcp.md).
- One grouping is designated the catch-all, so a fit always exists. It is never blank, and always one of the
  groupings sent.
- The groupings travel in alphabetical order, and nothing depends on the position of one in the list.
- The assumed currency is stated as present or absent; it is never left unsaid.
- An absent request is refused before the connector is reached.
- The credential is minted per call, names the user as its subject, and is what the connector calls back with
  ([the tool it calls](../in/mcp.md)).
- The credential also names the [message](../../domain/message-reference.md) the turn is about, so everything
  recorded during it can be found again afterwards
  ([ADR 0010](../../adr/0010-a-message-reference-rides-the-caller-token-not-the-extraction-request.md)).
- The connector forwards the credential untouched, so nothing carried on it is part of what the two sides agreed
  in the schema.
- Nothing is retried and nothing is cached: the same text sent twice is two calls.
- A call answers within `spring.grpc.client.channel.ai-connector.default.deadline`, which has to cover the whole
  model-driven loop: listing the tools, a provider call, a category lookup and a recording callback per expense,
  a provider call per result, and a further pair per expense retried.
- Three ceilings nest, outermost first: [`MCP_JWT_TTL`](../../configuration.md) on the credential this service
  mints, then the call's deadline, then the connector's own per-callback timeout — so a single slow callback
  cannot spend the turn.
- A call that runs out of time is abandoned on this side while the connector runs on: a failure here does not
  mean nothing was recorded, and what was recorded by then is still reported to the user.
- The connector's own serving status is polled and reported in this service's health endpoint, so a target
  pointing nowhere shows there rather than at the first message.
- The check asks about the connector's server as a whole, not one service on it, and carries no credential.

## Failures

| Condition                                                  | Signal                                                    |
|------------------------------------------------------------|-----------------------------------------------------------|
| The request is absent                                      | rejected as invalid; the connector is never reached       |
| The user has no grouping spending can be filed under       | rejected as invalid where the request is built            |
| The connector refuses the call as unauthenticated          | the extraction fails, naming that status                  |
| The call fails, times out, or the connector is unreachable | the extraction fails, naming the status it came back with |
| The health check fails, or reports anything but serving    | the health endpoint reports down, carrying what came back |

## Compatibility

Both sides build from the one schema, so a change to it reaches the build rather than the runtime.

The credential's shape is the fragile part: the connector passes it through untouched, and it is this service
that mints and later validates it. Changing who signs it, or how long it lives, changes both ends of the turn.

Removing what the connector promises breaks this side: an acknowledgement that no longer means the turn was
acted on, and a permanent refusal arriving as an unavailability, are the sharpest.

Pointing the service at a different connector is an address change and nothing else — but that connector must
reach this service's tool back, with the credential it was given.
