# Bug: a pending day's entry description is cut to "Tr…" on a wide screen

**Format:** 2
**Affected Modules:** `web-app`
**Source:** the request — "the expense name is trimmed to Tr… while I have Train to Cologne in the database"
**Baseline:** `95afced4`; `web-app` 356 total, 0 skipped, green (read from the last full run, `c928a87c`, with
`web-app/` unchanged since); the reproduction `src/conventions.test.ts` › "never lets a breakpoint’s span move a
grid cell off the line it starts on" is disabled beside it

## What happens

- **Given** a day in the listing holding a pending entry described "Train to Cologne"
- **When** the listing is shown at the `sm` breakpoint or wider
- **Then** the description fills its own column, between the tick and the merchant
- **Actual** the description sits in the 1.5rem tick column and reads "Tr…"; the tick drops to a second row

## How it reproduces

jsdom lays nothing out (`web-app/docs/conventions/testing.md`, **What the Suite Cannot See**), so no component
test can measure the cell. The user chose a module rule in `src/conventions.test.ts` instead, failing on the
cause. Run twice, failing both times:

```
tools/agent-test/agent-test.sh --module web-app --tests "src/conventions.test.ts"

Result: FAIL
Totals: 2 tests, 1 passed, 1 failed, 0 skipped
message: class lists pairing a col-start or row-start with a breakpoint’s span on the same axis; end the cell
with col-end or row-end instead: expected [ …(3) ] to deeply equal []
+   "components/ExpenseDaySection.tsx: 'col-start-1 row-span-2 row-start-1 sm:row-span-1'",
+   "components/ExpenseDaySection.tsx: 'col-span-2 col-start-2 row-start-1 sm:col-span-1'",
+   "components/ExpenseDaySection.tsx: 'col-span-2 col-start-1 row-start-1 sm:col-span-1'",
```

## Why it happens

1. A day with anything pending lays its lines out with `LINE_WITH_MARGIN`
   (`web-app/src/components/ExpenseDaySection.tsx:151`), whose first column is the 1.5rem tick margin.
2. The description's classes are `col-span-2 col-start-2 row-start-1 sm:col-span-1` (`:41`).
3. Tailwind 4.3 writes `col-span-N` as the shorthand `grid-column: span N / span N`
   (`web-app/node_modules/tailwindcss/dist/lib.js`). Inside the `sm` media query, `sm:col-span-1` therefore
   resets `grid-column-start` to auto, throwing away `col-start-2`.
4. The tick's `sm:row-span-1` (`:40`) likewise resets `grid-row`, throwing away `row-start-1`. The tick keeps a
   fixed column but no fixed row.
5. Grid auto-placement puts the row-locked description in the first free cell of row 1. The merchant, category,
   mark and amount hold columns 3 to 6, and the tick no longer holds row 1, so that cell is column 1. The tick
   then lands in row 2. — unverified in a browser; it follows from CSS Grid's placement order, and no screen
   was looked at.
6. `LINE_WITHOUT_MARGIN`'s description (`:51`) carries the same pair. It lands in column 1 by auto-placement,
   which is where it belongs, so it shows nothing.

## What the fix must not break

- On a phone, a pending line's description spans the two middle columns of row 1, with the tick spanning both
  rows in the margin.
- On a phone, a recorded line's description spans the first two columns of row 1.
- From `sm` up, every cell of both layouts sits in the column the day header and the other days line up with.
