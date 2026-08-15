#!/usr/bin/awk -f
#
# Parses an upgrade's checklist and its attempt log into one record per entry, and answers the query
# named by -v mode=.
#
# A step is "- [ ] <ID> · <kind> · <text>"; its block runs to the next step or heading, so the
# labelled lines under it stay attached. The kind decides which of those lines the step owes and
# which it may not carry, which is what validate checks.
#
# An attempt is "- **A1** · <step ID> · <text>" under "## Attempts". It owes its reasoning, its
# result, its evidence and what it rules out; the shape is templates/attempts.md.
#
# Invoked by upgrade.sh, which ships beside it; see the README in the same directory.

function trim(s) {
    sub(/^[ \t]+/, "", s)
    sub(/[ \t]+$/, "", s)
    return s
}

# A value the author left unfilled. An agent does exactly what the line says, so a dash where the
# change belongs is not a shorter instruction - it is no instruction.
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
function flush_awaiting(   aw) {
    if (awaiting != "") {
        split(awaiting, aw, "\t")
        problem(FILENAME ":" awaiting_line ": " aw[1] "'s \"" aw[2] ":\" lists nothing")
    }
    awaiting = ""
}

function close_step() {
    flush_awaiting()
    if (cur != "") {
        end_line[cur] = NR - 1
    }
    cur = ""
}

BEGIN {
    SEP = "\xc2\xb7"            # the middot the step and attempt headers separate on
    step_count = 0
    attempt_count = 0
    problem_count = 0
    fenced = 0
    fence_len = 0
    fence_char = ""
    fence_in_step = 0
    in_questions = 0
    in_attempts = 0
    open_question = ""
    awaiting = ""
    awaiting_evidence = ""
    cur = ""
    cur_attempt = ""

    lists_below["files"]      = 1
    lists_below["test-files"] = 1

    # Every labelled line a step may carry, and the kinds that take it. "*" means any kind.
    takes["files"]      = "*"
    takes["test-files"] = "migrate"
    takes["guide"]      = "*"
    takes["change"]     = "migrate"
    takes["needs"]      = "*"
    takes["docs"]       = "*"

    # What each kind cannot be written without.
    requires["bump"]    = "files guide"
    requires["migrate"] = "files guide change"

    kinds = " bump migrate "

    attempt_labels   = " why result evidence ruled-out "
    attempt_requires = "why result evidence ruled-out"
}

# A checkout with CRLF endings otherwise leaves a carriage return on every line.
{ sub(/\r$/, "") }

# A fenced block holds the format's own example, or an attempt's evidence. The marker's length
# decides what closes it, as in Markdown itself: pasted output routinely carries a fence of its own.
/^[ \t]*```/ || /^[ \t]*~~~/ {
    fence = $0
    indented = ($0 ~ /^[ \t]/)
    sub(/^[ \t]+/, "", fence)
    char = substr(fence, 1, 1)
    if (char == "`") {
        match(fence, /^`+/)
    } else {
        match(fence, /^~+/)
    }
    if (fenced) {
        if (char == fence_char && RLENGTH == fence_len && trim(substr(fence, RLENGTH + 1)) == "") {
            fenced = 0
            fence_len = 0
            awaiting_evidence = ""
        }
    } else {
        fenced = 1
        fence_len = RLENGTH
        fence_char = char
        fence_in_step = (cur != "" && indented)
    }
    if (fence_in_step) {
        end_line[cur] = NR
    }
    next
}
fenced {
    if (awaiting_evidence != "" && trim($0) != "") {
        evidence_seen[awaiting_evidence] = 1
    }
    if (fence_in_step) {
        end_line[cur] = NR
    }
    next
}

# "evidence:" is answered by the block that follows it, not by the next block anywhere in the file.
awaiting_evidence != "" && /[^ \t]/ { awaiting_evidence = "" }

/^#/ {
    close_step()
    cur_attempt = ""
    in_questions = ($0 ~ /^#+[ \t]+[Oo]pen [Qq]uestions?/)
    in_attempts  = ($0 ~ /^#+[ \t]+[Aa]ttempts?/)
    next
}

# - [ ] U01 · bump · `group:artifact` 1.0 -> 1.1
#
# At the left margin only. A checkbox indented under a step is part of that step's own text.
/^-[ \t]+\[[ xX]\][ \t]+/ {
    close_step()
    cur_attempt = ""

    line = $0
    ticked = (line ~ /\[[xX]\]/)
    sub(/^-[ \t]+\[[ xX]\][ \t]+/, "", line)

    id = ""
    if (match(line, /^[A-Za-z]+[0-9]+/)) {
        id = substr(line, RSTART, RLENGTH)
    }
    if (id == "") {
        problem(FILENAME ":" NR ": a step with no ID")
        next
    }
    if (in_attempts) {
        problem(FILENAME ":" NR ": " id " is defined inside the Attempts section, which holds no steps")
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

    # A step given up on keeps its row; "abandoned" in the header is how it says so.
    abandoned = (line ~ /abandoned/)

    order[++step_count] = id
    start_line[id] = NR
    end_line[id] = NR
    step_kind[id] = kind
    step_done[id] = ticked
    step_abandoned[id] = abandoned
    cur = id
    next
}

# - **A1** · U03 · what was tried
/^[ \t]*-[ \t]+\*\*A[0-9]+\*\*/ {
    flush_awaiting()
    cur = ""
    line = $0
    match(line, /A[0-9]+/)
    aid = substr(line, RSTART, RLENGTH)

    if (!in_attempts) {
        problem(FILENAME ":" NR ": " aid " is written outside an \"Attempts\" section, where nothing reads it")
        cur_attempt = ""
        next
    }
    if (aid in attempt_line) {
        problem(FILENAME ":" NR ": duplicate attempt " aid " (first at line " attempt_line[aid] ")")
        cur_attempt = ""
        next
    }

    phase = ""
    p1 = index(line, SEP)
    if (p1 > 0) {
        rest = substr(line, p1 + length(SEP))
        p2 = index(rest, SEP)
        phase = trim(p2 > 0 ? substr(rest, 1, p2 - 1) : rest)
    }
    if (phase == "") {
        problem(FILENAME ":" NR ": " aid " names no phase - put the step ID after the " SEP)
    } else {
        attempt_phase_line[aid] = NR
        attempt_phase[aid] = phase
    }

    attempt_order[++attempt_count] = aid
    attempt_line[aid] = NR
    cur_attempt = aid
    next
}

# - files: / - result: what happened
(cur != "" || cur_attempt != "") && /^[ \t]+-[ \t]+[A-Za-z-]+:/ {
    flush_awaiting()
    if (cur != "") {
        end_line[cur] = NR
    }
    line = trim($0)
    sub(/^-[ \t]+/, "", line)
    colon = index(line, ":")
    name = substr(line, 1, colon - 1)
    value = trim(substr(line, colon + 1))

    if (cur_attempt != "") {
        if (index(attempt_labels, " " name " ") == 0) {
            problem(FILENAME ":" NR ": " cur_attempt " carries an unknown line \"" name ":\"")
            next
        }
        aseen[cur_attempt "\t" name] = 1
        if (name == "evidence") {
            awaiting_evidence = cur_attempt
        } else if (is_placeholder(value)) {
            problem(FILENAME ":" NR ": " cur_attempt "'s \"" name ":\" is empty or still a placeholder")
        }
        next
    }

    if (!(name in takes)) {
        problem(FILENAME ":" NR ": " cur " carries an unknown line \"" name ":\"")
        next
    }
    allowed = takes[name]
    if (allowed != "*" && (step_kind[cur] in requires) && \
        index(" " allowed " ", " " step_kind[cur] " ") == 0) {
        problem(FILENAME ":" NR ": " cur " is a " step_kind[cur] " step and cannot carry \"" name ":\"")
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

    # A change names one guide item and one place, separated by the middot.
    if (name == "change" && index(value, SEP) == 0) {
        problem(FILENAME ":" NR ": " cur "'s \"change:\" names no place - put where it lands after a " SEP)
    }
    if (name == "needs") {
        rest = value
        while (match(rest, /U[0-9]+/)) {
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

    if (fenced) {
        problem(FILENAME ": a fenced block never closes")
    }
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
                problem(FILENAME ":" start_line[id] ": " id " is a " k " step and owes \"" need[j] ":\"")
            }
        }
    }

    for (key in referenced) {
        split(key, part, "\t")
        if (!(part[2] in start_line)) {
            problem(FILENAME ":" referenced[key] ": " part[1] " names " part[2] ", which no step defines")
        }
    }

    for (i = 1; i <= attempt_count; i++) {
        aid = attempt_order[i]
        n = split(attempt_requires, need, " ")
        for (j = 1; j <= n; j++) {
            if (!((aid "\t" need[j]) in aseen)) {
                problem(FILENAME ":" attempt_line[aid] ": " aid " owes \"" need[j] ":\"")
            }
        }
        if ((aid "\tevidence") in aseen && !(aid in evidence_seen)) {
            problem(FILENAME ":" attempt_line[aid] ": " aid "'s \"evidence:\" has no fenced output under it")
        }
        if ((aid in attempt_phase) && !(attempt_phase[aid] in start_line)) {
            problem(FILENAME ":" attempt_phase_line[aid] ": " aid " is filed under " attempt_phase[aid] \
                    ", which no step defines")
        }
    }

    if (mode == "list") {
        for (i = 1; i <= step_count; i++) {
            id = order[i]
            state = step_done[id] ? "x" : (step_abandoned[id] ? "a" : " ")
            print id "\t" state "\t" step_kind[id] "\t" start_line[id] "\t" end_line[id]
        }
        exit 0
    }

    if (mode == "attempts") {
        for (i = 1; i <= attempt_count; i++) {
            print attempt_order[i]
        }
        exit 0
    }

    if (mode == "validate") {
        for (i = 1; i <= problem_count; i++) {
            print problems[i]
        }
        if (problem_count == 0) {
            print FILENAME ": " step_count (step_count == 1 ? " step, " : " steps, ") \
                  attempt_count (attempt_count == 1 ? " attempt, " : " attempts, ") "no problems"
        }
        exit (problem_count > 0 ? 1 : 0)
    }
}
