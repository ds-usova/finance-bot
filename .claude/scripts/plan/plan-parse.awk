#!/usr/bin/awk -f
#
# Parses a plan's checklist into one record per item and answers the query named by -v mode=.
#
# An item is "- [ ] <ID> · <text>"; its block runs to the next item or heading, so a wrapped header
# and its scenario bullets stay attached. Only the header is searched for "after:", which keeps a
# scenario mentioning another item's ID from being read as a dependency.
#
# Invoked by plan.sh, which ships beside it; see the README in the same directory.

function close_item() {
    if (cur != "") {
        end[cur] = NR - 1
        cur = ""
    }
    in_header = 0
}

# Every ID the text carries, comma-joined, in the order they appear.
function ids_in(text,   out, rest, tok) {
    out = ""
    rest = text
    while (match(rest, /[A-Za-z]+[0-9]+/)) {
        tok = substr(rest, RSTART, RLENGTH)
        out = (out == "" ? tok : out "," tok)
        rest = substr(rest, RSTART + RLENGTH)
    }
    return out
}

# The dependency list of a header: everything from "after:" up to the next " · " field, or the end.
function deps_of(text,   tail, cut) {
    if (!match(text, /after:/)) {
        return ""
    }
    tail = substr(text, RSTART + RLENGTH)
    cut = index(tail, " · ")
    if (cut > 0) {
        tail = substr(tail, 1, cut - 1)
    }
    return ids_in(tail)
}

BEGIN {
    if (mode == "") {
        mode = "items"
    }
    n = 0
}

# A plan quotes its own step format in fenced examples; those bullets are illustrations, not work.
/^[ \t]*```/ {
    in_fence = !in_fence
    next
}

in_fence { next }

/^#+ / {
    close_item()
    if ($0 ~ /^### /) {
        group = substr($0, 5)
        section = ""
    } else if ($0 ~ /^#### /) {
        section = substr($0, 6)
    }
    next
}

/^- \[[ xX]\] / {
    close_item()
    rest = substr($0, 7)
    if (!match(rest, /^[A-Za-z]+[0-9]+/)) {
        n_unidentified++
        unidentified[n_unidentified] = NR ": " substr($0, 1, 70)
        next
    }
    cur = substr(rest, 1, RLENGTH)
    title = substr(rest, RLENGTH + 1)
    sub(/^[ ]*·[ ]*/, "", title)

    if (cur in seen) {
        n_dup++
        dup[n_dup] = cur " (lines " start[cur] " and " NR ")"
    }
    seen[cur] = 1
    n++
    order[n] = cur
    status[cur] = (substr($0, 4, 1) == " " ? "open" : "done")
    group_of[cur] = group
    section_of[cur] = section
    start[cur] = NR
    header[cur] = title
    in_header = 1
    next
}

# A wrapped header line: indented, not a sub-bullet, not blank.
in_header && /^[ \t]+[^ \t-]/ {
    line = $0
    sub(/^[ \t]+/, "", line)
    header[cur] = header[cur] " " line
    next
}

{
    if (in_header) {
        in_header = 0
    }
}

END {
    close_item()
    for (i = 1; i <= n; i++) {
        id = order[i]
        if (end[id] == "" || end[id] < start[id]) {
            end[id] = start[id]
        }
        deps[id] = deps_of(header[id])
    }

    if (mode == "items") {
        emit_items()
    } else if (mode == "range") {
        emit_range()
    } else if (mode == "status") {
        emit_status()
    } else if (mode == "next") {
        emit_next()
    } else if (mode == "validate") {
        emit_validate()
    } else {
        print "unknown mode: " mode > "/dev/stderr"
        exit 2
    }
}

function emit_items(   i, id) {
    for (i = 1; i <= n; i++) {
        id = order[i]
        print id "\t" status[id] "\t" group_of[id] "\t" section_of[id] "\t" \
              start[id] "\t" end[id] "\t" deps[id] "\t" header[id]
    }
}

function emit_range(   ) {
    if (!(want in seen)) {
        exit 1
    }
    print start[want] " " end[want]
}

function emit_status(   i, id, g, total, done, groups, ng, open_ids) {
    ng = 0
    for (i = 1; i <= n; i++) {
        id = order[i]
        g = (group_of[id] == "" ? "(ungrouped)" : group_of[id])
        if (!(g in g_total)) {
            ng++
            g_order[ng] = g
            g_total[g] = 0
            g_done[g] = 0
            g_open[g] = ""
        }
        g_total[g]++
        total++
        if (status[id] == "done") {
            g_done[g]++
            done++
        } else {
            g_open[g] = (g_open[g] == "" ? id : g_open[g] ", " id)
        }
    }
    for (i = 1; i <= ng; i++) {
        g = g_order[i]
        if (g_open[g] == "") {
            printf "%-28s %3d/%d\n", g, g_done[g], g_total[g]
        } else {
            printf "%-28s %3d/%-4d open: %s\n", g, g_done[g], g_total[g], g_open[g]
        }
    }
    printf "%-28s %3d/%d\n", "TOTAL", done, total
}

# Longest chain of still-open work starting at id, counting id itself.
function rank(id,   i, c, best, r) {
    if (id in rank_memo) {
        return rank_memo[id]
    }
    rank_memo[id] = 1
    best = 0
    for (i = 1; i <= n; i++) {
        c = order[i]
        if (status[c] == "done") {
            continue
        }
        if (("," deps[c] ",") ~ ("," id ",")) {
            r = rank(c)
            if (r > best) {
                best = r
            }
        }
    }
    rank_memo[id] = best + 1
    return rank_memo[id]
}

# Case-insensitive substring, so --group red reaches "Red Phase" without quoting the whole heading.
function has(haystack, needle) {
    return index(tolower(haystack), tolower(needle)) > 0
}

# Whether an item falls inside the scope the caller asked for. A run limited to one phase must not be
# handed the next phase's work when its own finishes, and the caller is the only one who knows.
function in_scope(id,   i, nsec, sec) {
    if (group_filter != "" && !has(group_of[id], group_filter)) {
        return 0
    }
    if (section_filter != "") {
        nsec = split(section_filter, sec, ",")
        for (i = 1; i <= nsec; i++) {
            if (sec[i] != "" && has(section_of[id], sec[i])) {
                return 1
            }
        }
        return 0
    }
    return 1
}

function check_scope(   i, id, g, seen_g, names, count) {
    count = 0
    names = ""
    for (i = 1; i <= n; i++) {
        g = group_of[order[i]]
        if (group_filter != "" && has(g, group_filter) && !(g in seen_g)) {
            seen_g[g] = 1
            count++
            names = (names == "" ? g : names ", " g)
        }
    }
    if (group_filter != "" && count == 0) {
        print "no group matching '" group_filter "' in this plan" > "/dev/stderr"
        exit 2
    }
    if (count > 1) {
        print "'" group_filter "' matches " count " groups - narrow it: " names > "/dev/stderr"
        exit 2
    }
    count = 0
    for (i = 1; i <= n; i++) {
        if (in_scope(order[i])) {
            count++
        }
    }
    if (count == 0) {
        print "no item is in scope" > "/dev/stderr"
        exit 2
    }
}

function emit_next(   i, id, j, d, nd, ready, blocked_by, k, tmp, stage) {
    check_scope()
    # Groups run in the order the plan lists them, so only the earliest one still holding open work
    # is schedulable - an item in a later group is not eligible just because it has no dependencies.
    stage = ""
    for (i = 1; i <= n; i++) {
        if (status[order[i]] != "done" && in_scope(order[i])) {
            stage = group_of[order[i]]
            break
        }
    }
    if (stage == "") {
        print "every item in scope is ticked"
        return
    }
    print "group: " stage

    for (i = 1; i <= n; i++) {
        id = order[i]
        if (status[id] == "done" || group_of[id] != stage || !in_scope(id)) {
            continue
        }
        nd = split(deps[id], d, ",")
        ready = 1
        blocked_by = ""
        for (j = 1; j <= nd; j++) {
            if (d[j] == "") {
                continue
            }
            if (!(d[j] in seen) || status[d[j]] != "done") {
                ready = 0
                blocked_by = (blocked_by == "" ? d[j] : blocked_by ", " d[j])
            }
        }
        if (ready) {
            k++
            elig[k] = id
        } else {
            waiting[id] = blocked_by
        }
    }
    if (k == 0) {
        print "nothing eligible - every open item in this group waits on another"
    }
    # Longest remaining chain first: with a parallelism cap, spawning a shorter branch
    # ahead of the critical path costs a whole wave.
    for (i = 1; i <= k; i++) {
        for (j = i + 1; j <= k; j++) {
            if (rank(elig[j]) > rank(elig[i])) {
                tmp = elig[i]
                elig[i] = elig[j]
                elig[j] = tmp
            }
        }
    }
    for (i = 1; i <= k; i++) {
        printf "%-6s depth %-3d %s\n", elig[i], rank(elig[i]), header[elig[i]]
    }
    if (verbose == "1") {
        for (i = 1; i <= n; i++) {
            id = order[i]
            if (id in waiting && in_scope(id)) {
                printf "  (waiting) %-6s after %s\n", id, waiting[id]
            }
        }
    }
}

# The loop as it reads in the plan: "GU01 after GU03 after GU02 after GU01".
function render_cycle(back_to,   i, from, out) {
    from = 0
    for (i = 1; i <= depth_i; i++) {
        if (path[i] == back_to) {
            from = i
            break
        }
    }
    if (from == 0) {
        return back_to
    }
    out = path[from]
    for (i = from + 1; i <= depth_i; i++) {
        out = out " after " path[i]
    }
    return out " after " back_to
}

# Depth-first cycle search; colour 1 is on the current path, 2 is finished.
function visit(id,   i, c, nd, d, j) {
    colour[id] = 1
    path[++depth_i] = id
    nd = split(deps[id], d, ",")
    for (j = 1; j <= nd; j++) {
        c = d[j]
        if (c == "" || !(c in seen)) {
            continue
        }
        if (colour[c] == 1) {
            cycle_found = cycle_found "\n  " render_cycle(c)
        } else if (colour[c] != 2) {
            visit(c)
        }
    }
    colour[id] = 2
    depth_i--
}

function emit_validate(   i, id, j, d, nd, problems) {
    problems = 0
    for (i = 1; i <= n_dup; i++) {
        print "duplicate ID: " dup[i]
        problems++
    }
    for (i = 1; i <= n_unidentified; i++) {
        print "checklist item without an ID at line " unidentified[i]
        problems++
    }
    for (i = 1; i <= n; i++) {
        id = order[i]
        nd = split(deps[id], d, ",")
        for (j = 1; j <= nd; j++) {
            if (d[j] != "" && !(d[j] in seen)) {
                print id " depends on " d[j] ", which no item defines"
                problems++
            }
        }
    }
    for (i = 1; i <= n; i++) {
        if (colour[order[i]] != 2) {
            visit(order[i])
        }
    }
    if (cycle_found != "") {
        print "circular dependencies:" cycle_found
        problems++
    }
    if (problems == 0) {
        print n " items, no problems"
    } else {
        exit 1
    }
}
