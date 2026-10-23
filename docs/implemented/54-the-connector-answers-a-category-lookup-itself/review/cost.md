# Cost · 54-the-connector-answers-a-category-lookup-itself

Written by `scripts/cost/cost.sh report` at 2026-10-14 17:25:19 (UTC+02:00, the latest record's offset).
Re-run the script rather than edit this file.
Rates: fetched 2026-10-08.

## Cost

| agent | n | $ | % | read $ | write $ | out $ | time | model |
|-------|---|---|---|--------|---------|-------|------|-------|
| session a7324744 (own turns) | 1 | $0.69 | 4% | $0.25 | $0.29 | $0.15 | 43m | claude-opus-5-5 |
| session c705dc2b (own turns) | 1 | $8.40 | 47% | $3.77 | $2.85 | $1.78 | 73m | claude-opus-5-5 |
| grill-design | 1 | $1.24 | 7% | $0.35 | $0.44 | $0.44 | 4m | claude-opus-5-5 |
| review-plan | 1 | $0.86 | 5% | $0.27 | $0.36 | $0.23 | 2m | claude-opus-5-5 |
| review-plan-facts | 1 | $0.51 | 3% | $0.08 | $0.25 | $0.17 | 2m | claude-sonnet-5-5 |
| implement-plan-module | 1 | $3.67 | 21% | $1.57 | $1.38 | $0.73 | 25m | claude-opus-5-5 |
| stabilize | 1 | $0.38 | 2% | $0.08 | $0.17 | $0.13 | 2m | claude-sonnet-5-5 |
| red-unit | 1 | $0.11 | 1% | $0.01 | $0.08 | $0.02 | 32s | claude-sonnet-5-5 |
| red-integration | 3 | $0.35 | 2% | $0.03 | $0.19 | $0.13 | 1m | claude-sonnet-5-5 |
| red-system | 3 | $0.20 | 1% | $0.03 | $0.11 | $0.06 | 58s | claude-sonnet-5-5 |
| green-unit | 1 | $0.08 | 0% | $0.01 | $0.04 | $0.03 | 22s | claude-sonnet-5-5 |
| green-integration | 2 | $0.14 | 1% | $0.01 | $0.10 | $0.03 | 1m | claude-sonnet-5-5 |
| refactor | 1 | $1.25 | 7% | $0.41 | $0.56 | $0.28 | 9m | claude-opus-5-5 |

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
| session a7324744 (own turns) | 15 | 32 | 7k | 36k | 1.3M | 103k | 10% |
| session c705dc2b (own turns) | 114 | 234 | 89k | 356k | 18.8M | 312k | 31% |
| grill-design | 30 | 60 | 22k | 89k | 1.8M | 89k | 9% |
| review-plan | 29 | 60 | 12k | 73k | 1.3M | 73k | 7% |
| review-plan-facts | 14 | 28 | 17k | 100k | 844k | 100k | 10% |
| implement-plan-module | 70 | 166 | 36k | 276k | 7.8M | 155k | 16% |
| stabilize | 17 | 34 | 13k | 67k | 785k | 67k | 7% |
| red-unit | 6 | 12 | 2k | 30k | 126k | 35k | 3% |
| red-integration | 14 | 28 | 13k | 77k | 278k | 55k | 5% |
| red-system | 15 | 30 | 6k | 46k | 252k | 30k | 3% |
| green-unit | 5 | 10 | 3k | 18k | 70k | 22k | 2% |
| green-integration | 8 | 16 | 3k | 39k | 122k | 36k | 4% |
| refactor | 21 | 42 | 14k | 113k | 2.1M | 134k | 13% |

- `cache read`: the conversation prefix, re-read from cache on each turn, not new tokens. It grows
  with roughly the square of the turn count.
- `peak ctx`: the largest one message's input + cache write + cache read, the most context the
  agent carried at once. A type's is its largest agent's.
- `of window`: `peak ctx` against the model's context window.

| plan | agents | $ | time |
|------|--------|---|------|
| `plan.md` | 15 | $7.55 | 53m |

## Task total

| $ | span |
|---|------|
| $17.88 | 119m (15:26 → 17:25) |
