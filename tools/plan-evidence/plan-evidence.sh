#!/usr/bin/env bash
#
# Produces the evidence file that a finished plan's test suite and coverage were actually measured,
# next to the plan itself. Every number in it comes from a run this script performed.
#
# See tools/plan-evidence/README.md for what the evidence proves and where that stops.

set -u

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Derived from the script's own location rather than from git, so that every path here is spelled the
# way the test runner spells it — on Windows `git rev-parse --show-toplevel` answers `C:/…` instead.
repo_root="$(cd "$script_dir/../.." && pwd)"

plan=""
modules=()
verify=0
wait_seconds=900

usage() {
    cat <<'EOF'
Usage:
  tools/plan-evidence/plan-evidence.sh --plan docs/implemented/15-a-task/plan.md
  tools/plan-evidence/plan-evidence.sh --plan docs/15-a-task/plan.md --module ledger-service
  tools/plan-evidence/plan-evidence.sh --plan docs/implemented/15-a-task/plan.md --verify

Options:
  --plan <path>     Required. The plan file. evidence.md and evidence.json are written beside it.
  --module <name>   Module to measure; may be repeated. Default: every module in the repository,
                    because "the plan is finished" is a claim about the whole tree, not one module.
  --verify          Measure nothing. Read the evidence already beside the plan and report whether it
                    still describes HEAD. Use it to detect an evidence file that has gone stale or
                    was edited by hand.
  --wait <seconds>  Passed to the test runner's queue. Default 900.

Exit codes: 0 verified - 1 not verified (a failure, coverage below minimum, or stale evidence)
            2 the run never started (bad usage, missing plan, no modules).
EOF
}

while [ $# -gt 0 ]; do
    case "$1" in
        --plan)    plan="${2:-}"; shift 2 ;;
        --module)  modules+=("${2:-}"); shift 2 ;;
        --wait)    wait_seconds="${2:-}"; shift 2 ;;
        --verify)  verify=1; shift ;;
        -h|--help) usage; exit 0 ;;
        *)         echo "Unknown option: $1 (try --help)" >&2; exit 2 ;;
    esac
done

if [ -z "$plan" ]; then
    echo "--plan <path> is required." >&2
    usage >&2
    exit 2
fi

case "$plan" in
    /*) plan_abs="$plan" ;;
    *)  plan_abs="$repo_root/$plan" ;;
esac

if [ ! -f "$plan_abs" ]; then
    echo "Plan file not found: $plan_abs" >&2
    exit 2
fi

plan_dir="$(cd "$(dirname "$plan_abs")" && pwd)"
plan_rel="${plan_abs#"$repo_root/"}"
evidence_md="$plan_dir/evidence.md"
evidence_json="$plan_dir/evidence.json"

head_sha="$(git -C "$repo_root" rev-parse --short HEAD 2>/dev/null || echo "unknown")"
branch="$(git -C "$repo_root" rev-parse --abbrev-ref HEAD 2>/dev/null || echo "unknown")"
dirty_count="$(git -C "$repo_root" status --porcelain 2>/dev/null | grep -vc "^$" || true)"
[ -n "$dirty_count" ] || dirty_count=0

# --verify reads; it never measures. Re-measuring is what a plain run does, and conflating the two
# would mean the check that evidence is stale could quietly replace the stale evidence.
if [ "$verify" = "1" ]; then
    if [ ! -f "$evidence_md" ]; then
        echo "No evidence beside the plan: $evidence_md"
        echo "Generate it with the same command without --verify."
        exit 1
    fi
    recorded_sha="$(sed -n 's/^| Commit *| `\([^`]*\)`.*/\1/p' "$evidence_md" | head -n 1)"
    recorded_verdict="$(sed -n 's/^\*\*Verdict: \(.*\)\*\*$/\1/p' "$evidence_md" | head -n 1)"
    echo "Evidence:        ${evidence_md#"$repo_root/"}"
    echo "Recorded commit: ${recorded_sha:-none}"
    echo "HEAD:            $head_sha"
    echo "Recorded verdict: ${recorded_verdict:-none}"
    if [ "$recorded_sha" != "$head_sha" ]; then
        # Committing the evidence itself moves HEAD, and so does every documentation commit after it,
        # so a bare SHA comparison would call fresh evidence stale. What matters is whether any code
        # has changed since it was measured.
        if ! changed="$(git -C "$repo_root" diff --name-only "$recorded_sha..HEAD" 2>/dev/null)"; then
            echo ""
            echo "STALE: the commit the evidence names is not in this history."
            exit 1
        fi
        if printf '%s\n' "$changed" | grep -q -v -e '^docs/' -e '^$'; then
            echo ""
            echo "STALE: code has changed since the evidence was measured."
            exit 1
        fi
        echo "Since then:       documentation only, no code"
    fi
    case "$recorded_verdict" in
        VERIFIED*) echo ""; echo "CURRENT: the evidence describes HEAD."; exit 0 ;;
        *)         echo ""; echo "NOT VERIFIED: $recorded_verdict"; exit 1 ;;
    esac
fi

# A module is a directory holding a Gradle wrapper. Sorted, so two runs list them the same way.
if [ ${#modules[@]} -eq 0 ]; then
    while IFS= read -r candidate; do
        modules+=("$candidate")
    done < <(cd "$repo_root" && find . -maxdepth 2 -name gradlew -not -path "./.git/*" \
        | sed 's|^\./||; s|/gradlew$||' | sort)
fi

if [ ${#modules[@]} -eq 0 ]; then
    echo "No modules to measure: none of the directories at the repository root holds a gradlew." >&2
    exit 2
fi

work_dir="$(mktemp -d)"
trap 'rm -rf "$work_dir"' EXIT INT TERM

# Everything measured is accumulated here as one tab-separated row per module, so the report is
# rendered in one pass at the end and the JSON is rendered from the same rows.
rows="$work_dir/modules.tsv"
: > "$rows"
laggards="$work_dir/laggards.tsv"
: > "$laggards"

ratio() {
    awk -v missed="$1" -v covered="$2" 'BEGIN {
        total = missed + covered
        if (total == 0) { print "n/a"; exit }
        printf "%.1f%%", 100 * covered / total
    }'
}

overall="VERIFIED"
skipped_seen=0

for module in "${modules[@]}"; do
    echo "Measuring $module..." >&2
    run_output="$work_dir/$module.out"
    mkdir -p "$(dirname "$run_output")"
    "$script_dir/../agent-test/agent-test.sh" --module "$module" --coverage --brief \
        --wait "$wait_seconds" > "$run_output" 2>&1
    run_exit=$?

    run_dir="$(sed -n 's/^Run directory: \([^ ]*\).*/\1/p' "$run_output" | head -n 1)"
    summary="$repo_root/$run_dir/summary.txt"
    if [ -f "$summary" ]; then
        verdict="$(sed -n 's/^Result: //p' "$summary" | head -n 1)"
        totals_line="$(grep '^Totals:' "$summary" | head -n 1)"
    else
        verdict="NOT RUN"
        totals_line=""
    fi
    [ -n "$verdict" ] || verdict="NOT RUN"

    tests=$(echo "$totals_line" | sed -n 's/.*Totals: \([0-9]*\) tests.*/\1/p')
    passed=$(echo "$totals_line" | sed -n 's/.*, \([0-9]*\) passed.*/\1/p')
    failed=$(echo "$totals_line" | sed -n 's/.*, \([0-9]*\) failed.*/\1/p')
    skipped=$(echo "$totals_line" | sed -n 's/.*, \([0-9]*\) skipped.*/\1/p')
    for name in tests passed failed skipped; do
        eval "[ -n \"\$$name\" ] || $name=0"
    done

    minimum="$(sed -n 's/^coverageMinimum=//p' "$repo_root/$module/gradle.properties" | head -n 1)"
    [ -n "$minimum" ] || minimum="none"
    minimum_percent="$(awk -v m="$minimum" 'BEGIN { if (m == "none") print "n/a"; else printf "%.0f%%", m * 100 }')"

    csv="$repo_root/$module/build/reports/jacoco/test/jacocoTestReport.csv"
    instructions="n/a"
    branches="n/a"
    if [ -f "$csv" ]; then
        counters="$(awk -F, 'NR > 1 { im += $4; ic += $5; bm += $6; bc += $7 } END { print im, ic, bm, bc }' "$csv")"
        set -- $counters
        instructions="$(ratio "$1" "$2")"
        branches="$(ratio "$3" "$4")"

        # The classes a reader would look at first: lowest instruction coverage, ties broken by name
        # so the list is identical between two runs of the same code. A fully covered class is left
        # out — a table padded to ten rows with 100% entries hides the one row worth reading.
        awk -F, -v module="$module" 'NR > 1 && $4 > 0 && ($4 + $5) > 0 {
            printf "%s\t%s.%s\t%.4f\t%d\n", module, $2, $3, $5 / ($4 + $5), $4
        }' "$csv" | sort -t "$(printf '\t')" -k3,3n -k2,2 | head -n 10 >> "$laggards"
    fi

    if [ "$run_exit" != "0" ]; then
        overall="NOT VERIFIED"
    fi
    if [ "$skipped" != "0" ]; then
        skipped_seen=1
    fi

    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
        "$module" "$verdict" "$tests" "$passed" "$failed" "$skipped" \
        "$instructions" "$branches" "$minimum_percent" "$run_dir" >> "$rows"
done

if [ "$dirty_count" != "0" ]; then
    overall="UNVERIFIED (uncommitted changes)"
elif [ "$overall" = "VERIFIED" ] && [ "$skipped_seen" = "1" ]; then
    overall="VERIFIED WITH SKIPPED TESTS"
fi

if [ "$dirty_count" = "0" ]; then
    tree_state="clean"
else
    tree_state="$dirty_count uncommitted file(s)"
fi

generated="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
task_name="$(basename "$plan_dir")"
module_list="$(printf '%s ' "${modules[@]}")"

{
    echo "# Implementation Evidence — $task_name"
    echo ""
    echo "Generated by \`tools/plan-evidence/plan-evidence.sh\`, which measured every number below by"
    echo "running the suite itself. It is rewritten in full on each run; a hand-edited line is"
    echo "indistinguishable from a measured one, so do not edit it."
    echo ""
    echo "**Verdict: $overall**"
    echo ""
    echo "| Field        | Value |"
    echo "|--------------|-------|"
    echo "| Plan         | \`$plan_rel\` |"
    echo "| Commit       | \`$head_sha\` on \`$branch\` |"
    echo "| Working tree | $tree_state |"
    echo "| Generated    | $generated |"
    echo "| Reproduce    | \`tools/plan-evidence/plan-evidence.sh --plan $plan_rel\` |"
    echo ""
    echo "## Modules"
    echo ""
    echo "| Module | Verdict | Tests | Passed | Failed | Skipped | Instructions | Branches | Minimum |"
    echo "|--------|---------|-------|--------|--------|---------|--------------|----------|---------|"
    while IFS="$(printf '\t')" read -r module verdict tests passed failed skipped instructions branches minimum run_dir; do
        echo "| $module | $verdict | $tests | $passed | $failed | $skipped | $instructions | $branches | $minimum |"
    done < "$rows"

    if [ "$skipped_seen" = "1" ]; then
        echo ""
        echo "Some tests were skipped. For the container-based suites that normally means Docker was not"
        echo "running, and a green verdict then covers less than it appears to."
    fi

    if [ -s "$laggards" ]; then
        echo ""
        echo "## Least-covered classes"
        echo ""
        echo "Fully covered classes are omitted; an empty section above means there were none left to name."
        echo ""
        echo "| Module | Class | Instructions | Missed |"
        echo "|--------|-------|--------------|--------|"
        sort -t "$(printf '\t')" -k3,3n -k2,2 "$laggards" | head -n 10 \
            | while IFS="$(printf '\t')" read -r module class covered missed; do
                percent="$(awk -v c="$covered" 'BEGIN { printf "%.1f%%", c * 100 }')"
                echo "| $module | \`$class\` | $percent | $missed |"
            done
    fi

    echo ""
    echo "## Runs"
    echo ""
    while IFS="$(printf '\t')" read -r module verdict tests passed failed skipped instructions branches minimum run_dir; do
        echo "- \`$module\` — \`$run_dir\` (console log, JUnit XML, HTML report; under \`build/\`, so it is not committed)"
    done < "$rows"
} > "$evidence_md"

{
    echo "{"
    echo "  \"plan\": \"$plan_rel\","
    echo "  \"commit\": \"$head_sha\","
    echo "  \"branch\": \"$branch\","
    echo "  \"workingTree\": \"$tree_state\","
    echo "  \"generated\": \"$generated\","
    echo "  \"verdict\": \"$overall\","
    echo "  \"modules\": ["
    first=1
    while IFS="$(printf '\t')" read -r module verdict tests passed failed skipped instructions branches minimum run_dir; do
        [ "$first" = "1" ] || echo ","
        first=0
        printf '    {"module": "%s", "verdict": "%s", "tests": %s, "passed": %s, "failed": %s, "skipped": %s, "instructions": "%s", "branches": "%s", "minimum": "%s"}' \
            "$module" "$verdict" "$tests" "$passed" "$failed" "$skipped" "$instructions" "$branches" "$minimum"
    done < "$rows"
    echo ""
    echo "  ]"
    echo "}"
} > "$evidence_json"

echo "Wrote ${evidence_md#"$repo_root/"} and ${evidence_json#"$repo_root/"}"
echo ""
echo "Verdict: $overall  (modules: $module_list)"

case "$overall" in
    VERIFIED*) exit 0 ;;
    *)         exit 1 ;;
esac
