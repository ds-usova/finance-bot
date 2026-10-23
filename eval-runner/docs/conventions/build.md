# [Conventions](../conventions.md) > Build

What is specific to this module. The wrapper, how to read a run, queueing, formatting and dependencies are
[Building a Java Module](../../../docs/conventions/java-build.md).

| Name                     | Value             |
|--------------------------|-------------------|
| `--module`               | `eval-runner`     |
| Test package root        | `bot.finance.eval` |
| Architecture-enforcement | none              |
| Coverage minimum         | none              |

## Module Tasks

- Compile: `tools/agent-test/agent-test.sh --module eval-runner --compile`
- One test class: `tools/agent-test/agent-test.sh --module eval-runner --tests "bot.finance.eval.<package>.<TestClassName>"`
- Full suite: `tools/agent-test/agent-test.sh --module eval-runner --all`
- Reformat to style (Spotless): `eval-runner/gradlew -p eval-runner spotlessApply`
- Run the runner by hand: `eval-runner/eval.sh`, as the [README](../../README.md) describes

## What This Module Lacks

- **No coverage guardrail.** The module applies no JaCoCo plugin and sets no `coverageMinimum`, unlike the rule in
  [Building a Java Module](../../../docs/conventions/java-build.md#test-coverage). Never run `--coverage` here.
- **No architecture test.**
- **No CI job.**

## Dependencies

Pinned on purpose: none.
