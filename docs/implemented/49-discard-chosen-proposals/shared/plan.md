# Plan: Discard Chosen Proposals — the shared schema

**Format:** 2
**Affected Modules:** `ledger-service`, `web-app`
**Spec:** [Discard Chosen Proposals](../spec.md)

## Architecture Decisions

No architectural placement decisions.

## Step-by-Step Implementation Map (To-Do List)

### Stabilization

#### API Contract

- [x] ST01 · Add two schemas to `openapi/ledger-api.yaml`'s `components/schemas`, after `Acceptance`, each with a
  `description` and an `example` as `AcceptanceRequest` and `Acceptance` have, from the field tables of
  [the REST API update](../rest-api-update.md); and add the discard clause to `WrittenMessage.days`'s
  `description`
  - writes: `openapi/ledger-api.yaml`
  ```yaml
  DiscardRequest:
    type: object
    description: Which pending entries to discard, by the ids the listing answered.
    properties:
      ids:
        type: array
        description: >-
          The ids of `PENDING` entries to discard. Each must name one of the caller's, and a repeated id is
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
  Discard:
    type: object
    description: >-
      What the write threw away. `discarded` plus `missing` always equals the number of ids the request carried.
    properties:
      discarded:
        type: integer
        description: How many pending proposals were thrown away.
      missing:
        type: integer
        description: >-
          How many ids named no pending proposal of the caller's — already resolved, or not theirs.
    required:
      - discarded
      - missing
    example:
      discarded: 2
      missing: 0
  ```
  `WrittenMessage.days`'s `description` becomes:
  ```yaml
  description: >-
    The UTC days the message's entries were stored on, pending or since accepted, oldest first. Empty
    until the state is DONE, and when nothing was stored. A discarded entry leaves no day, so the list
    becomes empty once every entry the message produced is discarded.
  ```
- [x] ST02 · Add `openapi/paths/expense-discards.yaml` with one `post` operation, shaped as
  `expense-acceptances.yaml` is: `operationId: discardExpenses`, tag `expenses`, a `summary` and a `description`
  saying an id naming no pending entry of the caller's is counted in `missing` rather than refused; request body
  `DiscardRequest`, required; `200` a `Discard`, described as the write having run and thrown away whatever the
  ids matched, which may be nothing; `400`, `401`, `404` and `503` from `../components/responses/errors.yaml`;
  `403` described as the request carrying no CSRF token · after: ST01
  - writes: `openapi/paths/expense-discards.yaml`
- [x] ST03 · Add `/api/v1/expenses/discards: $ref: './paths/expense-discards.yaml'` to `openapi/ledger-api.yaml`'s
  `paths` map **at the end**, after `/api/v1/messages/{id}` · after: ST02
  - writes: `openapi/ledger-api.yaml`

#### Interface-First / Build Stabilization

**Interface & Signature Sync**

- [x] ST04 · Regenerate and compile `ledger-service`:
  `tools/agent-test/agent-test.sh --module ledger-service --compile`, which reaches `openApiGenerate` through
  `compileJava`. Confirm `bot.finance.api.ExpensesApi` gains `discardExpenses`, that its request and response
  types are classes of their own rather than `AcceptExpensesRequest` and `AcceptExpenses200Response` reused, and
  that no existing generated type was renamed. Write the generated signature and the names of the request and
  response types into this item, beneath this line, so the `ledger-service` plan's steps read them rather than
  assume them.
  - writes: `ledger-service/build/generated/sources/openapi/`, `docs/49-discard-chosen-proposals/shared/plan.md`
  - generated: `ExpensesApi.PATH_DISCARD_EXPENSES = "/api/v1/expenses/discards"`, `POST`, consumes and produces
    `application/json`:
    ```java
    default ResponseEntity<DiscardExpenses200Response> discardExpenses(
        @Valid @RequestBody DiscardExpensesRequest discardExpensesRequest
    )
    ```
    Request type `bot.finance.api.model.DiscardExpensesRequest`, response type
    `bot.finance.api.model.DiscardExpenses200Response`. Standalone `DiscardRequest` and `Discard` models are
    generated too and unused by the operation, as `AcceptanceRequest` and `Acceptance` are beside the
    `acceptExpenses` pair. No existing generated type was renamed.
- [x] ST05 · Regenerate `web-app`'s types with `npm --prefix web-app run generate:api`, confirm
  `components['schemas']['DiscardRequest']` and `['Discard']` are present, then run
  `npm --prefix web-app run typecheck`.
  - writes: `web-app/src/api/generated/ledger-api.d.ts`

#### Closing item

- [x] ST06 · Confirm both modules stand where the baseline left them:
  `tools/agent-test/agent-test.sh --module ledger-service`, its `CleanArchitectureTest` included, and
  `tools/agent-test/agent-test.sh --module web-app`. This plan disables no test, so the totals and the skipped
  counts are the baseline's.
  - writes: none

## Open Questions

None. The schema is additive, and neither module has a call site it breaks.
