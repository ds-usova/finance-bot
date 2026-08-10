#!/usr/bin/env bash
#
# Reads a design file's Decisions by entry ID, and answers the one question every stage downstream
# asks of it: is this design settled, or does something still need deciding?
#
# The design file stays the single source of truth. Nothing here stores state beside it, and nothing
# here edits it - a decision is answered by whoever makes it, in the file.
#
# See the README next to this script.

set -u

# The parser ships beside this script and is found relative to it. The project is not: installed as
# a plugin, this file sits in a cache directory outside any checkout, so the design is located from
# where the command was run rather than from where the script lives.
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
parser="$script_dir/design-parse.awk"
repo_root="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"

design_file=""

usage() {
    cat <<'EOF'
Usage:
  <plugin>/scripts/design/design.sh settled [--file <design>]
  <plugin>/scripts/design/design.sh status  [--file <design>]
  <plugin>/scripts/design/design.sh show    <ID>... [--file <design>]
  <plugin>/scripts/design/design.sh validate [--file <design>]

Commands:
  settled   The gate. Exit 0 when no decision is still must-decide; exit 1 and list the open ones
            when any is. What plan-task and implement-plan check before they run.
  status    How many decisions rest on each basis.
  show      One decision: its question, Answer and Basis. Several IDs print in order, separated by
            a blank line.
  validate  Missing or out-of-order sections, duplicate IDs, an entry outside the Decisions section,
            a missing or repeated Answer:/Basis:, an unrecognized basis, a basis with no reason
            after it, an answered must-decide, an unanswered anything else, and a Design Findings
            section with no Grilled (...) line. A must-decide is not itself a problem here - a
            design in flight is expected to have them; that is what `settled` is for.

--file defaults to the single docs/<n>-<task>/design.md - a task owns a directory, holding design.md
and plan.md. An archived design under docs/implemented/<n>-<task>/design.md is addressed by passing
--file explicitly.

Exit codes: 0 done - 1 no such entry, not settled, or validate found problems - 2 bad usage.
EOF
}

die() {
    echo "$1" >&2
    exit "${2:-2}"
}

resolve_design() {
    local candidates=()
    if [ -n "$design_file" ]; then
        [ -f "$design_file" ] || die "no such design file: $design_file"
        return 0
    fi
    while IFS= read -r f; do
        candidates+=("$f")
    done < <(find "$repo_root/docs" -maxdepth 2 -name 'design.md' -type f \
        -not -path '*/implemented/*' 2>/dev/null | sort)

    case "${#candidates[@]}" in
        0) die "no <n>-<task>/design.md in $repo_root/docs - pass --file <design>" ;;
        1) design_file="${candidates[0]}" ;;
        *)
            {
                echo "docs/ holds ${#candidates[@]} designs in flight - pass --file <design>:"
                printf '  %s\n' "${candidates[@]#"$repo_root/"}"
            } >&2
            exit 2
            ;;
    esac
}

command="${1:-}"
[ -n "$command" ] || { usage; exit 2; }
shift

args=()
while [ $# -gt 0 ]; do
    case "$1" in
        --file)    design_file="${2:-}"; shift 2 ;;
        -h|--help) usage; exit 0 ;;
        # A bare design path is accepted wherever --file is, on every subcommand. Without this it
        # lands in args and is silently ignored, and the command validates the one design in flight
        # instead - the wrong file, with no way to tell from the output.
        *.md|*/*)
            [ -z "$design_file" ] || die "design file given twice: $design_file and $1"
            design_file="$1"; shift ;;
        *) args+=("$1"); shift ;;
    esac
done

case "$command" in
    validate)
        resolve_design
        awk -f "$parser" -v mode=validate "$design_file"
        ;;

    status)
        resolve_design
        awk -f "$parser" -v mode=status "$design_file"
        ;;

    settled)
        resolve_design
        open="$(awk -f "$parser" -v mode=open "$design_file")"
        if [ -z "$open" ]; then
            echo "settled - every decision has a basis"
            exit 0
        fi
        {
            echo "not settled - still must-decide:"
            echo "$open" | sed 's/^/  /'
        } >&2
        exit 1
        ;;

    show)
        [ "${#args[@]}" -gt 0 ] || die "show needs at least one decision ID"
        resolve_design
        first=1
        for id in "${args[@]}"; do
            range="$(awk -f "$parser" -v mode=range -v want="$id" "$design_file")" \
                || die "no decision $id in ${design_file#"$repo_root/"}" 1
            [ -n "$range" ] || die "no decision $id in ${design_file#"$repo_root/"}" 1
            [ "$first" = "0" ] && echo
            first=0
            sed -n "${range% *},${range#* }p" "$design_file"
        done
        ;;

    *)
        die "unknown command '$command' (try --help)"
        ;;
esac
