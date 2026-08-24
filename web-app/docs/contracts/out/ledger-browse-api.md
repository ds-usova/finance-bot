# Ledger Service — the browse API

Where the web app gets what it lists and what a person has configured, and where it sends what they ticked and
the currency they chose.

- **Counterpart:** [the ledger's browse API](../../../../ledger-service/docs/contracts/in/web-browse-api.md),
  which owns what each request takes and answers with
- **Specification:** [`openapi/ledger-api.yaml`](../../../../openapi/ledger-api.yaml), from which this module
  generates the types it declares the calls against
- **Transport:** HTTP on `/api/v1/expenses`, `/api/v1/expenses/acceptances`, `/api/v1/categories`,
  `/api/v1/groupings` and `/api/v1/preferences`, on the same origin as the page
- **Used by:** [Browse recorded expenses](../../usecases/browse-recorded-expenses.md)
- **Used by:** [Accept pending expenses](../../usecases/accept-pending-expenses.md)
- **Used by:** [Set a default currency](../../usecases/set-a-default-currency.md)

[The session API](ledger-session-api.md) is a separate interface on the same transport.

## What It Sends, and When

| When                                   | It sends                                               | Answered by                                                                                    |
|----------------------------------------|--------------------------------------------------------|------------------------------------------------------------------------------------------------|
| The expenses page opens                | a read of the listing, carrying no filter              | [Browse expenses](../../../../ledger-service/docs/usecases/browse-expenses.md)                 |
| The expenses page opens                | a read of every category                               | [Browse categories](../../../../ledger-service/docs/usecases/browse-categories.md)             |
| The expenses page opens                | a read of every grouping                               | [Browse groupings](../../../../ledger-service/docs/usecases/browse-groupings.md)               |
| A filter other than the period changes | a read of the listing, carrying the filter             | [Browse expenses](../../../../ledger-service/docs/usecases/browse-expenses.md)                 |
| The period is completed or cleared     | a read of the listing, carrying the filter             | [Browse expenses](../../../../ledger-service/docs/usecases/browse-expenses.md)                 |
| One day of the period is set alone     | nothing                                                | —                                                                                            |
| The person accepts what is ticked      | the ids of the ticked entries                          | [Accept chosen proposals](../../../../ledger-service/docs/usecases/accept-chosen-proposals.md) |
| An acceptance is answered              | a read of the listing, narrowed to the days it touched | [Browse expenses](../../../../ledger-service/docs/usecases/browse-expenses.md)                 |
| The settings page opens                | a read of the preferences                              | [Read the preferences](../../../../ledger-service/docs/usecases/read-the-preferences.md)       |
| A currency is picked                   | nothing                                                | —                                                                                            |
| A picked currency is saved             | the picked code, replacing the preferences             | [Replace the preferences](../../../../ledger-service/docs/usecases/replace-the-preferences.md) |

The listing carries only the filter fields that are set. An unset field is the ledger's default rather than an
explicit value. The period is the exception: `from` and `to` go together or not at all. The category read never
narrows by grouping, and asks for the whole tree once.

The read back after an acceptance carries the status and the category the page holds when the answer arrives,
its own period spanning the days that were touched, no offset, and a limit of the bound the specification sets
on one acceptance.

Every request carries cookies. The acceptance and the replacement are the writes, and each carries the CSRF
token, read back from the cookie the ledger set it in.

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
