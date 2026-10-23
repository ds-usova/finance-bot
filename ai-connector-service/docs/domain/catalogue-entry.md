# Catalogue entry

One of the person's groupings, with the categories filed under it.

## Invariants

| Field        | Bound                                       |
|--------------|---------------------------------------------|
| `grouping`   | mandatory, non-blank                        |
| `categories` | at least one; each non-blank; no name twice |

The categories keep the order the caller sent them in.

## Made of / held by

Names only. Sent with each request, never stored.

- [Category choice](category-choice.md) — one per category the entry lists.
- [Intent extraction](../contracts/in/intent-extraction.md) — where the caller sends one per grouping.
- [Record the spending a user's message names](../usecases/extract-intents.md) — in the classifier mode, chooses
  each expense's category from the entries and falls back on the catch-all's.
- [Category classifier](../contracts/out/classifier.md) — offered every category of every entry as an option.
- [AI provider](../contracts/out/ai-provider.md#the-category-lookup) — a lookup is answered with one entry's
  categories.
