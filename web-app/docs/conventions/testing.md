# [Conventions](../conventions.md) > Testing Conventions

On top of the repository-wide [Testing](../../../docs/conventions/testing.md).

## Test Layers

| Layer     | Test type | Covers                                               | Boundary                               |
|-----------|-----------|------------------------------------------------------|----------------------------------------|
| Unit      | unit      | A module's own logic — the API client, a hook        | `fetch` stubbed with `vi.stubGlobal`   |
| Component | unit      | What a component renders and what it calls back with | Its own props, or a hand-built context |
| Page      | unit      | A route's composition, including its redirects       | `api/` mocked with `vi.mock`           |

**All three are unit tests**, because each one fakes everything the code under test depends on. This module has
no test that runs against a real dependency and none that runs the whole system.

The whole stack is covered by `ledger-service`'s `WebSessionSystemTest`, and the Login Widget itself cannot be
driven from a test at all — see [Agent Configuration](agent.md).

## What the Suite Cannot See

jsdom parses markup and runs script; it lays nothing out. Every element measures zero, nothing overflows, no
colour resolves, no animation runs, and a third-party iframe renders nothing at all. So a whole class of defect
is invisible here whatever the suite's size:

| Outside the suite                          | Where it shows instead |
|--------------------------------------------|------------------------|
| Position, width, wrapping, what is cut off | the running app        |
| Colour, contrast, both themes              | the running app        |
| Scrolling, and a list clipped by a bound it never got | the running app |
| Motion, and what reduced motion gives      | the running app        |

**A test that asserts a class name is not a substitute.** It pins the mechanism rather than the outcome, which
[Testing Style](#testing-style) rules out, and it passes just as happily when the rule it names does nothing.

**A change touching any row above is looked at before it is called done** — the app is started and handed over,
with the screens and the states to check named. What to look at comes from the design: `grill-frontend` records
it as a decision, so the list is agreed before the code exists rather than recalled after it.

## Time Zones in a Test

The runner is fixed to UTC, so an assertion about a reader in another zone proves nothing by default. Pin the
zone with `vi.stubEnv('TZ', 'Pacific/Kiritimati')` and release it with `vi.unstubAllEnvs()`; `process.env.TZ`
directly does not type-check, `@types/node` not being a dependency.

## Test Tooling

Vitest with jsdom, Testing Library, and `@testing-library/user-event` for interaction. `@testing-library/jest-dom`
matchers are registered once in `vitest.setup.ts`, which also cleans up between tests.

## Naming Conventions

- A test file sits beside its subject: `client.ts` / `client.test.ts`.
- `describe` names the subject as a noun phrase — *the API client*, *the sign-in page*.
- `it` names the behaviour as a sentence completing "it …", stating the outcome, not the mechanism.

## Testing Style

- **Query by role and accessible name**, never by test id or class. A control a test cannot find by its
  accessible name is a control a screen reader cannot find either.
- **Stub at the module boundary.** `vi.mock('../api/session')` for a page, `vi.stubGlobal('fetch', …)` for the
  client. Nothing reaches past that.
- **Assert the invariant, not the mechanism.** That a write carries the CSRF header, not that a particular
  header-building helper was called.
- **Build the expected value by hand, never from the expression under test.** A case that formats its
  expectation with the same `Intl` call it is checking asserts only that the component called `Intl` the way the
  test did, and passes just as happily when the output is wrong.
- **A test owed a rework is skipped with `it.skip`, never commented out.**
- **A rule that holds for every file is asserted once**, in `src/conventions.test.ts`, rather than repeated per
  file. It reads the sources through `import.meta.glob` — `@types/node` is not a dependency, so `node:fs` does
  not type-check — and names every offending line, so a failure says what to rewrite.
