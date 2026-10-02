# Cost · 47-file-expenses-as-text-from-the-web

Written by `scripts/cost/cost.sh report` at 2026-10-02 06:51:41 (UTC+02:00, the latest record's offset).
Re-run the script rather than edit this file.
Rates: fetched 2026-09-29.

## Cost

| agent | n | $ | % | read $ | write $ | out $ | time | model |
|-------|---|---|---|--------|---------|-------|------|-------|
| session a917c2fd (own turns) | 1 | $3.11 | 5% | $0.78 | $1.88 | $0.45 | 203m | claude-opus-5-5 |
| session f40441d8 (own turns) | 1 | $18.59 | 29% | $9.07 | $4.91 | $4.61 | 386m | claude-opus-5-5 |
| grill-design | 1 | $2.73 | 4% | $0.56 | $1.40 | $0.77 | 14m | claude-opus-5-5 |
| grill-frontend | 1 | $2.09 | 3% | $0.17 | $1.34 | $0.57 | 13m | claude-opus-5-5 |
| review-plan | 3 | $3.57 | 6% | $0.60 | $2.18 | $0.79 | 5m | claude-opus-5-5 |
| review-plan-facts | 3 | $6.59 | 10% | $2.80 | $1.95 | $1.84 | 9m | claude-sonnet-5-5, claude-opus-5-5 |
| implement-plan-module | 3 | $17.97 | 28% | $5.54 | $12.03 | $0.40 | 60m | claude-opus-5-5 |
| stabilization-step | 4 | $1.26 | 2% | $0.58 | $0.55 | $0.14 | 8m | claude-sonnet-5-5 |
| tdd-unit-red-phase-step | 5 | $1.44 | 2% | $0.48 | $0.75 | $0.21 | 5m | claude-sonnet-5-5 |
| tdd-unit-green-phase-step | 12 | $1.29 | 2% | $0.34 | $0.79 | $0.17 | 4m | claude-sonnet-5-5 |
| tdd-integration-red-phase-step | 1 | $0.59 | 1% | $0.24 | $0.26 | $0.09 | 3m | claude-sonnet-5-5 |
| tdd-system-red-phase-step | 2 | $0.41 | 1% | $0.10 | $0.26 | $0.05 | 3m | claude-sonnet-5-5 |
| tdd-integration-green-phase-step | 3 | $0.21 | 0% | $0.04 | $0.14 | $0.02 | 1m | claude-sonnet-5-5 |
| tdd-system-green-phase-step | 2 | $1.96 | 3% | $1.40 | $0.37 | $0.20 | 64m | claude-sonnet-5-5 |
| tdd-refactor-phase | 1 | $1.62 | 3% | $0.73 | $0.67 | $0.21 | 8m | claude-opus-5-5 |

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
| session a917c2fd (own turns) | 36 | 88 | 22k | 235k | 3.9M | 174k | 17% |
| session f40441d8 (own turns) | 143 | 296 | 230k | 614k | 45.3M | 502k | 50% |
| grill-design | 40 | 82 | 38k | 280k | 2.8M | 132k | 13% |
| grill-frontend | 19 | 40 | 29k | 269k | 866k | 112k | 11% |
| review-plan | 61 | 128 | 39k | 436k | 3.0M | 86k | 9% |
| review-plan-facts | 103 | 206 | 153k | 608k | 14.0M | 247k | 25% |
| implement-plan-module | 185 | 450 | 20k | 2.4M | 27.7M | 300k | 30% |
| stabilization-step | 51 | 102 | 14k | 220k | 2.9M | 129k | 13% |
| tdd-unit-red-phase-step | 55 | 110 | 21k | 300k | 2.4M | 90k | 9% |
| tdd-unit-green-phase-step | 73 | 146 | 17k | 317k | 1.7M | 66k | 7% |
| tdd-integration-red-phase-step | 20 | 40 | 9k | 105k | 1.2M | 110k | 11% |
| tdd-system-red-phase-step | 15 | 30 | 5k | 106k | 498k | 88k | 9% |
| tdd-integration-green-phase-step | 13 | 26 | 2k | 58k | 220k | 35k | 4% |
| tdd-system-green-phase-step | 102 | 204 | 20k | 146k | 7.0M | 115k | 11% |
| tdd-refactor-phase | 34 | 68 | 11k | 135k | 3.6M | 156k | 16% |

- `cache read`: the conversation prefix, re-read from cache on each turn, not new tokens. It grows
  with roughly the square of the turn count.
- `peak ctx`: the largest one message's input + cache write + cache read, the most context the
  agent carried at once. A type's is its largest agent's.
- `of window`: `peak ctx` against the model's context window.

| plan | agents | $ | time |
|------|--------|---|------|
| `shared/plan.md` | 4 | $4.85 | 37m |
| `ledger-service/plan.md` | 23 | $25.25 | 225m |
| `web-app/plan.md` | 12 | $6.82 | 53m |

## Task total

| $ | span |
|---|------|
| $63.42 | 595m (20:56 → 06:51) |
