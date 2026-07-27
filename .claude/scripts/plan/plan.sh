#!/usr/bin/env bash
#
# Reads and updates a plan's checklist by item ID, so that ticking a box or pulling out one step is
# an addressed operation rather than a text match against a wrapped bullet in a thousand-line file.
#
# The plan stays the single source of truth: nothing here stores state beside it, and the dependency
# graph is parsed out of the "after:" fields on demand rather than kept in a second file.
#
# See the README next to this script.

set -u

# The parser ships beside this script and is found relative to it. The project is not: installed as
# a plugin, this file sits in a cache directory outside any checkout, so the plan is located from
# where the command was run rather than from where the script lives.
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
parser="$script_dir/plan-parse.awk"
repo_root="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"

plan_file=""

usage() {
    cat <<'EOF'
Usage:
  <plugin>/scripts/plan/plan.sh status   [--file <plan>]
  <plugin>/scripts/plan/plan.sh next     [--group <g>] [--section <s>]... [--all] [--file <plan>]
  <plugin>/scripts/plan/plan.sh show     <ID> [--file <plan>]
  <plugin>/scripts/plan/plan.sh tick     <ID> [--file <plan>]
  <plugin>/scripts/plan/plan.sh block    <ID> <note> [--file <plan>]
  <plugin>/scripts/plan/plan.sh validate [--file <plan>]

Commands:
  status    Done/total per group, and the IDs still open.
  next      Items whose "after:" dependencies are all ticked, longest remaining chain first.
            --all also lists the items that are still waiting, and on what.
            --group and --section confine it to part of the plan, matched case-insensitively on any
            part of the heading. A run covering one phase must pass --group, or the phase after it
            becomes eligible the moment this one is finished. --section may be repeated.
  show      One item: its header and everything indented under it.
  tick      Mark the item done.
  block     Leave the item open and record the reason under Open Questions / Blockers.
  validate  Duplicate IDs, items with no ID, dependencies on IDs nothing defines, and cycles.

--file defaults to the single docs/<n>-plan-<name>.md, the location and naming the conventions give
plans in flight. Archived plans under docs/implemented/ are addressed by passing --file explicitly.

Exit codes: 0 done - 1 nothing matched, or validate found problems - 2 bad usage.
EOF
}

die() {
    echo "$1" >&2
    exit "${2:-2}"
}

# In-place editing is done by rewriting through a sibling temp file rather than with `sed -i`, whose
# spelling differs between GNU and BSD: on BSD the flag takes the backup suffix as its argument, so
# the GNU form silently means something else. The temp file is a sibling so the move stays on one
# filesystem.
rewrite_plan() {
    local tmp="${plan_file}.plan-tmp.$$"
    if "$@" > "$tmp" && mv "$tmp" "$plan_file"; then
        return 0
    fi
    rm -f "$tmp"
    die "could not write $plan_file"
}

resolve_plan() {
    local candidates=()
    if [ -n "$plan_file" ]; then
        [ -f "$plan_file" ] || die "no such plan file: $plan_file"
        return 0
    fi
    while IFS= read -r f; do
        candidates+=("$f")
    done < <(find "$repo_root/docs" -maxdepth 1 -name '[0-9]*-plan-*.md' -type f 2>/dev/null | sort)

    case "${#candidates[@]}" in
        0) die "no <n>-plan-<name>.md in $repo_root/docs - pass --file <plan>" ;;
        1) plan_file="${candidates[0]}" ;;
        *)
            {
                echo "docs/ holds ${#candidates[@]} markdown files - pass --file <plan>:"
                printf '  %s\n' "${candidates[@]#"$repo_root/"}"
            } >&2
            exit 2
            ;;
    esac
}

item_range() {
    awk -f "$parser" -v mode=range -v want="$1" "$plan_file"
}

command="${1:-}"
[ -n "$command" ] || { usage; exit 2; }
shift

args=()
verbose=0
group_filter=""
section_filter=""
while [ $# -gt 0 ]; do
    case "$1" in
        --file)    plan_file="${2:-}"; shift 2 ;;
        --group)   group_filter="${2:-}"; shift 2 ;;
        --section) section_filter="${section_filter:+$section_filter,}${2:-}"; shift 2 ;;
        --all)     verbose=1; shift ;;
        -h|--help) usage; exit 0 ;;
        *) args+=("$1"); shift ;;
    esac
done

case "$command" in
    status)
        resolve_plan
        awk -f "$parser" -v mode=status "$plan_file"
        ;;

    next)
        resolve_plan
        awk -f "$parser" -v mode=next -v verbose="$verbose" \
            -v group_filter="$group_filter" -v section_filter="$section_filter" "$plan_file"
        ;;

    validate)
        resolve_plan
        awk -f "$parser" -v mode=validate "$plan_file"
        ;;

    show)
        id="${args[0]:-}"
        [ -n "$id" ] || die "show needs an item ID"
        resolve_plan
        range="$(item_range "$id")" || die "no item $id in ${plan_file#"$repo_root/"}" 1
        [ -n "$range" ] || die "no item $id in ${plan_file#"$repo_root/"}" 1
        sed -n "${range% *},${range#* }p" "$plan_file"
        ;;

    tick)
        id="${args[0]:-}"
        [ -n "$id" ] || die "tick needs an item ID"
        resolve_plan
        range="$(item_range "$id")" || die "no item $id in ${plan_file#"$repo_root/"}" 1
        [ -n "$range" ] || die "no item $id in ${plan_file#"$repo_root/"}" 1
        line="${range% *}"
        if sed -n "${line}p" "$plan_file" | grep -q '^- \[[xX]\]'; then
            echo "$id was already ticked"
            exit 0
        fi
        rewrite_plan awk -v n="$line" 'NR == n { sub(/^- \[ \]/, "- [x]") } { print }' "$plan_file"
        sed -n "${line}p" "$plan_file"
        ;;

    block)
        id="${args[0]:-}"
        note="${args[1]:-}"
        [ -n "$id" ] && [ -n "$note" ] || die "block needs an item ID and a note"
        resolve_plan
        range="$(item_range "$id")" || die "no item $id in ${plan_file#"$repo_root/"}" 1
        [ -n "$range" ] || die "no item $id in ${plan_file#"$repo_root/"}" 1

        # Appended at the end of the Open Questions / Blockers section, so the record sits with the
        # questions the plan already owes an answer to rather than at the bottom of the file.
        blockers_start="$(grep -n '^## Open Questions / Blockers' "$plan_file" | head -1 | cut -d: -f1)"
        [ -n "$blockers_start" ] || die "$plan_file has no '## Open Questions / Blockers' section"
        next_section="$(awk -v s="$blockers_start" 'NR > s && /^## / { print NR; exit }' "$plan_file")"
        [ -n "$next_section" ] || next_section="$(( $(wc -l < "$plan_file") + 1 ))"
        insert_at="$(awk -v s="$blockers_start" -v e="$next_section" \
            'NR > s && NR < e && NF { last = NR } END { print (last ? last : s) }' "$plan_file")"

        # The note travels in the environment, not through -v, which would expand escape sequences
        # in whatever the caller wrote.
        entry="- **${id} blocked:** ${note}" \
            rewrite_plan awk -v n="$insert_at" '{ print } NR == n { print ENVIRON["entry"] }' "$plan_file"
        echo "$id left open; recorded under Open Questions / Blockers"
        ;;

    *)
        die "unknown command '$command' (try --help)"
        ;;
esac
