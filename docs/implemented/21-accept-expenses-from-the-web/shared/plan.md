# Plan: Accept Expenses from the Web App — the shared specification

**Affected Modules:** `ledger-service`, `web-app`
**Design:** [Accept Expenses from the Web App](../design.md)

The acceptance endpoint is described in `openapi/`, which both builds read: `ledger-service` generates the
endpoint interface it implements, `web-app` generates the request and response types it calls with. This plan
lands the specification and regenerates both modules from it. It is implemented first and alone; neither module
plan starts until every item here is ticked.

## Components

No class is designed here. The artifact is one path file and two schemas, plus the generated sources each
module's build derives from them.

| Artifact                                 | Holds                                                                |
|------------------------------------------|----------------------------------------------------------------------|
| `openapi/paths/expense-acceptances.yaml` | `POST /api/v1/expenses/acceptances`, operation `acceptExpenses`      |
| `AcceptanceRequest`                      | `ids` — array of int64, 1 to 100 items, each at least 1               |
| `Acceptance`                             | `accepted`, `missing` — both integers, both required                  |

| Where the generated sources land | Committed |
|----------------------------------|-----------|
| `ledger-service/build/generated/sources/openapi/` | no |
| `web-app/src/api/generated/ledger-api.d.ts`       | no  |

## Step-by-Step Implementation Map (To-Do List)

### Stabilization

#### API Contract

- [x] ST01 · Add `openapi/paths/expense-acceptances.yaml`, declaring all six statuses the design's status table
  names — `403` inline and bodiless as `openapi/paths/session.yaml` writes it, the rest reused from
  `openapi/components/responses/errors.yaml`:
  ```yaml
  post:
    operationId: acceptExpenses
    summary: Accept the signed-in person's pending proposals, by id.
    description: >-
      Every id is taken as the id of a `PENDING` entry of the signed-in person's, as the listing answered it. An
      id naming no such entry is counted in `missing` rather than refused, so `accepted` plus `missing` always
      equals the number of ids the request carried.
    tags:
      - expenses
    requestBody:
      required: true
      content:
        application/json:
          schema:
            $ref: '../ledger-api.yaml#/components/schemas/AcceptanceRequest'
    responses:
      '200':
        description: The write ran. It moved whatever the ids matched, which may be nothing.
        content:
          application/json:
            schema:
              $ref: '../ledger-api.yaml#/components/schemas/Acceptance'
      '400':
        $ref: '../components/responses/errors.yaml#/BadRequest'
      '401':
        $ref: '../components/responses/errors.yaml#/Unauthorized'
      '403':
        description: The request carried no CSRF token.
      '404':
        $ref: '../components/responses/errors.yaml#/NotFound'
      '503':
        $ref: '../components/responses/errors.yaml#/ServiceUnavailable'
  ```
- [x] ST02 · Add `AcceptanceRequest` and `Acceptance` to `openapi/ledger-api.yaml` under `components/schemas`,
  and the path entry **after** the four already listed under `paths:` — [Build](../../../ledger-service/docs/conventions/build.md)
  records that reordering the paths renames the five operation-derived response types and breaks the Java build.
  `uniqueItems` is deliberately absent: the generator turns it into a `Set`, which drops a repeated id instead of
  refusing it, and the design's 400 row and A6 require a refusal.
  ```yaml
  AcceptanceRequest:
    type: object
    description: Which pending entries to accept, by the ids the listing answered.
    properties:
      ids:
        type: array
        description: >-
          The ids of `PENDING` entries to accept. Each must name one of the caller's, and a repeated id is
          refused rather than collapsed.
        minItems: 1
        maxItems: 100
        items:
          type: integer
          format: int64
          minimum: 1
    required:
      - ids
    example:
      ids: [7, 12]
  Acceptance:
    type: object
    description: >-
      What the write moved. `accepted` plus `missing` always equals the number of ids the request carried.
    properties:
      accepted:
        type: integer
        description: How many proposals became expenses.
      missing:
        type: integer
        description: >-
          How many ids named no pending proposal of the caller's — already resolved, already discarded, or not
          theirs.
    required:
      - accepted
      - missing
    example:
      accepted: 2
      missing: 0
  ```

#### Interface-First / Build Stabilization

**Interface & Signature Sync**

- [x] ST03 · Run `web-app`'s codegen (`npm --prefix web-app run generate:api`) and confirm
  `components['schemas']['AcceptanceRequest']` and `components['schemas']['Acceptance']` are both present in
  `web-app/src/api/generated/ledger-api.d.ts`, and that no existing schema's shape changed. Nothing is committed:
  `.gitignore` ignores that directory, and `package.json` regenerates it before every script that reads it.
- [x] ST04 · Run `ledger-service`'s codegen (`ledger-service/gradlew -p ledger-service openApiGenerate`) and
  record, in this item, the generated names the new operation actually produced — the method added to
  `bot.finance.api.ExpensesApi`, its request-body type and its 200 response type. The module's
  [Build](../../../ledger-service/docs/conventions/build.md) records that an operation's top-level response
  schema is renamed after the operation and status code, so `Acceptance` may generate as
  `AcceptExpenses200Response`; the request body's fate is unobserved and is read here rather than guessed. Both
  module plans are written against whatever this step reads back.

  Read back from
  `ledger-service/build/generated/sources/openapi/src/main/java/bot/finance/api/ExpensesApi.java:92`:

  ```java
  default ResponseEntity<AcceptExpenses200Response> acceptExpenses(
      @Parameter(name = "AcceptExpensesRequest", description = "", required = true)
      @Valid @RequestBody AcceptExpensesRequest acceptExpensesRequest
  )
  ```

  | Schema              | Generated Java type                               |
  |---------------------|---------------------------------------------------|
  | `AcceptanceRequest` | `bot.finance.api.model.AcceptExpensesRequest`     |
  | `Acceptance`        | `bot.finance.api.model.AcceptExpenses200Response` |

  The request body is renamed after the operation too, so neither schema keeps its spec name in Java. The path
  constant is `ExpensesApi.PATH_ACCEPT_EXPENSES`. `web-app` is unaffected: its generator keeps the spec names, so
  `ledger-api.d.ts` carries `components['schemas']['AcceptanceRequest']` and `components['schemas']['Acceptance']`.
- [x] ST06 · Confirm both modules are back to green on their own terms, and that neither module's pre-existing
  suite regressed: `tools/agent-test/agent-test.sh --module ledger-service` including
  `bot.finance.architecture.CleanArchitectureTest`, and `npm --prefix web-app run verify`.

## Open Questions / Blockers

No question is open on this plan.

## Review Findings

- **F1:** ST03 said to commit `web-app/src/api/generated/ledger-api.d.ts`, which `.gitignore` ignores and
  `package.json` regenerates before every script that reads it.
  - Resolution: mechanical
  - Action: applied — ST03 now regenerates and confirms without committing, and the table row reads `no`.

- **F2:** ST05 stubbed a call site nothing breaks: the generated `ExpensesApi` declares every operation as a
  `default` method returning 501, so a new operation on the tag compiles untouched.
  - Resolution: decision
  - Action: resolved against the repository — `default ResponseEntity<ListExpenses200Response> listExpenses(...)`
    at `ledger-service/build/generated/sources/openapi/src/main/java/bot/finance/api/ExpensesApi.java:92`
    confirms it. ST05 is dropped, leaving a gap in the numbering, the plan's opening sentence no longer claims a
    broken call site, and `ledger-service/plan.md` ST08 no longer refers to a stub this plan left.

- **F3:** ST02 settled where a repeated id is refused, which is behaviour on a contract artifact and belongs in
  the design.
  - Resolution: decision
  - Action: resolved against the repository — recorded as `D64` in the design, on the precedent that `Limit` is
    bounded both in `openapi/components/parameters/paging.yaml` and again in `ExpenseFilter.MAX_LIMIT`. ST02's
    YAML is unchanged.
