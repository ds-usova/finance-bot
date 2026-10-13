# [Conventions](../conventions.md) > Code Style

What holds for every module is [Code Style](../../../docs/conventions/code-style.md).

## Production Code

- **Values are records** — `McpCall`, `Proposal`, `Category`.
- **A class with only static members is `final`** with a private constructor — `Wiring`.
- **Collaborators arrive through the constructor**, built in `Wiring`. A shared `JsonMapper` is a
  `private static final` field.
- **One failure type**, `EvalException`, carrying the stage it stopped in and the HTTP status where one was
  answered. An `IOException` is wrapped in it with the path or the request named. An `InterruptedException`
  restores the interrupt flag first.
- **A class's Javadoc is one sentence** saying what it does; a method's only where its contract is not visible.
- **Timeouts are named constants** in the class that uses them.
- **Long text — a pattern, a fixture, a query — is a text block** where it spans lines.

## Refactoring

- First: duplication between steps written apart, and the same parsing done twice.
- Extracted code goes to the package of the concern it belongs to, never to a shared `util`.
- Leave alone: the corpus format, the run file's shape (the viewer reads it), and the log line patterns the
  services write.
