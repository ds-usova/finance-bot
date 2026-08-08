# A person browsing their ledger from a browser — the browse API (HTTP)

A signed-in person opens the web app and the page reads what the ledger holds for them. Three reads cross this
boundary: one page of their spending, and the two levels of the tree that spending is filed under. Nothing is
written, and no operation names whose ledger it is.

- **Counterpart:** [the Web App](../../../../web-app/docs/contracts/out/ledger-browse-api.md), running in a
  person's browser
- **Transport:** HTTP under `/api/v1`, on the service's own port, reached from the same origin as the page
- **Schema:** [`openapi/paths/expenses.yaml`](../../../../openapi/paths/expenses.yaml),
  [`openapi/paths/categories.yaml`](../../../../openapi/paths/categories.yaml),
  [`openapi/paths/groupings.yaml`](../../../../openapi/paths/groupings.yaml), with
  [`openapi/components/schemas/expense.yaml`](../../../../openapi/components/schemas/expense.yaml) and
  [`openapi/components/schemas/category.yaml`](../../../../openapi/components/schemas/category.yaml)

The session that admits all three is [the session API](web-session-api.md)'s, a separate interface on the same
transport. The caller on the other side of all three is
[Browse recorded expenses](../../../../web-app/docs/usecases/browse-recorded-expenses.md).

## Operations

| Operation       | Purpose                                                                 | Used by                                                             |
|-----------------|-------------------------------------------------------------------------|---------------------------------------------------------------------|
| List expenses   | one page of the person's recorded spending and pending proposals        | [Browse a person's expenses](../../usecases/browse-expenses.md)     |
| List categories | the person's categories, each naming its grouping, optionally under one | [Browse a person's categories](../../usecases/browse-categories.md) |
| List groupings  | the person's groupings                                                  | [Browse a person's groupings](../../usecases/browse-groupings.md)   |

### What listing expenses takes

Every parameter is optional, and each narrows on its own. The bounds are the
[expense filter](../../domain/expense-filter.md)'s.

| Parameter    | Meaning                                         | Required                       |
|--------------|-------------------------------------------------|--------------------------------|
| `status`     | narrows to pending or to recorded               | no                             |
| `categoryId` | narrows to one category                         | no                             |
| `from`, `to` | narrows to a stretch of days, both days counted | no — both together, or neither |
| `limit`      | the page size                                   | no — a default applies         |
| `offset`     | how many matching rows to skip                  | no — zero applies              |

### What listing expenses answers with

The envelope:

| Field    | Meaning                                             |
|----------|-----------------------------------------------------|
| `items`  | the page of entries, newest first                   |
| `limit`  | the page size that was applied                      |
| `offset` | the offset that was applied                         |
| `total`  | how many rows the filter matches, ignoring the page |

Each entry:

| Field              | Meaning                                                    | Always present |
|--------------------|------------------------------------------------------------|----------------|
| `id`               | the row's id, unique within its `status` and not across it | yes            |
| `status`           | `PENDING` or `RECORDED`                                    | yes            |
| `categoryId`       | the category, as an id — no name and no grouping           | yes            |
| `description`      | what was bought                                            | yes            |
| `merchant`         | who it was bought from                                     | no             |
| `amountMinorUnits` | the amount in the currency's minor units                   | yes            |
| `currency`         | the ISO code of that currency                              | yes            |
| `createdAt`        | when the row was recorded, not when the money was spent    | yes            |

### What listing categories takes and answers with

`groupingId` narrows to one grouping, and is optional. The answer is an array, each entry carrying `id`, `name`,
`groupingId` and `groupingName`.

### What listing groupings answers with

An array, each entry carrying `id` and `name`. It takes nothing.

## Semantics

- **Every read is scoped to the signed-in person.** No operation takes a user identifier, and no parameter widens
  that scope.
- A `categoryId` or `groupingId` belonging to somebody else matches nothing. The answer is an empty page or an
  empty array, never a refusal.
- **The expense listing appends two stores.** Pending and recorded are two tables, not a column
  ([ADR 0012](../../adr/0012-a-set-of-rows-moves-between-tables-in-one-statement.md)), and the two number their
  rows independently. An `id` identifies a row only together with its `status`.
- Entries come back newest first, and two sharing an instant keep the same order on every call.
- `from` and `to` are whole days taken at UTC. A person east or west of UTC sees the day boundary shifted by
  their own offset.
- `items` and `total` are read separately. A row stored between the two makes them disagree by one.
- Accepting a proposal re-dates it to the moment it was accepted, so it can appear on two pages or on none.
- Categories and groupings are unpaged and answer the whole list. Neither takes a page size.
- Categories are ordered by grouping name, then by their own; groupings by name. A grouping holding no categories
  is still listed.
- **Reads carry no CSRF token and need none.** The chain still hands out the token cookie on them, which is what
  [the session API](web-session-api.md)'s writes require.
- Every read is repeatable and stores nothing.

## Failures

| Condition                                                             | Signal                                              |
|-----------------------------------------------------------------------|-----------------------------------------------------|
| No session cookie, or one this service did not sign                   | 401, with no body, written before the endpoint runs |
| `limit` outside its bounds, or not a number                           | 400, naming `limit` and the bound                   |
| `offset` negative, or not a number                                    | 400, naming `offset`                                |
| `status` is neither `PENDING` nor `RECORDED`                          | 400, naming `status` and the two values             |
| `from` or `to` is not a `YYYY-MM-DD` day                              | 400, naming the one at fault                        |
| One of `from` and `to` given without the other, or `to` before `from` | 400, naming both                                    |
| `categoryId` or `groupingId` is not a number                          | 400, naming it                                      |
| The session is valid but its user row is gone                         | 404, saying only that the caller is unknown         |
| The store cannot be reached                                           | 503, naming no table, statement or stack frame      |
| Anything else                                                         | 500, saying the request could not be completed      |

Every error body but the 401's is `application/json` carrying a single `message`. The 401 carries none: a filter
refuses the request before any endpoint or error mapping sees it.

A refusal reads nothing. No row is fetched for a request that fails on a parameter.

## Compatibility

The specification is shared. This service generates its endpoints from it and the browser generates its response
types from the same file, so one change reaches both at build time and neither side owns it.

The maximum page size is written twice — the [expense filter](../../domain/expense-filter.md) and the `limit`
parameter in the specification. The two have to agree, or a request the browser believes is legal is refused
here.

Adding a filter parameter or a field on an entry costs a browser nothing. Removing a parameter, narrowing a
bound, or turning an optional field required breaks the page.

The specification declares no shared component names, so the name this service generates for each response type
is derived from the operation and status code that first reaches it. Reordering the paths renames them, which
breaks this module's build rather than any caller.
