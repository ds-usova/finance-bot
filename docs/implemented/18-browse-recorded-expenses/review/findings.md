# Review: 18 Browse Recorded Expenses

The task delivered expense browsing, the category tree and the grouping list over the web API, plus the page that
reads them. Everything below is open — a defect the task confirmed and did not fix, or a gap nothing in the
design ever named. The [evidence](evidence.md) beside this file covers the rest.

## ledger-service

- **`WebExceptionHandler.onIllegalArgument` is a catch-all wearing a specific message** — it answers 400 with
  `"status must be PENDING or RECORDED"` to every `IllegalArgumentException` raised under
  `bot.finance.adapter.web`. Today the only source that reaches it is `ExpenseStatus.valueOf` in
  `ExpenseWebMapper`, so no failing case can be constructed; the next one to appear anywhere in that package is
  reported to the caller as a status problem rather than a 500. Narrowing it needs a scenario the plan never
  carried. `ledger-service/plan.md · B5`.

## web-app

- **An amount is divided by 100 whichever currency it is in** — `ExpenseList.tsx`'s `decimalAmount` hard-codes
  two fraction digits. The ledger takes the exponent from the currency instead
  (`Money.java:19`, `Currency.getDefaultFractionDigits`), so a JPY amount renders a hundred times too small and a
  KWD one ten times too large. The response already carries the currency beside the amount, so nothing is missing
  from it. No plan item: every scenario used the fixture's currency.

- **Nothing reaches the second page** — `ExpensesPage.tsx` sends neither `limit` nor `offset` and renders no
  pager, so a person sees the first 50 expenses and no more. The API carries both parameters
  (`openapi/components/parameters/paging.yaml`) and the envelope carries `total`, which `ExpenseList` already
  puts on screen as `Showing 50 of 138.` — the count is visible with no control to act on it. Recorded on
  [the use case](../../../../web-app/docs/usecases/browse-recorded-expenses.md); no plan item, since the design
  settled offset paging as a decision and no scenario asked for the control.

- **A slow read overwrites the answer to a newer filter** — `ExpensesPage`'s listing effect has no cancellation.
  The unfiltered read is still in flight when a category is chosen, the filtered read answers first, the
  unfiltered read answers second, and the page ends showing the filter the person has already left. The same
  window lets a stale `401` call `sessionExpired` after a later read has succeeded, signing the person out on a
  filter change. `AuthProvider` already guards its one read with a `cancelled` flag, so the module has the idiom.
  Left because fixing it changes observable behaviour no scenario covers. `web-app/plan.md · B10`.

- **`ExpensesPage` and `LoginPage` render a failure identically** — an `ErrorBanner` in `components/` is what the
  conventions' extraction rule points at, but it needs `LoginPage.tsx`, which this task never touched.
  `web-app/plan.md · B11`.

- **A scratch test in `build/` joins the module's suite** — Vitest's default `include` collects `**/*.test.tsx`
  from the module root, and its default `exclude` does not name `build/`, which is where the repository
  conventions put scratch files. One `test.exclude` entry in `vite.config.ts` closes it. `web-app/plan.md · B11`.
