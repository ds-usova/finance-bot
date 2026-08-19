# Re-pads GitHub-flavoured markdown tables: every column becomes as wide as its widest cell, and the
# rule row keeps whatever alignment colons it carried. Lines outside a table pass through untouched.
#
# A table is a run of consecutive lines that start (after optional indent) with "|", holding a rule
# row second. Anything else that starts with "|" is left alone, so a code block drawing box art is
# not silently rewritten.

function flush_table(   r, c, line, out, cell, pad, width) {
    if (rows == 0) return

    if (!has_rule) {
        for (r = 1; r <= rows; r++) print raw[r]
        reset_table()
        return
    }

    # Widths come from the content rows only. The rule row's own dashes are as wide as the column was
    # before this run, so counting them would widen the table by two on every pass.
    for (c = 1; c <= cols; c++) colwidth[c] = 0
    for (r = 1; r <= rows; r++) {
        if (r == rulerow) continue
        for (c = 1; c <= cols; c++) {
            if (length(cells[r, c]) > colwidth[c]) colwidth[c] = length(cells[r, c])
        }
    }

    for (r = 1; r <= rows; r++) {
        out = indent[r] "|"
        for (c = 1; c <= cols; c++) {
            width = colwidth[c]
            cell = cells[r, c]
            if (r == rulerow) {
                # Keep the colons, stretch the dashes to the column width. The rule row carries no
                # padding spaces, which is how every table already in this repository is written.
                left  = (cell ~ /^:/) ? ":" : "-"
                right = (cell ~ /:$/) ? ":" : "-"
                out = out left
                for (i = 0; i < width; i++) out = out "-"
                out = out right "|"
            } else {
                out = out " " cell
                for (i = length(cell); i < width; i++) out = out " "
                out = out " |"
            }
        }
        print out
    }
    reset_table()
}

function reset_table(   r, c) {
    for (r = 1; r <= rows; r++)
        for (c = 1; c <= cols; c++) cells[r, c] = ""
    for (c = 1; c <= cols; c++) colwidth[c] = 0
    rows = 0; cols = 0; has_rule = 0; rulerow = 0
}

function split_row(line, r,   body, n, i, parts, lead) {
    match(line, /^[ \t]*/)
    lead = substr(line, 1, RLENGTH)
    indent[r] = lead
    body = substr(line, RLENGTH + 1)
    # An escaped pipe (\|) is cell content, not a separator. Hide it behind a control byte for the
    # split and put it back in each cell.
    gsub(/\\\|/, "\001", body)
    sub(/^\|/, "", body)
    sub(/\|[ \t]*$/, "", body)
    n = split(body, parts, /\|/)
    for (i = 1; i <= n; i++) {
        gsub(/^[ \t]+|[ \t]+$/, "", parts[i])
        gsub(/\001/, "\\|", parts[i])
        cells[r, i] = parts[i]
    }
    if (n > cols) cols = n
    return n
}

BEGIN { rows = 0; cols = 0; has_rule = 0; in_fence = 0 }

/^[ \t]*```/ { flush_table(); in_fence = !in_fence; print; next }

in_fence { print; next }

/^[ \t]*\|/ {
    raw[++rows] = $0
    split_row($0, rows)
    # The rule row is the second line of a table and is all dashes and colons.
    if (rows == 2) {
        is_rule = 1
        for (c = 1; c <= cols; c++) {
            if (cells[2, c] !~ /^:?-+:?$/) { is_rule = 0; break }
        }
        if (is_rule) { has_rule = 1; rulerow = 2 }
    }
    next
}

{ flush_table(); print }

END { flush_table() }
