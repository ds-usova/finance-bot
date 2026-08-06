# [Conventions](../conventions.md) > Testing Conventions

## Test Layers

| Layer     | Covers                                               | Boundary                               |
|-----------|------------------------------------------------------|----------------------------------------|
| Unit      | A module's own logic — the API client, a hook        | `fetch` stubbed with `vi.stubGlobal`   |
| Component | What a component renders and what it calls back with | Its own props, or a hand-built context |
| Page      | A route's composition, including its redirects       | `api/` mocked with `vi.mock`           |

There is no end-to-end layer here. The whole stack is covered by `ledger-service`'s `WebSessionSystemTest`, and
the Login Widget itself cannot be driven from a test at all — see [Agent Configuration](agent.md).

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
- **A test owed a rework is skipped with `it.skip`, never commented out.**
