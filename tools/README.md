# Tools

Scripts that support working on this repository, rather than anything it ships. Each one lives in its own
directory with its README and whatever helper files it needs, so a tool is added or removed in one place.

| Tool                                      | What it answers                                            |
|-------------------------------------------|------------------------------------------------------------|
| [`agent-test/`](agent-test/README.md)     | Did the module compile, which tests failed, is it covered. |
| [`inspect-jar/`](inspect-jar/README.md)   | What is inside a dependency already in the Gradle cache.   |
| [`repad-tables/`](repad-tables/README.md) | Realigns the markdown tables in the files it is given.     |

Run them with bash from the **repository root** — on Windows, a Git Bash prompt:

```
tools/agent-test/agent-test.sh --module ai-connector-service --all
tools/inspect-jar/inspect-jar.sh --which BeanOutputConverter spring-ai
```

## Adding a tool

A new tool is a directory here holding its script and a `README.md` shaped like the existing ones: why it
exists, a usage table, exit codes, the Git Bash note, and a closing section on where its guarantees stop. Add
its row to the table above, and give it a `Bash(tools/<name>/<name>.sh:*)` entry in `.claude/settings.json` so
it does not need approving invocation by invocation.
