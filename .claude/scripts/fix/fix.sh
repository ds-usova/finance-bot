#!/usr/bin/env bash
#
# Reads and updates a bug fix's checklist by step ID, so that ticking a box or pulling out one step
# is an addressed operation rather than a text match against a wrapped bullet.
#
# The fix file stays the single source of truth: nothing here stores state beside it. There is no
# scheduling command - a fix runs its stabilize steps, then its red ones, then its green ones, and
# that order is the skill's rather than something to compute.
#
# See the README next to this script.

set -u

# The parser ships beside this script and is found relative to it. The project is not: installed as
# a plugin, this file sits in a cache directory outside any checkout, so the fix is located from
# where the command was run rather than from where the script lives.
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
parser="$script_dir/fix-parse.awk"
repo_root="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
# Git prints a drive-letter path on Windows while `pwd` prints a POSIX one, and comparing a directory
# against its parent needs both in the same spelling.
repo_root_abs="$(cd "$repo_root" && pwd)"

fix_file=""

usage() {
    cat <<'EOF'
Usage:
  <plugin>/scripts/fix/fix.sh status   [--file <fix>]
  <plugin>/scripts/fix/fix.sh show     <ID>... [--file <fix>]
  <plugin>/scripts/fix/fix.sh tick     <ID>... [--file <fix>]
  <plugin>/scripts/fix/fix.sh validate [--file <fix>]
  <plugin>/scripts/fix/fix.sh task     [<fix directory> | <fix>]

Commands:
  status    Done/total, and the IDs still open.
  show      One step: its header and everything indented under it. Several IDs print in order,
            separated by a blank line.
  tick      Mark the steps done. Several IDs are one batch: all are resolved before any is written,
            so a name nothing defines ticks none of them.
  validate  Duplicate or missing IDs, an unrecognized kind, a line the kind does not take, a line
            the kind owes and does not carry, a placeholder value, "needs:"/"disables:"/"fixes:"
            pointing at a step nothing defines, an attempt missing its reasoning, its result, its
            evidence or what it rules out, and an unanswered Open Question.
  task      Every fix file the bug holds, its done/total, and whether all of them are finished.

--file defaults to the single fix.md in flight under docs/. A bug owns a directory holding bug.md
and one fix per module it touches: fix.md for a single-module bug, <module>/fix.md for each of
several. An archived one under docs/implemented/ is addressed by passing --file explicitly, and so
is a bug.md, which validate reads for its Attempts section.

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
rewrite_file() {
    local tmp="${fix_file}.fix-tmp.$$"
    if "$@" > "$tmp" && mv "$tmp" "$fix_file"; then
        return 0
    fi
    rm -f "$tmp"
    return 1
}

# maxdepth 3 so a per-module fix at <n>-<bug>/<module>/fix.md is found alongside the single-module
# <n>-<bug>/fix.md.
find_fixes() {
    find "$repo_root_abs/docs" -mindepth 2 -maxdepth 3 -name fix.md -type f \
        -not -path '*/implemented/*' 2>/dev/null | sort
}

locate_fix() {
    if [ -n "$fix_file" ]; then
        [ -f "$fix_file" ] || die "no such file: $fix_file"
        return
    fi

    local found
    found="$(find_fixes)"

    local count
    count="$(printf '%s' "$found" | grep -c . || true)"

    case "$count" in
        0) die "no fix.md in flight under docs/ - name one with --file" ;;
        1) fix_file="$found" ;;
        *) die "several fixes are in flight; name one with --file:
$found" ;;
    esac
}

parse() {
    awk -v mode="$1" -f "$parser" "$fix_file"
}

cmd_status() {
    local total=0 done_count=0 open=""
    while IFS=$'\t' read -r id state _ _ _; do
        [ -n "$id" ] || continue
        total=$((total + 1))
        if [ "$state" = "x" ]; then
            done_count=$((done_count + 1))
        else
            open="${open:+$open }$id"
        fi
    done < <(parse list)

    [ "$total" -gt 0 ] || die "$fix_file defines no steps" 1

    printf '%s\n  %d/%d\n' "$fix_file" "$done_count" "$total"
    if [ -n "$open" ]; then
        printf '  open: %s\n' "$open"
    else
        printf '  every step is ticked\n'
    fi
}

cmd_show() {
    [ "$#" -gt 0 ] || die "show needs at least one ID"

    local first=1 id start end matched
    for id in "$@"; do
        matched=""
        while IFS=$'\t' read -r rid _ _ rstart rend; do
            if [ "$rid" = "$id" ]; then
                start="$rstart"
                end="$rend"
                matched=1
                break
            fi
        done < <(parse list)

        [ -n "$matched" ] || die "no such step: $id" 1

        [ "$first" = 1 ] || echo
        first=0
        sed -n "${start},${end}p" "$fix_file"
    done
}

cmd_tick() {
    [ "$#" -gt 0 ] || die "tick needs at least one ID"

    # Every ID is resolved before any line is written, so a typo ticks nothing.
    local lines="" id start matched
    for id in "$@"; do
        matched=""
        while IFS=$'\t' read -r rid _ _ rstart _; do
            if [ "$rid" = "$id" ]; then
                start="$rstart"
                matched=1
                break
            fi
        done < <(parse list)

        [ -n "$matched" ] || die "no such step: $id" 1
        lines="${lines:+$lines,}$start"
    done

    rewrite_file awk -v targets="$lines" '
        BEGIN { n = split(targets, t, ","); for (i = 1; i <= n; i++) mark[t[i]] = 1 }
        NR in mark { sub(/\[ \]/, "[x]") }
        { print }
    ' "$fix_file" || die "could not write $fix_file"

    echo "ticked: $*"
}

# A bug owns one directory directly under docs/, and its fix files sit either in it or one level
# deeper. Walking up to that level is exact, where looking for a sibling bug.md is not: an archived
# bug keeps the same shape one level lower.
bug_dir_of() {
    local dir parent
    dir="$(cd "$(dirname "$1")" && pwd)"
    while [ "$dir" != "/" ] && [ "$dir" != "$repo_root_abs" ]; do
        parent="$(dirname "$dir")"
        if [ "$parent" = "$repo_root_abs/docs" ] || [ "$parent" = "$repo_root_abs/docs/implemented" ]; then
            break
        fi
        dir="$parent"
    done
    echo "$dir"
}

cmd_task() {
    local given="${1:-}" bug_dir
    if [ -n "$given" ] && [ -d "$given" ]; then
        bug_dir="$(cd "$given" && pwd)"
    elif [ -n "$given" ]; then
        [ -f "$given" ] || die "no such fix file or bug directory: $given"
        bug_dir="$(bug_dir_of "$given")"
    else
        local dirs=() f d
        while IFS= read -r f; do
            [ -n "$f" ] || continue
            d="$(bug_dir_of "$f")"
            case " ${dirs[*]:-} " in
                *" $d "*) ;;
                *) dirs+=("$d") ;;
            esac
        done < <(find_fixes)

        case "${#dirs[@]}" in
            0) die "no bug directory under docs/ holds a fix - name one" ;;
            1) bug_dir="${dirs[0]}" ;;
            *)
                echo "docs/ holds ${#dirs[@]} bugs in flight - name one:" >&2
                printf '  %s\n' "${dirs[@]#"$repo_root_abs/"}" >&2
                exit 2
                ;;
        esac
    fi

    local fixes=() f
    while IFS= read -r f; do
        [ -n "$f" ] || continue
        fixes+=("$f")
    done < <(find "$bug_dir" -maxdepth 2 -name 'fix.md' -type f | sort)
    [ "${#fixes[@]}" -gt 0 ] || die "${bug_dir#"$repo_root_abs/"} holds no fix.md" 1

    echo "${bug_dir#"$repo_root_abs/"}"

    local open_total=0 total_n done_n state id st
    for f in "${fixes[@]}"; do
        fix_file="$f"
        total_n=0
        done_n=0
        while IFS=$'\t' read -r id st _ _ _; do
            [ -n "$id" ] || continue
            total_n=$((total_n + 1))
            [ "$st" = "x" ] && done_n=$((done_n + 1))
        done < <(parse list)

        if [ "$total_n" -gt 0 ] && [ "$done_n" -eq "$total_n" ]; then
            state="complete"
        else
            state="open"
            open_total=$((open_total + 1))
        fi
        printf '  %-34s %3s/%-3s  %s\n' "${f#"$bug_dir/"}" "$done_n" "$total_n" "$state"
    done

    if [ "$open_total" -gt 0 ]; then
        echo "$open_total of ${#fixes[@]} still open"
        exit 1
    fi
    echo "every fix complete - nothing is open in this bug"
}

command="${1:-}"
[ -n "$command" ] || { usage; exit 2; }
shift || true

args=()
while [ "$#" -gt 0 ]; do
    case "$1" in
        --file)
            [ "$#" -ge 2 ] || die "--file needs a path"
            fix_file="$2"
            shift 2
            ;;
        --help|-h)
            usage
            exit 0
            ;;
        # A mistyped flag must not fall through to the step IDs. "show"/"tick" would report it as a
        # step nothing defines, and "status"/"validate" take no IDs at all - so a wrong flag naming a
        # fix would be discarded in silence and the command answered for whichever file --file
        # defaulted to.
        --*)
            die "unknown option '$1' (try --help)"
            ;;
        *)
            args+=("$1")
            shift
            ;;
    esac
done

case "$command" in
    status|show|tick|validate|task) ;;
    --help|-h) usage; exit 0 ;;
    *) die "unknown command '$command' (try --help)" ;;
esac

if [ "$command" = "task" ]; then
    cmd_task "${args[0]:-}"
    exit 0
fi

locate_fix

case "$command" in
    status)   cmd_status ;;
    show)     cmd_show "${args[@]:-}" ;;
    tick)     cmd_tick "${args[@]:-}" ;;
    validate) parse validate ;;
esac
