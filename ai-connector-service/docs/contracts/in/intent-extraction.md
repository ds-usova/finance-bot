# Ledger Service — intent extraction (gRPC)

A line a user wrote crosses this boundary in, with a token to act as that user. The service acts on what the
message asks for and answers only that the turn is done.

The caller sends the user's text, the groupings their categories are filed under, the grouping to fall back on,
the day the turn runs on, and optionally the currency to assume. It decides nothing about the text itself.

- **Counterpart:** [the Ledger Service](../../../../ledger-service/docs/contracts/out/ai-connector.md)
- **Transport:** gRPC
- **Schema:** [`proto/intent_extraction.proto`](../../../../proto/intent_extraction.proto), shared at the
  repository root so both sides read the same file

## Operations

| Operation       | Purpose                                                          | Used by                                                                                                                                                                                                          |
|-----------------|------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Extract intents | acts on what a user's message asks for, in the user's order      | here, [Record the spending a user's message names](../../usecases/extract-intents.md) · on the caller's side, [Handle an incoming message](../../../../ledger-service/docs/usecases/handle-incoming-message.md) |
| Health check    | reports whether the server is serving, for the server as a whole | the [Ledger Service](../../../../ledger-service/docs/contracts/out/ai-connector.md), which reports it in its own health endpoint                                                                                 |

## The token

The caller mints it. Its whole path is
[the ledger's, drawn there](../../../../ledger-service/docs/contracts/in/mcp.md#how-a-caller-authenticates).
What this boundary promises about it:

- Every extraction call carries it as call metadata, not as a field.
- It is the identity every expense is recorded against, and the only identity this boundary carries.
- A health check carries none.
- It is required, and verified here before the request is looked at: signature against
  [the key set the ledger publishes](../out/ledger-mcp.md#the-ledgers-key-set), expiry, issuer and audience.
- Two claims are read off it — the person the call acts for, and the message that started the turn. Both are
  what a kept message is [filed under](../../domain/message-identity.md).
- Everything else on it is opaque here, and the whole token is forwarded to the ledger untouched.
- With `MEMORY_ENABLED` off none of that reading happens and the token is forwarded unread
  ([ADR 0017](../../../../docs/adr/0017-the-connector-verifies-its-caller-token-and-keeps-the-message-it-names.md)).

## What is kept of a call

- Never answered back over this boundary. What is kept is
  [the store's](../out/database.md) and no operation here reads it.
- The service never invents a name.
- The day the turn runs on is the caller's to choose, and is checked against no clock here.

## What the answer means

- A successful call answers with nothing at all.
- It says only that the message was acted on.
- A summary the message asked for reaches the user from the ledger, never through this boundary.

## How the message is acted on

- Only spending is recorded, and only spending is summarized
  ([Record the spending a user's message names](../../usecases/extract-intents.md)).
- A message asking for anything else records nothing and still succeeds.

## Failures

| Condition                                                   | Signal                                                               |
|-------------------------------------------------------------|----------------------------------------------------------------------|
| No token is sent                                            | the call is refused as unauthenticated; the provider is never called |
| The token's signature, expiry, issuer or audience does not hold | the same                                                          |
| The token names no person, or no message                    | the same                                                             |
| The ledger's key set cannot be read in time                 | the call fails as unavailable — the caller may retry               |
| The message cannot be kept                                  | none — the call runs on and answers as it would have               |
| A field breaks the rule the [schema](../../../../proto/intent_extraction.proto) states for it | rejected as an invalid argument; no call to the provider is made |
| The provider cannot be reached, refuses the call, or errors | the call fails as unavailable — the caller may retry               |
| The ledger cannot be reached to record an expense           | the call fails as unavailable — the caller may retry               |
| A tool call is refused, or the message under-says an expense | none — the call succeeds; what the model does with a refusal is [the ledger tools'](../out/ledger-mcp.md#how-a-turn-behaves) |
| Anything else fails inside the service                      | the call fails as unknown, with no internal detail in the failure    |

## Compatibility

- Both sides generate from the one schema file. A field added or renamed there reaches the caller's build, not
  its runtime.
- No field number is reserved. Neither service is deployed anywhere, so the two are released together and no
  counterpart of an older vintage can be confused by a reused number.
- A claim the caller adds to the token reaches
  [its own tool endpoint](../../../../ledger-service/docs/contracts/in/mcp.md) untouched, with no change to this
  schema and no release of this service
  ([ADR 0010](../../../../ledger-service/docs/adr/0010-a-message-reference-rides-the-caller-token-not-the-extraction-request.md)).
- The two claims this side reads are not the caller's alone. Renaming or dropping either refuses every call
  here, and the issuer and audience must keep matching what this side is configured with.
- Changing the key the caller signs with breaks nothing: the key set is republished and read from there.
- Widening what the service records breaks nothing.

Three promises cannot be removed without rewriting every caller:

| Promise                              | What breaks if it goes                   |
|--------------------------------------|-------------------------------------------|
| Expenses keep the user's order       | the caller can no longer trust the order |
| An unrecorded expense is not a failure | a partial turn starts arriving as an error |
| An empty answer means the turn was acted on | success stops being readable          |

That empty answer is also the room to grow: reporting what was recorded, or a reply for the user, means a
response body where there is none today.
