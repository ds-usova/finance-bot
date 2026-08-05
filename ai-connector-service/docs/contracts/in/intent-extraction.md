# Ledger Service — intent extraction (gRPC)

A line a user wrote crosses this boundary in, together with a token to act as that user. Nothing crosses back:
the service acts on what the message asks for and answers that the turn is done. The caller decides nothing
about the text; it sends what the user said, the groupings that user's categories are filed under, the grouping
to fall back on, the day the turn runs on, and — optionally — the currency to assume when an amount is stated
without one.

- **Counterpart:** [the Ledger Service](../../../../ledger-service/docs/contracts/out/ai-connector.md)
- **Transport:** gRPC
- **Schema:** [`proto/intent_extraction.proto`](../../../../proto/intent_extraction.proto), shared at the
  repository root so both sides read the same file

## Operations

| Operation       | Purpose                                                          | Used by                                                                                                                                                                                                          |
|-----------------|------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Extract intents | acts on what a user's message asks for, in the user's order      | here, [Record the spending a user's message names](../../usecases/extract-intents.md) · on the caller's side, [Handle an incoming message](../../../../ledger-service/docs/usecases/handle-incoming-message.md) |
| Health check    | reports whether the server is serving, for the server as a whole | the [Ledger Service](../../../../ledger-service/docs/contracts/out/ai-connector.md), which reports it in its own health endpoint                                                                                 |

## Semantics

Every extraction call carries the caller's bearer token as call metadata, not as a field. It is the identity
every expense the turn records is recorded against, and the only identity this boundary carries. A health check
carries none.

The token carries more than that identity: the caller mints it per message and puts on it a reference to the
message being handled. Everything on the token is opaque here — the service forwards it and reads nothing out
of it.

The token is required but not verified here — it is checked where it is spent, by the
[ledger's tool endpoint](../../../../ledger-service/docs/contracts/in/mcp.md). A caller this boundary has not
authenticated therefore records nothing, but does reach the model
([ADR 0009](../../../../docs/adr/0009-the-connector-does-not-authenticate-its-caller.md)).

A successful call answers with nothing at all. There is no count, no per-entry outcome, no total and no text
for the user — the answer says only that the message was acted on. A summary the message asked for reaches the
user from the ledger, never through this boundary.

Only spending is recorded, and only spending is summarized, by the
[use case's rule](../../usecases/extract-intents.md#rules). A message asking for anything else records nothing
and still succeeds.

Expenses are recorded in the order the user expressed them — nothing is reordered or merged.

An expense the ledger will not record is left unrecorded and the rest of the message is still recorded. The
caller is not told which expenses were recorded, or how many.

The groupings the caller sends are a closed set and must be non-empty, each a name of its own with no blank
among them. No category crosses this boundary: the service asks the
[ledger's tool endpoint](../../../../ledger-service/docs/contracts/in/mcp.md) which categories a grouping holds,
and every expense is filed under one of those, never under a grouping itself.

The caller designates one of the groupings as the catch-all, and it must be one of the groupings sent — so a fit
always exists. The service never invents a name.

The assumed currency applies only where the user stated an amount with no currency. It is accepted in any casing
and must be a code ISO 4217 knows.

The day the turn runs on is required, and is a calendar date written the ISO-8601 way. It is the caller's to
choose and is checked against no clock here — a day in the past or the future is accepted as sent. Every period
read out of a relative phrase is anchored on it, and the week counted from it starts on Monday.

The same text sent twice is acted on twice: nothing is remembered between calls, so a repeated request records
its expenses again and no duplicate is recognized.

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
| The day the turn runs on is not an ISO-8601 date            | rejected as an invalid argument; no call to the provider is made     |
| The provider cannot be reached, refuses the call, or errors | the call fails as unavailable — the caller may retry               |
| The ledger cannot be reached to record an expense           | the call fails as unavailable — the caller may retry               |
| The ledger refuses to record an expense                     | none — the call succeeds and that expense is left unrecorded       |
| The ledger refuses a category lookup                        | none — the model corrects the grouping's name and asks again       |
| The message under-says an expense                           | none — the call succeeds and that expense is left unrecorded       |
| Anything else fails inside the service                      | the call fails as unknown, with no internal detail in the failure    |

## Compatibility

Both sides generate from the one schema file, so a field added or renamed there reaches the caller's build
rather than its runtime.

No field number is reserved: neither service is deployed anywhere, so the two are released together and there is
no counterpart of an older vintage for a reused number to confuse. A partial deploy is a rebuild away from
being whole, not a window the schema has to survive.

What the token carries is the caller's alone to change. A claim it adds reaches
[its own tool endpoint](../../../../ledger-service/docs/contracts/in/mcp.md) untouched, without a change to this
schema or a release of this service
([ADR 0010](../../../../ledger-service/docs/adr/0010-a-message-reference-rides-the-caller-token-not-the-extraction-request.md)).

Removing a promise breaks every caller: an order that is not the user's, an unrecorded expense arriving as a
failed call, or an empty answer that stops meaning the turn was acted on would each force the caller to be
rewritten. Widening what the service records does not.

The empty answer is what leaves room to grow: reporting what was recorded, or a reply for the user, means a
response body where there is none today, and every caller reads it or ignores it as it chooses.
