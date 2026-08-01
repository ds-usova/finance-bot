# Ledger Service — intent extraction (gRPC)

A line a user wrote crosses this boundary in, together with a token to act as that user. Nothing crosses back:
the service acts on what the message asks for and answers that the turn is done. The caller decides nothing
about the text; it sends what the user said, the categories that user already has, and — optionally — the
currency to assume when an amount is stated without one.

- **Counterpart:** [the Ledger Service](../../../../ledger-service/docs/contracts/out/ai-connector.md)
- **Transport:** gRPC
- **Schema:** [`proto/intent_extraction.proto`](../../../../proto/intent_extraction.proto), shared at the
  repository root so both sides read the same file

## Operations

| Operation       | Purpose                                                          | Used by                                                                                                                                                                                                     |
|-----------------|------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Extract intents | acts on the actions a user's message asks for, in their order    | here, [Act on the actions in a user's message](../../usecases/extract-intents.md) · on the caller's side, [Handle an incoming message](../../../../ledger-service/docs/usecases/handle-incoming-message.md) |
| Health check    | reports whether the server is serving, for the server as a whole | the [Ledger Service](../../../../ledger-service/docs/contracts/out/ai-connector.md), which reports it in its own health endpoint                                                                            |

## Semantics

Every extraction call carries the caller's bearer token as call metadata, not as a field. It is the identity
every expense the turn records is recorded against, and the only identity this boundary carries. A health check
carries none.

A successful call answers with nothing at all. There is no count, no per-entry outcome and no text for the
user — the answer says only that the message was acted on.

Only expenses to record are acted on, by the
[use case's rule](../../usecases/extract-intents.md#rules). Everything else the message asks for is skipped, and
the call still succeeds: a message asking for nothing this service does is not a failure.

Actions are acted on in the order the user expressed them — nothing is reordered, merged or dropped. An expense
filed under a category the same message creates is therefore reached before that category exists in the ledger,
and its proposal is refused.

A turn stops at the first expense the ledger will not record. Whatever was recorded before it stands, and the
caller is not told what that was.

The categories the caller sends are a closed set and must be non-empty. Each is a name and the grouping it sits
in, both always present: a category that can hold an expense always hangs off a grouping. An expense is filed
under one of them, matched ignoring case and used in the caller's spelling, or under a category the same message
asks to create. The caller includes a catch-all, so a fit always exists; the service never proposes a category
of its own.

The assumed currency is applied here, not by the model, and only where the user stated an amount with no
currency. It is accepted in any casing and must be a code ISO 4217 knows.

The same text sent twice is acted on twice: nothing is remembered between calls, so a repeated request records
its expenses again and no duplicate is recognized.

## Failures

| Condition                                                    | Signal                                                                         |
|--------------------------------------------------------------|--------------------------------------------------------------------------------|
| No token is sent                                             | the call is refused as unauthenticated; the provider is never called           |
| The text is absent or only whitespace                        | rejected as an invalid argument; no call to the provider is made               |
| No categories are sent                                       | rejected as an invalid argument; no call to the provider is made               |
| A category's name or its grouping is blank                   | rejected as an invalid argument; no call to the provider is made               |
| An assumed currency is sent that ISO 4217 does not know      | rejected as an invalid argument; no call to the provider is made               |
| The provider cannot be reached, or its answer cannot be read | the call fails as unavailable — the caller may retry                           |
| The ledger refuses an expense                                | the call fails as a failed precondition — the same call would be refused again |
| The ledger cannot be reached to record an expense            | the call fails as unavailable — the caller may retry                           |
| One answer cannot be made sense of                           | none — the call succeeds and that entry is logged and skipped                  |
| Anything else fails inside the service                       | the call fails as unknown, with no internal detail in the failure              |

## Compatibility

Both sides generate from the one schema file, so a field added or renamed there reaches the caller's build
rather than its runtime.

Removing a promise breaks every caller: an order that is not the user's, an unusable entry arriving as a failed
call, or an empty answer that stops meaning the turn was acted on would each force the caller to be rewritten.
Widening what the service acts on does not.

The empty answer is what leaves room to grow: reporting per-entry outcomes, or a reply for the user, means a
response body where there is none today, and every caller reads it or ignores it as it chooses.
