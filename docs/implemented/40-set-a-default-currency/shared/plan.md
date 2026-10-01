# Plan: Set a default currency — the shared schema

**Affected Modules:** `ledger-service`, `web-app`
**Design:** [Set a default currency](../design.md)

`openapi/ledger-api.yaml` is the one artifact both builds read: `ledger-service` generates its endpoint interface
and wire types from it, `web-app` generates its response types. This plan writes it and regenerates both sides, so
neither module's plan waits on the other.

## Components

The artifact is a schema, not a class, so this plan draws no component diagram: nothing it creates has a
collaborator. What each module's generator makes of it is the table below, and the classes that consume those
types are drawn in the module plans.

| Schema              | Fields                                    | Bound                                                                          |
|---------------------|-------------------------------------------|--------------------------------------------------------------------------------|
| `Preferences`       | `defaultCurrency`: `[string, "null"]`     | an ISO 4217 code, upper-cased. `null` only where nothing was chosen            |
| `PreferencesUpdate` | `defaultCurrency`: `string`, required     | `^[A-Za-z]{3}$`, and a code an amount can be recorded in. Never null           |

| Operation                 | `operationId`        | Generated in `ledger-service`                                                 | Generated in `web-app`                       |
|---------------------------|----------------------|---------------------------------------------------------------------------------|-----------------------------------------------|
| `GET /api/v1/preferences` | `readPreferences`    | `PreferencesApi`, `Preferences`, `ReadPreferences200Response`                    | `components['schemas']['Preferences']`        |
| `PUT /api/v1/preferences` | `replacePreferences` | `PreferencesUpdate`, `ReplacePreferencesRequest`, `ReadPreferences200Response` again | `components['schemas']['PreferencesUpdate']` |

Each schema is generated under its own name **and** an operation-derived wrapper is generated for each top-level
use of it — `ExpensePage` stands beside `ListExpenses200Response` for exactly that reason. The wrapper is one class
per distinct response shape, named after the first operation that reaches it: `GET` and `PUT` both answer
`Preferences`, and the parser walks a path's methods in method-enum order, so `ReadPreferences200Response` is the
only wrapper generated and the `PUT` answers it too — the same way `CurrentSession200Response` serves `signIn`.

`bot.finance.api.model.Preferences` therefore collides by simple name with the
`bot.finance.application.dto.Preferences` the ledger plan creates. One of the two is fully qualified where they
meet, which is the web mapper.

## Step-by-Step Implementation Map (To-Do List)

### Stabilization

#### API Contract

- [x] ST01 · Add the `preferences` tag to `openapi/ledger-api.yaml`'s `tags` list, after `session`, and add
  `Preferences` and `PreferencesUpdate` to `components/schemas`. The request schema's pattern is `^[A-Za-z]{3}$`
  and never `^[A-Z]{3}$`: the generator turns the pattern into a `@Pattern`, which would refuse `eur` before the
  controller can normalize it. Both schemas carry a `description` and an `example`, as every schema in the file
  does, and both list `defaultCurrency` under `required` — `Preferences` too, so the field is present-and-nullable
  rather than optional, as `Expense` lists its nullable `merchant`.
- [x] ST02 · Add `openapi/paths/preferences.yaml` with `get` (`operationId: readPreferences`, 200 `Preferences`,
  401, 404, 503) and `put` (`operationId: replacePreferences`, request body `PreferencesUpdate` required, 200
  `Preferences`, 400, 401, 403, 404, 503), reusing `../components/responses/errors.yaml` for `BadRequest`,
  `Unauthorized`, `NotFound` and `ServiceUnavailable` as `expense-acceptances.yaml` does.
- [x] ST03 · Add `/api/v1/preferences: $ref: './paths/preferences.yaml'` to `openapi/ledger-api.yaml`'s `paths`
  map **at the end**, after `/api/v1/expenses/{status}/{id}`. The generator derives five existing response type
  names from the operation order, so inserting the entry anywhere earlier renames `ListExpenses200Response` and its
  kind and breaks `ledger-service`'s build.

#### Interface-First / Build Stabilization

**Interface & Signature Sync**

- [x] ST04 · Regenerate and compile `ledger-service` through the queueing wrapper:
  `tools/agent-test/agent-test.sh --module ledger-service --compile`, which reaches `openApiGenerate` through
  `compileJava`'s dependency and takes the lock that `build/classes` needs. Confirm
  `bot.finance.api.PreferencesApi`, `bot.finance.api.model.Preferences`, `PreferencesUpdate`,
  `ReadPreferences200Response` and `ReplacePreferencesRequest` are generated, and that no existing generated type
  was renamed.
- [x] ST05 · Regenerate `web-app`'s types: `npm --prefix web-app run generate:api`, which rewrites
  `web-app/src/api/generated/ledger-api.d.ts`. Confirm `components['schemas']['Preferences']` and
  `components['schemas']['PreferencesUpdate']` are present, then `npm --prefix web-app run typecheck`.

#### Closing item

- [x] ST06 · Confirm both modules stand where the baseline left them:
  `tools/agent-test/agent-test.sh --module ledger-service` (its `CleanArchitectureTest` included) and
  `tools/agent-test/agent-test.sh --module web-app`. No test is disabled by this plan, so the totals and the
  skipped counts are the baseline's.

## Open Questions / Blockers

None. The schema is additive, and neither module has a call site the change breaks.

## Review Findings

- **F1:** The Components table and ST04 expected a `ReplacePreferences200Response`; the generator emits one
  wrapper per distinct response shape, so the `PUT` answers `ReadPreferences200Response`.
  - Resolution: mechanical
  - Action: applied — corrected the table and ST04's confirmation list.

- **F2:** The note claimed a top-level schema is generated *only* under an operation-derived name; both are
  generated, so `bot.finance.api.model.Preferences` collides with the ledger's application dto of that name.
  - Resolution: mechanical
  - Action: applied — rewrote the note, added both schema names to the `ledger-service` column, and said where
    the clash is resolved.

- **F3:** ST02 gave the read no `503`, though the design's status table and A19 both require one.
  - Resolution: mechanical
  - Action: applied — added `503` to the `get` responses.

- **F4:** Neither operation declared a `404`, though `EntityNotFoundException` reaches them through the shared
  advice and every other browse-API path declares `NotFound`.
  - Resolution: decision
  - Action: resolved — the repository settles it: `WebExceptionHandler.onEntityNotFound` answers 404 for a
    session naming a user the store no longer holds, both use cases resolve the caller through
    `UserRepository.requireById`, and `expenses.yaml`, `expense-acceptances.yaml` and `expense-category.yaml`
    all declare `NotFound`. Added `404` to both operations, and recorded the omission as `F39` in the design's
    Design Findings.

- **F5:** ST01 did not say `Preferences` lists `defaultCurrency` under `required`, which A2's `null` answer needs.
  - Resolution: mechanical
  - Action: applied — ST01 now requires it on both schemas, citing `Expense.merchant` as the precedent.

- **F6:** ST04 ran `gradlew compileJava` directly, bypassing the wrapper's lock on the shared `build/classes`.
  - Resolution: mechanical
  - Action: applied — ST04 now runs `tools/agent-test/agent-test.sh --module ledger-service --compile`.

- **F7:** Q1 was not a question but carried an empty `- A:`, which would block the readiness gate.
  - Resolution: mechanical
  - Action: applied — replaced it with a plain statement that nothing is open.
