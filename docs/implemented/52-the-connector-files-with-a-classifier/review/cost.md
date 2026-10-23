# Cost · 52-the-connector-files-with-a-classifier

Written by `scripts/cost/cost.sh report` at 2026-10-14 13:47:28 (UTC+02:00, the latest record's offset).
Re-run the script rather than edit this file.
Rates: fetched 2026-10-08.

## Cost

| agent | n | $ | % | read $ | write $ | out $ | time | model |
|-------|---|---|---|--------|---------|-------|------|-------|
| session 6ad52699 (own turns) | 1 | $2.11 | 3% | $0.49 | $1.38 | $0.23 | 77m | claude-opus-5-5 |
| session 8a18a458 (own turns) | 1 | $40.20 | 58% | $19.01 | $14.57 | $6.61 | 832m | claude-opus-5-5 |
| grill-design | 1 | $3.31 | 5% | $0.99 | $1.37 | $0.95 | 9m | claude-opus-5-5 |
| review-plan | 1 | $0.83 | 1% | $0.16 | $0.40 | $0.26 | 4m | claude-opus-5-5 |
| review-plan-facts | 1 | $1.38 | 2% | $0.41 | $0.46 | $0.51 | 8m | claude-sonnet-5-5 |
| implement-plan-module | 1 | $10.66 | 15% | $5.05 | $3.67 | $1.94 | 31m | claude-opus-5-5 |
| stabilize | 3 | $2.73 | 4% | $1.15 | $0.71 | $0.88 | 18m | claude-sonnet-5-5 |
| red-unit | 6 | $1.24 | 2% | $0.14 | $0.51 | $0.59 | 5m | claude-sonnet-5-5 |
| red-integration | 2 | $1.20 | 2% | $0.36 | $0.45 | $0.39 | 5m | claude-sonnet-5-5 |
| red-system | 3 | $0.52 | 1% | $0.10 | $0.25 | $0.17 | 2m | claude-sonnet-5-5 |
| green-unit | 7 | $0.65 | 1% | $0.06 | $0.34 | $0.25 | 4m | claude-sonnet-5-5 |
| green-integration | 5 | $0.47 | 1% | $0.07 | $0.23 | $0.17 | 3m | claude-sonnet-5-5 |
| green-system | 2 | $0.53 | 1% | $0.13 | $0.21 | $0.20 | 5m | claude-sonnet-5-5 |
| refactor | 1 | $3.45 | 5% | $1.85 | $1.00 | $0.60 | 8m | claude-opus-5-5 |

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
| session 6ad52699 (own turns) | 24 | 54 | 12k | 172k | 2.5M | 150k | 15% |
| session 8a18a458 (own turns) | 314 | 656 | 331k | 1.8M | 95.1M | 501k | 50% |
| grill-design | 54 | 110 | 48k | 274k | 4.9M | 170k | 17% |
| review-plan | 15 | 32 | 13k | 81k | 810k | 81k | 8% |
| review-plan-facts | 35 | 70 | 51k | 184k | 4.1M | 184k | 18% |
| implement-plan-module | 118 | 284 | 97k | 734k | 25.2M | 332k | 33% |
| stabilize | 90 | 180 | 88k | 283k | 11.4M | 252k | 25% |
| red-unit | 44 | 88 | 59k | 205k | 1.4M | 85k | 8% |
| red-integration | 42 | 84 | 39k | 180k | 3.6M | 171k | 17% |
| red-system | 23 | 46 | 17k | 98k | 955k | 78k | 8% |
| green-unit | 33 | 66 | 25k | 136k | 563k | 50k | 5% |
| green-integration | 39 | 78 | 17k | 92k | 720k | 32k | 3% |
| green-system | 33 | 66 | 20k | 82k | 1.3M | 73k | 7% |
| refactor | 60 | 122 | 30k | 200k | 9.2M | 221k | 22% |

- `cache read`: the conversation prefix, re-read from cache on each turn, not new tokens. It grows
  with roughly the square of the turn count.
- `peak ctx`: the largest one message's input + cache write + cache read, the most context the
  agent carried at once. A type's is its largest agent's.
- `of window`: `peak ctx` against the model's context window.

| plan | agents | $ | time |
|------|--------|---|------|
| `plan.md` | 32 | $23.64 | 94m |

## Task total

| $ | span |
|---|------|
| $69.26 | 909m (22:38 → 13:47) |
