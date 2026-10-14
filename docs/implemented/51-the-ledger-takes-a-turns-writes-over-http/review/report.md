# Report: The Ledger Takes a Turn's Writes over gRPC

**done and archived, 1 open**

## Done

| Plan                     | Steps    | Suite                                                          |
|--------------------------|----------|----------------------------------------------------------------|
| `shared/plan.md`         | 7 of 7   | ledger-service 1367 green; ai-connector-service 403 green      |
| `ledger-service/plan.md` | 45 of 45 | 1447 green, 0 skipped                                          |
| `eval-runner/plan.md`    | 6 of 6   | 21 green, 0 skipped                                            |

Acceptance: 16 of 16 scenarios proved — [acceptance.md](acceptance.md)

## Measured

Nothing measured: the conventions say performance is not measured.

## Cost

$57.85 · 39 agents · 12 h 38 min — [cost.md](cost.md)

[Agent activity](activity.html)

## Open

1 refactoring candidate — [findings.md](findings.md)

## Manual checks

**[ ] `eval-runner` — a run reads the ledger's call lines and records them under `tool`**

- **Given** the stack `eval-runner/eval.sh` starts, with `TURN_CALL_LOG_ENABLED` on for the ledger
- **When** `eval-runner/eval.sh --cases` runs one case expecting a `create_expense_proposal` call
- **Then** the run file under `build/eval/` holds that call in the attempt's `mcpCalls`, its operation under the key `tool`
