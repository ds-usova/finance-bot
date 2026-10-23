# A person browsing their ledger from a browser — the browse API (HTTP)

A signed-in person opens the web app, reads what the ledger holds for them, accepts the spending they choose to,
and files spending as text. A signed-in caller can also throw chosen pending spending away. No operation names
whose ledger it is.

- **Counterpart:** [the Web App](../../../../web-app/docs/contracts/out/ledger-browse-api.md), running in a
  person's browser
- **Transport:** HTTP under `/api/v1`, on the service's own port, reached from the same origin as the page
- **Specification:** [`openapi/ledger-api.yaml`](../../../../openapi/ledger-api.yaml) — every parameter, every
  field, every refusal and an example of each answer

The session that admits every operation is [the session API](web-session-api.md)'s, a separate interface on the
same transport.

## Operations

| Tag           | Operation               | Answers with                                                                             | Implemented by                                                                     | Called by                                                                                 |
|---------------|-------------------------|------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------|
| `expenses`    | `listExpenses`          | one page of the person's recorded spending and pending proposals, and each day's figures | [Browse a person's expenses](../../usecases/browse-expenses.md)                    | [Browse recorded expenses](../../../../web-app/docs/usecases/browse-recorded-expenses.md) |
| `expenses`    | `listExpenses`          | the same                                                                                 | [Browse a person's expenses](../../usecases/browse-expenses.md)                    | [Accept pending expenses](../../../../web-app/docs/usecases/accept-pending-expenses.md)   |
| `expenses`    | `listExpenses`          | the same                                                                                 | [Browse a person's expenses](../../usecases/browse-expenses.md)                    | [File expenses as text](../../../../web-app/docs/usecases/file-expenses-as-text.md)       |
| `expenses`    | `acceptExpenses`        | how many of the named proposals became expenses, and how many named nothing              | [Accept the proposals a person chose](../../usecases/accept-chosen-proposals.md)   | [Accept pending expenses](../../../../web-app/docs/usecases/accept-pending-expenses.md)   |
| `expenses`    | `discardExpenses`       | how many of the named proposals were thrown away, and how many named nothing             | [Discard the proposals a person chose](../../usecases/discard-chosen-proposals.md) | —                                                                                         |
| `expenses`    | `changeExpenseCategory` | the entry as it now stands, under a different category                                   | [Change an entry's category](../../usecases/change-an-expense-category.md)         | [Browse recorded expenses](../../../../web-app/docs/usecases/browse-recorded-expenses.md) |
| `categories`  | `listCategories`        | the person's categories, each naming its grouping, optionally under one                  | [Browse a person's categories](../../usecases/browse-categories.md)                | [Browse recorded expenses](../../../../web-app/docs/usecases/browse-recorded-expenses.md) |
| `groupings`   | `listGroupings`         | the person's groupings                                                                   | [Browse a person's groupings](../../usecases/browse-groupings.md)                  | [Browse recorded expenses](../../../../web-app/docs/usecases/browse-recorded-expenses.md) |
| `preferences` | `readPreferences`       | the person's settings, as they currently stand                                           | [Read a person's preferences](../../usecases/read-the-preferences.md)              | [Set a default currency](../../../../web-app/docs/usecases/set-a-default-currency.md)     |
| `preferences` | `replacePreferences`    | those settings as they stand once the whole resource has been replaced                   | [Replace a person's preferences](../../usecases/replace-the-preferences.md)        | [Set a default currency](../../../../web-app/docs/usecases/set-a-default-currency.md)     |
| `messages`    | `fileWrittenMessage`    | the text, filed and waiting to be read, before any reading starts                        | [File a text for reading](../../usecases/file-written-message.md)                  | [File expenses as text](../../../../web-app/docs/usecases/file-expenses-as-text.md)       |
| `messages`    | `readWrittenMessage`    | one filed text as it now stands, and once read, what it came to and its days             | [Read what became of a filed text](../../usecases/read-written-message.md)         | [File expenses as text](../../../../web-app/docs/usecases/file-expenses-as-text.md)       |
| `messages`    | `listWrittenMessages`   | the person's text still being read, if any                                               | [List a person's texts being read](../../usecases/browse-written-messages.md)      | [File expenses as text](../../../../web-app/docs/usecases/file-expenses-as-text.md)       |

## Compatibility

The specification is shared, and neither side owns it. This service generates its endpoints from it, the browser
generates its response types from the same file, so one change reaches both at build time.

Adding a filter parameter or a field on an entry costs a browser nothing. Removing a parameter, narrowing a
bound, or turning an optional field required breaks the page.

Adding a value to a filed text's state or outcome breaks the page, which acts on each value by name.

Narrowing how many ids one acceptance may carry breaks the page too: it ticks a whole day at once, and a day can
hold as many entries as a page does.
