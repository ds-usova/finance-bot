# Turns Gradle's JUnit XML result files into a plain-text summary.
#
# Invoked by tools/agent-test/agent-test.sh over every TEST-*.xml of a single run; the caller passes
# command, exitCode, and coverageFailed as -v variables and captures stdout as the run's summary.

function unescape(s) {
    gsub(/&lt;/, "<", s)
    gsub(/&gt;/, ">", s)
    gsub(/&quot;/, "\"", s)
    gsub(/&apos;/, "'", s)
    gsub(/&#10;/, " ", s)
    gsub(/&#13;/, "", s)
    gsub(/&amp;/, "\\&", s)
    return s
}

function attribute(text, name,    pattern, raw) {
    pattern = name "=\"[^\"]*\""
    if (!match(text, pattern)) return ""
    raw = substr(text, RSTART + length(name) + 2, RLENGTH - length(name) - 3)
    return unescape(raw)
}

function shortClassName(fqn,    parts, count) {
    count = split(fqn, parts, ".")
    return parts[count]
}

BEGIN {
    failureCount = 0
    suiteCount = 0
}

# A new result file starts a new suite.
FNR == 1 {
    inSystemStream = 0
    inFailure = 0
}

/<system-(out|err)>/ { inSystemStream = 1 }
/<\/system-(out|err)>/ { inSystemStream = 0; next }
inSystemStream { next }

/<testsuite / {
    suiteName = attribute($0, "name")
    suiteTests = attribute($0, "tests") + 0
    suiteSkipped = attribute($0, "skipped") + 0
    suiteFailures = attribute($0, "failures") + 0 + attribute($0, "errors") + 0

    totalTests += suiteTests
    totalSkipped += suiteSkipped
    totalFailures += suiteFailures
    totalTime += attribute($0, "time") + 0

    suiteCount++
    suiteDisplayName[suiteCount] = suiteName
    suiteClass[suiteCount] = ""
    suiteCounts[suiteCount] = sprintf("%3d tests, %d failed, %d skipped", \
        suiteTests, suiteFailures, suiteSkipped)
    next
}

/<testcase / {
    currentTest = attribute($0, "name")
    currentClass = attribute($0, "classname")

    # A suite is named after its @DisplayName, which is ambiguous across classes; the test cases
    # carry the class name you would actually pass back to --tests.
    if (suiteCount > 0 && suiteClass[suiteCount] == "") suiteClass[suiteCount] = currentClass
}

# The opening tag of a failure carries the assertion message; the element body carries the stack trace.
/<(failure|error)[ >]/ {
    inFailure = 1
    bodyLines = 0
    failureCount++
    failureHeader[failureCount] = shortClassName(currentClass) " > " currentTest
    failureMessage[failureCount] = attribute($0, "message")
    failureType[failureCount] = attribute($0, "type")
    failureBody[failureCount] = ""

    # Anything after the opening tag on the same line is already part of the body.
    tail = $0
    sub(/^.*<(failure|error)[^>]*>/, "", tail)
    $0 = tail
}

inFailure {
    line = $0
    if (line ~ /<\/(failure|error)>/) {
        sub(/<\/(failure|error)>.*$/, "", line)
        inFailure = 0
    }

    gsub(/^[ \t]+|[ \t]+$/, "", line)
    if (line != "") {
        # Keep the exception line plus the frames pointing back into this project; the rest of a
        # JUnit stack trace is framework noise that never tells you what to fix.
        if (bodyLines < 2 || (line ~ /bot\.finance/ && bodyLines < 12)) {
            failureBody[failureCount] = failureBody[failureCount] "    " unescape(line) "\n"
            bodyLines++
        }
    }
    next
}

END {
    if (suiteCount == 0) {
        print "Result: NO TESTS RAN"
        print "Command: " command
        print "Gradle exit code: " exitCode
        print ""
        print "No JUnit result files were produced. The build failed before the tests ran"
        print "(compilation error, missing dependency, or a broken test-runtime setup)."
        exit 0
    }

    if (exitCode == 0 && totalFailures == 0) verdict = "PASS"
    else if (totalFailures == 0 && coverageFailed == 1) verdict = "COVERAGE BELOW MINIMUM"
    else verdict = "FAIL"

    print "Result: " verdict
    print "Command: " command
    print "Gradle exit code: " exitCode
    printf "Totals: %d tests, %d passed, %d failed, %d skipped (%.1fs)\n", \
        totalTests, totalTests - totalFailures - totalSkipped, totalFailures, totalSkipped, totalTime

    if (totalSkipped > 0 && totalSkipped == totalTests) {
        print ""
        print "WARNING: every test was skipped. For container-based suites this normally means Docker is not running."
    }

    if (failureCount > 0) {
        print ""
        print "== Failures =="
        for (i = 1; i <= failureCount; i++) {
            print ""
            print "- " failureHeader[i]
            if (failureType[i] != "") print "    type: " failureType[i]
            if (failureMessage[i] != "") print "    message: " failureMessage[i]
            printf "%s", failureBody[i]
        }
    }

    print ""
    print "== Test classes =="
    for (i = 1; i <= suiteCount; i++) {
        name = suiteClass[i] != "" ? suiteClass[i] : suiteDisplayName[i]
        printf "%-66s %s\n", name, suiteCounts[i]
    }
}
