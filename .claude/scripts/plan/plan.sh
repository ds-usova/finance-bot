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
# Git prints a drive-letter path on Windows while `pwd` prints a POSIX one, and comparing a directory
# against its parent needs both in the same spelling.
repo_root_abs="$(cd "$repo_root" && pwd)"

plan_file=""

usage() {
    cat <<'EOF'
Usage:
  <plugin>/scripts/plan/plan.sh status   [--file <plan>]
  <plugin>/scripts/plan/plan.sh next     [--group <g>] [--section <s>]... [--all] [--file <plan>]
  <plugin>/scripts/plan/plan.sh show     <ID>... [--file <plan>]
  <plugin>/scripts/plan/plan.sh tick     <ID>... [--file <plan>]
  <plugin>/scripts/plan/plan.sh block    <ID> <note> [--file <plan>]
  <plugin>/scripts/plan/plan.sh validate [--file <plan>]
  <plugin>/scripts/plan/plan.sh task     [<task directory> | <plan>]

Commands:
  status    Done/total per group, and the IDs still open.
  next      Items whose "after:" dependencies are all ticked, longest remaining chain first.
            --all also lists the items that are still waiting, and on what.
            --group and --section confine it to part of the plan, matched case-insensitively on any
            part of the heading. A run covering one phase must pass --group, or the phase after it
            becomes eligible the moment this one is finished. --section may be repeated.
  show      One item: its header and everything indented under it. Several IDs print in order,
            separated by a blank line.
  tick      Mark the items done. Several IDs are one batch: all are resolved before any is written,
            so a name nothing defines ticks none of them.
  block     Leave the item open and record the reason under Open Questions / Blockers.
  validate  Duplicate IDs, items with no ID, dependencies on IDs nothing defines, cycles, placeholder
            given/when/then values, update: bullets naming a test method that is nowhere in the tree,
            and findings missing a Resolution: or an unapplied mechanical Action:.
  task      Every plan the task holds, its done/total, and whether all of them are finished.
            Takes the task directory, or nothing when only one task is in flight. A plan works
            too, for a caller that has one and not the directory. Exit 0 means nothing is open
            anywhere in the task.

--file defaults to the single plan in flight under docs/. A task owns a directory holding design.md
and one plan per module it touches: plan.md for a single-module task, <module>/plan.md for each
module of a multi-module one. Archived plans under docs/implemented/ are addressed by passing --file
explicitly.

A multi-module task therefore has several plans in flight, and every command names the one it
addresses - the ambiguity is reported, never guessed.

Exit codes: 0 done - 1 nothing matched, validate found problems, or task found something open -
2 bad usage.
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
    # maxdepth 3 so a per-module plan at <n>-<task>/<module>/plan.md is found alongside the
    # single-module <n>-<task>/plan.md.
    done < <(find "$repo_root/docs" -maxdepth 3 -name 'plan.md' -type f \
        -not -path '*/implemented/*' 2>/dev/null | sort)

    case "${#candidates[@]}" in
        0) die "no <n>-<task>/plan.md or <n>-<task>/<module>/plan.md in $repo_root/docs - pass --file <plan>" ;;
        1) plan_file="${candidates[0]}" ;;
        *)
            {
                echo "docs/ holds ${#candidates[@]} plans in flight - pass --file <plan>:"
                printf '  %s\n' "${candidates[@]#"$repo_root/"}"
            } >&2
            exit 2
            ;;
    esac
}

item_range() {
    awk -f "$parser" -v mode=range -v want="$1" "$plan_file"
}

# A task owns one directory directly under docs/, and its plans sit either in it or one level deeper.
# Walking up to that level is exact, where looking for a sibling design.md is not: a task may be
# planned before its design is written, and an archived task keeps the same shape one level lower.
task_dir_of() {
    local dir
    dir="$(cd "$(dirname "$1")" && pwd)"
    while [ "$dir" != "/" ] && [ "$dir" != "$repo_root_abs" ]; do
        local parent
        parent="$(dirname "$dir")"
        if [ "$parent" = "$repo_root_abs/docs" ] || [ "$parent" = "$repo_root_abs/docs/implemented" ]; then
            break
        fi
        dir="$parent"
    done
    echo "$dir"
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
        # A bare plan path is accepted wherever --file is, on every subcommand. Without this the
        # ID-taking ones read it as an ID and fail with "no item docs/x.md in docs/x.md".
        *.md|*/*)
            [ -z "$plan_file" ] || die "plan file given twice: $plan_file and $1"
            plan_file="$1"; shift ;;
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
        problems=0

        # The parser's own checks first: everything answerable from the plan's text alone.
        if ! awk -f "$parser" -v mode=validate -v summary=0 "$plan_file"; then
            problems=1
        fi

        # An "update:" bullet names a test that already exists - that is what distinguishes it from a
        # new scenario. One naming nothing in the tree is a plan written against remembered code.
        #
        # Plan files are excluded from the search, this one above all: it names the method itself, so
        # searching a tree that contains it would confirm every name against the very text under test.
        while IFS="$(printf '\t')" read -r id method; do
            [ -n "${method:-}" ] || continue
            if ! grep -rqI --exclude='plan.md' \
                    --exclude-dir=build --exclude-dir=.git --exclude-dir=.gradle \
                    --exclude-dir=node_modules --exclude-dir=target --exclude-dir=out \
                    -F -e "$method(" "$repo_root" 2>/dev/null; then
                echo "$id names '$method()' in an update: bullet, which exists nowhere in the repository"
                problems=1
            fi
        done < <(awk -f "$parser" -v mode=updates "$plan_file")

        if [ "$problems" -eq 0 ]; then
            echo "$(awk -f "$parser" -v mode=count "$plan_file") items, no problems"
        fi
        exit "$problems"
        ;;

    show)
        [ "${#args[@]}" -gt 0 ] || die "show needs at least one item ID"
        resolve_plan
        first=1
        for id in "${args[@]}"; do
            range="$(item_range "$id")" || die "no item $id in ${plan_file#"$repo_root/"}" 1
            [ -n "$range" ] || die "no item $id in ${plan_file#"$repo_root/"}" 1
            [ "$first" = "0" ] && echo
            first=0
            sed -n "${range% *},${range#* }p" "$plan_file"
        done
        ;;

    tick)
        [ "${#args[@]}" -gt 0 ] || die "tick needs at least one item ID"
        resolve_plan
        # Every ID is resolved before any is written, so a typo in the third leaves the first two
        # alone instead of half-applying a stage's batch.
        lines=()
        for id in "${args[@]}"; do
            range="$(item_range "$id")" || die "no item $id in ${plan_file#"$repo_root/"}" 1
            [ -n "$range" ] || die "no item $id in ${plan_file#"$repo_root/"}" 1
            lines+=("${range% *}")
        done
        # Ticking replaces "- [ ]" with "- [x]" in place, so no line moves and the ranges resolved
        # above stay valid for the whole batch.
        for i in "${!args[@]}"; do
            line="${lines[$i]}"
            if sed -n "${line}p" "$plan_file" | grep -q '^- \[[xX]\]'; then
                echo "${args[$i]} was already ticked"
                continue
            fi
            rewrite_plan awk -v n="$line" 'NR == n { sub(/^- \[ \]/, "- [x]") } { print }' "$plan_file"
            sed -n "${line}p" "$plan_file"
        done
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

    task)
        # Given a plan, a task directory, or nothing at all: which plans the task holds, and whether
        # every one of them is finished. A single plan cannot answer that about the task it belongs
        # to, and archiving the directory is the decision that needs the answer.
        if [ -n "$plan_file" ] && [ -d "$plan_file" ]; then
            task_dir="$(cd "$plan_file" && pwd)"
        elif [ -n "$plan_file" ]; then
            [ -f "$plan_file" ] || die "no such plan file or task directory: $plan_file"
            task_dir="$(task_dir_of "$plan_file")"
        else
            dirs=()
            while IFS= read -r f; do
                d="$(task_dir_of "$f")"
                case " ${dirs[*]-} " in
                    *" $d "*) ;;
                    *) dirs+=("$d") ;;
                esac
            done < <(find "$repo_root_abs/docs" -maxdepth 3 -name 'plan.md' -type f \
                -not -path '*/implemented/*' 2>/dev/null | sort)
            case "${#dirs[@]}" in
                0) die "no task directory under docs/ holds a plan - name one" ;;
                1) task_dir="${dirs[0]}" ;;
                *)
                    {
                        echo "docs/ holds ${#dirs[@]} tasks in flight - name one:"
                        printf '  %s\n' "${dirs[@]#"$repo_root_abs/"}"
                    } >&2
                    exit 2
                    ;;
            esac
        fi

        plans=()
        while IFS= read -r f; do
            plans+=("$f")
        done < <(find "$task_dir" -maxdepth 2 -name 'plan.md' -type f | sort)
        [ "${#plans[@]}" -gt 0 ] || die "${task_dir#"$repo_root_abs/"} holds no plan.md" 1

        echo "${task_dir#"$repo_root_abs/"}"
        unfinished=0
        for f in "${plans[@]}"; do
            read -r done_n total_n < <(awk -f "$parser" -v mode=items "$f" |
                awk -F'\t' '$2 == "done" { d++ } { t++ } END { print (d + 0), (t + 0) }')
            if [ "$total_n" -eq 0 ]; then
                # No IDs at all: either an empty plan or one predating the format. Both are open
                # questions, and neither is something to archive on.
                state="no items - not in plan format"
                unfinished=$(( unfinished + 1 ))
            elif [ "$done_n" -eq "$total_n" ]; then
                state="complete"
            else
                state="$(( total_n - done_n )) open"
                unfinished=$(( unfinished + 1 ))
            fi
            printf '  %-34s %3s/%-3s  %s\n' "${f#"$task_dir/"}" "$done_n" "$total_n" "$state"
        done

        # The verdict is a fact about the plans, not a decision about the directory: what an exit 0
        # authorizes is the calling skill's rule, not this script's.
        if [ "$unfinished" -eq 0 ]; then
            echo "every plan complete - nothing is open in this task"
            exit 0
        fi
        echo "$unfinished of ${#plans[@]} plans still open"
        exit 1
        ;;

    *)
        die "unknown command '$command' (try --help)"
        ;;
esac
