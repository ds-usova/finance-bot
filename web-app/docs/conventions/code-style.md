# [Conventions](../conventions.md) > Code Style

## Production-Code Style

- **Components are functions.** Class components get no new React features, and split one concern across
  lifecycle methods that run at different times — a subscription's setup and teardown belong in one `useEffect`,
  not in two methods a screen apart. Classes are still right for types that are genuinely classes: a typed error
  such as `ApiError`, and an error boundary, which React has no hook for.
- **Named exports.** A module exports what it is named for; there are no default exports.
- **`type` over `interface`** for props and data shapes, so every declaration in the module reads the same way.
- **No `any`.** An unavoidably untyped value is `unknown` and narrowed at the point of use. The lint rule
  enforces this.
- **A union of string literals models a state**, never a pair of booleans. `status: 'loading' | 'authenticated'
  | 'anonymous'` cannot represent "loading and authenticated"; two booleans can.
- **An error carries its status.** A failed call throws `ApiError`, so a caller can act on a 401 differently
  from a 503.
- **Comments: the fewer the better.** Write one only for what the code cannot show — third-party behaviour a
  reader would otherwise look up, or a constraint forcing a workaround. Never restate a name or a signature.
  **Never cite a plan step, a design decision or an acceptance scenario by number** — `D34`, `RU03`, `A26`,
  `Q1`. Those live in an archived task directory a reader of this file has no reason to open, and they name
  nothing once the plan is finished.
  Write the reason itself, or leave it out. `src/conventions.test.ts` fails the run on one, so it is caught
  before review rather than in it.

## Refactoring Conventions

- **Priorities**: (1) deduplicate logic written independently in two places; (2) align idioms with this file;
  (3) collapse scaffolding left over from getting tests to pass.
- **Extraction targets**: shared rendering goes to `components/`; shared calls go to `api/`. A helper used by
  one page stays in that page's file.
- **Leave alone**: generated files, and anything under `dist/`.
- **Thresholds**: extract only when logic repeats in two or more files.
