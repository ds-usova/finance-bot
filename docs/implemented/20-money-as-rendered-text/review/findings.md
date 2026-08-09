# Review: Money Crosses the Browse API as Rendered Text

The browse API now answers every figure as text, and the web app parses none of it. Everything below is open —
none of it blocked the task, and none of it is scheduled.

## web-app

- **Four test names still describe summing, which nothing does any more** — the figures arrive through
  `dayTotals` and are passed through untouched. In `expenseDays.test.ts`: *sums a day's RECORDED entries into one
  EUR total when every entry shares that currency* and *sums only the RECORDED entries while still counting every
  entry and every one still awaiting a decision*. In `ExpenseDaySection.test.tsx`: *sums only the recorded entries
  into the total and says one entry awaits a decision* and *shows a total for each currency present among the
  day's recorded entries*. The [testing conventions](../../../web-app/docs/conventions/testing.md#naming-conventions)
  want an `it` to state the outcome rather than the mechanism, so all four now read against that rule. Left alone
  because RU01's and RU02's `update:` bullets quote each name verbatim, and renaming would contradict the record
  of what those steps did. From `web-app/plan.md` B1.

- **Two tests in `expenseDays.test.ts` now prove the same thing** — *carries no total for a day holding only an
  entry still awaiting a decision* and *leaves a section's totals empty when dayTotals holds no element for its
  day, without touching its entries or awaiting count*. Both put entries on one UTC day, pass a `dayTotals`
  element for another, and assert an empty `totals` beside `entries` and `awaiting`. Their one difference,
  pending-only versus recorded-plus-pending, stopped being a distinction when the lookup became a day-key lookup
  that never reads `status`. A deletion was outside every step's scope. From `web-app/plan.md` B2.

- **The day heading's figure spans carry no `whitespace-nowrap`, unlike the entry amount span** — so a long
  figure such as `CHF 1,245.00` can wrap inside that column at a narrow width, where
  [D22](../design.md#decisions) says the heading stays one line per currency. The manual review passed at the
  widths checked, so nothing was wrong on screen; the asymmetry is still in the code and no test covers it,
  because width and wrapping are outside
  [what the suite can see](../../../web-app/docs/conventions/testing.md#what-the-suite-cannot-see). From
  `web-app/plan.md` B3.

## shared

- **Five schemas are named in `components/schemas` but generate under a positional name at the point of use** —
  `ExpensePage`, `Category`, `Grouping`, `Session` and `Problem` produce classes nothing references, while their
  operations answer `ListExpenses200Response`, `ListCategories200ResponseInner`,
  `ListGroupings200ResponseInner`, `CurrentSession200Response` and `ListExpenses400Response`. Each is an
  operation's top-level response schema, and every operation is reached through an externally-`$ref`ed file under
  `openapi/paths/`, which the generator names positionally whatever the component block says. Accepted rather
  than fixed: the alternative was flattening the path files into the root document. Reordering the paths still
  renames those five and breaks the ledger's build while no caller notices. Recorded as
  [D33](../design.md#decisions) and in `shared/plan.md` ST22.

## Not from a plan

- **`ledger-service/docs/conventions/testing.md:155` says the display-name length rule is `@ArchIgnore`d.** It is
  not — `DisplayNameConventionsTest.aTestMethodsDisplayNameStaysUnderTheLimit` enforces 120 characters on every
  run, and a 169-character name failed a guardrail during this task. Pre-existing, outside every plan's scope,
  and it costs a re-delegation each time someone trusts the page.
