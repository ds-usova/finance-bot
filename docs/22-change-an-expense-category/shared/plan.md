# Plan: Change an Entry's Category — the shared specification

**Affected Modules:** `ledger-service`, `web-app`
**Design:** [Change an Entry's Category](../design.md)

The category endpoint is described in `openapi/`, which both builds read: `ledger-service` generates the endpoint
interface it implements, `web-app` generates the request and response types it calls with. This plan lands the
specification and regenerates both modules from it. It is implemented first and alone; neither module plan starts
until every item here is ticked.

## Components

No class is designed here. The artifact is one path file and two schemas, plus the generated sources each
module's build derives from them.

| Artifact                              | Holds                                                                           |
|---------------------------------------|---------------------------------------------------------------------------------|
| `openapi/paths/expense-category.yaml` | `PATCH /api/v1/expenses/{status}/{id}`, operation `changeExpenseCategory`       |
| `CategoryPatchOperation`              | `op` — `replace` only; `path` — `/categoryId` only; `value` — int64, at least 1 |
| `CategoryPatch`                       | an array of exactly one `CategoryPatchOperation`                                |

| Where the generated sources land                  | Committed |
|---------------------------------------------------|-----------|
| `ledger-service/build/generated/sources/openapi/` | no        |
| `web-app/src/api/generated/ledger-api.d.ts`       | no        |

## Step-by-Step Implementation Map (To-Do List)

### Stabilization

#### API Contract

- [x] ST01 · Add `openapi/paths/expense-category.yaml`. The `400` and `404` descriptions are written here rather
  than reused from `openapi/components/responses/errors.yaml`, because this endpoint refuses for reasons those
  shared descriptions rule out (D25); `403` is inline and bodiless as
  `openapi/paths/expense-acceptances.yaml` already writes it. The media type is `application/json-patch+json`,
  the first on this API that is not `application/json` (D20):
  ```yaml
  patch:
    operationId: changeExpenseCategory
    summary: Refile one of the signed-in person's entries under a different category.
    description: >-
      The entry is named by the `status` the listing answered and by the id it answered within that status. The
      document replaces `/categoryId` and nothing else, and the answer is the entry as it now stands. Both a
      pending proposal and a recorded expense can be refiled.
    tags:
      - expenses
    parameters:
      - name: status
        in: path
        required: true
        description: >-
          Which of the two tables the id names. An id is unique within its status and not across it, so the token
          is what says which entry is meant.
        schema:
          $ref: '../ledger-api.yaml#/components/schemas/ExpenseStatus'
        example: RECORDED
      - name: id
        in: path
        required: true
        description: The id of the caller's entry, as the listing answered it under this `status`.
        schema:
          type: integer
          format: int64
          minimum: 1
        example: 12
    requestBody:
      required: true
      content:
        application/json-patch+json:
          schema:
            $ref: '../ledger-api.yaml#/components/schemas/CategoryPatch'
    responses:
      '200':
        description: >-
          The entry was refiled, and is answered as it now stands. Answered for a category the entry already
          carried too; the day it appeared on is unchanged.
        content:
          application/json:
            schema:
              $ref: '../ledger-api.yaml#/components/schemas/Expense'
      '400':
        description: >-
          The document is not one `replace` of `/categoryId`, its value is below 1, the body is not JSON,
          `status` is neither token, or `categoryId` names no category of the caller's. The message names what
          was refused. Unlike every other refusal on this API, a row is read before this one: an unknown
          category, a grouping and another person's category are one answer.
        content:
          application/json:
            schema:
              $ref: '../ledger-api.yaml#/components/schemas/Problem'
            example:
              message: categoryId must name one of your own categories
      '401':
        $ref: '../components/responses/errors.yaml#/Unauthorized'
      '403':
        description: The request carried no CSRF token.
      '404':
        description: >-
          No entry of the caller's carries that id under that status, or the session outlives the user row it
          names. The two carry different messages: one names the entry, the other says the caller is unknown.
        content:
          application/json:
            schema:
              $ref: '../ledger-api.yaml#/components/schemas/Problem'
            example:
              message: no entry of yours carries that id
      '503':
        $ref: '../components/responses/errors.yaml#/ServiceUnavailable'
  ```
- [x] ST02 · Add `CategoryPatchOperation` and `CategoryPatch` to `openapi/ledger-api.yaml` under
  `components/schemas`, and the path entry **after** the five already listed under `paths:` —
  [Build](../../../ledger-service/docs/conventions/build.md) records that reordering the paths renames the
  operation-derived response types and breaks the Java build. `op` and `path` are single-value enums so that
  anything else is refused by the generated model, before the use case is reached (D3):
  ```yaml
  CategoryPatchOperation:
    type: object
    description: The one operation this endpoint accepts, in JSON Patch's own shape.
    properties:
      op:
        type: string
        description: Only `replace`. No other operation is accepted, `test` included.
        enum:
          - replace
      path:
        type: string
        description: Only `/categoryId`. Nothing else on an entry is editable from any surface.
        enum:
          - /categoryId
      value:
        type: integer
        format: int64
        minimum: 1
        description: The id of a category of the caller's, filed under a grouping.
    required:
      - op
      - path
      - value
    example:
      op: replace
      path: /categoryId
      value: 42
  CategoryPatch:
    type: array
    description: A JSON Patch document of exactly one operation.
    minItems: 1
    maxItems: 1
    items:
      $ref: '#/components/schemas/CategoryPatchOperation'
    example:
      - op: replace
        path: /categoryId
        value: 42
  ```
  ```yaml
  /api/v1/expenses/{status}/{id}:
    $ref: './paths/expense-category.yaml'
  ```

#### Interface-First / Build Stabilization

**Interface & Signature Sync**

- [x] ST03 · Run `web-app`'s codegen (`npm --prefix web-app run generate:api`) and confirm
  `components['schemas']['CategoryPatch']` and `components['schemas']['CategoryPatchOperation']` are both present
  in `web-app/src/api/generated/ledger-api.d.ts`, that the new path appears under `paths`, and that no existing
  schema's shape changed. Nothing is committed: `.gitignore` ignores that directory, and `package.json`
  regenerates it before every script that reads it.
- [x] ST04 · Run `ledger-service`'s codegen (`ledger-service/gradlew -p ledger-service openApiGenerate`) and
  record, in this item, the names the new operation actually produced — the method added to
  `bot.finance.api.ExpensesApi`, the Java type of each path parameter, the request-body parameter's type, and the
  200 response type. Three things are unobserved and are read back rather than guessed:
    - whether an **array** request body binds as `List<CategoryPatchOperation>` or as a wrapper type, this being
      the first array body in the tree;
    - whether the `minItems`/`maxItems` bound on that array reaches the generated signature as a `@Size`
      constraint, or is dropped;
    - whether a `200` whose schema is `$ref`ed to the named `Expense` component answers `Expense` or a
      `ChangeExpenseCategory200Response` derived from the operation, which
      [Build](../../../ledger-service/docs/conventions/build.md) records happens to an operation's top-level
      response schema;
    - whether a `$ref`ed `ExpenseStatus` **path** parameter reaches the signature as a generated enum or as a
      plain `String`. Expect `String`: the `status` **query** parameter `$ref`s the same schema, and the checked-in
      output flattens it to `@Valid @RequestParam(value = "status", required = false) @Nullable String status`.
      Record which it was, because a `String` means nothing in the framework refuses `ACCEPTED`, and the mapper is
      then what raises the 400 of A10 rather than `MethodArgumentTypeMismatchException`.

  Both module plans are written against whatever this step reads back. Where the array bound is dropped,
  `ledger-service/plan.md`'s `ExpenseWebMapper` step is the only thing that refuses a document of two operations,
  which it does either way.

  **Read back.** The generator added to `bot.finance.api.ExpensesApi`:

  ```java
  default ResponseEntity<ChangeExpenseCategory200Response> changeExpenseCategory(
      @PathVariable("status") String status,
      @Min(value = 1L) @PathVariable("id") Long id,
      @Size(min = 1, max = 1) @Valid @RequestBody List<@Valid CategoryPatchOperation> categoryPatchOperation)
  ```

    - The array body binds as `List<CategoryPatchOperation>`, not a wrapper type.
    - The `minItems`/`maxItems` bound reaches the signature, as `@Size(min = 1, max = 1)`. It is not dropped, so
      the framework refuses a document of two operations before the mapper is reached — `ExpenseWebMapper` still
      refuses one as well.
    - The `200` answers `ChangeExpenseCategory200Response`, derived from the operation, not the named `Expense`
      component — as [Build](../../../ledger-service/docs/conventions/build.md) records for a top-level response
      schema.
    - The `status` path parameter is a plain `String`, as expected. Nothing in the framework refuses `ACCEPTED`,
      so the mapper is what raises the 400 of A10.
- [x] ST05 · Drive the endpoint with `application/json-patch+json` before anything is written against it (D20).
  The generated interface declares every operation as a `default` method answering 501, so no call site breaks and
  nothing needs stubbing. Drive it through a throwaway `@WebMvcTest(ExpensesController.class)` under
  `build/scratch/` that does **not** carry `@WebAdapterTest`: that composed annotation imports
  `SecurityConfiguration`, whose web-session chain ends in `anyRequest().denyAll()` and names no matcher for this
  path, so a request through it is refused at the authorization filter and never reaches the generated method.
  The matcher that admits the path is `ledger-service/plan.md` ST08, which cannot run before this plan is
  finished.

  Record here what two things answered — a document of one declared operation reaching a typed parameter, and a
  document breaking a declared enum answering 400 rather than 500. A generator or framework that cannot carry the
  media type is a blocker back to the design, whose fallback is a hand-written controller method on the same path
  and media type.

  **Read back.** Both the generator and the framework carry `application/json-patch+json`. Nothing is blocked,
  and the design's fallback is not needed.

    - A document of one declared operation, `[{"op": "replace", "path": "/categoryId", "value": 42}]`, reached
      the generated method: `501`, empty body, from the interface's own `default`. The array body, the `@Size`
      bound and both path parameters all bound.
    - A document breaking the declared `op` enum, `[{"op": "add", ...}]`, answered `400` with
      `{"message": "the request body could not be read"}`. `CategoryPatchOperation.OpEnum.fromValue` throws,
      Jackson wraps it as `HttpMessageNotReadableException`, and `WebExceptionHandler` maps that — never a 500.

  Dropping `@WebAdapterTest` removes `SecurityConfiguration`, not security: with no `SecurityFilterChain` bean in
  the slice context, Boot's default chain fills in, so the probes carried `.with(csrf())` and `.with(user(...))`
  to reach the generated method. The slice also needs `@Import(Slf4jLoggerFactory.class)`, so that the
  auto-detected `WebExceptionHandler` advice can be constructed.

- [x] ST06 · Confirm both modules are back to green on their own terms, and that neither module's pre-existing
  suite regressed: `tools/agent-test/agent-test.sh --module ledger-service` including
  `bot.finance.architecture.CleanArchitectureTest`, and `npm --prefix web-app run verify`.

## Open Questions / Blockers

No question is open on this plan.

- **ST05 — the plan's own claim about the slice was wrong, and is corrected in the item.** It read that a
  `@WebMvcTest` without `@WebAdapterTest` needs no CSRF token. Removing the composed annotation removes
  `SecurityConfiguration`, but Boot's default chain then fills in, so the probes needed `.with(csrf())` and
  `.with(user(...))`. Nothing downstream depends on the wrong version; the observation ST05 exists to make was
  made.

## Review Findings

- **F1:** ST05 drove the endpoint through a path the security chain refuses, so it could observe nothing:
  `anyRequest().denyAll()` names no matcher for it, and every `@WebMvcTest` in the module imports the chain
  through `@WebAdapterTest`.
  - Resolution: decision
  - Action: resolved against the repository — `WebAdapterTest` `@Import`s `SecurityConfiguration`, and
    `SecurityConfiguration.webSessionSecurityFilterChain` matches `/api/**` and ends in `denyAll`, so a slice
    without that annotation is the only place the generated method is reachable before `ledger-service`'s ST08
    lands. ST05 now drives a throwaway `@WebMvcTest` under `build/scratch/` carrying no `@WebAdapterTest`. The
    alternative — moving the security matcher into this plan — was not taken: a matcher on one module's filter
    chain crosses between no modules, and this plan holds only what does.

- **F2:** ST04 left unread whether a `$ref`ed `ExpenseStatus` **path** parameter generates as an enum or as a
  `String`, which decides whether the framework or the mapper refuses A10's bad status.
  - Resolution: mechanical
  - Action: applied — added as a fourth read-back bullet, citing `listExpenses`' flattened
    `@Nullable String status`.

- **F3:** ST06 sat under the **Interface & Signature Sync** label rather than closing the section.
  - Resolution: mechanical
  - Action: applied — separated out as the section's closing item.

- **F4:** Both Components tables were unpadded.
  - Resolution: mechanical
  - Action: applied — re-emitted with every row and rule line aligned.
