# A person browsing their ledger from a browser — the browse API (HTTP)

A signed-in person opens the web app and the page reads what the ledger holds for them. Three reads cross this
boundary: one page of their spending, and the two levels of the tree that spending is filed under. Nothing is
written, and no operation names whose ledger it is.

- **Counterpart:** [the Web App](../../../../web-app/docs/contracts/out/ledger-browse-api.md), running in a
  person's browser
- **Transport:** HTTP under `/api/v1`, on the service's own port, reached from the same origin as the page
- **Specification:** [`openapi/ledger-api.yaml`](../../../../openapi/ledger-api.yaml) — every parameter, every
  field, every refusal and an example of each answer

The session that admits all three is [the session API](web-session-api.md)'s, a separate interface on the same
transport. The caller on the other side of all three is
[Browse recorded expenses](../../../../web-app/docs/usecases/browse-recorded-expenses.md).

## Operations

| Tag          | Operation        | Answers with                                                                             | Implemented by                                                      |
|--------------|------------------|------------------------------------------------------------------------------------------|---------------------------------------------------------------------|
| `expenses`   | `listExpenses`   | one page of the person's recorded spending and pending proposals, and each day's figures | [Browse a person's expenses](../../usecases/browse-expenses.md)     |
| `categories` | `listCategories` | the person's categories, each naming its grouping, optionally under one                  | [Browse a person's categories](../../usecases/browse-categories.md) |
| `groupings`  | `listGroupings`  | the person's groupings                                                                   | [Browse a person's groupings](../../usecases/browse-groupings.md)   |

## Semantics

What the specification cannot say.

- **Every read is scoped to the signed-in person.** No operation takes a user identifier, and no parameter widens
  that scope. An id belonging to somebody else matches nothing rather than being refused.
- **Reads carry no CSRF token and need none.** They still hand out the token cookie, which is what
  [the session API](web-session-api.md)'s writes require.
- **Rendering is English for every caller.** No locale is negotiated, and no request offers one.
- Every read is repeatable and stores nothing.

## Compatibility

The specification is shared. This service generates its endpoints from it and the browser generates its response
types from the same file, so one change reaches both at build time and neither side owns it.

Adding a filter parameter or a field on an entry costs a browser nothing. Removing a parameter, narrowing a
bound, or turning an optional field required breaks the page.
