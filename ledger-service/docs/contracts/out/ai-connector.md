# AI Connector Service — intent extraction (gRPC)

This service hands a user's turn to the connector and gets back an acknowledgement. Nothing else crosses back.
Whatever the turn recorded is read from this service's own store afterwards.

- **Counterpart:** [the connector's extraction RPC](../../../../ai-connector-service/docs/contracts/in/intent-extraction.md)
  — what it accepts, and how it refuses
- **Transport:** gRPC, one call per message. The address is [configuration](../../configuration.md).
- **Schema:** [`proto/intent_extraction.proto`](../../../../proto/intent_extraction.proto), shared with the
  counterpart at the repository root

## Operations

| Operation                      | Purpose                                   | Used by                                                                                                                                                                                           |
|--------------------------------|-------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Extract intents                | act on what a message asks for            | [Act on a user's message](../../usecases/handle-incoming-message.md); answered by [Record the spending a user's message names](../../../../ai-connector-service/docs/usecases/extract-intents.md) |
| Check the connector is serving | whether the connector is answering at all | this service's health endpoint                                                                                                                                                                    |

## What crosses

**Sent:** the user's text · the names of the groupings their categories are filed under · which of those is the
catch-all · the day the turn runs on · the currency to assume, present or absent · a credential naming the user,
carried on the call rather than in the payload.

**Answered:** an acknowledgement carrying nothing. It means the turn was acted on. No count, no per-action
outcome, no text for the user.

## What this service decides

- Only grouping names are sent. No category name crosses.
- Only groupings holding at least one category are sent.
- A category is reached from the other side, through [the tool the connector calls back on](../in/mcp.md).
- Groupings travel in alphabetical order. Nothing depends on the position of one.
- One grouping is designated the catch-all, so a fit always exists.
- That catch-all is [the one every catalogue starts with](../../domain/grouping.md), and nothing else. A
  catalogue that does not carry it produces no call at all, rather than a substitute.
- An invalid request is refused where it is built, so it never crosses.
- The day the turn runs on is a calendar date in UTC, from this service's own clock.
- A period asked for in words is anchored on that day. "Last week" is a UTC week for every user, wherever they
  are. No user's own time zone is recorded anywhere.
- The period itself never crosses here. The connector works it out and asks for it over
  [the tool it calls back on](../in/mcp.md).
- The credential is minted per call, and its subject is the
  [id the ledger stores the user under](../../domain/authenticated-user-id.md) — the payload carries no identity
  at all.
- It also names the [message](../../domain/incoming-message-id.md) the turn is about
  ([ADR 0015](../../adr/0015-a-turn-is-named-by-the-message-that-started-it-not-by-a-value-minted-beside-it.md)).
- The connector forwards it untouched. Nothing riding it is part of what the schema agreed. Its whole path is
  [drawn where it is spent](../in/mcp.md#how-a-caller-authenticates).
- Nothing is retried and nothing is cached. The same text sent twice is two calls.

## Timing

- A call must answer within `spring.grpc.client.channel.ai-connector.default.deadline`.
- That deadline covers the whole model-driven loop: listing the tools, then a provider call and a callback per
  expense, per period asked about, and per retry.
- Three ceilings nest, outermost first: [the lifetime](../../configuration.md) of the credential this service
  mints, then the call's deadline, then the connector's own per-callback timeout.
- So one slow callback cannot spend the turn.
- A call that runs out of time is abandoned here while the connector runs on.
- A failure here does not mean nothing was recorded. What was recorded by then still reaches the user.

## Health

- The connector's serving status is polled and reported in this service's health endpoint.
- A target pointing nowhere shows there, rather than at the first message.
- The check asks about the connector's server as a whole, not one service on it, and carries no credential.

## Failures

| Condition                                                  | Signal                                                     |
|------------------------------------------------------------|------------------------------------------------------------|
| The request is absent                                      | rejected as invalid; the connector is never reached        |
| The user's groupings do not carry the designated catch-all | the turn ends before the request is built; nothing is sent |
| The connector refuses the call as unauthenticated          | the extraction fails, naming that status                   |
| The call fails, times out, or the connector is unreachable | the extraction fails, naming the status it came back with  |
| The health check fails, or reports anything but serving    | the health endpoint reports down, carrying what came back  |

## Compatibility

- Both sides build from the one schema. A change to it reaches the build, not the runtime.
- The credential is the fragile part. This service mints and later validates it; the connector only passes it
  through. Changing who signs it, or how long it lives, changes both ends of the turn.
- Pointing at a different connector is an address change and nothing else. That connector must still reach this
  service's tools, with the credential it was given.
