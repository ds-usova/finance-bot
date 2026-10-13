# Evaluation Runner

Runs the [case corpus](../docs/eval/cases.md) against a running ledger and AI Connector and writes one JSON file per
run. It is the demo cut of the [evaluation plan](../docs/eval/plan.md): verdicts follow
[Oracle and scoring](../docs/eval/scoring.md). Traces, fixture restoration and baselines are left out.

## What It Needs Running

| Service              | Reached at                                        | For                                                         |
|----------------------|---------------------------------------------------|-------------------------------------------------------------|
| Ledger REST          | `EVAL_LEDGER_URL`                                 | signing in, filing messages, reading proposals              |
| Ledger metrics       | `EVAL_LEDGER_METRICS_URL`                         | tool-call deltas                                            |
| AI Connector metrics | `EVAL_CONNECTOR_METRICS_URL`                      | provider chat calls, duration and tokens per target         |
| AI Connector DB      | `EVAL_AI_DB_URL`, `_USER`, `_PASSWORD`            | memory readiness, read only                                 |
| Ledger console       | `EVAL_LEDGER_LOG`, a file the console is saved to | the `mcp_call` lines a case's `mcpCalls` is checked against |

Defaults point at the local ports: ledger `1000`, management `1010` and `1011`, Postgres `5432`. Docker Compose does
not publish the management ports, so a run against the compose stack needs them published.

`EVAL_TELEGRAM_BOT_TOKEN` is required and must equal the ledger's `TELEGRAM_BOT_TOKEN`. Each attempt signs in a new
Telegram user with a payload signed by it, so no case can recall another's history.

`EVAL_LEDGER_LOG` left unset leaves every case that declares `mcpCalls` an `error`. With the compose stack:

```
docker logs -f finance-bot-ledger-service > build/eval/ledger.log 2>&1
```

## Running

```
eval-runner/gradlew -p eval-runner run --args="--label before"
eval-runner/gradlew -p eval-runner run --args="--label after --cases 8-,10-003 --repetitions 1"
eval-runner/gradlew -p eval-runner run --args="compare build/eval/before.json build/eval/after.json"
```

| Argument            | Default                          | Meaning                                                 |
|---------------------|----------------------------------|---------------------------------------------------------|
| `--label`           | `run`                            | names the output, `build/eval/<label>.json`             |
| `--repetitions`     | `3`                              | attempts of each case                                   |
| `--cases`           | every case                       | comma-separated id prefixes                             |
| `--budget-enforced` | off                              | runs cases declaring `executionLimits`; else blocked    |
| `--corpus`, `--out` | `docs/eval/corpus`, `build/eval` | paths from the repository root                          |

`EVAL_TERMINAL_TIMEOUT_SECONDS` and `EVAL_READINESS_TIMEOUT_SECONDS` bound the wait for a terminal outcome and for
memory readiness.

The output is rewritten after every attempt, so an interrupted run keeps what it finished. `compare` writes the two
summaries as a Markdown table to `compare.md` beside the second file, then the cases whose pass count moved.

## Measurements

| Field                                 | Source                                                                                   |
|---------------------------------------|------------------------------------------------------------------------------------------|
| `targetTurnMs`                        | from the accepted `POST /api/v1/messages` to the first `DONE` poll                       |
| `usage.providerChatCalls`, `…Seconds` | `gen_ai_client_operation_seconds` for `chat`, scraped before filing and after inspection |
| `usage.inputTokens`, `outputTokens`   | `gen_ai_client_token_usage_total`, the same window                                       |
| `learning.<id>.availabilityMs`        | from the decision to memory readiness                                                    |
