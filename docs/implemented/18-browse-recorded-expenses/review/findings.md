# Review: 18 Browse Recorded Expenses

The task delivered expense browsing, the category tree and the grouping list over the web API, plus the page that
reads them. Everything below is open — a defect the task confirmed and did not fix, or a gap nothing in the
design ever named. The [evidence](evidence.md) beside this file covers the rest.

## ledger-service

- ~~**`WebExceptionHandler.onIllegalArgument` is a catch-all wearing a specific message**~~ — resolved, by
  removing the handler rather than narrowing it. `ExpenseWebMapper` translates the one `IllegalArgumentException`
  a caller could provoke — `ExpenseStatus.valueOf` on an unknown status — into `InvalidExpenseFilterException`,
  which was already mapped to 400. Every other `IllegalArgumentException` under `bot.finance.adapter.web` now
  falls to the 500 handler, which is what a defect deserves. The wire message is unchanged.
  `ledger-service/plan.md · B5`.

## web-app

- **An amount is divided by 100 whichever currency it is in** — `ExpenseList.tsx`'s `decimalAmount` hard-codes
  two fraction digits. The ledger takes the exponent from the currency instead
  (`Money.java:19`, `Currency.getDefaultFractionDigits`), so a JPY amount renders a hundred times too small and a
  KWD one ten times too large. The response already carries the currency beside the amount, so nothing is missing
  from it. No plan item: every scenario used the fixture's currency.

- ~~**Nothing reaches the second page**~~ — resolved. `Pager` in `components/` renders the way on and the way
  back, stepping by the `limit` the ledger answered with rather than one the page chose. `ExpensesPage` holds the
  offset in the filter it reads with, so changing a filter returns to the first page. The count moved out of
  `ExpenseList` onto the pager, beside the control that acts on it. Recorded on
  [the use case](../../../../web-app/docs/usecases/browse-recorded-expenses.md); no plan item, since the design
  settled offset paging as a decision and no scenario asked for the control.

- ~~**A slow read overwrites the answer to a newer filter**~~ — resolved. The listing effect now returns a
  cleanup that sets a `cancelled` flag, the same idiom `AuthProvider` uses, and neither the answer nor the
  refusal of a cancelled read touches state. So a stale `401` no longer calls `sessionExpired` after a later read
  has succeeded. The refactor pass could only record it, since a fix changes observable behaviour and that pass
  preserves it. `web-app/plan.md · B10`.

- ~~**`ExpensesPage` and `LoginPage` render a failure identically**~~ — resolved. `ErrorBanner` in `components/`
  now holds the `role="alert"` paragraph and both pages render it. The extraction needed `LoginPage.tsx`, which
  sat outside this task's diff, which is why the refactor pass could only record it. `web-app/plan.md · B11`.

- ~~**A scratch test in `build/` joins the module's suite**~~ — resolved. `vite.config.ts` now sets
  `test.exclude` to Vitest's own defaults plus `build/**`. Confirmed both ways with a throwaway test under
  `build/scratch/`: collected before the entry, not collected after. `web-app/plan.md · B11`.
