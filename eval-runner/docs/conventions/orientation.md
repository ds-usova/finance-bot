# [Conventions](../conventions.md) > Orientation

## Tech Stack

| Concern       | What                                                                                       |
|---------------|--------------------------------------------------------------------------------------------|
| Language      | Java, at the toolchain version `build.gradle` names                                        |
| Build         | Gradle wrapper, `application` plugin; main class `bot.finance.eval.EvalRunner`             |
| Versions      | the Spring Boot BOM only, for Jackson, the Postgres driver and logging; no Spring context  |
| JSON and YAML | Jackson 3 (`tools.jackson`)                                                                |
| HTTP          | the JDK's `java.net.http.HttpClient`                                                       |
| Database      | the Postgres JDBC driver, read only, against the connector's database                      |
| Logging       | SLF4J over Logback, configured in `src/main/resources/logback.xml`                         |

## Read Before Changing Anything

- [README](../../README.md) — how a run is started, what it needs running, what it measures.
- [Evaluation plan](../../../docs/eval/plan.md), [cases](../../../docs/eval/cases.md) and
  [scoring](../../../docs/eval/scoring.md) — what the runner implements.
- [`docs/eval/corpus/`](../../../docs/eval/corpus/) — the cases it runs; `CorpusTest` loads every one.
