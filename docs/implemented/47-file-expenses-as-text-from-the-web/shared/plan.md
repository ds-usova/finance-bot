# Plan: File Expenses as Text from the Web — the shared schema

**Format:** 2
**Affected Modules:** `ledger-service`, `web-app`
**Spec:** [File Expenses as Text from the Web](../spec.md)

## Architecture Decisions

No architectural placement decisions.

## Step-by-Step Implementation Map (To-Do List)

### Stabilization

#### API Contract

- [x] ST01 · Add the `messages` tag to `openapi/ledger-api.yaml`'s `tags` list, after `preferences`, and add four
  schemas to `components/schemas`, each with a `description` and an `example` as every schema there has, from
  the field table of [the REST API update](../rest-api-update.md):
  - writes: `openapi/ledger-api.yaml`
  ```yaml
  WrittenMessageState:
    type: string
    description: >-
      Whether the text is still being read or its reading has ended. A text still waiting to be read is
      answered as READING too.
    enum:
      - READING
      - DONE
    example: DONE
  WrittenMessageOutcome:
    type: string
    description: What reading the text came to.
    enum:
      - RECORDED
      - PARTIAL
      - NOTHING_IDENTIFIED
      - ANSWERED
      - FAILED
    example: RECORDED
  WrittenMessage:
    type: object
    description: One text the caller filed, and what became of it.
    properties:
      id:
        type: integer
        format: int64
        description: The filed message, unique across every person's.
      state:
        $ref: '#/components/schemas/WrittenMessageState'
      text:
        type: string
        description: The text as filed.
      outcome:
        description: What reading the text came to, or `null` until the state is DONE.
        oneOf:
          - $ref: '#/components/schemas/WrittenMessageOutcome'
          - type: 'null'
      days:
        type: array
        description: >-
          The UTC days the message's entries were stored on, pending or since accepted, oldest first. Empty
          until the state is DONE, and when nothing was stored.
        items:
          type: string
          format: date
    required:
      - id
      - state
      - text
      - outcome
      - days
    example:
      id: 42
      state: DONE
      text: coffee 3.40, taxi home 14
      outcome: RECORDED
      days: ['2026-10-01']
  WrittenMessageFiling:
    type: object
    description: The text to file.
    properties:
      text:
        type: string
        minLength: 1
        maxLength: 1000
        pattern: '^[\s\S]*\S[\s\S]*$'
        description: 1 to 1000 UTF-16 code units, not blank or only whitespace.
    required:
      - text
    example:
      text: coffee 3.40, taxi home 14
  ```
- [x] ST02 · Add `openapi/paths/messages.yaml` with two operations, reusing `../components/responses/errors.yaml`
  for `BadRequest`, `Unauthorized`, `NotFound` and `ServiceUnavailable`, each operation with a `summary` and a
  `description`, as `expense-acceptances.yaml` has · after: ST01
  - writes: `openapi/paths/messages.yaml`
  - `get` — `operationId: listWrittenMessages`, tag `messages`; query parameter `state`, required, `type: string`,
    `enum: [READING]`, with a `description` and `example: READING`; `200` an array of `WrittenMessage`, empty or
    of one; `400`, `401`, `404`, `503`.
  - `post` — `operationId: fileWrittenMessage`, tag `messages`; request body `WrittenMessageFiling`, required;
    `202` a `WrittenMessage` whose state is `READING`; `400`, `401`, `403` described as the request carrying no
    CSRF token, `404`, `429` carrying a `Problem`, described as the caller already having a text waiting or being
    read, with an `example` message; `503`.
- [x] ST03 · Add `openapi/paths/message.yaml` with `get` — `operationId: readWrittenMessage`, tag `messages`, a
  `summary` and a `description`; path parameter `id`, required, `type: integer`, `format: int64`, `minimum: 1`,
  with a `description` and `example: 42`, as `expense-category.yaml` gives its own; `200` a `WrittenMessage`;
  `400`, `401`, `503` from `errors.yaml`; and a `404` of its own carrying a `Problem` with an `example` message,
  described as no message of the caller's carrying that id, whoever else's it is, or the session outliving its
  user row, as `expense-category.yaml` describes its own · after: ST01
  - writes: `openapi/paths/message.yaml`
- [x] ST04 · Add `/api/v1/messages: $ref: './paths/messages.yaml'` and
  `/api/v1/messages/{id}: $ref: './paths/message.yaml'` to `openapi/ledger-api.yaml`'s `paths` map **at the end**,
  after `/api/v1/preferences` · after: ST02, ST03
  - writes: `openapi/ledger-api.yaml`

#### Interface-First / Build Stabilization

**Interface & Signature Sync**

- [x] ST05 · Regenerate and compile `ledger-service`:
  `tools/agent-test/agent-test.sh --module ledger-service --compile`, which reaches `openApiGenerate` through
  `compileJava`. Confirm `bot.finance.api.MessagesApi` is generated with `fileWrittenMessage`,
  `readWrittenMessage` and `listWrittenMessages`, and that no existing generated type was renamed. Write the
  generated signatures and the names of the request, response and enum types they use into this item, beneath
  this line, so the `ledger-service` plan's steps read them rather than assume them.
  - writes: `ledger-service/build/generated/sources/openapi/`
  - generated, in `bot.finance.api.MessagesApi`, each returning `NOT_IMPLEMENTED` by default:
    - `ResponseEntity<ListWrittenMessages200ResponseInner> fileWrittenMessage(@Valid @RequestBody FileWrittenMessageRequest fileWrittenMessageRequest)`
      — `POST /api/v1/messages`
    - `ResponseEntity<List<ListWrittenMessages200ResponseInner>> listWrittenMessages(@NotNull @Valid @RequestParam(value = "state", required = true) String state)`
      — `GET /api/v1/messages`; `state` is a `String`, not an enum
    - `ResponseEntity<ListWrittenMessages200ResponseInner> readWrittenMessage(@Min(value = 1L) @PathVariable("id") Long id)`
      — `GET /api/v1/messages/{id}`
  - types, in `bot.finance.api.model`: the `WrittenMessage` body is `ListWrittenMessages200ResponseInner`, built
    as `(Long id, WrittenMessageState state, String text, WrittenMessageOutcome outcome, List<LocalDate> days)`,
    its `outcome` a `JsonNullable<WrittenMessageOutcome>`; the `WrittenMessageFiling` body is
    `FileWrittenMessageRequest`; the enums are `WrittenMessageState` and `WrittenMessageOutcome`; every `Problem`
    response is `ListExpenses400Response`. Standalone `WrittenMessage` and `WrittenMessageFiling` classes are
    generated too, used by no operation.
- [x] ST06 · Regenerate `web-app`'s types with `npm --prefix web-app run generate:api`, confirm
  `components['schemas']['WrittenMessage']`, `['WrittenMessageState']`, `['WrittenMessageOutcome']` and
  `['WrittenMessageFiling']` are present, then run `npm --prefix web-app run typecheck`.
  - writes: `web-app/src/api/generated/ledger-api.d.ts`

#### Closing item

- [x] ST07 · Confirm both modules stand where the baseline left them:
  `tools/agent-test/agent-test.sh --module ledger-service`, its `CleanArchitectureTest` included, and
  `tools/agent-test/agent-test.sh --module web-app`. This plan disables no test, so the totals and the skipped
  counts are the baseline's.
  - writes: none

## Open Questions

None. The schema is additive, and neither module has a call site it breaks.
