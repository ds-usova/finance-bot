# Ledger Service — the browse API

Where the web app gets what it lists and what a person has configured, and where it sends what they ticked and
the currency they chose.

- **Counterpart:** [the ledger's browse API](../../../../ledger-service/docs/contracts/in/web-browse-api.md),
  which owns what each request takes and answers with
- **Specification:** [`openapi/ledger-api.yaml`](../../../../openapi/ledger-api.yaml), from which this module
  generates the types it declares the calls against
- **Transport:** HTTP under `/api/v1`, on the same origin as the page

[The session API](ledger-session-api.md) is a separate interface on the same transport.

## What It Sends, and When

One endpoint serves more than one of this module's use cases, so the endpoint leads and the caller follows.
[The counterpart](../../../../ledger-service/docs/contracts/in/web-browse-api.md) owns what each one takes and
answers with.

| Endpoint                               | Called by                                                              | When                                    | It sends                         |
|----------------------------------------|------------------------------------------------------------------------|-----------------------------------------|----------------------------------|
| `GET /api/v1/expenses`                 | [Browse recorded expenses](../../usecases/browse-recorded-expenses.md) | the page opens, or a filter changes     | the filter, where one is set     |
| `GET /api/v1/expenses`                 | [Browse recorded expenses](../../usecases/browse-recorded-expenses.md) | a refile lands under a category filter  | the day that entry sat on        |
| `GET /api/v1/expenses`                 | [Accept pending expenses](../../usecases/accept-pending-expenses.md)   | an acceptance is answered               | the days it touched              |
| `GET /api/v1/categories`               | [Browse recorded expenses](../../usecases/browse-recorded-expenses.md) | the page opens                          | —                              |
| `GET /api/v1/groupings`                | [Browse recorded expenses](../../usecases/browse-recorded-expenses.md) | the page opens                          | —                              |
| `PATCH /api/v1/expenses/{status}/{id}` | [Browse recorded expenses](../../usecases/browse-recorded-expenses.md) | a row is refiled under another category | the category id, as a JSON Patch |
| `POST /api/v1/expenses/acceptances`    | [Accept pending expenses](../../usecases/accept-pending-expenses.md)   | the person accepts what is ticked       | the ids of the ticked entries    |
| `GET /api/v1/preferences`              | [Set a default currency](../../usecases/set-a-default-currency.md)     | the settings page opens                 | —                              |
| `PUT /api/v1/preferences`              | [Set a default currency](../../usecases/set-a-default-currency.md)     | a picked currency is saved              | the picked code                  |

The listing carries only the filter fields that are set. An unset field is the ledger's default rather than an
explicit value. The period is the exception: `from` and `to` go together or not at all, so setting one day alone
sends nothing until the other follows. The category read never narrows by grouping, and asks for the whole tree
once.

The read back after an acceptance carries the status and the category the page holds when the answer arrives,
its own period spanning the days that were touched, no offset, and a limit of the bound the specification sets
on one acceptance.

Every request carries cookies. Every write carries the CSRF token, read back from the cookie the ledger set it
in.

## What It Does With the Answer

- **The listing**: rendered in the order it came, cut into days in the page, with its total driving the pager.
- **Every figure**: shown as the ledger rendered it, never parsed or reformatted here.
- **The day figures**: matched to the day sections by the UTC day each names. A day answered no figure shows
  none.
- **The categories**: held for the life of the page. They name each row's category and fill the category control.
- **The groupings**: order the sections of the category list and name their headings. A heading cannot be chosen,
  so a grouping is never sent. This read alone fixes the order the groupings appear in.
- **The acceptance**: neither count is shown as a number of its own. Ids that matched nothing are reported to the
  person in words. The days the ticked entries sat on are read again and merged back into the page on screen.
- **The replacement's answer**: required, but its content is not read. The save is reported from the call
  succeeding.
- Nothing is cached across page loads. A reload reads all of them again.

## When the Call Fails

| The failure                  | What the person gets                                                        |
|------------------------------|-----------------------------------------------------------------------------|
| A refusal carrying a body    | the ledger's own wording, from the body's message                           |
| A body empty or not JSON     | wording this module synthesizes from the method, the path and the status    |
| A 401 on any request         | nothing: the page treats it as an expired session and sends them to sign in |
| A 403 on the acceptance      | the ledger's wording, shown like any other refusal, and the ticks stand     |
| A refusal on the replacement | the ledger's wording, beside the save control, and the picked code stands   |
| The network                  | the browser's own words, never the ledger's                                 |
| Any other refusal            | the wording as it came, with whatever is already on screen left in place    |

Nothing is retried. A person who wants another attempt changes a filter or reloads.

## Compatibility

A field the specification drops or renames fails this module's build.
