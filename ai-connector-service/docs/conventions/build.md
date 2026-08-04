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

- Coverage guardrail: `tools/agent-test/agent-test.sh --module ai-connector-service --coverage`
- Coverage report (JaCoCo): `ai-connector-service/gradlew -p ai-connector-service test jacocoTestReport`
- Reformat to style (Spotless): `ai-connector-service/gradlew -p ai-connector-service spotlessApply`
- Check formatting (Spotless): `ai-connector-service/gradlew -p ai-connector-service spotlessCheck` — runs as
  part of `check`
- Contract codegen: `ai-connector-service/gradlew -p ai-connector-service generateProto` — rarely needed on its
  own, since `compileJava` depends on it and a `.proto` edit regenerates on the next compile.

The first build after a clean checkout downloads the `protoc` toolchain and needs network access.

## Test Isolation

The suite runs entirely in-process — the one external system is stubbed by WireMock — so every failure it
reports is a real one. Rerunning is not a diagnosis.
