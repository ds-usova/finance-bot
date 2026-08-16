# Parses a design file: its section shape, and the D<n> entries under Decisions.
#
# Strict POSIX awk - no gensub, no length(array), no third argument to match(). Checked with
# `gawk --posix`, so mawk and BSD awk serve as well as gawk.
#
# Modes (-v mode=...):
#   validate  problems on stdout, exit 1 if any
#   open      the IDs whose Basis is must-decide, one per line
#   status    counts per basis
#   range     with -v want=<ID>: "<first line> <last line>" of that entry

function trim(s) {
    sub(/^[ \t]+/, "", s)
    sub(/[ \t]+$/, "", s)
    return s
}

# An em dash is one character to a multibyte-aware awk and three bytes to every other one. Both
# spellings are tried rather than assuming which awk is running.
function strip_dash(s) {
    s = trim(s)
    if (substr(s, 1, 1) == "\342\200\224") s = substr(s, 2)
    else if (substr(s, 1, 3) == "\342\200\224") s = substr(s, 4)
    else if (substr(s, 1, 1) == "-") s = substr(s, 2)
    return trim(s)
}

# What counts as unfilled. The same placeholder set plan.sh rejects in a given:/when:/then:.
function unfilled(v) {
    v = trim(v)
    if (v == "") return 1
    if (v == "-" || v == "\342\200\224") return 1
    if (toupper(v) == "TBD" || toupper(v) == "N/A") return 1
    return 0
}

# The number inside a D<n> or F<n> id, for ordering.
function idnum(id) {
    sub(/^[A-Za-z]+/, "", id)
    return id + 0
}

function close_entry() {
    if (cur > 0) eline[cur] = lastcontent
    cur = 0
}

function problem(msg) {
    print msg
    problems = 1
}

BEGIN {
    nent = 0; nsec = 0; cur = 0; problems = 0; infence = 0; nrow = 0
    lastcontent = 0; seen_modules = 0; grilled = 0; nsub = 0; nsce = 0
    nreq = split("## Objective,## Context,## Proposed Solution,## Acceptance Scenarios,## Decisions,## Design Findings", req, ",")
}

# A design file quotes its own entry format; bullets inside a fence are examples, not entries.
{
    if ($0 ~ /^[ \t]*```/) { infence = 1 - infence; next }
    if (infence) next
}

/^\*\*Affected Modules:\*\*/ { seen_modules = 1 }

/^## / {
    close_entry()
    nsec++
    sect[nsec] = trim($0)
    section = trim($0)
    lastcontent = NR
    next
}

section == "## Design Findings" && /Grilled \(/ { grilled = 1 }

# The size report. A design grows with the subjects it carries, and a #### section under Proposed
# Solution is where a subject shows: the counts are printed with the verdict so the session sees the
# size on every run and splits the task before the file outgrows its reader.
section == "## Proposed Solution" && /^#### / { nsub++ }
section == "## Acceptance Scenarios" && /^- \*\*A[0-9]+:\*\*/ { nsce++ }

# A Design Findings row carries an F id in its first cell. Numbered on the same terms as a D entry, so the
# same duplicate check applies - the body cites these.
section == "## Design Findings" && /^\|[ \t]*F[0-9]+[ \t]*\|/ {
    fid = $0
    sub(/^\|[ \t]*/, "", fid)
    sub(/[ \t]*\|.*$/, "", fid)
    nrow++
    rowid[nrow] = fid
    lastcontent = NR
    next
}

/^- \*\*D[0-9]+:\*\*/ {
    close_entry()
    nent++
    cur = nent

    id = $0
    sub(/^- \*\*/, "", id)
    sub(/:\*\*.*$/, "", id)

    q = $0
    sub(/^- \*\*D[0-9]+:\*\*[ \t]*/, "", q)

    eid[nent] = id
    question[nent] = trim(q)
    sline[nent] = NR
    insec[nent] = section
    nans[nent] = 0
    nbasis[nent] = 0
    ans[nent] = ""
    kw[nent] = ""
    why[nent] = ""
    lastcontent = NR
    next
}

# Answer and Basis are nested under their entry, so the bullet is indented. A flat one is still read:
# the indentation is a rendering rule, and an older design file must not stop parsing because of it.
cur > 0 && /^[ \t]*- Answer:/ {
    v = $0
    sub(/^[ \t]*- Answer:[ \t]*/, "", v)
    nans[cur]++
    ans[cur] = trim(v)
    lastcontent = NR
    next
}

cur > 0 && /^[ \t]*- Basis:/ {
    v = $0
    sub(/^[ \t]*- Basis:[ \t]*/, "", v)
    raw = trim(v)
    nbasis[cur]++

    k = ""
    if (raw ~ /^assumed/) k = "assumed"
    else if (raw ~ /^decided/) k = "decided"
    else if (raw ~ /^deferred/) k = "deferred"
    else if (raw ~ /^must-decide/) k = "must-decide"

    kw[cur] = k
    if (k == "") why[cur] = raw
    else why[cur] = strip_dash(substr(raw, length(k) + 1))
    lastcontent = NR
    next
}

NF { lastcontent = NR }

END {
    close_entry()

    if (mode == "range") {
        for (i = 1; i <= nent; i++)
            if (eid[i] == want) { print sline[i] " " eline[i]; exit 0 }
        exit 1
    }

    if (mode == "open") {
        for (i = 1; i <= nent; i++)
            if (kw[i] == "must-decide") print eid[i] "\t" question[i]
        exit 0
    }

    if (mode == "status") {
        for (i = 1; i <= nent; i++) {
            k = (kw[i] == "" ? "unrecognized" : kw[i])
            count[k]++
        }
        n = split("assumed,decided,deferred,must-decide,unrecognized", order, ",")
        for (i = 1; i <= n; i++)
            if (count[order[i]] > 0) print order[i] "\t" count[order[i]]
        print "total\t" nent
        exit 0
    }

    # mode == validate
    if (!seen_modules)
        problem("no '**Affected Modules:**' line - it belongs at the very top, under the title")

    # Sections must appear, and in the order the format fixes. Each required heading is located
    # once, then the positions are checked for monotonicity - so two swapped sections report as one
    # displaced section rather than as every section after them being "out of order" too.
    for (j = 1; j <= nreq; j++) {
        pos[j] = 0
        for (i = 1; i <= nsec; i++)
            if (sect[i] == req[j]) pos[j] = i
        if (pos[j] == 0) problem("section '" req[j] "' is missing")
    }
    prev = 0
    for (j = 1; j <= nreq; j++) {
        if (pos[j] == 0) continue
        if (pos[j] < prev) problem("section '" req[j] "' comes before '" lastseen "'")
        else { prev = pos[j]; lastseen = req[j] }
    }

    if (nent == 0)
        problem("the Decisions section records no D<n> entry - every change makes at least one call")

    for (i = 1; i <= nent; i++) {
        if (seen[eid[i]]) problem(eid[i] " is defined twice")
        seen[eid[i]] = 1

        if (insec[i] != "## Decisions")
            problem(eid[i] " sits under '" insec[i] "' - entries belong in the Decisions section")

        if (unfilled(question[i]))
            problem(eid[i] " states no question")

        if (nbasis[i] == 0) { problem(eid[i] " has no 'Basis:' line"); continue }
        if (nbasis[i] > 1) problem(eid[i] " has " nbasis[i] " 'Basis:' lines")
        if (nans[i] == 0) { problem(eid[i] " has no 'Answer:' line"); continue }
        if (nans[i] > 1) problem(eid[i] " has " nans[i] " 'Answer:' lines")

        if (kw[i] == "") {
            problem(eid[i] " has an unrecognized Basis - one of assumed, decided, deferred, must-decide")
            continue
        }

        # Every basis owes its reason: the evidence for an assumption, who chose a decision, what a
        # deferral costs, what the repository does not say. A bare keyword is the hedge this catches.
        if (unfilled(why[i]))
            problem(eid[i] " is '" kw[i] "' with nothing after it - say what it rests on")

        if (kw[i] == "must-decide" && !unfilled(ans[i]))
            problem(eid[i] " is must-decide but carries an Answer - settle the Basis or clear the Answer")
        if (kw[i] != "must-decide" && unfilled(ans[i]))
            problem(eid[i] " is '" kw[i] "' with no Answer")

        # Decisions is what the user reads: the calls they made, and the ones waiting for them. A question the
        # repository settles was still asked, and its record is a Design Findings row rather than an entry.
        if (kw[i] == "assumed" || kw[i] == "deferred")
            problem(eid[i] " is '" kw[i] "' - only decided and must-decide are entries, the rest are Design Findings rows")
    }

    for (i = 1; i <= nrow; i++) {
        if (seenrow[rowid[i]]) problem(rowid[i] " is defined twice")
        seenrow[rowid[i]] = 1
    }

    # Findings numbers are assigned once and never reused, so the table is read in ascending order. A row
    # inserted above a higher-numbered one is the edit that anchored on the wrong line, and it is caught here
    # rather than by a grep after the fact. Decisions are not checked: a design may group them by subject.
    for (i = 2; i <= nrow; i++)
        if (idnum(rowid[i]) < idnum(rowid[i - 1]))
            problem(rowid[i] " comes after " rowid[i - 1] " - findings rows keep ascending order")

    if (!grilled)
        problem("Design Findings carries no 'Grilled (<date>):' line - the grill has not run")

    if (!problems) print nsub " solution sections, " nsce " scenarios, " nent " decisions, " nrow " findings, no problems"
    exit problems
}
