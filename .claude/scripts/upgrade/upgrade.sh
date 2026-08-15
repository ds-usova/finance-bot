#!/usr/bin/env bash
#
# Reads and updates an upgrade's checklist by step ID, so that ticking a box or pulling out one step is
# an addressed operation rather than a text match against a wrapped bullet.
#
# The upgrade file stays the single source of truth: nothing here stores state beside it. There is no
# scheduling command, because an upgrade declares no order - "needs:" states a fact, not a sequence.
#
# See the README next to this script.

set -u

# The parser ships beside this script and is found relative to it. The project is not: installed as
# a plugin, this file sits in a cache directory outside any checkout, so the upgrade is located from
# where the command was run rather than from where the script lives.
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
parser="$script_dir/upgrade-parse.awk"
repo_root="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"

upgrade_file=""

usage() {
    cat <<'EOF'
Usage:
  <plugin>/scripts/upgrade/upgrade.sh status   [--file <upgrade>]
  <plugin>/scripts/upgrade/upgrade.sh show     <ID>... [--file <upgrade>]
  <plugin>/scripts/upgrade/upgrade.sh tick     <ID>... [--file <upgrade>]
  <plugin>/scripts/upgrade/upgrade.sh validate [--file <upgrade>]

Commands:
  status    Done/total, the IDs still open, and the IDs abandoned.
  show      One step: its header and everything indented under it. Several IDs print in order,
            separated by a blank line.
  tick      Mark the steps done. Several IDs are one batch: all are resolved before any is written,
            so a name nothing defines ticks none of them.
  validate  Duplicate or missing IDs, an unrecognized kind, a line the kind does not take, a line
            the kind owes and does not carry, a placeholder value, a "change:" naming no place,
            "needs:" pointing at a step nothing defines, an attempt missing a line or its evidence,
            an attempt filed under no step, and an unanswered Open Question.

--file defaults to the single upgrade.md in flight under docs/. A <module>/steps.md, and an
archived upgrade under docs/implemented/, are addressed by passing --file explicitly.

Exit codes: 0 done - 1 nothing matched or validate found problems - 2 bad usage.
EOF
}

die() {
    echo "$1" >&2
    exit "${2:-2}"
}

# In-place editing is done by rewriting through a sibling temp file rather than with `sed -i`, whose
# spelling differs between GNU and BSD.
rewrite_file() {
    local tmp="${upgrade_file}.upgrade-tmp.$$"
    if "$@" > "$tmp" && mv "$tmp" "$upgrade_file"; then
        return 0
    fi
    rm -f "$tmp"
    return 1
}

locate_upgrade() {
    if [ -n "$upgrade_file" ]; then
        [ -f "$upgrade_file" ] || die "no such file: $upgrade_file"
        return
    fi

    local found
    found="$(find "$repo_root/docs" -mindepth 2 -maxdepth 2 -name upgrade.md \
        -not -path '*/implemented/*' 2>/dev/null | sort)"

    local count
    count="$(printf '%s' "$found" | grep -c . || true)"

    case "$count" in
        0) die "no upgrade.md in flight under docs/ - name one with --file" ;;
        1) upgrade_file="$found" ;;
        *) die "several upgrades are in flight; name one with --file:
$found" ;;
    esac
}

parse() {
    awk -v mode="$1" -f "$parser" "$upgrade_file"
}

cmd_status() {
    local total=0 done_count=0 open="" abandoned=""
    while IFS=$'\t' read -r id state _ _ _; do
        [ -n "$id" ] || continue
        total=$((total + 1))
        case "$state" in
            x) done_count=$((done_count + 1)) ;;
            a) abandoned="${abandoned:+$abandoned }$id" ;;
            *) open="${open:+$open }$id" ;;
        esac
    done < <(parse list)

    [ "$total" -gt 0 ] || die "$upgrade_file defines no steps" 1

    printf '%s\n  %d/%d\n' "$upgrade_file" "$done_count" "$total"
    [ -z "$open" ] || printf '  open: %s\n' "$open"
    [ -z "$abandoned" ] || printf '  abandoned: %s\n' "$abandoned"
    if [ -z "$open" ] && [ -z "$abandoned" ]; then
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
        sed -n "${start},${end}p" "$upgrade_file"
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
    ' "$upgrade_file" || die "could not write $upgrade_file"

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
            upgrade_file="$2"
            shift 2
            ;;
        --help|-h)
            usage
            exit 0
            ;;
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

locate_upgrade

case "$command" in
    status)   cmd_status ;;
    show)     cmd_show ${args[@]+"${args[@]}"} ;;
    tick)     cmd_tick ${args[@]+"${args[@]}"} ;;
    validate) parse validate ;;
esac
