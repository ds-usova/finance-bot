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

The caller mints it and the caller checks it. Its whole path is
[the ledger's, drawn there](../../../../ledger-service/docs/contracts/in/mcp.md#how-a-caller-authenticates).
What this boundary promises about it:

- Every extraction call carries it as call metadata, not as a field.
- It is the identity every expense is recorded against, and the only identity this boundary carries.
- A health check carries none.
- Everything on it is opaque here. The service forwards it and reads nothing out of it.
- It is required, but never verified here.
- So a caller this boundary has not authenticated records nothing, but does reach the model
  ([ADR 0009](../../../../docs/adr/0009-the-connector-does-not-authenticate-its-caller.md)).

## What the caller sends

| Field                    | Rule                                                                         |
|--------------------------|------------------------------------------------------------------------------|
| The text                 | what the user said, unread by the caller                                    |
| The groupings            | a closed set, non-empty, each a name of its own, none blank                 |
| The catch-all grouping   | one of the groupings sent, so a fit always exists                           |
| The day the turn runs on | a calendar date written `YYYY-MM-DD`, required                              |
| The currency to assume   | optional; any casing; a code ISO 4217 knows                                 |

- No category crosses this boundary. The service asks the
  [ledger's tool endpoint](../../../../ledger-service/docs/contracts/in/mcp.md) which categories a grouping
  holds.
- Every expense is filed under one of those, never under a grouping itself.
- The service never invents a name.
- The assumed currency applies only where the user stated an amount with no currency.
- The day the turn runs on is the caller's to choose, and is checked against no clock here. A day in the past or
  the future is accepted as sent.
- Every period read out of a relative phrase is anchored on that day. The week counted from it starts on Monday.

## What the answer means

- A successful call answers with nothing at all.
- No count, no per-entry outcome, no total, no text for the user.
- It says only that the message was acted on.
- A summary the message asked for reaches the user from the ledger, never through this boundary.

## How the message is acted on

- Only spending is recorded, and only spending is summarized
  ([Record the spending a user's message names](../../usecases/extract-intents.md)).
- A message asking for anything else records nothing and still succeeds.
- Expenses are recorded in the order the user expressed them. Nothing is reordered or merged.
- An expense the ledger will not record is left unrecorded. The rest of the message is still recorded.
- The caller is never told which expenses were recorded, or how many.
- The same text sent twice is acted on twice. Nothing is remembered between calls, so no duplicate is
  recognized.

## Failures

| Condition                                                   | Signal                                                               |
|-------------------------------------------------------------|----------------------------------------------------------------------|
| No token is sent                                            | the call is refused as unauthenticated; the provider is never called |
| The text is absent or only whitespace                       | rejected as an invalid argument; no call to the provider is made     |
| No groupings are sent                                       | rejected as an invalid argument; no call to the provider is made     |
| A grouping's name is blank                                  | rejected as an invalid argument; no call to the provider is made     |
| The catch-all grouping is blank or not sent                 | rejected as an invalid argument; no call to the provider is made     |
| The catch-all grouping is not one of the groupings sent     | rejected as an invalid argument; no call to the provider is made     |
| An assumed currency is sent that ISO 4217 does not know     | rejected as an invalid argument; no call to the provider is made     |
| The day the turn runs on is absent or only whitespace       | rejected as an invalid argument; no call to the provider is made     |
| The day the turn runs on is not written `YYYY-MM-DD`        | rejected as an invalid argument; no call to the provider is made     |
| The provider cannot be reached, refuses the call, or errors | the call fails as unavailable — the caller may retry               |
| The ledger cannot be reached to record an expense           | the call fails as unavailable — the caller may retry               |
| The ledger refuses to record an expense                     | none — the call succeeds and that expense is left unrecorded       |
| The ledger refuses a category lookup                        | none — the model corrects the grouping's name and asks again       |
| The message under-says an expense                           | none — the call succeeds and that expense is left unrecorded       |
| Anything else fails inside the service                      | the call fails as unknown, with no internal detail in the failure    |

## Compatibility

- Both sides generate from the one schema file. A field added or renamed there reaches the caller's build, not
  its runtime.
- No field number is reserved. Neither service is deployed anywhere, so the two are released together and no
  counterpart of an older vintage can be confused by a reused number.
- What the token carries is the caller's alone to change. A claim it adds reaches
  [its own tool endpoint](../../../../ledger-service/docs/contracts/in/mcp.md) untouched, with no change to this
  schema and no release of this service
  ([ADR 0010](../../../../ledger-service/docs/adr/0010-a-message-reference-rides-the-caller-token-not-the-extraction-request.md)).
- Widening what the service records breaks nothing.

Three promises cannot be removed without rewriting every caller:

| Promise                              | What breaks if it goes                   |
|--------------------------------------|-------------------------------------------|
| Expenses keep the user's order       | the caller can no longer trust the order |
| An unrecorded expense is not a failure | a partial turn starts arriving as an error |
| An empty answer means the turn was acted on | success stops being readable          |

That empty answer is also the room to grow. Reporting what was recorded, or a reply for the user, means a
response body where there is none today. Every caller reads it or ignores it as it chooses.
