# [Conventions](../conventions.md) > Build

What is specific to this module. The wrapper, how to read a run, queueing, and dependency inspection are
[Building a Java Module](../../../docs/conventions/java-build.md).

| Name                     | Value                                               |
|--------------------------|-----------------------------------------------------|
| `--module`               | `ai-connector-service`                              |
| Test package root        | `bot.finance.ai`                                    |
| Architecture-enforcement | `bot.finance.ai.architecture.CleanArchitectureTest` |
| Coverage minimum         | `0.85` instructions                                 |

## Module Tasks

- Architecture check: `tools/agent-test/agent-test.sh --module ai-connector-service --tests "bot.finance.ai.architecture.CleanArchitectureTest"`
- Coverage guardrail: `tools/agent-test/agent-test.sh --module ai-connector-service --coverage`
- Coverage report (JaCoCo): `ai-connector-service/gradlew -p ai-connector-service jacocoTestReport`, after a
  `--coverage` run
- Reformat to style (Spotless): `ai-connector-service/gradlew -p ai-connector-service spotlessApply`
- Check formatting (Spotless): `ai-connector-service/gradlew -p ai-connector-service spotlessCheck` — runs as
  part of `check`
- Contract codegen: `ai-connector-service/gradlew -p ai-connector-service generateProto` — rarely needed on its
  own, since `compileJava` depends on it and a `.proto` edit regenerates on the next compile.

The first build after a clean checkout downloads the `protoc` toolchain and needs network access.

## Test Isolation

The suite runs against a stub server for the AI provider and the ledger, and containerized Postgres (pgvector),
Redis and Toxiproxy for the classes that need a real database or the change stream. An unexplained
container-startup failure is memory pressure until a rerun says otherwise; any other failure is a real one.

`.github/workflows/build.yml` runs `./gradlew build` on every pull request to `main`. The coverage guardrail is not
part of it.

A run without Docker skips every container-backed class instead of failing it, so the coverage guardrail is only
met with Docker up.
