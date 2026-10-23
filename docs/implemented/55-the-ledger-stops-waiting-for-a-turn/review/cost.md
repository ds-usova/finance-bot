# Cost · 55-the-ledger-stops-waiting-for-a-turn

Written by `scripts/cost/cost.sh report` at 2026-10-14 22:48:03 (UTC+02:00, the latest record's offset).
Re-run the script rather than edit this file.
Rates: fetched 2026-10-08.

## Cost

| agent | n | $ | % | read $ | write $ | out $ | time | model |
|-------|---|---|---|--------|---------|-------|------|-------|
| session 150b6ba3 (own turns) | 1 | $3.10 | 5% | $1.55 | $0.94 | $0.61 | 152m | claude-opus-5-5 |
| session a7324744 (own turns) | 1 | $13.60 | 20% | $6.53 | $4.08 | $2.99 | 210m | claude-opus-5-5 |
| grill-design | 1 | $3.28 | 5% | $0.71 | $1.51 | $1.06 | 11m | claude-opus-5-5 |
| review-plan | 3 | $3.22 | 5% | $0.50 | $2.05 | $0.67 | 4m | claude-opus-5-5 |
| review-plan-facts | 3 | $3.80 | 6% | $1.24 | $1.20 | $1.35 | 9m | claude-sonnet-5-5 |
| implement-plan-module | 3 | $21.50 | 32% | $8.49 | $9.93 | $3.07 | 76m | claude-opus-5-5 |
| stabilize | 8 | $5.35 | 8% | $1.87 | $1.50 | $1.99 | 19m | claude-sonnet-5-5 |
| red-unit | 6 | $2.43 | 4% | $0.49 | $0.85 | $1.09 | 8m | claude-sonnet-5-5 |
| red-integration | 5 | $1.60 | 2% | $0.27 | $0.63 | $0.70 | 7m | claude-sonnet-5-5 |
| red-system | 5 | $2.00 | 3% | $0.54 | $0.71 | $0.75 | 8m | claude-sonnet-5-5 |
| green-unit | 9 | $1.29 | 2% | $0.17 | $0.67 | $0.45 | 7m | claude-sonnet-5-5 |
| green-integration | 5 | $0.68 | 1% | $0.08 | $0.38 | $0.22 | 4m | claude-sonnet-5-5 |
| green-system | 2 | $0.37 | 1% | $0.08 | $0.19 | $0.10 | 2m | claude-sonnet-5-5 |
| refactor | 2 | $4.91 | 7% | $2.12 | $1.75 | $1.03 | 16m | claude-opus-5-5 |

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
| session 150b6ba3 (own turns) | 48 | 114 | 30k | 118k | 7.7M | 225k | 23% |
| session a7324744 (own turns) | 153 | 332 | 149k | 510k | 32.7M | 424k | 42% |
| grill-design | 42 | 86 | 53k | 302k | 3.5M | 170k | 17% |
| review-plan | 47 | 98 | 33k | 410k | 2.5M | 100k | 10% |
| review-plan-facts | 92 | 184 | 136k | 481k | 12.4M | 242k | 24% |
| implement-plan-module | 232 | 554 | 154k | 2.0M | 42.5M | 311k | 31% |
| stabilize | 194 | 388 | 199k | 599k | 18.7M | 331k | 33% |
| red-unit | 84 | 168 | 109k | 340k | 4.9M | 170k | 17% |
| red-integration | 50 | 100 | 70k | 254k | 2.7M | 136k | 14% |
| red-system | 78 | 156 | 75k | 282k | 5.4M | 158k | 16% |
| green-unit | 62 | 124 | 45k | 267k | 1.7M | 61k | 6% |
| green-integration | 34 | 68 | 22k | 152k | 801k | 50k | 5% |
| green-system | 27 | 54 | 10k | 76k | 813k | 54k | 5% |
| refactor | 75 | 150 | 52k | 351k | 10.6M | 207k | 21% |

- `cache read`: the conversation prefix, re-read from cache on each turn, not new tokens. It grows
  with roughly the square of the turn count.
- `peak ctx`: the largest one message's input + cache write + cache read, the most context the
  agent carried at once. A type's is its largest agent's.
- `of window`: `peak ctx` against the model's context window.

| plan | agents | $ | time |
|------|--------|---|------|
| `shared/plan.md` | 4 | $1.57 | 29m |
| `ledger-service/plan.md` | 24 | $27.59 | 168m |
| `ai-connector-service/plan.md` | 23 | $17.98 | 100m |

## Task total

| $ | span |
|---|------|
| $67.13 | 366m (16:42 → 22:48) |
