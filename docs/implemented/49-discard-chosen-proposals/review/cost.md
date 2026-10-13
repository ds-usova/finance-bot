# Cost · 49-discard-chosen-proposals

Written by `scripts/cost/cost.sh report` at 2026-10-13 13:29:39 (UTC+02:00, the latest record's offset).
Re-run the script rather than edit this file.
Rates: fetched 2026-10-08.

## Cost

| agent | n | $ | % | read $ | write $ | out $ | time | model |
|-------|---|---|---|--------|---------|-------|------|-------|
| session (own turns) | 1 | $7.67 | 42% | $3.51 | $2.72 | $1.44 | 825m | claude-opus-5-5 |
| grill-design | 1 | $1.62 | 9% | $0.60 | $0.51 | $0.51 | 8m | claude-opus-5-5 |
| review-plan | 2 | $1.11 | 6% | $0.29 | $0.55 | $0.27 | 2m | claude-opus-5-5 |
| review-plan-facts | 2 | $1.84 | 10% | $0.49 | $0.75 | $0.60 | 4m | claude-opus-5-5, claude-sonnet-5-5 |
| implement-plan-module | 2 | $3.52 | 19% | $1.74 | $0.98 | $0.80 | 25m | claude-opus-5-5 |
| stabilize | 2 | $0.45 | 2% | $0.05 | $0.25 | $0.15 | 2m | claude-sonnet-5-5 |
| red-unit | 1 | $0.14 | 1% | $0.01 | $0.07 | $0.05 | 34s | claude-sonnet-5-5 |
| red-integration | 3 | $0.60 | 3% | $0.09 | $0.32 | $0.19 | 2m | claude-sonnet-5-5 |
| red-system | 1 | $0.16 | 1% | $0.01 | $0.10 | $0.05 | 30s | claude-sonnet-5-5 |
| green-unit | 1 | $0.06 | 0% | $0.00 | $0.04 | $0.02 | 18s | claude-sonnet-5-5 |
| green-integration | 2 | $0.10 | 1% | $0.01 | $0.06 | $0.03 | 17s | claude-sonnet-5-5 |
| refactor | 1 | $0.98 | 5% | $0.34 | $0.45 | $0.19 | 5m | claude-opus-5-5 |

- `$`: what the run would cost at API rates. On a subscription plan it is not a bill.
- `read $`: cache reads at the read multiplier (a tenth of the input rate, or less on some models),
  plus input at the input rate.
- `write $`: cache writes at 1.25× the input rate for the 5-minute TTL and 2× for the 1-hour one.
- `out $`: output at the output rate.
- `%`: the share of the task's total.
- `—`: a row priced nowhere. `*`: a sum that leaves out an unpriced agent.

## Volume

| agent | turns | input | output | cache write | cache read | peak ctx | of window |
|-------|-------|-------|--------|-------------|------------|----------|-----------|
| session (own turns) | 100 | 216 | 72k | 340k | 17.5M | 287k | 29% |
| grill-design | 46 | 92 | 25k | 102k | 3.0M | 102k | 10% |
| review-plan | 32 | 68 | 14k | 109k | 1.4M | 78k | 8% |
| review-plan-facts | 51 | 102 | 47k | 229k | 3.8M | 157k | 16% |
| implement-plan-module | 88 | 198 | 40k | 196k | 8.7M | 148k | 15% |
| stabilize | 16 | 32 | 15k | 98k | 516k | 67k | 7% |
| red-unit | 6 | 12 | 5k | 30k | 122k | 34k | 3% |
| red-integration | 25 | 50 | 19k | 127k | 881k | 71k | 7% |
| red-system | 6 | 12 | 5k | 40k | 143k | 44k | 4% |
| green-unit | 4 | 8 | 2k | 16k | 49k | 20k | 2% |
| green-integration | 6 | 12 | 3k | 26k | 71k | 20k | 2% |
| refactor | 20 | 40 | 9k | 90k | 1.7M | 112k | 11% |

- `cache read`: the conversation prefix, re-read from cache on each turn, not new tokens. It grows
  with roughly the square of the turn count.
- `peak ctx`: the largest one message's input + cache write + cache read, the most context the
  agent carried at once. A type's is its largest agent's.
- `of window`: `peak ctx` against the model's context window.

| plan | agents | $ | time |
|------|--------|---|------|
| `shared/plan.md` | 4 | $2.13 | 17m |
| `ledger-service/plan.md` | 13 | $6.83 | 45m |

## Task total

| $ | span |
|---|------|
| $18.25 | 825m (23:44 → 13:29) |
