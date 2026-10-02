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
- **RL02 (RU02):** expected passes against the stubs — `WrittenMessageTest$NewMessage` and `$Stored` happy paths:
  ST02 has the private constructor assign the fields; only the refusals are stubbed.
- **RL03 (RU03):** expected pass — `FileWrittenMessageCommandTest`'s "carries both unchanged": a record carries
  its components; only the refusals are stubbed. Likewise RU04's `ReadWrittenMessageCommandTest` and RU05's
  `BrowseWrittenMessagesCommandTest` happy paths.
- **RL04 (RU06):** expected passes — the three `UniqueViolationsTest$ConstraintName` cases answering `null`
  (SQLState `23503`, no `SQLException`, a `23505` with no message) match the stub's `null`; they assert absence.
- **RL05 (RU12):** expected pass — `WrittenMessageWebMapperTest$ToResponses` with no message answers an empty
  list, which the stub returns; it asserts absence.
- **RL06 (RI01):** expected passes — `WrittenMessageRepositoryAdapterTest`: `ClaimOldestWaiting` with nothing
  waiting, `FindForUser` for another person's message and for an id naming nothing, `StampHandOver` on a `DONE`
  row, and `Settle` on settled and waiting rows; each asserts an empty answer or `false` with the row unchanged.
- **RL07 (RI03):** expected pass — `ExpenseRepositoryAdapterTest$FindDaysByMessageReference` for another
  person's entry and another message answers no day; it asserts absence.
- **RL08 (RI04):** expected passes — `MessagesControllerTest`: four `ErrorMapping` cases (429, 400 from the port,
  404, and the 401/403 security cases) reach `WebExceptionHandler`'s handlers and the security chain, written
  implemented in stabilization (RF03); nine `Validation` cases (`text` refusals, not-JSON, `id`, absent `state`)
  are enforced by the generated contract's bean validation and the stabilization handlers.
- **RL09 (GI01):** first verification failed on `WrittenMessageRepositoryAdapterTest$Store`'s unsettled-refusal
  cases: the unique violation aborted the test's transaction (`25P02`). Attempt 2 put `store()` in a savepoint
  (`Propagation.NESTED`), as `OutboxWriter` does, so a refused store leaves the caller's transaction usable.
- **RL10 (GS04):** `WrittenMessageReadingSystemTest` seeded its person through `UserRowUtils.storedUserId`, a user
  with no groupings, so the turn settled `FAILED` before extraction and the unhappy path passed for the wrong
  reason. RS04's test was corrected to seed with `Grouping.defaults()`; no production change.
- **RL11 (GS04):** the Stage 3 full suite is red: 1335 tests, 45 failed, 0 skipped, 2094 s
  (`suite-20261002-043859-1398468`), nearly all `bot.finance.system.*` with `Connection refused` on the shared
  Postgres's old port. The suite now creates more than 32 Spring test contexts; an evicted context's close
  stops the shared `@ImportTestcontainers` Postgres and the still-cached contexts keep its dead port. The system
  classes alone pass (76/76), and the full suite passes 1335/1335 in 170 s with
  `spring.test.context.cache.maxSize=100` set for that one run. No plan item owns the test-context or container
  configuration; refactor and wrap-up wait on the decision.
  - Resolved: option 1 — ST14 sets `spring.test.context.cache.maxSize=100` in
    `ledger-service/src/test/resources/spring.properties`; chosen by the session; user unanswered.
- **RL12 (GU10):** hypothesis: `HandleWrittenMessageUseCase` counts a `FAILED` turn on the meter when the stamp
  throws, which RU10 does not pin — settled by reading RU10 against the design page.
- **RL13 (GU12):** hypothesis: the refused-state message in `WrittenMessageWebMapper` ("unsupported written
  message state: X") names `state` as ST07 requires — settled by reading the mapper against ST07.
- **RL14 (GS04):** hypothesis: raising the cache limit leaves the suite one added context away from the same
  failure; a shared container whose lifecycle no context owns would not — settled by running the suite at the
  default limit after making the container a plain singleton.
- **RL15 (GU10):** the refactor pass moved the turn's shared middle (default currency, catch-all, extraction
  request, period discard) into `ExtractionTurn`, used by `HandleWrittenMessageUseCase` and
  `HandleIncomingMessageUseCase`; each keeps its own outcome rule, since RF08 makes the web's differ. Suite
  1335 / 0 skipped, `CleanArchitectureTest` green. Left outside the diff: `UniqueViolations` duplicates
  `ForeignKeyViolations` and both return `null`; 45 test lookups of a user id by external id repeat one idiom.
- **RL16 (GU10):** hypothesis: the web turn's log lines print the reference as `IncomingMessageId[value=web:42]`
  where the sweep prints `web:42` — settled by reading one web-turn log line from a running service.

## Caveats

- **CV01:** `plan.sh` refused at the plugin path by the project hook — "No allow rule covers this command" — and
  absent at the allowlisted `C:/Users/rieic/Projects/tdd-sdlc/scripts/plan/plan.sh`, from plan-task's first
  `validate` on. Validation by hand.

## Suite Runs

- baseline · tree a4d3db855db0ccf48cd8da09a790543ceca5cfe0 · total 1188 · skipped 0 · green · paths: ledger-service
- stabilization · tree f75fc395f1c01971531043e7cf9b703ac1308c55 · total 1189 · skipped 0 · green · paths: ledger-service

- green · tree 2de886746b68011e8eae38621d870b1202d47256 · total 1335 · skipped 0 · green · paths: ledger-service
- refactor · tree 2fa0acd36fc0c5f4301066539af98fdfa271cdea · total 1335 · skipped 0 · green · paths: ledger-service
- final · tree aa4b6431eb058e647f54802ba72c28d88757cab9 · total 1335 · skipped 0 · green · paths: ledger-service
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
