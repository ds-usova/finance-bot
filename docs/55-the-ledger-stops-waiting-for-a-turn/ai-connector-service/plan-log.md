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

## Suite Runs

- baseline · tree 048cc230856ecbf280af51b7f0d34a3096eab09a · total 569 · skipped 0 · green · paths: ai-connector-service
- stabilization · tree 170a01880dc531bf69a9c26a903926dac9a25643 · total 569 · skipped 25 · green · paths: ai-connector-service

## Stubs

Marker: `stub-intent:`

- `ai-connector-service/src/main/java/bot/finance/ai/application/usecase/AcceptTurnUseCase.java`
- `ai-connector-service/src/main/java/bot/finance/ai/adapter/grpc/UnverifiedCallerClaims.java`
- `ai-connector-service/src/main/java/bot/finance/ai/adapter/grpc/GrpcTurnRunnerAdapter.java`
- `ai-connector-service/src/main/java/bot/finance/ai/adapter/ledger/GrpcTurnEndAdapter.java`
