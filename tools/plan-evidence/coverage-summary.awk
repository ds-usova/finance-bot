# Reads an istanbul-format coverage-summary.json — what vitest writes under the json-summary
# reporter — and answers one of two questions about it.
#
#   -v mode=total -v metric=lines            the whole module's percentage, as "84.2%"
#   -v mode=files -v metric=lines -v module=<name>
#                                            one row per file that is not fully covered:
#                                            module <tab> path <tab> ratio <tab> uncovered
#
# The rows match the shape plan-evidence.sh already builds from JaCoCo's CSV, so the same sort and
# the same table render both stacks.
#
# The file is one JSON object whose keys are "total" plus one path per source file, each holding a
# lines/statements/functions/branches object. Rather than parse JSON in general, this walks the
# top-level entries by counting braces, which is all the shape requires.
#
# The whole file is accumulated first: the reporter pretty-prints one entry per line, and a
# record-separator trick to slurp it is not portable to --posix.

{ text = text $0 }

# The value of "<field>" inside the "<name>" metric object, or "" when either is missing.
function metric_field(body, name, field,   seg, v) {
    if (!match(body, "\"" name "\"[ \t]*:[ \t]*\\{[^}]*\\}")) return ""
    seg = substr(body, RSTART, RLENGTH)
    if (!match(seg, "\"" field "\"[ \t]*:[ \t]*-?[0-9.]+")) return ""
    v = substr(seg, RSTART, RLENGTH)
    sub(/.*:[ \t]*/, "", v)
    return v
}

# A source path is reported from the module's own root: an absolute path says where the machine
# keeps the checkout, which is noise in a document someone else reads. Windows paths arrive with
# their backslashes escaped, so both spellings collapse to one separator first.
function short_path(key,   p) {
    p = key
    gsub(/\\\\/, "/", p)
    gsub(/\\/, "/", p)
    gsub(/\/\/+/, "/", p)
    if (match(p, /\/src\//)) p = substr(p, RSTART + 1)
    return p
}

END {
    while (match(text, /"[^"]*"[ \t]*:[ \t]*\{/)) {
        key = substr(text, RSTART + 1, RLENGTH - 1)
        sub(/"[ \t]*:[ \t]*\{$/, "", key)

        rest = substr(text, RSTART + RLENGTH)
        depth = 1
        i = 0
        n = length(rest)
        while (depth > 0 && i < n) {
            i++
            c = substr(rest, i, 1)
            if (c == "{") depth++
            else if (c == "}") depth--
        }
        body = substr(rest, 1, i - 1)
        text = substr(rest, i + 1)

        if (mode == "total") {
            if (key != "total") continue
            pct = metric_field(body, metric, "pct")
            if (pct == "") print "n/a"
            else printf "%.1f%%\n", pct
            exit
        }

        if (key == "total") continue

        total = metric_field(body, metric, "total")
        covered = metric_field(body, metric, "covered")
        if (total == "" || covered == "" || total + 0 == 0) continue

        uncovered = total - covered
        if (uncovered <= 0) continue
        printf "%s\t%s\t%.4f\t%d\n", module, short_path(key), covered / total, uncovered
    }

    if (mode == "total") print "n/a"
}
