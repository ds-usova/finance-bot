# [Conventions](../conventions.md) > Testing Conventions

What holds for every module is [Testing](../../../docs/conventions/testing.md). Commands are in [Build](build.md).

## Test Types

| Part of the module                                                   | Test type | Against                                                        |
|----------------------------------------------------------------------|-----------|----------------------------------------------------------------|
| parsing, matching, judging, scoring, loading the corpus, reporting   | unit      | text blocks, in-memory values, `@TempDir` files                 |
| a class that calls a service over HTTP                               | unit      | a stub served by the JDK's `com.sun.net.httpserver.HttpServer` on a random port |
| the memory probe's Postgres read                                     | none      | exercised by an evaluation run only                             |
| the runner end to end                                                | none      | an `eval.sh` run against the started stack, by hand             |

There are no integration and no system tests. A plan for this module has unit steps only.

**Performance:** not measured.

## Tooling

- JUnit 5 and AssertJ. Nothing else: no mocking library, no containers.
- `CorpusTest` reads the repository's own corpus, so a case the runner cannot execute fails the build.

## Naming

- A test class is the class under test plus `Test`, in the same package, package-private.
- A test method is a sentence in lower camel case saying what holds: `readsOnlyTheCallsOfTheNamedMessage`.

## Style

- Fixtures are text blocks: log lines, YAML expectations, JSON.
- A test reaches a package-private static method of the class under test where one exists, such as a `parse`.
- Small private builders at the top of the class make the values each test needs.
- One `assertThat` chain per fact; `extracting` for a list's fields.
- A comment only where a case's point is not visible, as `findsTheLargestMatchingRatherThanTheFirst` has.
- A test is disabled with JUnit's `@Disabled` and a reason.

## Unstable Tests

None known.
