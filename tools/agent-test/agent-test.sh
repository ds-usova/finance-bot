#!/usr/bin/env bash
#
# The single entry point for compiling and testing a module, selected with --module.
#
# It exists so that a run is reported as a ready-made summary instead of console output that each
# caller has to parse for itself, and so that concurrent runs cannot overwrite each other's results.
#
# See tools/agent-test/README.md for what the runner guarantees and where those guarantees stop.

set -u

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/../.." && pwd)"

module=""
patterns=()
label=""
mode="test"
lock_wait=540
use_lock=1
keep_runs=20

usage() {
    cat <<'EOF'
Usage:
  tools/agent-test/agent-test.sh --module <name> --compile
  tools/agent-test/agent-test.sh --module <name> --tests "bot.finance.application.usecase.HandleIncomingMessageUseCaseTest"
  tools/agent-test/agent-test.sh --module <name> --tests "bot.finance.architecture.CleanArchitectureTest"
  tools/agent-test/agent-test.sh --module <name> --all

Options:
  --module <name>     Required. Module directory at the repository root to compile and test.
  --tests <pattern>   JUnit pattern to run; may be repeated. Omit (or --all) to run the whole suite.
                      Prefer a fully qualified class name: a pattern containing ** also drags in the
                      architecture tests, which ignore Gradle's filter.
  --compile           Compile main and test sources only; run no tests.
  --label <name>      Prefix of the run directory under build/agent-runs. Defaults to the test class.
  --wait <seconds>    How long to wait for another run to finish before giving up. Default 540.
  --no-lock           Start immediately even if another run is in progress. Results stay separate,
                      but the runs compete for build/classes, Docker, and RAM.
  --keep <count>      Previous run directories to keep. Older ones are deleted as a run starts.
                      Default 20; the current run's own directory is never counted.

Output:
  A summary on stdout, plus the same text in <run directory>/summary.txt next to the raw console
  log, the JUnit XML, and the HTML report. The run directory is unique per invocation.

Exit codes: 0 all tests passed - 1 tests failed or the build broke - 2 the run never started.
EOF
}

while [ $# -gt 0 ]; do
    case "$1" in
        --module)   module="${2:-}"; shift 2 ;;
        --tests)    patterns+=("${2:-}"); shift 2 ;;
        --label)    label="${2:-}"; shift 2 ;;
        --wait)     lock_wait="${2:-}"; shift 2 ;;
        --keep)     keep_runs="${2:-}"; shift 2 ;;
        --compile)  mode="compile"; shift ;;
        --all)      mode="test"; shift ;;
        --no-lock)  use_lock=0; shift ;;
        -h|--help)  usage; exit 0 ;;
        *)          echo "Unknown option: $1 (try --help)" >&2; exit 2 ;;
    esac
done

if [ -z "$module" ]; then
    echo "--module <name> is required." >&2
    usage >&2
    exit 2
fi

module_dir="$repo_root/$module"
runs_root="$module_dir/build/agent-runs"

if [ ! -d "$module_dir" ]; then
    echo "Module directory not found: $module_dir" >&2
    exit 2
fi

# The last package segment before any wildcard: "bot.finance.application.**" labels itself
# "application" rather than the "**" a plain suffix strip would leave behind.
derive_label() {
    local pattern="${1%%[*]*}"
    pattern="${pattern%.}"
    printf '%s' "${pattern##*.}"
}

if [ -z "$label" ]; then
    if [ "$mode" = "compile" ]; then
        label="compile"
    elif [ ${#patterns[@]} -gt 0 ]; then
        label="$(derive_label "${patterns[0]}")"
    else
        label="suite"
    fi
fi
label="$(printf '%s' "$label" | tr -c 'A-Za-z0-9._-' '-')"
[ -n "$label" ] || label="run"

mkdir -p "$runs_root" || exit 2

# Oldest first by modification time, so a run still in flight is never the one that gets dropped.
# This runs before the current run's own directory exists, so --keep counts previous runs only.
prune_old_runs() {
    local dir
    ls -1dt "$runs_root"/*/ 2>/dev/null \
        | grep -v '/\.lock/$' \
        | tail -n +"$(( keep_runs + 1 ))" \
        | while IFS= read -r dir; do rm -rf "$dir"; done
}
prune_old_runs

# Every invocation gets a directory of its own, so two agents that pick the same label still cannot
# delete each other's results while a run is reading them.
run_dir="$runs_root/$label-$(date +%Y%m%d-%H%M%S)-$$"
mkdir -p "$run_dir" || exit 2
console_log="$run_dir/console.log"
summary_file="$run_dir/summary.txt"

# Unique run directories keep the reports apart, but build/classes, the Gradle caches, Docker, and
# this machine's RAM are still shared, so runs queue on a directory-based lock.
lock_dir="$runs_root/.lock"
lock_grace=30
lock_age_limit=900
have_lock=0

release_lock() {
    if [ "$have_lock" = "1" ]; then
        rm -rf "$lock_dir" 2>/dev/null
        have_lock=0
    fi
}
trap release_lock EXIT INT TERM

report_not_run() {
    {
        echo "Result: NOT RUN"
        echo "Gradle exit code: n/a"
        echo ""
        echo "$1"
    } > "$summary_file"
    echo "Run directory: ${run_dir#"$repo_root/"}"
    echo ""
    cat "$summary_file"
    exit 2
}

# mkdir stamps the directory before its holder can write any metadata into it, so the directory's
# own mtime is the one timestamp that is always readable, and a lock younger than the grace period
# is treated as live no matter what it contains.
lock_is_stale() {
    local created age pid
    created="$(stat -c %Y "$lock_dir" 2>/dev/null || echo "")"
    [ -n "$created" ] || return 1
    age=$(( $(date +%s) - created ))
    [ "$age" -gt "$lock_grace" ] || return 1

    pid="$(cat "$lock_dir/pid" 2>/dev/null || echo "")"
    if [ -n "$pid" ] && ! kill -0 "$pid" 2>/dev/null; then
        return 0
    fi
    [ "$age" -gt "$lock_age_limit" ]
}

acquire_lock() {
    local waited=0
    local announced=0
    local breaks=0
    while ! mkdir "$lock_dir" 2>/dev/null; do
        if lock_is_stale; then
            if [ "$breaks" -ge 3 ]; then
                report_not_run "A stale lock at $lock_dir could not be removed after three attempts.
Delete that directory by hand, or pass --no-lock."
            fi
            echo "Breaking a stale lock left behind by an earlier run." >&2
            rm -rf "$lock_dir" 2>/dev/null
            breaks=$(( breaks + 1 ))
        elif [ "$announced" = "0" ]; then
            echo "Waiting for the run started by $(cat "$lock_dir/label" 2>/dev/null || echo 'another agent') to finish..." >&2
            announced=1
        fi

        sleep 5
        waited=$(( waited + 5 ))
        if [ "$waited" -ge "$lock_wait" ]; then
            report_not_run "Gave up after ${lock_wait}s waiting for another test run to finish.
Retry with a longer --wait, or pass --no-lock to accept running alongside it."
        fi
    done
    have_lock=1
    printf '%s' "$$" > "$lock_dir/pid"
    printf '%s' "$label" > "$lock_dir/label"
}

if [ "$use_lock" = "1" ]; then
    acquire_lock
fi

gradle_args=(--console=plain --stacktrace)
if [ "$mode" = "compile" ]; then
    gradle_args+=(compileJava compileTestJava)
else
    gradle_args+=(-I "$script_dir/agent-reports.gradle" "-DagentRunDir=$run_dir")

    # The ArchUnit engine ignores Gradle's --tests filter, so a filtered run would otherwise also
    # report architecture failures the caller did not ask about and did not cause.
    if [ ${#patterns[@]} -gt 0 ]; then
        wants_architecture=0
        for pattern in "${patterns[@]}"; do
            case "$pattern" in *[Aa]rchitecture*) wants_architecture=1 ;; esac
        done
        [ "$wants_architecture" = "1" ] || gradle_args+=(-DagentExcludeArchUnit=true)
    fi

    gradle_args+=(test)
    for pattern in ${patterns[@]+"${patterns[@]}"}; do
        gradle_args+=(--tests "$pattern")
    done
fi

command_line="gradlew ${gradle_args[*]}"

cd "$module_dir" || exit 2
if [ -x ./gradlew ]; then
    ./gradlew "${gradle_args[@]}" > "$console_log" 2>&1
else
    ./gradlew.bat "${gradle_args[@]}" > "$console_log" 2>&1
fi
exit_code=$?

release_lock

if [ "$mode" = "compile" ]; then
    {
        if [ "$exit_code" = "0" ]; then
            echo "Result: COMPILES"
        else
            echo "Result: COMPILE ERROR"
        fi
        echo "Command: $command_line"
        echo "Gradle exit code: $exit_code"
    } > "$summary_file"
else
    result_files=("$run_dir"/test-results/TEST-*.xml)
    if [ ! -e "${result_files[0]}" ]; then
        result_files=()
    fi
    awk -v command="$command_line" -v exitCode="$exit_code" \
        -f "$script_dir/junit-summary.awk" ${result_files[@]+"${result_files[@]}"} < /dev/null \
        > "$summary_file"
fi

if [ "$use_lock" = "0" ]; then
    echo "Note: ran with --no-lock, so another run may have been compiling into build/classes." >> "$summary_file"
fi

# Compilation errors and Gradle's own failures never reach the JUnit XML, so lift them out of the
# console log — they are the whole story when the build never got as far as running a test.
if [ "$exit_code" != "0" ]; then
    {
        echo ""
        echo "== Build output =="
        grep -E "error:|^e: |FAILURE:|What went wrong|^> " "$console_log" \
            | grep -v "There were failing tests" \
            | head -n 25
    } >> "$summary_file"
fi

echo "Run directory: ${run_dir#"$repo_root/"}  (console.log, test-results/, test-report/)"
echo ""
cat "$summary_file"

if [ "$exit_code" = "0" ]; then exit 0; fi
exit 1
