# Plan Log: File Expenses as Text from the Web — `ledger-service`

## Review Findings

- **RF01:** ST02 and RU02 put the 1000-unit bound on `WrittenMessage`, which ADR 0004 forbids a core type.
  - Resolution: decision
  - Action: resolved — `ledger-service/docs/adr/0004-column-widths-are-checked-in-the-persistence-adapter.md`
    puts widths in the adapter: the bound moved to `ColumnLimits` with a `ColumnLimitsSchemaTest` case in ST06,
    and its two cases from RU02 to RI01 `store()`.

- **RF02:** RI04's `state` refusals could not pass: an absent parameter reaches the catch-all 500, and nothing
  refused `DONE` or `reading`.
  - Resolution: decision
  - Action: resolved — `rest-api-update.md` requires 400 for both: ST07 adds a
    `MissingServletRequestParameterException` handler answering 400, and the mapper refuses any state but
    `READING` (ST07, RU12).

- **RF03:** ST06's entity mapping and ST07's exception handlers are written implemented, not stubbed.
  - Resolution: mechanical
  - Action: kept — the user chose to leave the mapping and the handlers written in stabilization, as task 22's
    ST04 and ST06 did; RI01 and RI04 assert them.
  - Escalated: stubbing them moves code into `GI01` and `GI04` beyond the one class each names.

- **RF04:** ST09 and ST11 both write `SecurityConfiguration.java` with no order between them.
  - Resolution: mechanical
  - Action: applied — `after: ST09` on ST11.

- **RF05:** ST01, ST02, ST04, ST06, ST08, ST09 and ST10 carried reasoning or design ids.
  - Resolution: mechanical
  - Action: applied — cut each to what it creates or changes.

- **RF06:** `toResponses()` had no empty-list case, and RS03 named AC05, which is filing's.
  - Resolution: mechanical
  - Action: applied — added the case to RU12 and dropped AC05 from RS03.

- **RF07:** `HandleWrittenMessageUseCase` copies the Telegram turn's middle — currency, catch-all, request,
  period discard, outcome.
  - Resolution: decision
  - Action: resolved — the module's refactor pass deduplicates logic repeated across classes as its first
    priority (`ledger-service/docs/conventions/code-style.md`, Refactoring Conventions); the plan keeps the two
    turns apart and leaves the shared middle to that pass.

- **RF08:** No scenario said what a failed extraction leaving a period and no entry gives.
  - Resolution: decision
  - Action: resolved — `reading-a-written-message.md` counts entries alone, so it is `FAILED`; added to RU10.

- **RF09:** A stamp, a settle and a sweep's store failing had no scenario.
  - Resolution: mechanical
  - Action: applied — added two RU10 cases and one RU11 case.

- **RF10:** RI01 never exercised `store()`'s fallback to `PersistenceFailedException`.
  - Resolution: mechanical
  - Action: applied — added a `WithAMockedStore` case for another constraint.

- **RF11:** The claim, settle and sweep log lines of DF08 and DF31 were in no step.
  - Resolution: mechanical
  - Action: applied — named in ST05's two stub intents.

- **RF12:** ST09 picks values no decision sets: a 10-second reader poll, a 30-second sweep margin, and a new
  `MCP_JWT_CLOCK_SKEW` variable at 60 seconds.
  - Resolution: decision
  - Action: confirmed — the user chose "10 с / 30 с / 60 с": `WRITTEN_MESSAGE_READER_POLL_INTERVAL=10s`,
    `WRITTEN_MESSAGE_SWEEP_MARGIN=30s` and the new `MCP_JWT_CLOCK_SKEW=60s`, as ST09 sets them.

## Run Log

- **RL01 (ST06):** the stabilization suite counts 1189 against the baseline's 1188: the one added test is the
  `ColumnLimitsSchemaTest$WrittenText` width case ST06 names.

## Caveats

- **CV01:** `plan.sh` refused at the plugin path by the project hook — "No allow rule covers this command" — and
  absent at the allowlisted `C:/Users/rieic/Projects/tdd-sdlc/scripts/plan/plan.sh`, from plan-task's first
  `validate` on. Validation by hand.

## Suite Runs

- baseline · tree a4d3db855db0ccf48cd8da09a790543ceca5cfe0 · total 1188 · skipped 0 · green · paths: ledger-service
- stabilization · tree f75fc395f1c01971531043e7cf9b703ac1308c55 · total 1189 · skipped 0 · green · paths: ledger-service

## Stubs

Marker: `stub-intent:`

- `ledger-service/src/main/java/bot/finance/domain/model/WrittenMessage.java`
- `ledger-service/src/main/java/bot/finance/domain/value/IncomingMessageId.java`
- `ledger-service/src/main/java/bot/finance/application/dto/FileWrittenMessageCommand.java`
- `ledger-service/src/main/java/bot/finance/application/dto/ReadWrittenMessageCommand.java`
- `ledger-service/src/main/java/bot/finance/application/dto/BrowseWrittenMessagesCommand.java`
- `ledger-service/src/main/java/bot/finance/application/usecase/FileWrittenMessageUseCase.java`
- `ledger-service/src/main/java/bot/finance/application/usecase/ReadWrittenMessageUseCase.java`
- `ledger-service/src/main/java/bot/finance/application/usecase/BrowseWrittenMessagesUseCase.java`
- `ledger-service/src/main/java/bot/finance/application/usecase/HandleWrittenMessageUseCase.java`
- `ledger-service/src/main/java/bot/finance/application/usecase/SweepWrittenMessagesUseCase.java`
- `ledger-service/src/main/java/bot/finance/adapter/persistence/WrittenMessageRepositoryAdapter.java`
- `ledger-service/src/main/java/bot/finance/adapter/persistence/UniqueViolations.java`
- `ledger-service/src/main/java/bot/finance/adapter/persistence/ColumnLimits.java`
- `ledger-service/src/main/java/bot/finance/adapter/persistence/ExpenseRepositoryAdapter.java`
- `ledger-service/src/main/java/bot/finance/adapter/web/MessagesController.java`
- `ledger-service/src/main/java/bot/finance/adapter/web/WrittenMessageWebMapper.java`
- `ledger-service/src/main/java/bot/finance/adapter/async/WrittenMessageReader.java`
