#!/usr/bin/env bash
#
# Read-only inspection of a dependency already in the Gradle cache.
#
# It exists so that reading a third-party class is one command instead of a pipeline that extracts
# archives into scratch directories: every mode streams from the jar where it lies and writes nothing.
#
# See tools/inspect-jar/README.md for what each mode answers and where its guarantees stop.

set -u

cache="${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1"

usage() {
    cat <<'EOF'
Usage:
  tools/inspect-jar/inspect-jar.sh --find   <artifact>
  tools/inspect-jar/inspect-jar.sh --list   <artifact> [entry-filter]
  tools/inspect-jar/inspect-jar.sh --which  <entry-filter> [artifact]
  tools/inspect-jar/inspect-jar.sh --cat    <artifact> <entry-path>
  tools/inspect-jar/inspect-jar.sh --source <artifact> <fully.qualified.Class>
  tools/inspect-jar/inspect-jar.sh --javap  <artifact> <fully.qualified.Class>

<artifact> is any fragment of the jar's file name — "openai-java-core", "spring-grpc-server",
"spring-ai-retry-2.0.0". It must match exactly one jar, or the candidates are listed instead.

Modes:
  --find    Where the jar is, and which versions are cached.
  --list    Entries in the jar; the optional filter is a case-insensitive substring.
  --which   Which cached jar contains an entry — when the artifact is what you are looking for.
  --cat     One entry, streamed to stdout by its path inside the jar.
  --source  A class's .java, from the artifact's -sources jar.
  --javap   A class disassembled with -p -c, read straight off the jar's classpath.
            Nested classes take a '$': 'com.openai.core.ClientOptions$Builder'.

Exit codes: 0 found - 1 no match - 2 ambiguous or bad usage.
EOF
}

# All cached jars matching a file-name fragment. Sources and javadoc jars are held back unless the
# caller's fragment asks for them by name, so a plain artifact fragment resolves to the binary jar.
find_jars() {
    local fragment="$1" want="${2:-binary}"
    if [ ! -d "$cache" ]; then
        echo "no Gradle cache at $cache" >&2
        return 1
    fi
    find "$cache" -name "*${fragment}*.jar" -type f 2>/dev/null | while read -r jar; do
        case "$jar" in
            *-sources.jar) [ "$want" = sources ] && echo "$jar" ;;
            *-javadoc.jar) [ "$want" = javadoc ] && echo "$jar" ;;
            *)             [ "$want" = binary ]  && echo "$jar" ;;
        esac
    done
    # An empty result is not a failure here; only a missing cache is, and that returned above.
    return 0
}

# Resolves a fragment to exactly one jar, or explains why it could not.
resolve_jar() {
    local fragment="$1" want="${2:-binary}" matches count
    matches="$(find_jars "$fragment" "$want")" || return 2
    count="$(printf '%s' "$matches" | grep -c . || true)"
    if [ "$count" -eq 0 ]; then
        echo "no ${want} jar matching '${fragment}' in $cache" >&2
        return 1
    fi
    if [ "$count" -gt 1 ]; then
        echo "'${fragment}' matches ${count} jars - narrow it (a version usually does):" >&2
        printf '%s\n' "$matches" | sed 's/^/  /' >&2
        return 2
    fi
    printf '%s\n' "$matches"
}

class_to_path() {
    printf '%s\n' "$1" | tr '.' '/'
}

mode="${1:-}"
[ -n "$mode" ] || { usage; exit 2; }
shift

case "$mode" in
    --find)
        fragment="${1:-}"; [ -n "$fragment" ] || { usage; exit 2; }
        found=0
        for want in binary sources javadoc; do
            jars="$(find_jars "$fragment" "$want")" || exit 1
            [ -n "$jars" ] || continue
            found=1
            printf '%s\n' "$jars"
        done
        [ "$found" -eq 1 ] || { echo "nothing matching '${fragment}' in $cache" >&2; exit 1; }
        ;;

    --list)
        fragment="${1:-}"; filter="${2:-}"
        [ -n "$fragment" ] || { usage; exit 2; }
        jar="$(resolve_jar "$fragment")" || exit $?
        if [ -n "$filter" ]; then
            unzip -Z1 "$jar" | grep -i -- "$filter" || { echo "no entry matching '${filter}'" >&2; exit 1; }
        else
            unzip -Z1 "$jar"
        fi
        ;;

    --which)
        filter="${1:-}"; fragment="${2:-}"
        [ -n "$filter" ] || { usage; exit 2; }
        hits=0
        while read -r jar; do
            if unzip -Z1 "$jar" 2>/dev/null | grep -qi -- "$filter"; then
                echo "$jar"
                unzip -Z1 "$jar" 2>/dev/null | grep -i -- "$filter" | sed 's/^/    /'
                hits=1
            fi
        done < <(find "$cache" -name "*${fragment}*.jar" -type f 2>/dev/null | grep -v -- '-javadoc\.jar$')
        [ "$hits" -eq 1 ] || { echo "no cached jar carries an entry matching '${filter}'" >&2; exit 1; }
        ;;

    --cat)
        fragment="${1:-}"; entry="${2:-}"
        [ -n "$fragment" ] && [ -n "$entry" ] || { usage; exit 2; }
        jar="$(resolve_jar "$fragment")" || exit $?
        unzip -p "$jar" "$entry" || { echo "no entry '${entry}' in $jar" >&2; exit 1; }
        ;;

    --source)
        fragment="${1:-}"; class="${2:-}"
        [ -n "$fragment" ] && [ -n "$class" ] || { usage; exit 2; }
        jar="$(resolve_jar "$fragment" sources)" || exit $?
        # A nested class lives in its outer class's file.
        entry="$(class_to_path "${class%%\$*}").java"
        unzip -p "$jar" "$entry" || { echo "no source '${entry}' in $jar" >&2; exit 1; }
        ;;

    --javap)
        fragment="${1:-}"; class="${2:-}"
        [ -n "$fragment" ] && [ -n "$class" ] || { usage; exit 2; }
        jar="$(resolve_jar "$fragment")" || exit $?
        javap -p -c -cp "$jar" "$class"
        ;;

    -h|--help)
        usage
        ;;

    *)
        echo "unknown mode '${mode}'" >&2
        usage
        exit 2
        ;;
esac
