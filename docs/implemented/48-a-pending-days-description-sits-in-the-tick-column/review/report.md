# Report: A pending day's description sits in the tick column

**Done and archived.**

## Done

| Module    | Steps  | Suite                 |
|-----------|--------|-----------------------|
| `web-app` | 2 of 2 | 357 green, 0 skipped  |

## Measured

Nothing measured: the conventions say performance is not measured.

## Cost

$8.86 · 1 agent · 6 h 36 min — [cost.md](cost.md)

[Agent activity](activity.html)

## Open

Nothing open — [findings.md](findings.md)

## Manual checks

**[ ] `web-app` — a pending entry's description fills its own column on a wide screen**

- **Given** the expenses page at `sm` width or wider, on a day holding a pending entry described
  "Train to Cologne"
- **When** it renders
- **Then** the description reads in full between the tick and the merchant, with the tick on the same row

**[ ] `web-app` — a pending line still prints on two rows on a phone**

- **Given** the same day at phone width
- **When** it renders
- **Then** the tick spans both rows of the line, beside the description and the merchant
