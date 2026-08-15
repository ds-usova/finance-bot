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
  <plugin>/scripts/fix/fix.sh start    <ID> <what is being tried...> [--file <fix>]
  <plugin>/scripts/fix/fix.sh tick     <ID>... [--file <fix>]
  <plugin>/scripts/fix/fix.sh validate [--file <fix> | <bug directory>]
  <plugin>/scripts/fix/fix.sh task     [<fix directory> | <fix>]
  <plugin>/scripts/fix/fix.sh attempts [<bug directory> | <fix>]

Commands:
  status    Done/total, and the IDs still open.
  show      One step: its header and everything indented under it. Several IDs print in order,
            separated by a blank line.
  start     Write the "**In flight:**" header line: the step's ID and, in a clause, what is being
            tried. Run it when a step starts and whenever the approach changes.
  tick      Mark the steps done and empty the "**In flight:**" line. Several IDs are one batch: all
            are resolved before any is written, so a name nothing defines ticks none of them.
  validate  Duplicate or missing IDs, an unrecognized kind, an ID whose prefix contradicts it, a
            line the kind does not take, a line the kind owes and does not carry, a placeholder
            value, "needs:"/"disables:"/"fixes:" pointing at a step nothing defines, a reproduction
            no green step fixes, an attempt missing its reasoning, its result, its evidence or what
            it rules out, and an unanswered Open Question. Given a bug directory rather than a file,
            it validates bug.md and every fix.md the directory holds, in one call.
  task      Every fix file the bug holds, its done/total, and whether all of them are finished.
  attempts  The attempt IDs every file of the bug holds, as one line - "bug.md · A1-A3,
            module-a/fix.md · A1, module-b/fix.md · -" - and, where bug.md carries an
            "**Attempts:**" header line, that line rewritten to say so.

--file names a file on every subcommand. validate, task and attempts also take a path positionally:
validate accepts a bug directory and validates everything in it, task and attempts accept a bug
directory or any fix inside one. Without either, the single fix.md in flight under docs/ is used. A
bug owns a directory holding bug.md and one fix per module it touches: fix.md for a single-module
bug, <module>/fix.md for each of several. An archived one under docs/implemented/ is addressed explicitly, and so is a
bug.md, which validate reads for its Attempts section.

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

# A truncated read must not answer as a whole one. Only validate reports it and carries on, since
# reporting it is the whole of what validate does.
assert_read_whole() {
    parse fence || die "$fix_file was read as far as an unclosed fenced block - nothing below it counted" 1
}

# A checklist is not a fix, and the kinds do not tell them apart: a rework's steps have kinds of
# their own and one of them is also called "stabilize". Only the name does, which is why the format
# fixes it. This guards the write path - ticking somebody else's checklist rewrites their file.
assert_is_fix() {
    case "$(basename "$fix_file")" in
        fix.md) return 0 ;;
    esac
    die "$fix_file is not a fix.md - $(basename "$fix_file") belongs to another format" 1
}

# Read-only commands say so and carry on, since a file under another name may still be one being
# written, and answering is more use than refusing.
warn_unless_fix() {
    case "$(basename "$fix_file")" in
        fix.md|bug.md) ;;
        *) echo "note: $fix_file is neither fix.md nor bug.md, and is read as one anyway" >&2 ;;
    esac
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
    warn_unless_fix

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
    assert_is_fix

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

    # Anchored to the checkbox at the head of the line. An unanchored substitution rewrites the first
    # "[ ]" anywhere on it, which on an already-ticked step is somewhere in its prose - a silent edit
    # to a step's text that nothing else would ever report.
    rewrite_file awk -v targets="$lines" '
        BEGIN { n = split(targets, t, ","); for (i = 1; i <= n; i++) mark[t[i]] = 1 }
        NR in mark { sub(/^-[ \t]+\[ \]/, "- [x]") }
        { print }
    ' "$fix_file" || die "could not write $fix_file"

    # A ticked step is no longer in flight. Emptied here so the line cannot go stale exactly when a
    # step lands, which is the one moment a resumed run would misread it.
    set_in_flight ""

    echo "ticked: $*"
}

# The "**In flight:**" header line names the step being applied and the approach being tried. Only
# the value changes; a file with no such line is one this format did not write, and gets none added.
set_in_flight() {
    local value="$1"
    grep -q '^\*\*In flight:\*\*' "$fix_file" || return 1
    rewrite_file awk -v value="$value" '
        /^\*\*In flight:\*\*/ { print "**In flight:**" (value == "" ? "" : " " value); next }
        { print }
    ' "$fix_file" || die "could not write $fix_file"
}

cmd_start() {
    [ "$#" -ge 1 ] || die "start needs a step ID and what is being tried"
    assert_is_fix

    local id="$1" matched="" rid
    shift
    while IFS=$'\t' read -r rid _ _ _ _; do
        [ "$rid" = "$id" ] && matched=1 && break
    done < <(parse list)
    [ -n "$matched" ] || die "no such step: $id" 1

    local text="$*"
    [ -n "$text" ] || die "start needs what is being tried, in a clause, after the ID"

    set_in_flight "$id"$' \xc2\xb7 '"$text" || die "$fix_file carries no \"**In flight:**\" line" 1
    echo "in flight: $id"$' \xc2\xb7 '"$text"
}

# "A1-A3" where the IDs run 1..n without a gap, the list otherwise, "-" for none.
attempt_range() {
    local ids=("$@") n="$#"
    [ "$n" -gt 0 ] || { printf '%s' $'\xe2\x80\x94'; return; }
    [ "$n" -gt 1 ] || { printf '%s' "${ids[0]}"; return; }
    local i=1 contiguous=1
    while [ "$i" -le "$n" ]; do
        [ "${ids[$((i - 1))]}" = "A$i" ] || { contiguous=0; break; }
        i=$((i + 1))
    done
    if [ "$contiguous" = 1 ]; then
        printf '%s' "${ids[0]}"$'\xe2\x80\x93'"${ids[$((n - 1))]}"
    else
        local IFS=,
        printf '%s' "${ids[*]}"
    fi
}

cmd_attempts() {
    local given="${1:-}" bug_dir
    if [ -n "$given" ] && [ -d "$given" ]; then
        bug_dir="$(cd "$given" && pwd)"
    elif [ -n "$given" ]; then
        [ -f "$given" ] || die "no such fix file or bug directory: $given"
        bug_dir="$(bug_dir_of "$given")" || exit "$?"
    else
        die "attempts needs a bug directory or a fix inside one"
    fi

    local files=() f
    while IFS= read -r f; do
        [ -n "$f" ] || continue
        files+=("$f")
    done < <(find "$bug_dir" -maxdepth 2 \( -name 'bug.md' -o -name 'fix.md' \) -type f | sort)
    [ "${#files[@]}" -gt 0 ] || die "${bug_dir#"$repo_root_abs/"} holds no bug.md or fix.md" 1

    local summary="" ids part
    for f in "${files[@]}"; do
        fix_file="$f"
        ids=()
        while IFS= read -r part; do
            [ -n "$part" ] && ids+=("$part")
        done < <(parse attempts)
        summary="${summary:+$summary, }${f#"$bug_dir/"}"$' \xc2\xb7 '"$(attempt_range ${ids[@]+"${ids[@]}"})"
    done

    echo "$summary"

    local bug_md="$bug_dir/bug.md"
    if [ -f "$bug_md" ] && grep -q '^\*\*Attempts:\*\*' "$bug_md"; then
        fix_file="$bug_md"
        rewrite_file awk -v value="$summary" '
            /^\*\*Attempts:\*\*/ { print "**Attempts:** " value; next }
            { print }
        ' "$bug_md" || die "could not write $bug_md"
        echo "written to ${bug_md#"$repo_root_abs/"}"
    fi
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
            echo "$dir"
            return 0
        fi
        dir="$parent"
    done
    # Walking off the top means the file sits outside docs/. Answering with the repository root would
    # then report every fix.md in the tree as one bug's.
    die "$1 is not inside a bug directory under docs/"
}

# A bug's files are validated together, so the gate before the first source edit is one call however
# many modules the bug reaches. bug.md is included: it is where the diagnosis attempts live.
cmd_validate_dir() {
    local dir="$1" f found=0 failed=0
    [ -d "$dir" ] || die "no such directory: $dir"

    while IFS= read -r f; do
        [ -n "$f" ] || continue
        found=$((found + 1))
        fix_file="$f"
        parse validate || failed=1
    done < <(find "$dir" -maxdepth 2 \( -name 'fix.md' -o -name 'bug.md' \) -type f | sort)

    [ "$found" -gt 0 ] || die "${dir} holds no bug.md or fix.md" 1
    return "$failed"
}

cmd_task() {
    local given="${1:-}" bug_dir
    if [ -n "$given" ] && [ -d "$given" ]; then
        bug_dir="$(cd "$given" && pwd)"
    elif [ -n "$given" ]; then
        [ -f "$given" ] || die "no such fix file or bug directory: $given"
        # bug_dir_of dies in a subshell, so its exit status is what carries the refusal out.
        bug_dir="$(bug_dir_of "$given")" || exit "$?"
    else
        local dirs=() f d
        while IFS= read -r f; do
            [ -n "$f" ] || continue
            d="$(bug_dir_of "$f")" || exit "$?"
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
    status|show|start|tick|validate|task|attempts) ;;
    --help|-h) usage; exit 0 ;;
    *) die "unknown command '$command' (try --help)" ;;
esac

case "$command" in
    task)
        # --file names a fix as it does everywhere else; the bug it belongs to is derived from it.
        cmd_task "${args[0]:-$fix_file}"
        exit 0
        ;;
    attempts)
        cmd_attempts "${args[0]:-$fix_file}"
        exit 0
        ;;
    validate)
        # A directory validates the whole bug; a path to one file is the same as naming it with
        # --file, since that is the spelling a reader reaches for first.
        if [ "${#args[@]}" -gt 0 ]; then
            if [ -d "${args[0]}" ]; then
                cmd_validate_dir "${args[0]}"
                exit "$?"
            fi
            [ -f "${args[0]}" ] || die "no such file or directory: ${args[0]}"
            fix_file="${args[0]}"
        fi
        ;;
    show|start|tick)
        # An empty array expands to one empty string, which would reach the command as an ID nothing
        # defines and be reported as a missing step rather than as the usage error it is.
        [ "${#args[@]}" -gt 0 ] || die "$command needs at least one ID"
        ;;
esac

# A path given positionally names the file on every command, as it does on validate and task. Without
# this, "status docs/7-x/module-a/fix.md" would answer for whichever fix.md the default resolution
# found, which on a multi-module bug is the wrong one and says nothing about it.
rest=()
for arg in ${args[@]+"${args[@]}"}; do
    if [ -f "$arg" ]; then
        fix_file="$arg"
    else
        rest+=("$arg")
    fi
done
args=(${rest[@]+"${rest[@]}"})

locate_fix

case "$command" in
    status)   assert_read_whole; cmd_status ;;
    show)     warn_unless_fix; assert_read_whole; cmd_show "${args[@]}" ;;
    start)    assert_read_whole; cmd_start "${args[@]}" ;;
    tick)     assert_read_whole; cmd_tick "${args[@]}" ;;
    validate) warn_unless_fix; parse validate ;;
esac
