#!/usr/bin/env bash
# Re-pad every GitHub-flavoured markdown table in the given files so each column is as wide as its
# widest cell. Rewrites in place; a file with no table is left byte-for-byte alone.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

usage() {
    cat <<'EOF'
Usage:
  tools/repad-tables/repad-tables.sh <file.md>...
  tools/repad-tables/repad-tables.sh --check <file.md>...

Options:
  --check   Report which files would change and exit 1 if any would. Writes nothing.

Editing one cell of a markdown table leaves every other row misaligned. This realigns the whole
table, which is what the conventions ask for after any edit to one.
EOF
}

check_only=0
files=()
while [ $# -gt 0 ]; do
    case "$1" in
        --check)   check_only=1; shift ;;
        -h|--help) usage; exit 0 ;;
        *)         files+=("$1"); shift ;;
    esac
done

if [ "${#files[@]}" -eq 0 ]; then
    usage >&2
    exit 2
fi

changed=0
for file in "${files[@]}"; do
    if [ ! -f "$file" ]; then
        echo "no such file: $file" >&2
        exit 2
    fi

    tmp="${file}.repad-tmp.$$"
    awk -f "$script_dir/repad-tables.awk" "$file" > "$tmp"

    if cmp -s "$file" "$tmp"; then
        rm -f "$tmp"
        continue
    fi

    changed=1
    if [ "$check_only" -eq 1 ]; then
        rm -f "$tmp"
        echo "would re-pad: $file"
    else
        mv "$tmp" "$file"
        echo "re-padded: $file"
    fi
done

if [ "$check_only" -eq 1 ] && [ "$changed" -eq 1 ]; then
    exit 1
fi
exit 0
