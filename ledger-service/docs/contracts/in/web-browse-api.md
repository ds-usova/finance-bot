# A person browsing their ledger from a browser — the browse API (HTTP)

A signed-in person opens the web app, reads what the ledger holds for them, and accepts the spending they choose
to. Four things cross this boundary: one page of their spending, the two levels of the tree that spending is
filed under, and the acceptance of the pending entries they ticked. No operation names whose ledger it is.

- **Counterpart:** [the Web App](../../../../web-app/docs/contracts/out/ledger-browse-api.md), running in a
  person's browser
- **Transport:** HTTP under `/api/v1`, on the service's own port, reached from the same origin as the page
- **Specification:** [`openapi/ledger-api.yaml`](../../../../openapi/ledger-api.yaml) — every parameter, every
  field, every refusal and an example of each answer

The session that admits every operation is [the session API](web-session-api.md)'s, a separate interface on the
same transport.

## Operations

| Tag          | Operation        | Answers with                                                                             | Implemented by                                                                   | Called by                                                                                 |
|--------------|------------------|------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------|
| `expenses`   | `listExpenses`   | one page of the person's recorded spending and pending proposals, and each day's figures | [Browse a person's expenses](../../usecases/browse-expenses.md)                  | [Browse recorded expenses](../../../../web-app/docs/usecases/browse-recorded-expenses.md) |
| `expenses`   | `acceptExpenses` | how many of the named proposals became expenses, and how many named nothing              | [Accept the proposals a person chose](../../usecases/accept-chosen-proposals.md) | [Accept pending expenses](../../../../web-app/docs/usecases/accept-pending-expenses.md)   |
| `categories` | `listCategories` | the person's categories, each naming its grouping, optionally under one                  | [Browse a person's categories](../../usecases/browse-categories.md)              | [Browse recorded expenses](../../../../web-app/docs/usecases/browse-recorded-expenses.md) |
| `groupings`  | `listGroupings`  | the person's groupings                                                                   | [Browse a person's groupings](../../usecases/browse-groupings.md)                | [Browse recorded expenses](../../../../web-app/docs/usecases/browse-recorded-expenses.md) |

## Semantics

What the specification cannot say.

- **Every operation is scoped to the signed-in person.** None takes a user identifier, and no parameter widens
  that scope. An id belonging to somebody else matches nothing rather than being refused — on the acceptance it
  is counted as missing, so the boundary never discloses whether a stranger's id exists.
- **Reads carry no CSRF token and need none.** They still hand out the token cookie, which is what every write
  on this boundary requires.
- **The acceptance is the only write.** It carries the token from that cookie, exactly as
  [the session API](web-session-api.md)'s writes do.
- **An acceptance is repeatable and not idempotent in its counts.** Posting the same ids again moves nothing and
  counts all of them as missing, which is a different answer to the same request rather than a failure.
- **The response is written before Telegram is told anything.** Clearing the buttons on a report the acceptance
  emptied happens afterwards, off the request thread, and a failure there never shows on this boundary.
- **Rendering is English for every caller.** No locale is negotiated, and no request offers one.
- Every read is repeatable and stores nothing.

## Compatibility

The specification is shared. This service generates its endpoints from it and the browser generates its response
types from the same file, so one change reaches both at build time and neither side owns it.

Adding a filter parameter or a field on an entry costs a browser nothing. Removing a parameter, narrowing a
bound, or turning an optional field required breaks the page.

Narrowing how many ids one acceptance may carry breaks the page too: it ticks a whole day at once, and a day can
hold as many entries as a page does.
