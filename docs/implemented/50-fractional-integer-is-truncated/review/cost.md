# Cost · 50-fractional-integer-is-truncated

Written by `scripts/cost/cost.sh report` at 2026-10-13 14:29:28 (UTC+02:00, the latest record's offset).
Re-run the script rather than edit this file.
Rates: fetched 2026-10-08.

## Cost

| agent | n | $ | % | read $ | write $ | out $ | time | model |
|-------|---|---|---|--------|---------|-------|------|-------|
| session (own turns) | 1 | $14.82 | 99% | $8.84 | $3.77 | $2.21 | 885m | claude-opus-5-5 |
| fix-bug-module | 1 | $0.18 | 1% | $0.04 | $0.08 | $0.05 | 5m | claude-sonnet-5-5 |

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
| session (own turns) | 177 | 376 | 111k | 472k | 44.2M | 418k | 42% |
| fix-bug-module | 18 | 36 | 5k | 34k | 435k | 34k | 3% |

- `cache read`: the conversation prefix, re-read from cache on each turn, not new tokens. It grows
  with roughly the square of the turn count.
- `peak ctx`: the largest one message's input + cache write + cache read, the most context the
  agent carried at once. A type's is its largest agent's.
- `of window`: `peak ctx` against the model's context window.

| plan | agents | $ | time |
|------|--------|---|------|
| `fix.md` | 1 | $0.18 | 5m |

## Task total

| $ | span |
|---|------|
| $15.00 | 885m (23:44 → 14:29) |
