#!/usr/bin/env bash
#
# Runs the evaluation end to end: builds and starts the services it needs, captures the ledger's console for the
# mcp_call lines, waits for both services to report healthy, and runs the evaluation runner with every setting it
# needs read from infrastructure/.env. Everything printed is also saved to build/eval/eval.log. The services are
# stopped afterwards unless they were already running or --keep-stack is given.
#
# See eval-runner/README.md.

set -euo pipefail

usage() {
    cat <<'EOF'
Usage:
  eval-runner/eval.sh [--keep-stack] [runner arguments]  start the stack, then run the corpus
  eval-runner/eval.sh compare <before> <after>           compare two runs; starts nothing

Runner arguments are passed through: --label, --repetitions, --cases, --budget-enforced, --corpus, --out.
--keep-stack leaves the services running after the run.
EOF
}

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

mkdir -p build/eval
exec > >(tee build/eval/eval.log) 2>&1

run_runner() {
    eval-runner/gradlew -p eval-runner --quiet run "--args=$*"
}

case "${1:-}" in
    -h | --help) usage; exit 0 ;;
    compare) run_runner "$@"; exit 0 ;;
esac

keep_stack=0
runner_args=()
for arg in "$@"; do
    if [[ "$arg" == --keep-stack ]]; then
        keep_stack=1
    else
        runner_args+=("$arg")
    fi
done

env_file=infrastructure/.env
if [[ ! -f "$env_file" ]]; then
    printf '%s is missing; copy it from %s.example and fill it in\n' "$env_file" "$env_file" >&2
    exit 2
fi
set -a
# shellcheck disable=SC1090
. <(tr -d '\r' < "$env_file")
set +a

compose=(docker compose --env-file "$env_file"
    -f infrastructure/docker-compose.yaml -f eval-runner/compose.eval.yaml)
services=(finance-postgres redis ledger-service ai-connector-service)
health_timeout="${EVAL_STACK_TIMEOUT_SECONDS:-300}"

if [[ -n "$("${compose[@]}" ps --status running --quiet ledger-service)" ]]; then
    keep_stack=1
fi

log_pid=""
finish() {
    local status=$?
    if [[ -n "$log_pid" ]]; then
        kill "$log_pid" 2> /dev/null || true
    fi
    if ((keep_stack == 0)); then
        printf 'stopping the stack\n'
        "${compose[@]}" stop "${services[@]}" || true
    fi
    if ((status == 0)); then
        printf 'done; the run is in build/eval/, this console in build/eval/eval.log\n'
    else
        printf 'failed with exit code %s; this console is in build/eval/eval.log\n' "$status"
    fi
}
trap finish EXIT

printf 'starting the stack\n'
"${compose[@]}" up --detach --build "${services[@]}"

ledger_log=build/eval/ledger.log
docker logs --follow --since "$(date -u +%Y-%m-%dT%H:%M:%SZ)" finance-bot-ledger-service > "$ledger_log" 2>&1 &
log_pid=$!

await_healthy() {
    local name="$1" url="$2" deadline=$((SECONDS + health_timeout))
    until curl --silent --fail --output /dev/null "$url"; do
        if ((SECONDS >= deadline)); then
            printf '%s did not report healthy at %s within %ss\n' "$name" "$url" "$health_timeout" >&2
            exit 1
        fi
        sleep 2
    done
    printf '%s is healthy\n' "$name"
}
await_healthy ledger-service http://localhost:1010/actuator/health
await_healthy ai-connector-service http://localhost:1011/actuator/health

export EVAL_TELEGRAM_BOT_TOKEN="$FINANCE_BOT_TELEGRAM_BOT_TOKEN"
export EVAL_AI_DB_URL="jdbc:postgresql://localhost:5432/$FINANCE_BOT_AI_DB"
export EVAL_AI_DB_USER="$FINANCE_BOT_AI_DB_USER"
export EVAL_AI_DB_PASSWORD="$FINANCE_BOT_AI_DB_PASSWORD"
export EVAL_LEDGER_LOG="$ledger_log"

run_runner "${runner_args[@]}"
