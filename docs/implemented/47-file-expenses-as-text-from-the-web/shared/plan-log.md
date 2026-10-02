# Plan Log: File Expenses as Text from the Web — the shared schema

## Review Findings

- **RF01:** An absent `state` answers 500, since no handler maps `MissingServletRequestParameterException`.
  - Resolution: decision
  - Action: resolved — `rest-api-update.md` requires 400 for an absent `state`; the `ledger-service` plan's ST07
    adds the handler and keeps the parameter required.

- **RF02:** The `ledger-service` plan's RS01 and RS04 armed one tool call for a text naming two expenses.
  - Resolution: mechanical
  - Action: applied — both arm two calls and assert two pending entries.

- **RF03:** The `ledger-service` plan's ST09 and ST11 both wrote `SecurityConfiguration.java` unordered.
  - Resolution: mechanical
  - Action: applied — `after: ST09` on ST11.

- **RF04:** Shared ST01 and ST04 and several `ledger-service` items carried their own reasoning.
  - Resolution: mechanical
  - Action: applied — cut each to what it creates or changes.

- **RF05:** ST01 promised an `example` per schema and gave none.
  - Resolution: mechanical
  - Action: applied — superseded by RF13, which keeps the promise and adds them.

- **RF06:** The `ledger-service` plan stubbed two command mappers no step covers.
  - Resolution: decision
  - Action: resolved — each is one constructor call, the simple delegation `step-formats.md` keeps out of the
    red phase; ST07 now writes them implemented.

- **RF07:** The `ledger-service` plan's RS03 named AC05, and RI04 named AC08 with no `READING` read by id.
  - Resolution: mechanical
  - Action: applied — dropped AC05 from RS03 and added a `READING` by-id scenario to RI04.

- **RF08:** Scenarios in the `ledger-service` plan's RU09, RU10 and RU13 and the web-app plan's RU02 and RU03
  passed against their stubs.
  - Resolution: mechanical
  - Action: applied — each now asserts a call or a state the stub cannot produce.

- **RF09:** The `ledger-service` plan's RU01 `Long.MAX_VALUE` scenario drove the same branch as id 42.
  - Resolution: mechanical
  - Action: applied — dropped it.

- **RF10:** No web-app scenario kept focus after an outcome or filed by clicking the control.
  - Resolution: mechanical
  - Action: applied — focus in RU06's `RECORDED` scenario, and a click scenario in RU02.

- **RF11:** The web-app plan's RU06 repeated RU03's load-time 401.
  - Resolution: mechanical
  - Action: applied — dropped it; RU06 keeps AC32 for the read back's 401.

- **RF12:** A text of only non-ASCII whitespace passes the `@Pattern`, then fails the domain and answers a 400
  not naming `text`.
  - Resolution: decision
  - Action: resolved — `WebExceptionHandler` answers `InvalidExpenseAcceptanceException` with its own message;
    the `ledger-service` plan's ST07 adds the same for `InvalidWrittenMessageException`, with messages naming
    the field, and RI04 covers it.

- **RF13:** `docs/conventions/documentation.md` asks for an `example` in the schema; the schemas, the `id`
  parameter, the 429 and the 404 had none, and the operations no `summary` or `description`.
  - Resolution: mechanical
  - Action: applied — added them in ST01, ST02 and ST03.

- **RF14:** The generated server writes `"outcome": null, "days": []` for a `READING` message, which the schema
  says is absent.
  - Resolution: decision
  - Action: applied — the user chose the schema following the server: `outcome` required and `null` until DONE,
    `days` required and empty until DONE, as `Expense.merchant` is present and nullable; ST01, the REST API
    update and DF36 say so.

## Caveats

- **CV01:** `plan.sh` refused at the plugin path by the project hook — "No allow rule covers this command" — and
  absent at the allowlisted `C:/Users/rieic/Projects/tdd-sdlc/scripts/plan/plan.sh`, from plan-task's first
  `validate` on. Validation by hand.

## Suite Runs

- baseline · tree a4d3db855db0ccf48cd8da09a790543ceca5cfe0 · total 1188 · skipped 0 · green · paths: ledger-service
- baseline · tree 06fe7bff94e06247fc12814ed0600e501e821a1c · total 290 · skipped 0 · green · paths: web-app
- stabilization · tree a4d3db855db0ccf48cd8da09a790543ceca5cfe0 · total 1188 · skipped 0 · green · paths: ledger-service
