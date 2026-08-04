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

# The text after the first colon, trimmed. Used for "given:"/"Resolution:"-style labelled lines.
function value_after_colon(line,   pos, val) {
    pos = index(line, ":")
    if (pos == 0) {
        return ""
    }
    val = substr(line, pos + 1)
    sub(/^[ \t]+/, "", val)
    sub(/[ \t]+$/, "", val)
    return val
}

# A scenario the planner left unfilled. A step agent writes exactly what is listed, so a dash where the
# expected outcome belongs is not a shorter instruction - it is no instruction.
function is_placeholder(v) {
    return v == "" || v == "-" || v == "--" || v == "—" || v == "–" || v == "..." || v == "…" \
        || v == "TBD" || v == "tbd" || v == "TODO" || v == "todo" || v == "N/A" || v == "n/a"
}

# Every `someMethod(` named in a line, recorded against the item it sits under. Only called for
# "update:" bullets, which by definition name a test that already exists.
function scan_methods(line,   rest, tok, key) {
    rest = line
    while (match(rest, /`[A-Za-z_][A-Za-z0-9_]*\(/)) {
        tok = substr(rest, RSTART + 1, RLENGTH - 2)
        rest = substr(rest, RSTART + RLENGTH)
        key = cur "\t" tok
        if (key in upd_seen) {
            continue
        }
        upd_seen[key] = 1
        n_upd++
        upd_id[n_upd] = cur
        upd_m[n_upd] = tok
    }
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
    in_update = 0
    cur_f = ""
    in_findings = ($0 ~ /^## Review Findings/)
    if ($0 ~ /^### /) {
        group = substr($0, 5)
        section = ""
        # Position in the plan, which is the order the groups run in. Headings outside the step map
        # are numbered too and never compared, because no checklist item sits under them.
        if (!(group in group_pos)) {
            group_pos[group] = ++n_groups
        }
    } else if ($0 ~ /^#### /) {
        section = substr($0, 6)
    }
    next
}

# A review finding: "- **F1:** …", followed by its Resolution and Action lines.
in_findings && /^- \*\*F[0-9]+:\*\*/ {
    match($0, /F[0-9]+/)
    cur_f = substr($0, RSTART, RLENGTH)
    n_f++
    f_order[n_f] = cur_f
    f_line[cur_f] = NR
    next
}

in_findings && cur_f != "" && /^[ \t]*- Resolution:/ {
    f_res[cur_f] = value_after_colon($0)
    next
}

in_findings && cur_f != "" && /^[ \t]*- Action:/ {
    f_act[cur_f] = value_after_colon($0)
    next
}

in_findings && cur_f != "" && /^[ \t]*- Escalated:/ {
    f_esc[cur_f] = value_after_colon($0)
    next
}

/^- \[[ xX]\] / {
    close_item()
    in_update = 0
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

# A scenario line inside an item, as a bullet ("- given: …") or a continuation ("  when: …").
cur != "" && /^[ \t]*-?[ \t]*(given|when|then):/ {
    in_header = 0
    in_update = 0
    scenario_line = $0
    sub(/^[ \t]*-?[ \t]*/, "", scenario_line)
    scenario_label = substr(scenario_line, 1, index(scenario_line, ":") - 1)
    if (is_placeholder(value_after_colon(scenario_line))) {
        n_placeholder++
        placeholder[n_placeholder] = cur " leaves \"" scenario_label ":\" empty at line " NR
    }
    next
}

# An "update:" bullet names tests that already exist; it may wrap over several lines.
cur != "" && /^[ \t]*-[ \t]*update:/ {
    in_header = 0
    in_update = 1
    scan_methods($0)
    next
}

in_update && /^[ \t]+[^ \t-]/ {
    scan_methods($0)
    next
}

{
    in_update = 0
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
    } else if (mode == "updates") {
        emit_updates()
    } else if (mode == "count") {
        print n
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

function emit_updates(   i) {
    for (i = 1; i <= n_upd; i++) {
        # A ticked item's update: bullets describe work that already happened, and the rename or
        # deletion they asked for is why the method is gone. Only an open item's can be checked
        # against the tree.
        if (status[upd_id[i]] == "done") {
            continue
        }
        print upd_id[i] "\t" upd_m[i]
    }
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
    for (i = 1; i <= n_placeholder; i++) {
        print placeholder[i]
        problems++
    }
    for (i = 1; i <= n_f; i++) {
        id = f_order[i]
        if (f_res[id] == "") {
            print id " (line " f_line[id] ") has no 'Resolution:' line - mechanical or decision"
            problems++
        } else if (f_res[id] != "mechanical" && f_res[id] != "decision") {
            print id " has Resolution: '" f_res[id] "' - must be mechanical or decision"
            problems++
        } else if (f_res[id] == "mechanical" && f_act[id] == "" && f_esc[id] == "") {
            print id " is mechanical but its 'Action:' is empty - apply it and record what changed"
            problems++
        }
    }
    for (i = 1; i <= n; i++) {
        id = order[i]
        nd = split(deps[id], d, ",")
        for (j = 1; j <= nd; j++) {
            if (d[j] == "") {
                continue
            }
            if (!(d[j] in seen)) {
                print id " depends on " d[j] ", which no item defines"
                problems++
            # A group runs to completion before the next one starts, so an edge into a later group
            # can never resolve: the item waits for work its own stage has already ruled out.
            } else if (group_of[id] in group_pos && group_of[d[j]] in group_pos \
                    && group_pos[group_of[id]] < group_pos[group_of[d[j]]]) {
                print id " (" group_of[id] ") depends on " d[j] " (" group_of[d[j]] "), which runs later"
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
        # plan.sh has its own checks to add and owns the verdict when it passes summary=0.
        if (summary != "0") {
            print n " items, no problems"
        }
    } else {
        exit 1
    }
}
