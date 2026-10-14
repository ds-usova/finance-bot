# Plan Log: The Ledger Stops Waiting for a Turn — ledger-service

## Review Findings

- **RF01:** RU14's one-second wait scenario already passed against the unchecked record.
  - Resolution: mechanical
  - Action: applied — dropped it.

- **RF02:** RI03's filed-instant scenario already passed after ST06.
  - Resolution: mechanical
  - Action: applied — dropped it and `findForUser()` from `covers:`.

- **RF03:** Several scenarios asserted only a negative or a stub's default.
  - Resolution: mechanical
  - Action: applied — dropped the pure negatives; the hand-over failures now also assert the attempt and the read were made.

- **RF04:** DF26 was covered only in part.
  - Resolution: mechanical
  - Action: applied — RU08 and RU09 pin a failed stamp or acceptance, and a failed attempt record, as propagating.

- **RF05:** RS06's 429 scenario was already held by existing tests.
  - Resolution: mechanical
  - Action: applied — dropped it; a coverage note names the existing system and adapter tests holding AC32.

- **RF06:** RS03's no-credential scenario duplicated RI07.
  - Resolution: mechanical
  - Action: applied — dropped it.

- **RF07:** RI07's new error-mapping scenario repeated existing parameterised tests that a third operation leaves incomplete.
  - Resolution: mechanical
  - Action: applied — replaced by a premise update reaching `assertRefusedOnBothOperations()` and its two callers.

- **RF08:** ST23's callbacks off the answering thread race `GrpcStubServerCallbackTest`'s assertions.
  - Resolution: mechanical
  - Action: applied — ST23 moves them inside the await.

- **RF09:** RS02's shared arrangement would leak one scenario's update and script into the next.
  - Resolution: mechanical
  - Action: applied — the arrangement moves into the existing test's body.

- **RF10:** ST10–ST12 stub out existing bodies, where a signature change keeps its logic.
  - Resolution: decision
  - Action: resolved — kept as a departure: `handle()` and `handleNext()` are rewritten from nothing, and keeping their old bodies would keep removed port methods alive; their tests stay disabled naming the reworking step.

- **RF11:** RU05 and RU06 pinned a swallow living in an injected `ExtractionTurn` with no step of its own.
  - Resolution: decision
  - Action: resolved — the helpers take `SpendingQueryRepository` and swallow a failed discard themselves, as `ExtractionTurn.discardPeriods` does today.

- **RF12:** The helpers wrote DF24's error line on the sweep and the refusal too.
  - Resolution: decision
  - Action: resolved — the end and a refused hand-over write it, both being failed extractions logged at error today (`HandleIncomingMessageUseCase.java`); the sweep writes only its warning.

- **RF13:** GI07's tests run through GI08's target, and an `after:` would make a cycle.
  - Resolution: decision
  - Action: resolved — left as it is: GI07 asserts only the guard's statuses, which hold against the stub's empty answer.

- **RF14:** ST17 did not say where the text sweep's wait comes from.
  - Resolution: mechanical
  - Action: applied — a ten-minute placeholder with a `TODO`, replaced in ST21.

- **RF15:** `SweepWrittenMessagesPort`'s javadoc still spoke of reading texts only.
  - Resolution: mechanical
  - Action: applied — ST17 writes it and rewords it.

- **RF16:** ST01, ST10, ST11, ST12, ST14 and ST22 carried their own reasoning.
  - Resolution: mechanical
  - Action: applied — cut.

- **RF17:** RU10 tested a mapping `IncomingTurnReport` owns.
  - Resolution: mechanical
  - Action: applied — dropped it.

- **RF18:** DF25 was not covered.
  - Resolution: mechanical
  - Action: applied — RU12 settles a waiting text with only a period `FAILED`.

- **RF19:** AC23 was covered only for the sweep's find.
  - Resolution: mechanical
  - Action: applied — RU11 and RU12 pin a failed read, delete or settle as propagating.

- **RF20:** The error line was pinned only for `FAILED`.
  - Resolution: mechanical
  - Action: applied — RU05 and RU06 pin it for `PARTIAL` too, on the end and refusal entry point.

- **RF21:** Two boundary values were missing.
  - Resolution: mechanical
  - Action: applied — RU02 adds `web:-1` and an overflowing id; RU01 refuses an id of zero or less.

- **RF22:** A refused hand-over whose settle then throws was unwritten.
  - Resolution: decision
  - Action: resolved — it propagates, as DF26's failures do; RU08, RU09 and both ports' javadoc say so.

## Run Log

- **RL01 (ST22):** hypothesis: `ledger-service/docs/contracts/out/ai-connector.md` still describes the connector
  deadline as covering the whole turn, where it now covers only the hand-over — settled by reading that page against
  ST22's deadline.
- **RL02 (ST13):** `WrittenMessageRepositoryAdapter.settle()` carries a `TODO` to settle a `WAITING` text as well as
  a `READING` one, the behaviour RI03's `settle()` scenario pins.
- **RL03 (RU12):** plan defect — the scenario "a waiting text with only a period under it is settled `FAILED`"
  fits no test: `SweepWrittenMessagesUseCase` takes no `SpendingQueryRepository`, so a period is indistinguishable
  from nothing under the text, which the "nothing under it" scenario already pins. Left unwritten.
- **RL04 (RS05):** "never handed over" is asserted as the connector's last request not being this text;
  `GrpcStubServer` keeps no request count, so a leftover row from another class could mask a hand-over.
- **RL05 (RU13, RI06):** the red wave's run found two tests passing against the stubs for want of a positive check —
  `TurnHandOverReaderTest`'s `handOverNext()`-throws test and `MicrometerTurnMetersTest`'s zero-gauge test. Each was
  re-delegated once.
- **RL06 (red wave):** expected passes against the stubs, each asserting an absence or a minimum the stub already
  answers, or behaviour stabilization delivered:
  - `HandleIncomingMessageUseCaseTest.whenCommandIsNull_thenThrowsInvalidIncomingMessageExceptionAndNoPortIsCalled`
    and `EndTurnUseCaseTest.whenTheCommandIsNull_thenThrowsInvalidTurnEndExceptionAndNoPortIsCalled` — the null
    guards are stabilization's.
  - `IncomingTurnTest.whenTurnIsBuiltFromEveryField_thenItCarriesThemAndHasNoIdAttemptOrHandOver` and
    `whenTurnIsRebuiltWithIdAttemptAndHandOver_thenItAnswersThem` — the entity's fields are stabilization's.
  - `IncomingMessageIdTest.whenIdDoesNotNameAWrittenMessage_thenWrittenMessageIdIsEmpty` (six values) — the stub
    answers empty.
  - `IncomingTurnRepositoryAdapterTest`: `whenOnlyHandedOverOrDeletedTurnsFollowTheInstant_thenAnswerIsEmpty`,
    `whenTurnIsAlreadyHandedOver_thenAnswersFalseAndRowIsUnchanged`, `whenTurnIsDeleted_thenAnswersFalseAndNoRowAppears`,
    `whenTurnWasKeptForAnotherPerson_thenAnswerIsEmpty`, `whenNoTurnIsQueued_thenAnswerIsEmpty` — the stubs answer
    empty or false.
  - `WrittenMessageRepositoryAdapterTest`: `whenOnlyReadingAndDoneTextsFollowTheInstant_thenAnswerIsEmpty`,
    `whenTextIsReadingOrDone_thenAnswersFalseAndRowIsUnchanged`, `whenNoTextWaits_thenAnswerIsEmpty` — the stubs
    answer empty or false.
  - `WrittenMessageRepositoryAdapterConcurrencyTest.whenTwoThreadsSettleTogether_thenExactlyOneAnswersTrue` — the
    existing settle of a `READING` text.
  - `AiConnectorIntentExtractionAdapterTest.whenStubServerAnswersLaterThanDeadline_thenThrowsIntentExtractionFailedExceptionNamingDeadlineExceeded`
    — the adapter already maps any status to the failed exception, and ST26 set the 1 s deadline.
  - `LedgerTurnEndGrpcServiceTest.whenEndTurnIsCalled_thenNoTurnCallLineIsLogged` — the stub logs nothing.
  - `TurnOperationsSecurityConfigurationTest.whenALedgerTokenIsPresentedToEndTurn_thenTheCallIsAnswered` and
    `whenTheReflectionServiceIsAskedWithoutACredential_thenItNamesTheTurnEndService` — the service is registered and
    the stub answers empty (RF13).
  - `EndTurnSystemTest.whenTurnIsEndedWithAnotherPersonsToken_thenNothingIsReportedAndRowRemains` — nothing happens
    against the stub.
- **RL07 (GI01):** keeping a turn under a message already kept marked the caller's transaction rollback-only
  (`UnexpectedRollbackException`) in the green wave's run; re-delegated once, `keep` now inserts with
  `ON CONFLICT DO NOTHING`.
- **RL08 (green checkpoint):** the full-suite run backing the unit and integration green checkpoint was stopped by
  the host for low memory while the connector's pipeline also ran; no result was read. Re-run once, with this module
  alone on the machine.
  - Resolved: re-run as the unit and integration packages (`domain`, `application`, `adapter`, `common`), the
    checkpoint's own scope: 1505 tests, all green.
- **RL09 (GS01):** `ReceiveTelegramMessageSystemTest.whenSendersStoredPreferenceHoldsEur_thenExtractionRequestCarriesEurAsDefaultCurrency`
  signs in through `BrowserSessions`, which uses RestAssured's static port; the class never sets it, so run alone the
  call goes to `localhost:8080` (`ConnectException`). It passed in the baseline only on statics another class leaked.
  A test defect of the red step's class, not production: re-delegated to the red step to set the port in the class's
  own `@BeforeEach`.
- **RL10 (Stage 4 entry):** the full-suite run before the refactor pass (`suite-20261014-215306-157109`) never
  finished: it ran in the background past the foreground limit and wrote no summary. From mid-run every system test
  failed on `PSQLException` / `ConnectException` (Postgres refusing connections) and `ConditionTimeoutException`, the
  container dying under memory pressure — not a test result. Re-run in the foreground, split by package.
- **RL11 (RI05, RI08):** that run's `DisplayNameConventionsTest.aTestMethodsDisplayNameStaysUnderTheLimit` failed on
  two display names this plan wrote, over 120 characters:
  `AiConnectorIntentExtractionAdapterTest$Extract.whenStubServerAnswersLaterThanDeadline_…` (123) and
  `LedgerTurnEndGrpcServiceTest$HappyPath.whenEndTurnIsCalledWithUnavailable_…` (131). Re-delegated to the red step
  to shorten them.
- **RL12 (Stage 3 exit):** the suite re-run in the foreground in two halves, nothing else running: the non-system
  packages (`architecture`, `domain`, `application`, `adapter`, `common`) 1516 tests, all green; `bot.finance.system.*`
  95 tests, all green. Together 1611 tests, 0 skipped, green.

## Suite Runs

- baseline · tree 05fd1145a8cd5dca1a037b4123c7e61fef2142e1 · total 1447 · skipped 0 · green · paths: ledger-service
- stabilization · tree 4993313657c0f827cb7b471cc22b64dec37c9c62 · total 1448 · skipped 69 · green · paths: ledger-service

## Stubs

Marker: `stub-intent:`

- `ledger-service/src/main/java/bot/finance/domain/model/IncomingTurn.java`
- `ledger-service/src/main/java/bot/finance/domain/value/IncomingMessageId.java`
- `ledger-service/src/main/java/bot/finance/application/usecase/HandleIncomingMessageUseCase.java`
- `ledger-service/src/main/java/bot/finance/application/usecase/HandleWrittenMessageUseCase.java`
- `ledger-service/src/main/java/bot/finance/application/usecase/IncomingTurnReport.java`
- `ledger-service/src/main/java/bot/finance/application/usecase/WrittenTurnOutcome.java`
- `ledger-service/src/main/java/bot/finance/application/usecase/HandOverIncomingTurnUseCase.java`
- `ledger-service/src/main/java/bot/finance/application/usecase/EndTurnUseCase.java`
- `ledger-service/src/main/java/bot/finance/application/usecase/SweepIncomingTurnsUseCase.java`
- `ledger-service/src/main/java/bot/finance/adapter/persistence/IncomingTurnRepositoryAdapter.java`
- `ledger-service/src/main/java/bot/finance/adapter/persistence/WrittenMessageRepositoryAdapter.java`
- `ledger-service/src/main/java/bot/finance/adapter/metrics/MicrometerTurnMeters.java`
- `ledger-service/src/main/java/bot/finance/adapter/aiconnector/LedgerTurnProtoMapper.java`
- `ledger-service/src/main/java/bot/finance/adapter/aiconnector/LedgerTurnEndGrpcService.java`
