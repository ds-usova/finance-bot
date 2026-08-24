# Review: Set a default currency

**1 bug, fixed · 2 refactoring candidates open, 11 manual checks.**

## Bug

**Fixed** in
[41-an-unrecognised-stored-currency-code](../../41-an-unrecognised-stored-currency-code/bug.md): the row is read
inside the `try` and the `CurrencyCode` built outside it, so only the query is guarded as a store failure. A code
the domain refuses is answered as nothing chosen — the state the use case's **Nothing chosen** outcome already
describes — and logged as a warning, rather than reaching the caller as a refusal they sent nothing to cause.

**`ledger-service` — a stored currency code the JDK no longer recognises is answered as a store outage**

- **Given** a `user_preference` row whose `default_currency_code` is not a code `Currency.getInstance` accepts —
  reachable by a write that bypasses the endpoint, since the column is `VARCHAR(3)` with no check constraint, or by
  a code a later JDK stops recognising
- **When** that person reads or replaces their preferences, or sends a message
- **Then** the caller is told the value was refused — 400 by the exception table, or the turn simply runs with no
  default
- **Actual** the `CurrencyCode` is constructed inside `catch (RuntimeException e)`, so the compact constructor's
  `InvalidMoneyException` is wrapped into `PersistenceFailedException` and the caller gets 503 — the store was
  available and answered
- **Fix** move the `CurrencyCode` construction out of the `try`, so only the query is guarded · `unverified` —
  found by reading, and proving it needs a test the refactor pass's unchanged-count guardrail forbade ·
  `UserPreferenceRepositoryAdapter.findDefaultCurrency`

## Refactoring candidate

| #  | Status | Module           | What                                                                                                 | Why                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
|----|--------|------------------|------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| R1 | open   | `ledger-service` | Loosen the log assertion pinning an SLF4J overload, and restore the class's own three-argument idiom | `verify(log).warn(anyString(), any())` grips the two-argument overload rather than the fact of the warning, so `readDefaultCurrency` formats eagerly while its neighbour `storeReport` ten lines below does not. The refactor pass may not touch assertions, so the test holds the production code in a shape the module does not otherwise use                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| R2 | open   | `web-app`        | Show module-owned wording for a refused call, instead of the ledger's own message                    | The browser puts the ledger's `message` on screen at five call sites, and falls back to three English literals hardcoded in the pages. Those strings are composed in Java and never reach the catalogue, so a translated surface carries untranslatable text — the same reason D1 kept the currency list in the browser. `LoginPage` already does it the other way, showing `t('signIn.refused')`. Beyond the module: `ledger-api.yaml` describes `Problem.message` as "words a caller can show a person", so that description and both browse-API contract pages move with the code, and the design's F23/A13/A15 stop describing what is built. Four things need deciding — whether the ledger's message goes to the console for diagnosis, whether any refusal keeps specificity, whether wording is per-surface or shared, and whether `Problem.message` stays in the contract for other callers. Only the last of those can reach `ledger-service`: if the answer strips user-facing prose from the contract, `WebExceptionHandler`'s messages become identifiers |

## Manual test

Every check below is `web-app`, because it is the module whose suite cannot see position, wrapping, colour or
scrolling. The screens and states come from the design's F25, agreed before the code existed.

**[ ] `/settings` at the shell's full width**

- **Given** a signed-in person with a stored currency, on a wide window, in both themes
- **When** the page renders
- **Then** the picker's panel sits at the field width `ExpenseFilters` gives a field, not stretched across the shell

**[ ] `/settings` at the shell's narrowest width**

- **Given** the same page, at the narrowest width the shell supports
- **When** the page renders
- **Then** nothing overflows the viewport horizontally

**[ ] The trigger reads as a choice when a currency is stored**

- **Given** a person whose preference holds `EUR`, the picker closed
- **When** it renders
- **Then** the trigger reads as a made choice rather than as an empty field

**[ ] The trigger reads as unset when nothing is stored**

- **Given** a person with no preference row, the picker closed
- **When** it renders
- **Then** the trigger reads as an empty field rather than as a made choice

**[ ] The list's order reads by name, with the code trailing**

- **Given** the picker open and unscrolled
- **When** the person looks down the entries
- **Then** the names lead the eye and the codes trail, in name order

**[ ] The list is visibly bounded before it is scrolled**

- **Given** the picker open over all 155 codes
- **When** the person looks at it without scrolling
- **Then** the bound is apparent from the popover itself, before scrolling reveals it

**[ ] The longest entry gives way predictably**

- **Given** the picker open on its longest currency name, and that currency chosen so the trigger carries it too
- **When** the person reads the trigger and the row
- **Then** the trigger truncates and the row wraps, and in both the code stays readable

**[ ] An exact code search ranks first**

- **Given** the picker open
- **When** the person types `usd`
- **Then** `US Dollar (USD)` stands at the top of the narrowed list

**[ ] The empty search state is legible**

- **Given** the picker open
- **When** the person types a query matching no currency
- **Then** the empty-list wording is readable in both themes

**[ ] The header does not wrap at its narrowest**

- **Given** a signed-in person at the narrowest width, with the linked wordmark, the gear, the theme control and
  the sign-out control all present
- **When** the header renders
- **Then** nothing in it wraps to a second line

**[ ] A refused save reads as belonging to the save control**

- **Given** the page with a differing currency picked, and the ledger refusing the write
- **When** the person saves
- **Then** the refusal's wording reads as attached to the save control rather than to the page
