# The Jar Inspector

`tools/inspect-jar/inspect-jar.sh` answers questions about a dependency that is already in the Gradle cache: where it is,
what is inside it, what a class's source says, and what a compiled method actually does.

## Why it exists

Reading a third-party class used to be a pipeline assembled on the spot — `find` the jar under
`~/.gradle/caches`, `unzip -l` to see the entries, extract the sources jar into a temporary directory, `javap` a
class from the extracted tree, then `grep` the result. Every step wrote something somewhere, and none of the
answers were reproducible afterwards.

The inspector collapses that into one command per question, and **writes nothing**: `unzip -p` streams a single
entry to stdout and `javap -cp` reads a class off the archive without unpacking it. There is no scratch
directory to place, clean up, or grant access to.

## Usage

Run it with bash, from the **repository root**:

```
tools/inspect-jar/inspect-jar.sh --find   openai-java-core
tools/inspect-jar/inspect-jar.sh --which  BeanOutputConverter spring-ai
tools/inspect-jar/inspect-jar.sh --javap  openai-java-core 'com.openai.core.ClientOptions$Builder'
```

| Mode                            | Answers                                                               |
|---------------------------------|-----------------------------------------------------------------------|
| `--find <artifact>`             | Where the jar is, and which versions and companion jars are cached.   |
| `--list <artifact> [filter]`    | The entries in the jar; the filter is a case-insensitive substring.   |
| `--which <filter> [artifact]`   | Which cached jar carries an entry — when the artifact is the unknown. |
| `--cat <artifact> <entry-path>` | One entry, streamed by its path inside the jar.                       |
| `--source <artifact> <Class>`   | A class's `.java`, from the artifact's `-sources` jar.                |
| `--javap <artifact> <Class>`    | The class disassembled with `-p -c`.                                  |

Exit codes: **0** found, **1** no match, **2** the artifact fragment was ambiguous, or the usage was wrong.

### Naming an artifact

`<artifact>` is any fragment of the jar's **file name** — `openai-java-core`, `spring-grpc-server`,
`spring-ai-model-2.0.0`. It has to resolve to exactly one jar; when it does not, the candidates are listed and
the run exits 2, so adding the version is usually enough.

Sources and javadoc jars are held back from that resolution, which is why a plain fragment reaches the binary
jar and `--source` still finds the sources one.

### Naming a class

Fully qualified, and a nested class takes a `$`, quoted so the shell leaves it alone:

```
tools/inspect-jar/inspect-jar.sh --javap openai-java-core 'com.openai.core.ClientOptions$Builder'
```

`--source` maps a nested class back to its outer class's file, since that is where the source lives.

### On Windows

Run it from a **Git Bash** prompt. It needs `unzip` on the `PATH` (Git Bash supplies it) and `javap` from the
JDK. `.gitattributes` pins `*.sh` to LF, because a CRLF checkout fails on the first line.

## Where it stops

It reads what Gradle has **already downloaded** — it resolves nothing and fetches nothing. A dependency no
module compiles against is not in the cache, so build the module first.

Sources jars are downloaded only when something asks for them, typically an IDE sync. `--source` failing while
`--find` shows the binary jar means the sources were never fetched, not that the class is missing; `--javap`
still works, and reports the original file name in its first line.

`--which` without an artifact fragment opens every jar in the cache — around 80 seconds against this one.
Narrowing it to a group or artifact, as in `--which BeanOutputConverter spring-ai`, brings it back to seconds.
