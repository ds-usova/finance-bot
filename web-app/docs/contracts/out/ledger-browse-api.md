# Ledger Service — the browse API

Where the web app gets what it lists. It reads the person's expenses, and the category tree the filter offers and
each row is named from.

- **Counterpart:** [the ledger's browse API](../../../../ledger-service/docs/contracts/in/web-browse-api.md),
  which owns what each request takes and answers with
- **Specification:** [`openapi/ledger-api.yaml`](../../../../openapi/ledger-api.yaml), from which this module
  generates the types it declares the calls against
- **Transport:** HTTP on `/api/v1/expenses`, `/api/v1/categories` and `/api/v1/groupings`, on the same origin as
  the page
- **Used by:** [Browse recorded expenses](../../usecases/browse-recorded-expenses.md)

The session this module holds is a different interface on the same transport —
[the session API](ledger-session-api.md).

## What It Sends, and When

| When                                   | It sends                                   | Answered by                                                                        |
|----------------------------------------|--------------------------------------------|------------------------------------------------------------------------------------|
| The expenses page opens                | a read of the listing, carrying no filter  | [Browse expenses](../../../../ledger-service/docs/usecases/browse-expenses.md)     |
| The expenses page opens                | a read of every category                   | [Browse categories](../../../../ledger-service/docs/usecases/browse-categories.md) |
| The expenses page opens                | a read of every grouping                   | [Browse groupings](../../../../ledger-service/docs/usecases/browse-groupings.md)   |
| A filter other than the period changes | a read of the listing, carrying the filter | [Browse expenses](../../../../ledger-service/docs/usecases/browse-expenses.md)     |
| The period is completed or cleared     | a read of the listing, carrying the filter | [Browse expenses](../../../../ledger-service/docs/usecases/browse-expenses.md)     |
| One day of the period is set alone     | nothing                                    | —                                                                                  |

The listing carries only the filter fields that are set, so a field left unset is the ledger's default rather than
an explicit value. The period is the exception: `from` and `to` go together or not at all, because the ledger
refuses a lone day. The category read never narrows by grouping — this module asks for the whole tree once and
narrows it in the page.

Every request carries cookies. None is a write, so none carries the CSRF token.

## What It Does With the Answer

- **The listing**: rendered in the order it came, cut into days in the page, with its total driving the pager.
- **The categories**: held for the life of the page. They name each row's category and fill the category control.
- **The groupings**: fill the grouping control, which narrows the categories offered and is never sent.
- Nothing is cached across page loads. A reload reads all three again.

## When the Call Fails

| The failure               | What the person gets                                                        |
|---------------------------|-----------------------------------------------------------------------------|
| A refusal carrying a body | the ledger's own wording, from the body's message                           |
| A body empty or not JSON  | wording this module synthesizes from the method, the path and the status    |
| No session                | nothing: the page treats it as an expired session and sends them to sign in |
| The network               | the browser's own words, never the ledger's                                 |
| Any other refusal         | the wording as it came, with whatever is already on screen left in place    |

Nothing is retried. A person who wants another attempt changes a filter or reloads.

## Compatibility

The types the calls are declared against are generated from the specification on every build. A field the
specification drops or renames fails this module's build rather than reaching a person as a blank cell.
