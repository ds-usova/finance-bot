# Review: Redesign the Web App

The web app gained Tailwind CSS v4, a shadcn/ui component set it owns, an app shell carrying sign-out and a
light/dark theme, a listing grouped into collapsible UTC days, and an English message catalogue.

Nothing is open. Both findings this task recorded were fixed in a later change, kept below with what closed them.

## web-app — closed

- **A day section's date heading named the day before, for a reader west of UTC** — `ExpenseDaySection`
  formatted the fallback heading with `new Intl.DateTimeFormat(locale)`, which has no `timeZone` and so
  formatted in the runtime's own zone, over the instant `${day.day}T00:00:00Z`. `toDaySections` groups strictly
  by the UTC day (**D5**), so at `America/New_York` a section holding `2026-08-01` headed itself `7/31/2026`. The
  plan's **B1**. Fixed: the formatter now passes `timeZone: 'UTC'`, alongside a readable date format.

- **The test covering that heading could not fail on it** — it built its expected value with the same
  `Intl.DateTimeFormat` expression it asserted against, so it proved only that the component called `Intl` the
  way the test did, and held in every zone. The plan's **B2**, and the reason the defect survived a green suite.
  Fixed: the case asserts the fixed string `Aug 1, 2026`, which a zone-dependent heading cannot satisfy.
