# Ledger Service — intent extraction (gRPC)

A line a user wrote crosses this boundary in, and the actions it asks for cross back out. The caller decides
nothing about the text; it sends what the user said, the categories that user already has, and — optionally —
the currency to assume when an amount is stated without one.

- **Counterpart:** the Ledger Service, which executes the returned actions against the user's ledger
- **Transport:** gRPC
- **Schema:** [`proto/intent_extraction.proto`](../../../../proto/intent_extraction.proto), shared at the
  repository root so both sides read the same file

## Operations

| Operation       | Purpose                                                    | Used by                                                                                                          |
|-----------------|-------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------|
| Extract intents | reads the actions a user's message asks for, in their order | [Extract the intents in a user's message](../../usecases/extract-intents.md); on the caller's side, the Ledger Service, which has no page yet |

## Semantics

The answer is a list, in the order the user expressed the actions — nothing is reordered, merged, or dropped.
A caller executing the list in sequence may therefore meet an expense whose category is created later in the
same list, or one whose category the same list deletes; reconciling that is the caller's job.

The answer is never empty. A message asking for nothing yields a single entry marked unknown, carrying the
reason, so the caller has one shape to read rather than two.

Unknown is per entry, not per message: one unusable part leaves the rest actionable, with the bad entry in
place saying what was wrong with it. Unknown is a classification outcome and never a failure — the call
succeeds. The schema also carries a never-set value for the action, which this service never produces.

The categories the caller sends are a closed set and must be non-empty. An expense is filed under one of them,
matched ignoring case and returned in the caller's spelling, or under a category the same message asks to
create. The caller includes a catch-all, so a fit always exists; the service never proposes a category of its
own. That constraint applies to the category an expense is filed under, not to the category a user asks to
create — creating one is exactly how something absent from the set gets named.

The assumed currency is applied here, not by the model, and only where the user stated an amount with no
currency. It is accepted in any casing and must be a code ISO 4217 knows.

Money crosses as whole minor units plus an ISO 4217 code — `1250` and `"EUR"` for €12.50. The exponent comes
from the currency, so JPY scales by zero and EUR by two. An amount is exact at every step and is never a
binary floating-point number, so a caller reading one into one loses what the user said.

The same text sent twice is extracted twice: nothing is remembered between calls, so a repeated request is
never a duplicate the service could recognize.

## Failures

| Condition                                                                | Signal                                                                  |
|--------------------------------------------------------------------------|-------------------------------------------------------------------------|
| The text is absent or only whitespace                                    | rejected as an invalid argument; no call to the provider is made        |
| No categories are sent, or one of them is blank                          | rejected as an invalid argument; no call to the provider is made        |
| An assumed currency is sent that ISO 4217 does not know                  | rejected as an invalid argument; no call to the provider is made        |
| The provider cannot be reached, or its answer cannot be read             | the call fails as unavailable — the caller may retry                    |
| One answer cannot be made sense of                                       | none — the call succeeds and that entry is unknown, with the reason     |
| Anything else fails inside the service                                   | the call fails as unknown, with no internal detail in the failure       |

## Compatibility

Both sides generate from the one schema file, so a field added or renamed there reaches the caller's build
rather than its runtime. A caller must still tolerate an action or an intent kind it does not recognize: the
answer is a list of independent entries and a new kind of entry may appear among ones it understands.

Removing a promise breaks every caller: an answer that could be empty, an order that is not the user's, or an
unknown that arrives as a failed call would each force the caller to be rewritten. Widening what the service
extracts — more intent kinds, more fields on one — does not.
