#!/usr/bin/awk -f
#
# Parses a rework's checklist into one record per step and answers the query named by -v mode=.
#
# A step is "- [ ] <ID> · <kind> · <text>"; its block runs to the next step or heading, so the
# labelled lines under it stay attached. The kind decides which of those lines the step owes and
# which it may not carry, which is what validate checks.
#
# Invoked by rework.sh, which ships beside it; see the README in the same directory.

function trim(s) {
    sub(/^[ \t]+/, "", s)
    sub(/[ \t]+$/, "", s)
    return s
}

# A value the author left unfilled. A step agent does exactly what the line says, so a dash where the
# scenario belongs is not a shorter instruction - it is no instruction.
function is_placeholder(v) {
    return v == "" || v == "-" || v == "--" || v == "\xe2\x80\x94" || v == "\xe2\x80\x93" \
        || v == "..." || v == "\xe2\x80\xa6" || v == "TBD" || v == "tbd" || v == "TODO" \
        || v == "todo" || v == "N/A" || v == "n/a" || v ~ /^</
}

function problem(text) {
    problems[++problem_count] = text
}

# "files:" and "test-files:" put each path on a bullet of its own, so their label line carries no value.
# The label is only filled once a bullet follows it; one that never gets a bullet lists nothing at all.
# Reported here, where the label is superseded, so problems stay in line order.
function flush_awaiting(   aw) {
    if (awaiting != "") {
        split(awaiting, aw, "\t")
        problem(FILENAME ":" awaiting_line ": " aw[1] "'s \"" aw[2] ":\" lists nothing")
    }
    awaiting = ""
}

# "an extract step" reads as English; "a extract step" reads as a bug in the tool.
function a(word) {
    return (word ~ /^[aeiou]/ ? "an " : "a ") word
}

function close_step() {
    flush_awaiting()
    if (cur != "") {
        end_line[cur] = NR - 1
    }
    cur = ""
}

BEGIN {
    SEP = "\xc2\xb7"            # the middot the step header separates on
    step_count = 0
    problem_count = 0
    fenced = 0
    in_questions = 0
    open_question = ""
    awaiting = ""

    # The labels whose value is a list of bullets under them rather than text on the label line.
    lists_below["files"]      = 1
    lists_below["test-files"] = 1

    # Every labelled line a step may carry, and the kinds that take it. "*" means any kind.
    takes["files"]      = "inline extract pin stabilize"
    takes["test-files"] = "*"
    takes["runs"]       = "inline"
    takes["frozen"]     = "extract"
    takes["cover"]      = "extract"
    takes["survives"]   = "tests"
    takes["measures"]   = "tests"
    takes["proves"]     = "pin"
    takes["disables"]   = "stabilize"
    takes["needs"]      = "*"
    takes["docs"]       = "*"

    # What each kind cannot be written without. A pin edits a check or a setting, so it owes one of
    # "files:"/"test-files:" rather than either in particular; that pair is checked in END.
    requires["inline"]    = "files runs"
    requires["extract"]   = "files test-files frozen cover"
    requires["tests"]     = "test-files survives"
    requires["pin"]       = "proves"
    requires["stabilize"] = "files"

    kinds = " inline extract tests pin stabilize "
}

# A fenced block holds the format's own example. Counting its bullets as steps would give every
# rework the template's phantom IDs.
/^[ \t]*```/ {
    fenced = !fenced
    next
}
fenced { next }

/^#/ {
    close_step()
    in_questions = ($0 ~ /^#+[ \t]+Open Questions/)
    next
}

# - [ ] R01 · extract · what moves
/^[ \t]*-[ \t]+\[[ xX]\][ \t]+/ {
    close_step()

    line = $0
    ticked = (line ~ /\[[xX]\]/)
    sub(/^[ \t]*-[ \t]+\[[ xX]\][ \t]+/, "", line)

    id = ""
    if (match(line, /^[A-Za-z]+[0-9]+/)) {
        id = substr(line, RSTART, RLENGTH)
    }
    if (id == "") {
        problem(FILENAME ":" NR ": a step with no ID")
        next
    }
    if (id in start_line) {
        problem(FILENAME ":" NR ": duplicate ID " id " (first at line " start_line[id] ")")
        next
    }

    kind = ""
    p1 = index(line, SEP)
    if (p1 > 0) {
        rest = substr(line, p1 + length(SEP))
        p2 = index(rest, SEP)
        kind = trim(p2 > 0 ? substr(rest, 1, p2 - 1) : rest)
    }
    if (kind == "" || index(kinds, " " kind " ") == 0) {
        problem(FILENAME ":" NR ": " id " has no recognized kind (got \"" kind "\")")
    }

    order[++step_count] = id
    start_line[id] = NR
    end_line[id] = NR
    step_kind[id] = kind
    step_done[id] = ticked
    cur = id
    next
}

# - files: `path/to/A`
cur != "" && /^[ \t]+-[ \t]+[A-Za-z-]+:/ {
    flush_awaiting()
    end_line[cur] = NR
    line = trim($0)
    sub(/^-[ \t]+/, "", line)
    colon = index(line, ":")
    name = substr(line, 1, colon - 1)
    value = trim(substr(line, colon + 1))

    if (!(name in takes)) {
        problem(FILENAME ":" NR ": " cur " carries an unknown line \"" name ":\"")
        next
    }
    # A step whose kind was not recognized is reported once, at its header. Judging its lines against
    # a kind that does not exist would bury that one line under a cascade.
    allowed = takes[name]
    if (allowed != "*" && (step_kind[cur] in requires) && \
        index(" " allowed " ", " " step_kind[cur] " ") == 0) {
        problem(FILENAME ":" NR ": " cur " is " a(step_kind[cur]) " step and cannot carry \"" name ":\"")
    }
    if (is_placeholder(value)) {
        if (value == "" && (name in lists_below)) {
            awaiting = cur "\t" name
            awaiting_line = NR
        } else {
            problem(FILENAME ":" NR ": " cur "'s \"" name ":\" is empty or still a placeholder")
        }
    }
    seen[cur "\t" name] = 1

    if (name == "survives" && index(value, SEP) == 0) {
        problem(FILENAME ":" NR ": " cur "'s \"survives:\" names no tier - put what it runs against after a " SEP)
    }
    if (name == "needs" || name == "disables") {
        rest = value
        while (match(rest, /R[0-9]+/)) {
            referenced[cur "\t" substr(rest, RSTART, RLENGTH)] = NR
            rest = substr(rest, RSTART + RLENGTH)
        }
    }
    next
}

#     - `path/to/A`
awaiting != "" && /^[ \t]+-[ \t]+[^ \t]/ {
    awaiting = ""
    end_line[cur] = NR
    next
}

cur != "" && /^[ \t]+[^ \t]/ { end_line[cur] = NR; next }

# - **Q1:** … / - A:
in_questions && /^[ \t]*-[ \t]+\*\*Q[0-9]+/ {
    if (open_question != "") {
        problem(FILENAME ":" question_line ": " open_question " has no answer")
    }
    line = $0
    match(line, /Q[0-9]+/)
    open_question = substr(line, RSTART, RLENGTH)
    question_line = NR
    next
}
in_questions && /^[ \t]+-[ \t]+A:/ {
    if (trim(substr($0, index($0, ":") + 1)) != "") {
        open_question = ""
    }
    next
}

END {
    close_step()
    flush_awaiting()

    if (open_question != "") {
        problem(FILENAME ":" question_line ": " open_question " has no answer")
    }

    for (i = 1; i <= step_count; i++) {
        id = order[i]
        k = step_kind[id]
        if (k == "" || !(k in requires)) {
            continue
        }
        n = split(requires[k], need, " ")
        for (j = 1; j <= n; j++) {
            if (!((id "\t" need[j]) in seen)) {
                problem(FILENAME ":" start_line[id] ": " id " is " a(k) " step and owes \"" need[j] ":\"")
            }
        }
        if (k == "pin" && !((id "\tfiles") in seen) && !((id "\ttest-files") in seen)) {
            problem(FILENAME ":" start_line[id] ": " id " is a pin step and owes \"files:\" or \"test-files:\"")
        }
    }

    for (key in referenced) {
        split(key, part, "\t")
        if (!(part[2] in start_line)) {
            problem(FILENAME ":" referenced[key] ": " part[1] " names " part[2] ", which no step defines")
        }
    }

    if (mode == "list") {
        for (i = 1; i <= step_count; i++) {
            id = order[i]
            print id "\t" (step_done[id] ? "x" : " ") "\t" step_kind[id] "\t" \
                  start_line[id] "\t" end_line[id]
        }
        exit 0
    }

    if (mode == "validate") {
        for (i = 1; i <= problem_count; i++) {
            print problems[i]
        }
        exit (problem_count > 0 ? 1 : 0)
    }
}
