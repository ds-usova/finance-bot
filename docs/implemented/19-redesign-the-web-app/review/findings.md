# Review: Redesign the Web App

The web app gained Tailwind CSS v4, a shadcn/ui component set it owns, an app shell carrying sign-out and a
light/dark theme, a listing grouped into collapsible UTC days, and an English message catalogue. Everything below
is what the task leaves open.

## web-app

- **A day section's date heading can name the day before, for a reader west of UTC** —
  [`ExpenseDaySection.tsx:22`](../../../web-app/src/components/ExpenseDaySection.tsx) formats the fallback heading
  with `new Intl.DateTimeFormat(locale)`, which has no `timeZone` and so formats in the runtime's own zone, over
  the instant `${day.day}T00:00:00Z`. `toDaySections` groups strictly by the UTC day (**D5**), so at
  `America/New_York` a section holding the UTC day `2026-08-01` heads itself `7/31/2026`. Passing
  `{ timeZone: 'UTC' }` settles it. Found by the refactor pass, which may not change behaviour, and recorded as
  the plan's **B1**. Established from the code rather than from a run: the suite's runner is pinned to UTC, and
  this session could not execute a check under another zone.

- **The test covering that heading cannot fail on it** —
  [`ExpenseDaySection.test.tsx`](../../../web-app/src/components/ExpenseDaySection.test.tsx) builds its expected
  value with the same `new Intl.DateTimeFormat('en').format(new Date(…))` expression it asserts against, so it
  proves only that the component calls `Intl` the way the test does. It holds in every zone, including one where
  the heading is wrong. RU04's scenario needs a fixed expected string under a pinned zone. The plan's **B2**, and
  the reason the defect above survived a green suite.
