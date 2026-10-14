# Plan Log: The Ledger Stops Waiting for a Turn — ai-connector-service

## Review Findings

- **RF01:** GI01 and GI02 each needed the other's target, and an `after:` either way made a cycle.
  - Resolution: decision
  - Action: resolved — ST15 wires the endpoint to `AcceptTurnPort` in stabilization, as `templates/example-plan.md` wires a controller to its port, so GI01 follows GI02.

- **RF02:** RS04's tools-mode missing-target scenario already passed after ST07.
  - Resolution: mechanical
  - Action: applied — dropped the scenario.

- **RF03:** ST07 named `LedgerTurnProperties` as losing a mode condition it never had.
  - Resolution: mechanical
  - Action: applied — removed it from the text and `writes:`.

- **RF04:** ST07's end adapter compared the token's expiry with no injected clock.
  - Resolution: mechanical
  - Action: applied — it takes the `Clock` bean.

- **RF05:** RI02 listed three bad tokens the interceptor does not tell apart.
  - Resolution: mechanical
  - Action: applied — kept one, a token that is not a JWT.

- **RF06:** RI03 asserted retry rules no step stated: one attempt with no expiry, and none on `INVALID_ARGUMENT`.
  - Resolution: decision
  - Action: resolved — DN02 retries only while the end is unavailable or unanswered, and the ledger always mints `exp` (`AccessTokenMinter.java`); ST07's stub intent states both.

- **RF07:** RS03 carried four scenarios where the conventions' thin slice allows two.
  - Resolution: decision
  - Action: resolved — `ai-connector-service/docs/conventions/testing.md` ("System"); dropped busy, which RU02, RU05 and RI01 hold.

- **RF08:** RS05's repeated registration raced the runner freeing the turn key.
  - Resolution: decision
  - Action: resolved — the second hand-over is repeated until its own end is recorded, since a re-send while the key is held starts nothing (DF23).

- **RF09:** ST09 carried its own reasoning.
  - Resolution: mechanical
  - Action: applied — cut the clause.

- **RF10:** RI01's update left a test named for an opaque token it no longer sends.
  - Resolution: mechanical
  - Action: applied — the update renames it and its display name.

- **RF11:** In the classifier mode an interrupt at the run's bound did not stop the filing, against AC20.
  - Resolution: decision
  - Action: resolved — spec AC20 ("Nothing is filed after that"); new RU06/GU06 make `ClassifierTurn` stop before a filing once interrupted, raising `ExpenseRecordingFailedException`.

- **RF12:** Nothing checked that a timed-out turn's end still carries the caller token.
  - Resolution: mechanical
  - Action: applied — RU05's timeout scenario and RS03's run-bound scenario assert it.

- **RF13:** The `UNAVAILABLE` mapping in `GrpcStatusConfiguration`, and the test pinning it, became unreachable.
  - Resolution: decision
  - Action: resolved — the turn body no longer runs inside the call; ST16 removes the mapping and RI01 deletes the test.

- **RF14:** Nothing said what the runner answers once closed.
  - Resolution: decision
  - Action: resolved — BUSY, so the ledger keeps the turn queued (DN01, DN06); ST06 and RU05 state it.

- **RF15:** RI03 had no case for a token already expired when the end is reported.
  - Resolution: mechanical
  - Action: applied — added it: one `EndTurn`, one `ERROR`.

## Run Log

- **RL01 (ST07):** the `ledger-turn` channel bean no longer took `LedgerTurnProperties`, so a blank
  `LEDGER_TURN_TARGET` failed on the channel's authority instead of naming the variable, and ST15 had disabled
  `ExtractionModeStartupSystemTest.whenClassifierModeHasNoLedgerTurnTarget_thenApplicationFailsToStartNamingLedgerTurnTarget()`
  for it. Re-delegated to restore the parameter and re-enable the test, whose assertion stands.
- **RL02 (ST15):** the stabilization exit run reads 569 total, 25 skipped: ST10 added one test
  (`LedgerTurnServerTest`), and the disabled parameterized `IntentExtractionGrpcServiceTest` currency test reports
  one skip where it ran twice. All 25 skips name RI01, RI02, RS01, RS02 or RS05–RS08.
- **RL03 (RI01):** expected pass — `IntentExtractionGrpcServiceTest$ErrorMapping.whenPortAnswersBusy_thenFailsWithResourceExhausted()`
  passes red, because ST15 already maps BUSY to `RESOURCE_EXHAUSTED` in the service. The class's reworked tests
  hold existing behaviour and pass too; only the turn-key scenario is red.
- **RL04 (GU06):** `ClassifierTurn` also stops before recording a period once its thread is interrupted, not only
  before a filing; no scenario covers the period case.
- **RL05 (GU05):** hypothesis: an exception thrown by the end handed to `GrpcTurnRunnerAdapter.start()` is not
  caught by the runner and escapes to the worker thread's uncaught-exception handler after the key is freed —
  settled by deciding whether the runner should catch it.
- **RL06 (GU05):** wave verification attempt 1 failed `GrpcTurnRunnerAdapterTest$Close` past-timeout scenario:
  `close()` returned before the interrupted turn's end finished. Re-delegated with the failure.
- **RL07 (RS03):** GS03's preflight failed both happy-path tests of `TurnRunSystemTest`: neither stubs `/mcp`, so
  in the class's fresh context the lazily started MCP client fails `tools/list`, the turn ends `UNAVAILABLE` before
  the provider is called. A test-premise gap, not a production defect; RS03 re-delegated to stub the ledger's MCP
  endpoint in both, as the unhappy test does.
- **RL08 (RS01):** hypothesis: `ExtractIntentsSystemTest.whenProviderAnswersAfterOneSecond_…` has the same gap and
  passes only because an earlier test in the class initialised the shared MCP client — settled by running it alone.
  - Resolved: confirmed at Phase 3 — run alone it ended `TURN_END_UNAVAILABLE`. The test now stubs the ledger's MCP
    endpoint, as RL07 did; alone and with its class (7 of 7) it passes. The task's evidence run measures the suite.
- **RL09 (RS01):** the post-green full suite (629 total, 1 failed) failed `DisplayNameConventionsTest`: six new
  display names reach 120 characters or more, in `CallerTokenInterceptorTest`, `IntentExtractionGrpcServiceTest`,
  `GrpcTurnEndAdapterTest`, `ClassifierTurnTest`, `ExtractIntentsSystemTest` and `TurnRunSystemTest`. Re-delegated
  to the red agents of RU06, RI01–RI03, RS01 and RS03 to shorten them.
- **RL10 (PI01):** placed repo-root ADR 0022 (`docs/adr/0022-…`), `Supersedes: 0017`. ADR 0017's status is not yet
  flipped to `Superseded by ADR 0022`; the ledger plan's repo-root ADR takes the next free number, 0023 or above.

## Suite Runs

- baseline · tree 048cc230856ecbf280af51b7f0d34a3096eab09a · total 569 · skipped 0 · green · paths: ai-connector-service
- stabilization · tree 170a01880dc531bf69a9c26a903926dac9a25643 · total 569 · skipped 25 · green · paths: ai-connector-service

- green · tree 858bb2c6bb8e605726931006718c3f4c2b616dfd · total 629 · skipped 0 · green · paths: ai-connector-service
- refactor · tree 0c97573b7bfa49df261d9dd70bf508aa2fc4b8b8 · total 629 · skipped 0 · green · paths: ai-connector-service
## Stubs

Marker: `stub-intent:`

- `ai-connector-service/src/main/java/bot/finance/ai/application/usecase/AcceptTurnUseCase.java`
- `ai-connector-service/src/main/java/bot/finance/ai/adapter/grpc/UnverifiedCallerClaims.java`
- `ai-connector-service/src/main/java/bot/finance/ai/adapter/grpc/GrpcTurnRunnerAdapter.java`
- `ai-connector-service/src/main/java/bot/finance/ai/adapter/ledger/GrpcTurnEndAdapter.java`
