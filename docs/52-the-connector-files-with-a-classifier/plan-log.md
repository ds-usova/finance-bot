# Plan Log: The Connector Files a Message's Spending with a Classifier

## Review Findings

- **RF01:** ST16 built `ClassifierTurn` with a `Clock` that only the memory-gated `MemoryConfiguration` declares.
  - Resolution: decision
  - Action: resolved — the `Clock` bean moves to `UseCaseConfiguration`, unconditionally (ST16); the use cases are
    its only readers besides the memory beans, which register with it on.

- **RF02:** ST20's new `request(...)` overload erased to the same signature as an existing one.
  - Resolution: mechanical
  - Action: applied — renamed `requestWithCatalogue(...)`.

- **RF03:** Red scenarios already met by stabilization: RU01's and RU02's copy checks, RU05's null merchant, RU06's
  run-with-examples.
  - Resolution: mechanical
  - Action: applied — dropped those scenarios.

- **RF04:** RU04's only scenario was met by ST06's null refusal.
  - Resolution: decision
  - Action: resolved — ST06 leaves the null check as a `TODO`, so RU04 stays red.

- **RF05:** RI06 duplicated RU05's merchant mapping.
  - Resolution: decision
  - Action: resolved — kept: `JdbcMessageMemoryAdapterTest` is the only test reading a stored merchant back through
    `findExamples()` against the database.

- **RF06:** Green `after:` markers missed `ExpenseToFile` and `ExampleExpense` used as real values.
  - Resolution: mechanical
  - Action: applied — GU06, GU08, GU09 gained GU03/GU04 as needed.

- **RF07:** `CategoryChoice` validated itself with no Red step.
  - Resolution: decision
  - Action: resolved — ST02 leaves its checks as a `TODO`; RU13/GU13 added.

- **RF08:** ST15 built the channel from a raw target, unlike the named-channel precedent in
  `AiConnectorChannelConfiguration`.
  - Resolution: mechanical
  - Action: applied — named channel `ledger-turn`, its target reading `ledger.turn.target`.

- **RF09:** ST13 and ST15 carried "unverified" bullets.
  - Resolution: mechanical
  - Action: applied — both cut.

- **RF10:** RU08's DN01 and DN02 scenarios could not tell the two rules apart with a one-category catch-all, and
  its read-out-examples half could not fail.
  - Resolution: mechanical
  - Action: applied — both use a catch-all listing `Gifts` then `Uncategorized Expenses`; the read-out half dropped.

- **RF11:** AC03's and AC04's concrete values were missing.
  - Resolution: mechanical
  - Action: applied — RU08 and RI02 carry them.

- **RF12:** RS01 had two error paths at system level.
  - Resolution: decision
  - Action: resolved — the 503 path dropped; the module's testing conventions take one representative error path
    per RPC.

- **RF13:** OQ01, the classifier's wire shape, is open, and RU09, RI03, ST14 and ST22 depend on it.
  - Resolution: decision
  - Action: answered — "Allow docs.typesafe.ai"; the domain is on the fetch allow list (OQ01).

- **RF14:** Architecture Decisions omitted the ungated meters and `ExtractionProperties` in `adapter/config`.
  - Resolution: mechanical
  - Action: applied — added a subsection covering the mode, the clock and the meters.

- **RF15:** ST11 did not update `adapter/metrics/package-info.java`.
  - Resolution: mechanical
  - Action: applied — added to ST11.

- **RF16:** No step carried the caller token onto the ledger calls.
  - Resolution: decision
  - Action: resolved — `CallerTokenLedgerTurnInterceptor`, as `CallerTokenMcpRequestCustomizer` does for MCP (ST28,
    RU14, GU14, an Architecture Decisions subsection); the inbound deadline travels on the gRPC context unaided.

- **RF17:** A read-out expense with no merchant had no scenario.
  - Resolution: mechanical
  - Action: applied — added to RI02 and RU08.

- **RF18:** RU08's out-of-bounds scenario asserted only the filing, and omitted a null confidence and a bad position.
  - Resolution: mechanical
  - Action: applied — both added, and the failure meter and WARN asserted.

- **RF19:** An empty or differently-cased `EXTRACTION_MODE`, `0`/`1` as a minimum confidence, and a timeout of zero
  or less have no stated outcome.
  - Resolution: decision
  - Action: answered — "Strict": empty or wrong case refused naming `EXTRACTION_MODE`, a timeout of zero or less
    refused, `0` and `1` accepted; written to the spec as DN07, and to RU10, RU11, RU12.

- **RF20:** A grouping or category repeated in the catalogue, or groupings that disagree with it, are not refused.
  - Resolution: decision
  - Action: answered — "Refuse, both modes"; written to the spec as DN08 and AC17, and to RU01, RU02 and RI01.

- **RF21:** AC09 has no system scenario once RS01 is trimmed.
  - Resolution: mechanical
  - Action: applied — none needed; RU08's and RI02's failure scenarios stay.

## Run Log

- **RL01 (ST16):** widened: moving the `Clock` out of `MemoryConfiguration` broke the context of the three Jdbc
  adapter tests that imported it for the clock; `PersistenceAdapterTest` now imports a test `Clock`, and the three
  tests drop the `MemoryConfiguration` import. Added to ST16's `writes:`.
- **RL02 (ST15):** widened: `application.yaml` gains `spring.grpc.client.channel.ledger-turn.target:
  ${ledger.turn.target}`, which ST15 names and its `writes:` omitted. Added to ST15's `writes:`.
- **RL03 (ST22):** the stabilize agent had no web tool and guessed the classifier answer envelope as `questions`.
  docs.typesafe.ai (quickstart, primitives/choice) gives: `Authorization: Bearer <API_KEY>`; request `{model,
  state, questions: {<id>: {type: "choice", instructions, criteria: {<option>: <description|null>}}}}`; answer
  `{model, answers: {<id>: {type: "choice", choice, confidence, probabilities: {<option>: p}}}, usage}`.
  `SystemOneFixtures` re-delegated to build the documented envelope.
- **RL04 (RU08):** expected pass: `ClassifierTurnTest.Mode.whenModeRead_thenItIsClassifierMode` — ST10's stub
  already answers the classifier mode.
- **RL05 (RU11):** expected pass: `TypesafeClassifierPropertiesTest.Binding.whenMinConfidenceIsExactlyZeroOrOne_thenPropertiesBind`
  — the record binds without checks until the green step; the test asserts that 0 and 1 are not refused.
- **RL06 (RI01):** expected pass: `IntentExtractionGrpcServiceTest` happy path
  `whenRequestCarriesCatalogueOfTwoGroupings_thenCommandHoldsEntriesInOrderWithCategories` — ST05 already maps
  each request entry into the command, so the plan's happy-path scenario was met by stabilization (plan defect of
  the RF03 kind; kept as a regression guard).
- **RL07 (RI02):** left: the timeout half of "the provider does not answer" — the read-out has no timeout setting
  of its own; written as a connection reset instead.
- **RL08 (RU08):** hypothesis: a classifier answer's position is the 0-based index into the usable expenses sent,
  not into the whole read-out — settled by the green step against the-two-calls.md.
- **RL09 (RU08):** hypothesis: the log assertions (reason keywords `description`/`amount`/`currency`, refusals
  matched by the expense description or period day, the no-identity WARN lacking `null`) match the turn's wording —
  settled by the green step.
- **RL10 (RU01):** hypothesis: the null category list fails red with an NPE from `List.copyOf` rather than on the
  exception type — settled by the green step.
- **RL11 (RI04):** hypothesis: the 900 ms bound in the inbound-deadline test may be flaky on a slow machine —
  settled by the green runs.
- **RL13 (RU08):** expected pass: `ClassifierTurnTest.Run.NothingToClassify.whenNothingIsRead_thenNeitherClassifierNorLedgerCalled`
  — asserts absence (no classifier or ledger call), which the empty stub meets.
- **RL14 (RU14):** a first wave compile failure in `CallerTokenLedgerTurnInterceptorTest` (Mockito generics on
  `newCall`) was re-delegated once and fixed; the no-token test now also verifies `next.newCall` is never invoked.
- **RL15 (RS03, RS01):** hypothesis: a retried `UNAVAILABLE` filing would break RS01's one-call count, and RS02's
  messages may name the property keys rather than the variables — settled by the system green steps.
- **RL12 (RI03, RU09):** hypothesis: question keys are `"0"`, `"1"`, … and the returned positions match them —
  settled by the green steps.

## Suite Runs

- baseline · tree 028a66520c771d7e2411fbfaa1d5c3907576d4af · total 403 · skipped 0 · green · paths: ai-connector-service
- stabilization · tree c80bf22a310701db18d9eaed1c361b697f6bd16f · total 407 · skipped 0 · green · paths: ai-connector-service

## Stubs

Marker: `stub-intent:`

- `ai-connector-service/src/main/java/bot/finance/ai/application/usecase/ClassifierTurn.java`
- `ai-connector-service/src/main/java/bot/finance/ai/adapter/metrics/MicrometerTurnMeters.java`
- `ai-connector-service/src/main/java/bot/finance/ai/adapter/ai/AiSpendingReadOutAdapter.java`
- `ai-connector-service/src/main/java/bot/finance/ai/adapter/typesafe/TypesafeCategoryClassifierAdapter.java`
- `ai-connector-service/src/main/java/bot/finance/ai/adapter/typesafe/SystemOneRequestMapper.java`
- `ai-connector-service/src/main/java/bot/finance/ai/adapter/ledger/GrpcLedgerTurnAdapter.java`
- `ai-connector-service/src/main/java/bot/finance/ai/adapter/ledger/CallerTokenLedgerTurnInterceptor.java`
