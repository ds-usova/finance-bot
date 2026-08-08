# Plan: Browse Recorded Expenses — the shared specification

**Affected Modules:** `ledger-service`, `web-app`
**Design:** [Browse Recorded Expenses](../design.md)

This plan lands the one artifact both modules read at build time, wires each build to it, and leaves both modules
compiling and green. It runs first and alone; neither module plan starts until every item here is ticked.

## Components

No class is created here, so there is no component diagram. What this plan creates is a file tree and two build
hookups.

The specification is layered under repo-root `openapi/`, exactly as the design's **Proposed Solution** lays it out.
Every file outside `ledger-api.yaml` is reached by a relative `$ref` and holds no `paths` of its own.

| File                                        | Declares                                                             |
|---------------------------------------------|----------------------------------------------------------------------|
| `openapi/ledger-api.yaml`                   | `info`, `servers`, the cookie security scheme, the four tags, `paths` |
| `openapi/paths/expenses.yaml`               | `GET /api/v1/expenses`, tag `expenses`                               |
| `openapi/paths/categories.yaml`             | `GET /api/v1/categories`, tag `categories`                           |
| `openapi/paths/groupings.yaml`              | `GET /api/v1/groupings`, tag `groupings`                             |
| `openapi/paths/session.yaml`                | `POST`, `GET`, `DELETE /api/v1/session`, tag `session`               |
| `openapi/components/schemas/expense.yaml`   | `Expense`, `ExpenseStatus`, `ExpensePage`                            |
| `openapi/components/schemas/category.yaml`  | `Category`, `Grouping`                                               |
| `openapi/components/schemas/session.yaml`   | `Session`, `TelegramLoginPayload`                                    |
| `openapi/components/schemas/problem.yaml`   | `Problem`                                                            |
| `openapi/components/parameters/paging.yaml` | `Limit`, `Offset`                                                    |
| `openapi/components/parameters/expense-filters.yaml` | `StatusFilter`, `CategoryIdFilter`, `FromFilter`, `ToFilter` |
| `openapi/components/responses/errors.yaml`  | `BadRequest`, `Unauthorized`, `ServiceUnavailable`                   |

What each build turns it into:

| Module           | Generator             | Output                                                | Committed |
|------------------|-----------------------|-------------------------------------------------------|-----------|
| `ledger-service` | `org.openapi.generator`, generator `spring` | `build/generated/sources/openapi/`, package `bot.finance.api` | no |
| `web-app`        | `openapi-typescript`  | `src/api/generated/`                                  | no        |

One generated Java interface per tag — `ExpensesApi`, `CategoriesApi`, `GroupingsApi`, `SessionApi` — is what
`ledger-service`'s controllers implement. One generated `.d.ts` is what `web-app`'s `api/` functions are declared
against.

## Step-by-Step Implementation Map (To-Do List)

### Stabilization

#### API Contract

- [x] ST01 · Create `openapi/components/schemas/problem.yaml` — `Problem` as `{ "message": string }`, `message`
  required, nothing else (D35). It is what every non-401 error response carries.
- [x] ST02 · Create `openapi/components/schemas/session.yaml`:
  - `Session` — `externalId` (string, required), and no other property; it is the whole of what the session
    endpoints answer today.
  - `TelegramLoginPayload` — `type: object`, `additionalProperties: true`, no required properties; the whole
    payload the Login Widget hands the page, unchanged
    ([the session contract](../../../ledger-service/docs/contracts/in/web-session-api.md)). The widget really
    sends numbers as well as strings — `LoginPage.test.tsx:52` fires the callback with `{ id: 42 }` — so a
    string-valued map would contradict a passing test.
- [x] ST03 · Create `openapi/components/schemas/expense.yaml`:
  - `ExpenseStatus` — a string enum of `PENDING` and `RECORDED`.
  - `Expense` — `id` (integer, int64), `status` (`$ref` `ExpenseStatus`), `categoryId` (integer, int64),
    `description` (string), `merchant` (`type: [string, "null"]`, the 3.1 spelling — `nullable` is not a keyword
    in 3.1), `amountMinorUnits` (integer, int64), `currency` (string), `createdAt` (string, `date-time`).
    Everything but `merchant` is required. No category name and no grouping (D7).
  - `ExpensePage` — `items` (array of `Expense`), `limit` (integer), `offset` (integer), `total` (integer,
    int64); all required.
- [x] ST04 · Create `openapi/components/schemas/category.yaml`:
  - `Category` — `id` (integer, int64), `name` (string), `groupingId` (integer, int64), `groupingName` (string);
    all required.
  - `Grouping` — `id` (integer, int64), `name` (string); both required.
- [x] ST05 · Create `openapi/components/parameters/paging.yaml` — both `in: query`, neither required:
  - `Limit` — integer, `minimum: 1`, `maximum: 100`, `default: 50` (D4).
  - `Offset` — integer, `minimum: 0`, `default: 0`.

  `maximum: 100` is one of the two places the page-size cap is written; the other is `ExpenseFilter.MAX_LIMIT` in
  `ledger-service`, and the two must agree.
- [x] ST06 · Create `openapi/components/parameters/expense-filters.yaml` — all `in: query`, none required:
  `StatusFilter` (`$ref` `ExpenseStatus`), `CategoryIdFilter` (integer, int64), `FromFilter` and `ToFilter`
  (string, `format: date`). Neither date parameter is required on its own; a half period is refused by the
  service, not by the schema (D24).
- [x] ST07 · Create `openapi/components/responses/errors.yaml`:
  - `BadRequest` — 400, `application/json`, `Problem`.
  - `Unauthorized` — 401, **no content** (D38): a filter writes it before any handler runs.
  - `ServiceUnavailable` — 503, `application/json`, `Problem`.
- [x] ST08 · Create `openapi/paths/expenses.yaml` — `get`, `operationId: listExpenses`, `tags: [expenses]`, the six
  parameters `$ref`d from ST05 and ST06, `200` returning `ExpensePage`, and the three error responses `$ref`d
  from ST07. Also `404` with a `Problem`, for the session that outlived its user row (D10).
- [x] ST09 · Create `openapi/paths/categories.yaml` — `get`, `operationId: listCategories`, `tags: [categories]`, an
  optional `groupingId` query parameter (integer, int64), `200` returning an array of `Category`, plus `400`,
  `401`, `404` and `503` — the `400` because a `groupingId` that is not a number answers one, which the ledger
  plan's `RI05` asserts. No paging (D20).
- [x] ST10 · Create `openapi/paths/groupings.yaml` — `get`, `operationId: listGroupings`, `tags: [groupings]`, no
  parameters, `200` returning an array of `Grouping`, plus `401`, `404` and `503`. No paging (D20).
- [x] ST11 · Create `openapi/paths/session.yaml`, `tags: [session]`, describing what the session endpoints already
  answer, at the versioned path (D8, D36):
  - `post` — `operationId: signIn`, body `TelegramLoginPayload`, `200` returning `Session`, `401` with a
    `Problem`, `403` with no content, `503` with a `Problem`.
  - `get` — `operationId: currentSession`, `200` returning `Session`, `401` with no content.
  - `delete` — `operationId: signOut`, `204` with no content, `403` with no content.

  The `403` is what a CSRF-refused write answers — `SessionControllerTest.whenTheRequestCarriesNoCsrfToken_thenTheSignInIsRefused`
  asserts it — and it carries no body, because the chain's `AccessDeniedHandler` writes it rather than
  `WebExceptionHandler`. The `401` beside it is what the same request answers against the running service when the
  caller holds no session (D37); both are real, so both are declared.
- [x] ST12 · Create `openapi/ledger-api.yaml` — OpenAPI 3.1, `info` naming the ledger's API and the repository
  version, `servers` holding the single relative `/` entry (one origin, [ADR 0014](../../adr/0014-the-web-app-and-the-ledger-are-served-from-one-origin.md)),
  an `apiKey`-in-cookie security scheme named for `fb_session`, the four tags, and `paths` reaching each file in
  ST08–ST11 by relative `$ref` under `/api/v1/expenses`, `/api/v1/categories`, `/api/v1/groupings` and
  `/api/v1/session`.

#### Interface-First / Build Stabilization

**Configuration**

- [x] ST13 · Wire the generator into `ledger-service`:
  - add `openApiGeneratorVersion` to `ledger-service/gradle.properties`, pinned to the current stable
    `org.openapi.generator` release;
  - add `id "org.openapi.generator" version "$openApiGeneratorVersion"` to `ledger-service/build.gradle`;
  - configure the generate task with input `"$rootDir/../openapi/ledger-api.yaml"`, generator `spring`,
    `interfaceOnly=true`, `useTags=true`, output `layout.buildDirectory.dir("generated/sources/openapi")` —
    `Project.buildDir` is gone in the Gradle 9.3.0 the wrapper pins, and no `.gradle` file here uses it — api
    package `bot.finance.api`, model package `bot.finance.api.model`;
  - add `<the generate task's output>/src/main/java` to the `main` source set, which is where the generator
    writes its Java under the supporting files it also emits, and make `compileJava` depend on the generate task
    (D31).

  `swagger-annotations` and `spring-boot-starter-validation` are already declared, which is what the generated
  interfaces need.
  - **As wired:** `openApiGeneratorVersion=7.24.0`. One dependency beyond the two above turned out to be
    required — `org.openapitools:jackson-databind-nullable`, pinned by `jacksonDatabindNullableVersion=0.2.11`.
    The generated model wraps `merchant` in `JsonNullable<String>` because the schema declares the 3.1 nullable
    union, and nothing on the existing classpath carries that type.
- [x] ST14 · Run the ledger's generation and **read the output before anything is written against it** (D17, D18):
  `tools/agent-test/agent-test.sh --module ledger-service --compile`, which is what holds the lock over the shared
  `build/classes` that compiling writes to. Confirm three things and record them in this item as a
  sub-bullet: that the relative `$ref`s resolved into one model set; that one interface per tag was emitted, named
  `ExpensesApi`, `CategoriesApi`, `GroupingsApi` and `SessionApi`; and whether the generator put `@Min`/`@Max` on
  the `limit` and `offset` request parameters, since D25 turns on it. A different interface name or a missing
  validation annotation is a fact the module plan is written against, not a defect.
  - **Recorded output** — `org.openapi.generator` 7.24.0, generator `spring`, wrote
    `ledger-service/build/generated/sources/openapi/src/main/java`. The module compiles against it
    (`--compile` → `COMPILES`).
    - **Interfaces:** one per tag, under `bot.finance.api`, named exactly as expected — `ExpensesApi`,
      `CategoriesApi`, `GroupingsApi`, `SessionApi`. Each is `@Validated`, and every method is a `default`
      returning `501 NOT IMPLEMENTED`, so a controller overrides rather than implements an abstract method.
    - **Models: the names are synthesized, not the schema names.** The relative `$ref`s all resolved, and
      identical schemas were deduplicated into one class each — but `ledger-api.yaml` declares no
      `components.schemas` map, so the generator had no name to hang each resolved schema on and derived one from
      the first operation and status code that reached it, in path-declaration order. What it emitted, under
      `bot.finance.api.model`:

      | Schema                 | Generated class                      |
      |------------------------|--------------------------------------|
      | `ExpensePage`          | `ListExpenses200Response`            |
      | `Expense`              | `ListExpenses200ResponseItemsInner`  |
      | `Problem`              | `ListExpenses400Response`            |
      | `Category`             | `ListCategories200ResponseInner`     |
      | `Grouping`             | `ListGroupings200ResponseInner`      |
      | `Session`              | `CurrentSession200Response`          |
      | `TelegramLoginPayload` | none — see below                     |

      `ListExpenses400Response` is the one `Problem` class every 400, 404 and 503 body across all four interfaces
      uses; `CurrentSession200Response` is the one `Session` class both session reads and `signIn` use. These
      names are positional: reordering `paths` in `ledger-api.yaml`, or adding an operation ahead of an existing
      one, renames the classes.
    - **`TelegramLoginPayload` produced no class at all** — it declares `additionalProperties: true` and no
      properties, so `signIn`'s body is typed `Map<String, Object> requestBody` directly on the interface.
    - **`@Min` / `@Max`:** present, so D25 holds. `listExpenses` carries
      `@Min(value = 1) @Max(value = 100) ... Integer limit` and `@Min(value = 0) ... Integer offset`, both
      `@Valid @RequestParam(required = false)` with the schema defaults `"50"` and `"0"`.
    - **Field types:** `merchant` is `JsonNullable<String>` (hence the extra dependency in ST13), and `createdAt`
      is `OffsetDateTime`, not `Instant`.
- [x] ST15 · Keep the generated package out of the core and out of the coverage figure:
  - add `"bot.finance.api.."` to the banned-package list in
    `bot.finance.architecture.CleanArchitectureTest.domainAndApplicationStayFrameworkAgnostic`, beside
    `bot.finance.ai..`;
  - add `bot/finance/api/**` to the `coveredClasses` exclude list in `ledger-service/build.gradle`, beside the
    generated proto types.
- [x] ST16 · Add `COPY openapi ./openapi` to [`ledger-service/Dockerfile`](../../../ledger-service/Dockerfile),
  beside `COPY proto ./proto` and before the `WORKDIR /repo/ledger-service` line. Without it
  `$rootDir/../openapi/ledger-api.yaml` does not exist in the build stage and `bootJar` fails (D30).
- [x] ST17 · Wire the generator into `web-app`:
  - `npm install --save-dev openapi-typescript` from `web-app/`, so the manifest and the lockfile cannot disagree;
  - add a `generate:api` script running `openapi-typescript ../openapi/ledger-api.yaml -o src/api/generated/ledger-api.d.ts`;
  - make `build`, `test:run`, `verify` and `verify:coverage` depend on it, so no run reaches a tree without the
    generated types — `verify:coverage` included, since it is the module's documented Coverage task and the one
    run `agent-test.sh --module web-app --coverage` drives.
- [x] ST18 · Run `npm run generate:api` from `web-app/` and **read the output before anything is written against
  it** (D18). Record in this item, as a sub-bullet, the exported type the response schemas land under — the shape
  `components['schemas']['ExpensePage']` resolves to is what `web-app`'s `api/` functions are declared against.
  - **Recorded output** — `openapi-typescript` 7.13.0 wrote `web-app/src/api/generated/ledger-api.d.ts` with no
    warnings. Its top-level exports are `paths`, `webhooks`, `components`, `$defs` and `operations`. The layered
    relative `$ref`s resolved into **one flat set** — `components['schemas']`, `components['parameters']` and
    `components['responses']` each mirror their directory 1:1, and nothing was inlined per path.
    - Every schema landed under the name the specification used: `Expense`, `ExpensePage`, `ExpenseStatus`,
      `Category`, `Grouping`, `Session`, `TelegramLoginPayload`, `Problem`.
    - `components['schemas']['ExpensePage']` is
      `{ items: components["schemas"]["Expense"][]; limit: number; offset: number; total: number }`, and
      `Expense` beside it is `{ id: number; status: components["schemas"]["ExpenseStatus"]; categoryId: number;
      description: string; merchant?: string | null; amountMinorUnits: number; currency: string;
      createdAt: string }`. Every `format` is a comment, so both `int64` and `date-time` are plain `number` and
      `string`.
    - `ExpenseStatus` is the union `"PENDING" | "RECORDED"`; `TelegramLoginPayload` is
      `{ [key: string]: unknown }`; `Problem` is `{ message: string }`.
    - The named parameters (`Limit`, `Offset`, `StatusFilter`, `CategoryIdFilter`, `FromFilter`, `ToFilter`) also
      landed as `components['parameters']` entries and are referenced from `operations['listExpenses']`.
      `listCategories`' `groupingId` stayed inline, as the specification declares it inline.
    - A response declaring no content (`401` on `GET`, `403` and `204` on the session writes) came through as
      `content?: never`, not as an empty object.
- [x] ST19 · Exempt the generated TypeScript from every gate that would otherwise fail on output nobody wrote
  (D44):
  - `/web-app/src/api/generated/` in the repo-root [`.gitignore`](../../../.gitignore), beside the other `web-app`
    entries;
  - `src/api/generated` in the `ignores` list in [`web-app/eslint.config.js`](../../../web-app/eslint.config.js);
  - `src/api/generated` in [`web-app/.prettierignore`](../../../web-app/.prettierignore);
  - `src/api/generated/**` in `test.coverage.exclude` in [`web-app/vite.config.ts`](../../../web-app/vite.config.ts).

  `format:check` runs before the build that generates the types, so without the Prettier entry a clean checkout
  passes and every run after it fails.
- [x] ST20 · Add `COPY openapi /repo/openapi` to [`web-app/Dockerfile`](../../../web-app/Dockerfile), before
  `RUN npm run build` and after the `COPY web-app/ ./` line, so the build stage can reach the specification the
  `build` script now generates from (D30). The image is built from the repository root already.
- [x] ST21 · Correct the three documents whose statements the wiring above has just made false:
  - [Architecture & Layering](../../../ledger-service/docs/conventions/architecture.md#file-locations) — the API
    schema is `openapi/ledger-api.yaml` at the repository root, layered under `openapi/paths/` and
    `openapi/components/`, generated into `build/generated/sources/openapi/` and never committed; and
    `bot.finance.api..` joins the banned-package list under **Architecture Enforcement**;
  - [`ledger-service` Build](../../../ledger-service/docs/conventions/build.md) — "Contract codegen: none is
    wired" becomes the generate task and its input;
  - [`web-app` Build](../../../web-app/docs/conventions/build.md) — the same row in **Module Tasks** becomes
    `npm run generate:api` and its input.
- [x] ST22 · Confirm both modules are back to green before either module plan starts:
  `tools/agent-test/agent-test.sh --module ledger-service --all` — the suite passes, `CleanArchitectureTest`
  included — and `tools/agent-test/agent-test.sh --module web-app --all`, plus `npm run verify` from `web-app/`,
  which is the gate that runs `format:check` against the newly generated tree.

## Open Questions / Blockers

- **Q1:** ST14 and ST18 run a generator that has never run in this tree (D17, D18). If either resolver collapses
  the layered `$ref`s or emits interfaces under names the module plans do not expect, the module plans are written
  against the names actually emitted — recorded in ST14 and ST18 — rather than the ones above. Nothing else in
  this plan changes. Confirm that is the intended handling?
  - A: The recorded output wins. The module plans are written against the names ST14 and ST18 actually recorded —
    `ExpensesApi` and the rest are the expectation, not a requirement. Nothing else in any plan changes, and no
    step is blocked.
  - **What actually happened**, applying that answer: `web-app` matched the expectation exactly, and
    `ledger-service` matched it for the four interface names but **not for the model classes**. The Java
    generator synthesized every model name from an operation and a status code — `Expense` is
    `ListExpenses200ResponseItemsInner`, `ExpensePage` is `ListExpenses200Response`, and so on; the full table is
    in ST14. Those synthesized names are what `ledger-service/plan.md` is written against. Two consequences a
    reader of that plan needs:
    - the names are positional, so reordering `paths` in `ledger-api.yaml` renames the classes;
    - declaring a `components.schemas` map in `ledger-api.yaml` would give the generator the schema names back.
      That was not done, because it would be forcing the output to match the expectation, which the answer above
      rules out. It stays available if the synthesized names later prove not worth living with.

## Review Findings

- **F1:** ST13 used `"$buildDir/…"`, which no longer evaluates on the Gradle 9.3.0 the wrapper pins.
  - Resolution: mechanical
  - Action: applied — ST13 names `layout.buildDirectory.dir("generated/sources/openapi")`.

- **F2:** ST13 registered the generator's output directory as a source directory, but the generator writes its
  Java under `<outputDir>/src/main/java`, so nothing would have compiled.
  - Resolution: mechanical
  - Action: applied — ST13 names that subdirectory as the source directory.

- **F3:** ST02 declared `TelegramLoginPayload` as a string-valued map, contradicting the design's
  `additionalProperties: true` and the numeric `id` the widget really sends.
  - Resolution: decision
  - Action: resolved — the design's **Details** already fixes `additionalProperties: true`, and
    `LoginPage.test.tsx:52` fires the callback with `{ id: 42 }`, so a string-valued map contradicts a passing
    test. ST02 now declares `additionalProperties: true`; `ledger-service/plan.md`'s ST13 renders the generated
    `Map<String, Object>` to a `Map<String, String>` before the verifier, so `TelegramLoginVerifier`'s signature
    is unchanged and D22 still holds; `web-app`'s `TelegramAuthPayload` is assignable to the generated type and
    needs no change.

- **F4:** ST03 wrote `merchant` as "nullable", which is not a keyword in the OpenAPI 3.1 ST12 fixes.
  - Resolution: mechanical
  - Action: applied — ST03 names the 3.1 form `type: [string, "null"]`.

- **F5:** ST09 declared no 400 for `GET /api/v1/categories`, which a non-numeric `groupingId` answers and which
  the ledger plan's `RI05` asserts.
  - Resolution: mechanical
  - Action: applied — the `BadRequest` response joins ST09.

- **F6:** ST11 declared no 403 on the two session writes, which a CSRF-refused one answers today.
  - Resolution: decision
  - Action: resolved — `SessionControllerTest.whenTheRequestCarriesNoCsrfToken_thenTheSignInIsRefused` asserts
    403 and `WebSessionSystemTest.whenAGenuinePayloadIsPostedWithoutACsrfToken_thenItIsRefusedAndNoUserIsStored`
    asserts 401 for the same request against the running service, which D37 already records as the same
    asymmetry: both are real, so ST11 declares both. The 403 carries no content, because the chain's
    `AccessDeniedHandler` writes it rather than `WebExceptionHandler`.

- **F7:** ST14 called `gradlew compileJava` raw, bypassing the runner lock over the shared `build/classes`.
  - Resolution: mechanical
  - Action: applied — ST14 goes through `agent-test.sh --module ledger-service --compile`.

- **F8:** ST17 left `verify:coverage` off the scripts depending on `generate:api`, though it is the module's
  documented Coverage task.
  - Resolution: mechanical
  - Action: applied — ST17 adds the dependency to it.
