# Cost · 53-the-runner-measures-a-classifier-turn

Written by `scripts/cost/cost.sh report` at 2026-10-14 15:38:42 (UTC+02:00, the latest record's offset).
Re-run the script rather than edit this file.
Rates: fetched 2026-10-08.

## Cost

| agent | n | $ | % | read $ | write $ | out $ | time | model |
|-------|---|---|---|--------|---------|-------|------|-------|
| session 6ad52699 (own turns) | 1 | $13.48 | 66% | $6.82 | $4.42 | $2.24 | 175m | claude-opus-5-5 |
| session c705dc2b (own turns) | 1 | $0.74 | 4% | $0.26 | $0.33 | $0.16 | 13m | claude-opus-5-5 |
| grill-design | 1 | $1.63 | 8% | $0.56 | $0.60 | $0.47 | 4m | claude-opus-5-5 |
| review-plan | 1 | $0.70 | 3% | $0.20 | $0.28 | $0.22 | 2m | claude-opus-5-5 |
| review-plan-facts | 1 | $0.28 | 1% | $0.03 | $0.14 | $0.10 | 1m | claude-sonnet-5-5 |
| implement-plan-module | 1 | $2.26 | 11% | $1.10 | $0.62 | $0.54 | 6m | claude-opus-5-5 |
| stabilize | 5 | $0.20 | 1% | $0.02 | $0.12 | $0.06 | 45s | claude-sonnet-5-5 |
| red-unit | 2 | $0.27 | 1% | $0.02 | $0.10 | $0.15 | 1m | claude-sonnet-5-5 |
| green-unit | 3 | $0.17 | 1% | $0.02 | $0.10 | $0.05 | 54s | claude-sonnet-5-5 |
| refactor | 1 | $0.65 | 3% | $0.21 | $0.27 | $0.17 | 2m | claude-opus-5-5 |

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
| session 6ad52699 (own turns) | 181 | 386 | 112k | 553k | 34.1M | 332k | 33% |
| session c705dc2b (own turns) | 15 | 32 | 8k | 41k | 1.3M | 105k | 11% |
| grill-design | 39 | 78 | 24k | 119k | 2.8M | 119k | 12% |
| review-plan | 26 | 54 | 11k | 55k | 984k | 55k | 6% |
| review-plan-facts | 9 | 18 | 10k | 57k | 302k | 57k | 6% |
| implement-plan-module | 58 | 136 | 27k | 125k | 5.5M | 125k | 13% |
| stabilize | 19 | 38 | 6k | 48k | 215k | 18k | 2% |
| red-unit | 13 | 26 | 15k | 42k | 224k | 31k | 3% |
| green-unit | 14 | 28 | 5k | 40k | 187k | 23k | 2% |
| refactor | 17 | 34 | 8k | 55k | 1.1M | 76k | 8% |

- `cache read`: the conversation prefix, re-read from cache on each turn, not new tokens. It grows
  with roughly the square of the turn count.
- `peak ctx`: the largest one message's input + cache write + cache read, the most context the
  agent carried at once. A type's is its largest agent's.
- `of window`: `peak ctx` against the model's context window.

| plan | agents | $ | time |
|------|--------|---|------|
| `plan.md` | 14 | $4.54 | 15m |

## Task total

| $ | span |
|---|------|
| $20.40 | 188m (12:30 → 15:38) |
