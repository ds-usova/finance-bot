#!/usr/bin/env bash
#
# Reads and updates a rework's checklist by step ID, so that ticking a box or pulling out one step is
# an addressed operation rather than a text match against a wrapped bullet.
#
# The rework file stays the single source of truth: nothing here stores state beside it. There is no
# scheduling command, because a rework declares no order - "needs:" states a fact, not a sequence.
#
# See the README next to this script.

set -u

# The parser ships beside this script and is found relative to it. The project is not: installed as
# a plugin, this file sits in a cache directory outside any checkout, so the rework is located from
# where the command was run rather than from where the script lives.
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
parser="$script_dir/rework-parse.awk"
repo_root="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"

rework_file=""

usage() {
    cat <<'EOF'
Usage:
  <plugin>/scripts/rework/rework.sh status   [--file <rework>]
  <plugin>/scripts/rework/rework.sh show     <ID>... [--file <rework>]
  <plugin>/scripts/rework/rework.sh tick     <ID>... [--file <rework>]
  <plugin>/scripts/rework/rework.sh validate [--file <rework>]

Commands:
  status    Done/total, and the IDs still open.
  show      One step: its header and everything indented under it. Several IDs print in order,
            separated by a blank line.
  tick      Mark the steps done. Several IDs are one batch: all are resolved before any is written,
            so a name nothing defines ticks none of them.
  validate  Duplicate or missing IDs, an unrecognized kind, a line the kind does not take, a line
            the kind owes and does not carry, a placeholder value, a "survives:" naming no tier,
            "needs:"/"disables:" pointing at a step nothing defines, and an unanswered Open Question.

--file defaults to the single rework.md in flight under docs/. A <module>/steps.md, and an
archived rework under docs/implemented/, are addressed by passing --file explicitly.

Exit codes: 0 done - 1 nothing matched or validate found problems - 2 bad usage.
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
    local tmp="${rework_file}.rework-tmp.$$"
    if "$@" > "$tmp" && mv "$tmp" "$rework_file"; then
        return 0
    fi
    rm -f "$tmp"
    return 1
}

locate_rework() {
    if [ -n "$rework_file" ]; then
        [ -f "$rework_file" ] || die "no such file: $rework_file"
        return
    fi

    local found
    found="$(find "$repo_root/docs" -mindepth 2 -maxdepth 2 -name rework.md \
        -not -path '*/implemented/*' 2>/dev/null | sort)"

    local count
    count="$(printf '%s' "$found" | grep -c . || true)"

    case "$count" in
        0) die "no rework.md in flight under docs/ - name one with --file" ;;
        1) rework_file="$found" ;;
        *) die "several reworks are in flight; name one with --file:
$found" ;;
    esac
}

parse() {
    awk -v mode="$1" -f "$parser" "$rework_file"
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

    [ "$total" -gt 0 ] || die "$rework_file defines no steps" 1

    printf '%s\n  %d/%d\n' "$rework_file" "$done_count" "$total"
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
        sed -n "${start},${end}p" "$rework_file"
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
    ' "$rework_file" || die "could not write $rework_file"

    echo "ticked: $*"
}

command="${1:-}"
[ -n "$command" ] || { usage; exit 2; }
shift || true

args=()
while [ "$#" -gt 0 ]; do
    case "$1" in
        --file)
            [ "$#" -ge 2 ] || die "--file needs a path"
            rework_file="$2"
            shift 2
            ;;
        --help|-h)
            usage
            exit 0
            ;;
        # A mistyped flag must not fall through to the step IDs. "show"/"tick" would report it as a step
        # nothing defines, and "status"/"validate" take no IDs at all - so a wrong flag naming a rework
        # would be discarded in silence and the command answered for whichever file --file defaulted to.
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
    status|show|tick|validate) ;;
    --help|-h) usage; exit 0 ;;
    *) die "unknown command '$command' (try --help)" ;;
esac

locate_rework

case "$command" in
    status)   cmd_status ;;
    show)     cmd_show ${args[@]+"${args[@]}"} ;;
    tick)     cmd_tick ${args[@]+"${args[@]}"} ;;
    validate) parse validate ;;
esac
