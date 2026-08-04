# [Conventions](../conventions.md) > Build

The exact commands that compile, test, and check this module. Every command is copy-pasteable and states where it
runs from.

- Run commands from: `<the repository root, or the module root — say which; every command below assumes it>`

## Commands

- Compile / type-check: `<e.g. ./gradlew :widget-service:compileJava compileTestJava, npx tsc --noEmit>`
- Run a single test class: `<e.g. ./gradlew :widget-service:test --tests "com.example.widget.WidgetTest">`
- Run the full test suite: `<e.g. ./gradlew :widget-service:test>`
- Run the architecture-enforcement check: `<e.g. ./gradlew :widget-service:test --tests "com.example.architecture.*">`
- Format: `<e.g. ./gradlew :widget-service:spotlessApply; or "none — no formatter configured">`
- Contract codegen: `<e.g. ./gradlew :widget-service:openApiGenerate; or "n/a">`

## Reading a Run

`<what the output means and where the detail lives — the verdict to trust, the report or log file to open on a
failure, and anything that must not be done, such as reading a raw build log instead of the summary. If runs go
through a wrapper script, name it and link its documentation.>`

## Module Facts

`<what a command needs to name this module — its project path or package root, its architecture-test class, and
any task only this module has.>`

## Docker / Local Runtime

`<what has to be running for the tests or the app to work locally, and the command that starts it; or "nothing">`
