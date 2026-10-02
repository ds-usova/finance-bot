# Fix: the entry line's cells end on a line instead of spanning from `sm` up

**Format:** 2
**Affected Module:** `web-app`
**Bug:** [a pending day's entry description is cut to "Tr…" on a wide screen](bug.md)

## Steps

| #    | Kind  | What changes                                                    | Touches                 |
|------|-------|-----------------------------------------------------------------|-------------------------|
| FR01 | red   | enable the rule refusing a breakpoint span beside a start line  | `conventions.test.ts`   |
| FG01 | green | end the tick and description cells with `row-end` and `col-end` | `ExpenseDaySection.tsx` |

- [x] FR01 · red · enable the module rule refusing a breakpoint's span beside a start line on the same axis
  - test-files:
    - `web-app/src/conventions.test.ts`
  - reproduces: on a day holding a pending entry, a wide screen prints the entry's description in the tick
    column, cut to "Tr…"
  - runs: `src/conventions.test.ts` › never lets a breakpoint’s span move a grid cell off the line it starts on

- [x] FG01 · green · every entry-line cell keeps its start line at every width: the tick is
  `col-start-1 row-start-1 row-end-3 sm:row-end-2`, the margin layout's description
  `col-start-2 col-end-4 row-start-1 sm:col-end-3`, the plain layout's `col-start-1 col-end-3 row-start-1 sm:col-end-2`
  - files:
    - `web-app/src/components/ExpenseDaySection.tsx`
  - fixes: FR01
  - runs: `src/conventions.test.ts` › never lets a breakpoint’s span move a grid cell off the line it starts on
