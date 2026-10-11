# Ledger Service — the browse API

Where the web app gets what it lists and what a person has configured, and where it sends what they ticked, the
currency they chose and the texts they file.

- **Counterpart:** [the ledger's browse API](../../../../ledger-service/docs/contracts/in/web-browse-api.md),
  which owns what each request takes and answers with
- **Specification:** [`openapi/ledger-api.yaml`](../../../../openapi/ledger-api.yaml), from which this module
  generates the types it declares the calls against
- **Transport:** HTTP under `/api/v1`, on the same origin as the page

[The session API](ledger-session-api.md) is a separate interface on the same transport.

## What It Sends, and When

| Endpoint                            | Called by                                                              | When                                                | It sends                          |
|-------------------------------------|------------------------------------------------------------------------|-----------------------------------------------------|-----------------------------------|
| `GET /api/v1/expenses`              | [Browse recorded expenses](../../usecases/browse-recorded-expenses.md) | the page opens, or a filter changes                 | the filter, where one is set      |
| `GET /api/v1/expenses`              | [Browse recorded expenses](../../usecases/browse-recorded-expenses.md) | a refile lands under a category filter              | the day that entry sat on         |
| `GET /api/v1/expenses`              | [Accept pending expenses](../../usecases/accept-pending-expenses.md)   | an acceptance is answered                           | the days it touched               |
| `GET /api/v1/expenses`              | [File expenses as text](../../usecases/file-expenses-as-text.md)       | a filed text's outcome names days the page can show | those days                        |
| `GET /api/v1/categories`            | [Browse recorded expenses](../../usecases/browse-recorded-expenses.md) | the page opens                                      | —                                 |
| `GET /api/v1/groupings`             | [Browse recorded expenses](../../usecases/browse-recorded-expenses.md) | the page opens                                      | —                                 |
| `PATCH /api/v1/expenses/{id}`       | [Browse recorded expenses](../../usecases/browse-recorded-expenses.md) | a row is refiled under another category             | the category id, as a JSON Patch  |
| `POST /api/v1/expenses/acceptances` | [Accept pending expenses](../../usecases/accept-pending-expenses.md)   | the person accepts what is ticked                   | the ids of the ticked entries     |
| `GET /api/v1/messages`              | [File expenses as text](../../usecases/file-expenses-as-text.md)       | the expenses page opens                             | the state `READING`               |
| `GET /api/v1/messages`              | [File expenses as text](../../usecases/file-expenses-as-text.md)       | a filing is refused as one already being read       | the state `READING`               |
| `POST /api/v1/messages`             | [File expenses as text](../../usecases/file-expenses-as-text.md)       | the person files the text in the write-in line      | the text, as written              |
| `GET /api/v1/messages/{id}`         | [File expenses as text](../../usecases/file-expenses-as-text.md)       | every 5 seconds while a text is being read          | the id filing or listing answered |
| `GET /api/v1/preferences`           | [Set a default currency](../../usecases/set-a-default-currency.md)     | the settings page opens                             | —                                 |
| `PUT /api/v1/preferences`           | [Set a default currency](../../usecases/set-a-default-currency.md)     | a picked currency is saved                          | the picked code                   |

The listing carries only the filter fields that are set. The period is the exception: `from` and `to` go together
or not at all. Setting one day alone sends nothing until the other follows. The category read never narrows by
grouping. It asks for the whole tree once.

The read back after an acceptance or a filed text's outcome carries the status and the category the page holds
when the answer arrives, its own period spanning the days to read, no offset, and a limit of the bound the
specification sets on one acceptance.

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
- **The refiled row**: replaced in place. Under a category filter the day it sits on is read again instead.
- **The acceptance**: neither count is shown as a number of its own. Ids that matched nothing are reported to the
  person in words. The days the ticked entries sat on are read again and merged back into the page on screen.
- **The replacement's answer**: required, but its content is not read. The save is reported from the call
  succeeding.
- **The texts being read**: the first is asked after.
- **The filing**: only its id is read.
- **The text asked after**: `READING` is asked again. Any other state ends the asking, and its outcome decides
  what [the line shows](../../usecases/file-expenses-as-text.md#outcomes). Its days name what is read back. Its
  text is not read.
- Nothing is cached across page loads.

## When the Call Fails

| The failure                                 | What the person gets                                                                   |
|---------------------------------------------|----------------------------------------------------------------------------------------|
| A refusal carrying a body                   | the calling surface's fixed wording, not the body's message                            |
| A body empty or not JSON                    | the calling surface's fixed wording                                                    |
| A 401 on any request                        | nothing: the page treats it as an expired session and sends them to sign in            |
| A 403 on the acceptance                     | the listing's fixed wording, shown like any other refusal, and the ticks stand         |
| A refusal on a refile                       | the row's own fixed wording, under that row, and its category stands                   |
| A 404 on a refile                           | the same, and the day it sat on is read again                                          |
| A refusal on the replacement                | the settings page's fixed wording, beside the save control, and the picked code stands |
| A 429 on a filing                           | no refusal: the text being read is asked for and shown instead                         |
| A refusal on a filing                       | the write-in line's fixed wording beneath it, and the text stays in the line           |
| A filing's answer empty or not JSON         | the browser's own words beneath the line, and the text stays in it                     |
| A 404 asking after a text                   | the line's "could not be read" refusal, and the text returns to the line               |
| Any other failure asking after a text       | nothing: it is asked again 5 seconds later                                             |
| A failure asking which texts are being read | nothing: the line becomes editable                                                     |
| The network                                 | the browser's own words, never the ledger's                                            |
| Any other refusal                           | the calling surface's fixed wording, with whatever is already on screen left in place  |

Nothing else is retried.

## Compatibility

A field the specification drops or renames fails this module's build.
