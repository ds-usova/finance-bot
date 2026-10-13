# Plan Log: Discard Chosen Proposals — the shared schema

## Review Findings

- **RF01:** ST04's `writes:` omitted the plan file it asks the generated signature to be written into.
  - Resolution: mechanical
  - Action: applied — added `docs/49-discard-chosen-proposals/shared/plan.md` to ST04's `writes:`.

- **RF02:** ST01 claimed every schema in `openapi/ledger-api.yaml` carries a `description` and an `example`;
  `Session` and `TelegramLoginPayload` carry neither.
  - Resolution: mechanical
  - Action: applied — the clause now names `AcceptanceRequest` and `Acceptance` as the model.

- **RF03:** ST01's `Discard.missing` description added "already discarded", which the field table in
  `rest-api-update.md` does not carry.
  - Resolution: decision
  - Action: resolved — the clause is dropped to match `rest-api-update.md`; "already resolved" already covers an
    entry discarded earlier (AC03).

## Suite Runs

- baseline · tree b9f1c91d76d38ae60095c741fa9e4389da987c05 · total 357 · skipped 0 · green · paths: web-app
- baseline · tree c468bb6ce1f6d8b3b0539a0e2f0eb31f6ad048b0 · total 1335 · skipped 0 · green · paths: ledger-service
- stabilization · tree c468bb6ce1f6d8b3b0539a0e2f0eb31f6ad048b0 · total 1335 · skipped 0 · green · paths: ledger-service
- stabilization · tree b9f1c91d76d38ae60095c741fa9e4389da987c05 · total 357 · skipped 0 · green · paths: web-app

## Stubs

Marker: `stub-intent:`

## Run Log

- **RL01 (ST06):** `plan.sh suite check` answered "unchanged since baseline" after ST01–ST05. The recorded tree
  covers only the module paths, and this plan's change is in `openapi/` plus generated output git ignores, so the
  check could not see it. Both suites were run anyway; both trees hash as at baseline.

