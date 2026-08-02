# Ledger Service — intent extraction (gRPC)

A line a user wrote crosses this boundary in, together with a token to act as that user. Nothing crosses back:
the service records the spending the message names and answers that the turn is done. The caller decides nothing
about the text; it sends what the user said, the categories that user already has, and — optionally — the
currency to assume when an amount is stated without one.

- **Counterpart:** [the Ledger Service](../../../../ledger-service/docs/contracts/out/ai-connector.md)
- **Transport:** gRPC
- **Schema:** [`proto/intent_extraction.proto`](../../../../proto/intent_extraction.proto), shared at the
  repository root so both sides read the same file

## Operations

| Operation       | Purpose                                                          | Used by                                                                                                                                                                                                          |
|-----------------|------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Extract intents | records the spending a user's message names, in their order      | here, [Record the spending a user's message names](../../usecases/extract-intents.md) · on the caller's side, [Handle an incoming message](../../../../ledger-service/docs/usecases/handle-incoming-message.md) |
| Health check    | reports whether the server is serving, for the server as a whole | the [Ledger Service](../../../../ledger-service/docs/contracts/out/ai-connector.md), which reports it in its own health endpoint                                                                                 |

## Semantics

Every extraction call carries the caller's bearer token as call metadata, not as a field. It is the identity
every expense the turn records is recorded against, and the only identity this boundary carries. A health check
carries none.

The token is required but not verified here — it is checked where it is spent, by the
[ledger's tool endpoint](../../../../ledger-service/docs/contracts/in/mcp.md). A caller this boundary has not
authenticated therefore records nothing, but does reach the model
([ADR 0009](../../../../docs/adr/0009-the-connector-does-not-authenticate-its-caller.md)).

A successful call answers with nothing at all. There is no count, no per-entry outcome and no text for the
user — the answer says only that the message was acted on.

Only spending is recorded, by the [use case's rule](../../usecases/extract-intents.md#rules). A message asking
for anything else records nothing and still succeeds.

Expenses are recorded in the order the user expressed them — nothing is reordered or merged.

An expense the ledger will not record is left unrecorded and the rest of the message is still recorded. The
caller is not told which expenses were recorded, or how many.

The categories the caller sends are a closed set and must be non-empty. Each is a name and the grouping it sits
in, both always present: a category that can hold an expense always hangs off a grouping. Every expense is filed
under one of them. The caller includes a catch-all, so a fit always exists; the service never invents a category.

The assumed currency applies only where the user stated an amount with no currency. It is accepted in any casing
and must be a code ISO 4217 knows.

The same text sent twice is acted on twice: nothing is remembered between calls, so a repeated request records
its expenses again and no duplicate is recognized.

## Failures

| Condition                                                   | Signal                                                               |
|-------------------------------------------------------------|----------------------------------------------------------------------|
| No token is sent                                            | the call is refused as unauthenticated; the provider is never called |
| The text is absent or only whitespace                       | rejected as an invalid argument; no call to the provider is made     |
| No categories are sent                                      | rejected as an invalid argument; no call to the provider is made     |
| A category's name or its grouping is blank                  | rejected as an invalid argument; no call to the provider is made     |
| An assumed currency is sent that ISO 4217 does not know     | rejected as an invalid argument; no call to the provider is made     |
| The provider cannot be reached, refuses the call, or errors | the call fails as unavailable — the caller may retry               |
| The ledger cannot be reached to record an expense           | the call fails as unavailable — the caller may retry               |
| The ledger refuses an expense                               | none — the call succeeds and that expense is left unrecorded       |
| The message under-says an expense                           | none — the call succeeds and that expense is left unrecorded       |
| Anything else fails inside the service                      | the call fails as unknown, with no internal detail in the failure    |

## Compatibility

Both sides generate from the one schema file, so a field added or renamed there reaches the caller's build
rather than its runtime.

Removing a promise breaks every caller: an order that is not the user's, an unrecorded expense arriving as a
failed call, or an empty answer that stops meaning the turn was acted on would each force the caller to be
rewritten. Widening what the service records does not.

The empty answer is what leaves room to grow: reporting what was recorded, or a reply for the user, means a
response body where there is none today, and every caller reads it or ignores it as it chooses.
