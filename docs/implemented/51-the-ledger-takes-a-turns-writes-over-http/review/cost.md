# Cost · 51-the-ledger-takes-a-turns-writes-over-http

Written by `scripts/cost/cost.sh report` at 2026-10-14 11:16:08 (UTC+02:00, the latest record's offset).
Re-run the script rather than edit this file.
Rates: fetched 2026-10-08.

## Cost

| agent | n | $ | % | read $ | write $ | out $ | time | model |
|-------|---|---|---|--------|---------|-------|------|-------|
| session (own turns) | 1 | $28.27 | 49% | $11.80 | $11.76 | $4.71 | 758m | claude-opus-5-5 |
| grill-design | 1 | $4.33 | 7% | $1.70 | $1.56 | $1.07 | 10m | claude-opus-5-5 |
| review-plan | 3 | $2.25 | 4% | $0.35 | $1.29 | $0.61 | 3m | claude-opus-5-5 |
| review-plan-facts | 3 | $2.91 | 5% | $0.82 | $1.14 | $0.96 | 9m | claude-opus-5-5, claude-sonnet-5-5 |
| implement-plan-module | 4 | $11.41 | 20% | $4.37 | $5.03 | $2.01 | 60m | claude-opus-5-5 |
| stabilize | 3 | $1.56 | 3% | $0.42 | $0.55 | $0.60 | 12m | claude-sonnet-5-5 |
| red-unit | 5 | $0.51 | 1% | $0.05 | $0.26 | $0.20 | 3m | claude-sonnet-5-5 |
| green-unit | 4 | $0.29 | 0% | $0.03 | $0.16 | $0.09 | 2m | claude-sonnet-5-5 |
| refactor | 2 | $3.87 | 7% | $1.83 | $1.19 | $0.85 | 10m | claude-opus-5-5 |
| red-system | 1 | $0.41 | 1% | $0.07 | $0.21 | $0.14 | 2m | claude-sonnet-5-5 |
| red-integration | 9 | $1.32 | 2% | $0.19 | $0.65 | $0.47 | 6m | claude-sonnet-5-5 |
| green-integration | 4 | $0.73 | 1% | $0.11 | $0.36 | $0.26 | 6m | claude-sonnet-5-5 |

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
| session (own turns) | 198 | 418 | 235k | 1.5M | 59.0M | 469k | 47% |
| grill-design | 75 | 152 | 53k | 311k | 8.5M | 187k | 19% |
| review-plan | 44 | 94 | 31k | 258k | 1.7M | 85k | 8% |
| review-plan-facts | 76 | 152 | 69k | 323k | 5.9M | 190k | 19% |
| implement-plan-module | 175 | 408 | 101k | 1.0M | 21.8M | 242k | 24% |
| stabilize | 60 | 120 | 60k | 219k | 4.2M | 162k | 16% |
| red-unit | 27 | 54 | 20k | 106k | 519k | 47k | 5% |
| green-unit | 19 | 38 | 9k | 65k | 277k | 29k | 3% |
| refactor | 67 | 134 | 42k | 237k | 9.2M | 214k | 21% |
| red-system | 13 | 26 | 14k | 83k | 703k | 83k | 8% |
| red-integration | 63 | 126 | 47k | 261k | 1.9M | 91k | 9% |
| green-integration | 36 | 72 | 26k | 146k | 1.1M | 73k | 7% |

- `cache read`: the conversation prefix, re-read from cache on each turn, not new tokens. It grows
  with roughly the square of the turn count.
- `peak ctx`: the largest one message's input + cache write + cache read, the most context the
  agent carried at once. A type's is its largest agent's.
- `of window`: `peak ctx` against the model's context window.

| plan | agents | $ | time |
|------|--------|---|------|
| `shared/plan.md` | 4 | $2.64 | 34m |
| `ledger-service/plan.md` | 27 | $19.53 | 658m |
| `eval-runner/plan.md` | 7 | $3.09 | 42m |

## Task total

| $ | span |
|---|------|
| $57.85 | 758m (22:38 → 11:16) |
