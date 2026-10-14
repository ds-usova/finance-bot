# Plan Log: The Ledger Takes a Turn's Writes over gRPC — `ledger-service`

## Review Findings

- **RF01:** ST04 leaves the catalogue uncopied, which fails the existing test that mutates the list after
  construction.
  - Resolution: decision
  - Action: resolved — `stabilizing.md` disables a test that compiles but would now fail; ST04 disables it naming
    RU01, and RU01 re-enables it.

- **RF02:** ST11 reuses "the tools' validators", but `SecurityConfiguration.validator(...)` is private and the MCP
  decoder fetches a key set.
  - Resolution: decision
  - Action: resolved — `TurnOperationsSecurityConfiguration` shares the package, so ST11 makes `validator(...)`
    package-private and adds `SecurityConfiguration.java` to its `writes:`; the key is the one
    `sessionJwtDecoder` already reads from `TokenSigningKeys`.

- **RF03:** GU02 lacked `after: GU01`.
  - Resolution: mechanical
  - Action: applied — added.

- **RF04:** GI03's tests run the real service, mapper and call line, yet GI03 waits on nothing.
  - Resolution: decision
  - Action: resolved — RI03 asserts only that a credentialed call is answered rather than refused, which holds
    against the stubbed service and the real one alike; GI04 stays after GI03.

- **RF05:** AC04's amount the currency cannot record had no scenario.
  - Resolution: mechanical
  - Action: applied — `12.505` EUR joined RI04's Validation.

- **RF06:** AC11 names a text filed from the web too, but only the Telegram path has a system scenario.
  - Resolution: decision
  - Action: resolved — both turns build their request through the one `ExtractionTurn.request()`, so RS03 and
    RU02 hold for both (`ExtractionTurn.java`).

- **RF07:** The derived `IntentExtractionRequest.categoryGroupings()` named in no `covers:`.
  - Resolution: decision
  - Action: resolved — the converted existing tests assert it (`IntentExtractionRequestTest`, the
    every-component and one-grouping cases).

- **RF08:** RI01, RI02, RS01 and RI04 repeated coverage existing tests or other steps already give.
  - Resolution: mechanical
  - Action: applied — RI01 keeps only the categories and their order, RS01 drops the no-credential case, RI04
    keeps one refusal per branch; RI02 already makes the one assertion that the catalogue crosses the wire.

- **RF09:** ST01 and ST11 carried reasoning, and the stub-marker sentence cited a rule the code-style page lacks.
  - Resolution: mechanical
  - Action: applied — reasoning cut, citation dropped.

- **RF10:** ST13's `McpTokens.wrongIssuerToken` serves only RI03, so it is not shared infrastructure.
  - Resolution: mechanical
  - Action: answered — "Drop ST13". RI03 adds the token itself; ST13's id stays unused.

- **RF11:** The artifact's status table words NOT_FOUND and INTERNAL differently from the tools whose reasons it
  says the operations give.
  - Resolution: decision
  - Action: resolved — `turn-operations.md` says every reason is the one the matching tool gives; its table's
    column paraphrases, so the scenarios keep the tools' text.

- **RF12:** ST08 deleted `McpCallLine` without placing `arguments(...)` and its caller lookups.
  - Resolution: mechanical
  - Action: applied — `arguments(...)` moves to `TurnCallLog`; the lookups are `write()`'s.

- **RF13:** The call-line diagram omitted `TurnCallLog`'s new dependency on `AuthenticatedCaller`.
  - Resolution: mechanical
  - Action: applied — drawn.

- **RF14:** `TurnCallLog` falls outside the conventions' definition of `adapter/logging`.
  - Resolution: decision
  - Action: resolved — the plan's placement stands, since the class writes a log line; ST06 widens the `logging`
    entry in `ledger-service/docs/conventions/architecture.md`.

- **RF15:** `LedgerTurnGrpcService` repeats the tools' refusal classification and reason strings, and
  `LedgerTurnProtoMapper` the tool mapper's amount formatting.
  - Resolution: decision
  - Action: resolved — deduplicating mapping written independently is the refactor pass's first priority
    (`ledger-service/docs/conventions/code-style.md`, Refactoring Conventions).

- **RF16:** ST08 changes `SummarizeSpendingMcpTool`, but no step proves its `summarize_spending` line via MCP.
  - Resolution: decision
  - Action: answered — "Add RI07/GI07". RI07 and GI07 cover `SummarizeSpendingMcpToolTest`'s line.

- **RF17:** RI04 lacked a blank `category`, a blank `merchant`, and the accepted upper bounds of `amount`.
  - Resolution: mechanical
  - Action: applied — all three added.

- **RF18:** RU01 covered only a null entry, not an entry's empty or blank categories.
  - Resolution: mechanical
  - Action: applied — added; ST03's stub now answers each grouping with one category, so the new check refuses
    nothing before RI01 lands.

- **RF19:** The artifact's reflection without a credential, server call metrics and refusal logging had no step.
  - Resolution: decision
  - Action: resolved — `turn-operations.md` states all three, so RI03 gained a reflection scenario, RS01 the
    call timer on the scrape (its name unverified), and ST10's intent the refusal logging.

## Run Log

- **RL01 (RS01):** expected pass — `FileExpenseProposalSystemTest.whenOperationPathIsPostedToHttpPort_thenItIsNotAnsweredAndNothingIsStored`
  passes against the stubs: it asserts the HTTP port does not answer the operation's path.
- **RL02 (RS01):** expected pass — `FileExpenseProposalSystemTest.whenCallHasBeenAnswered_thenScrapeShowsServerCallTimerAsOk`
  passes against the stubs: the server's call timer is the framework's, wired by ST01, and the stub answers `OK`.
- **RL03 (RI03):** expected pass — `TurnOperationsSecurityConfigurationTest`'s health and reflection tests pass
  against the permit-all stub: they assert both services stay open without a credential.
- **RL04 (RI04):** left — the blank `category` case was not written; the step marks it unverified.
- **RL05 (RS03):** unrelated failure —
  `ReceiveTelegramMessageSystemTest.whenSendersStoredPreferenceHoldsEur_thenExtractionRequestCarriesEurAsDefaultCurrency`
  fails with `ConnectException` (`BrowserSessions.csrfToken`) when its class runs before any class that sets
  `RestAssured.port`; the class never sets it itself. Reproduce: run the class alone. Pre-existing order dependence.
- **RL06 (RS01):** hypothesis: the server's call timer renders as a `grpc_server…` line holding
  `FileExpenseProposal` and `OK`, so the scrape assertion is deliberately loose — settled by one scrape after a call.
- **RL07 (RS02):** hypothesis: an unknown subject `Long.MAX_VALUE - 7` reaches the port as NOT_FOUND rather than
  failing on a key or length check — settled by the first green run.
- **RL08 (RI03):** hypothesis: `McpTokens.expiredToken` expires exactly at the 60 s skew and is refused only because
  time passes before verification — settled by running the expired case repeatedly once the check lands.
- **RL09 (RI04):** hypothesis: a refusal's description names the field by its proto name (`currency_code`), and
  `12.505` EUR's `InvalidMoneyException` never says `amount`, so the description must add it — settled by the
  Validation cases going green.
- **RL10 (RI05):** hypothesis: the rejected-line tests read the caller (`userId=`) at `write()` time — settled by
  the MCP tools' tests going green.
- **RL11 (RI03):** expected pass — after one re-delegation to drop a port verification RF04 rules out,
  `TurnOperationsSecurityConfigurationTest.whenALedgerTokenIsPresented_thenTheCallIsAnswered` passes against the
  permit-all stub: it asserts only that the call is not refused.
- **RL12 (RU01):** expected pass — the null-entry case of
  `IntentExtractionRequestTest.whenCatalogueHoldsInvalidEntry_thenThrowsInvalidExtractionRequestException` passes
  red: the existing grouping check already refuses the null grouping a null entry yields.
- **RL13 (RU04):** expected pass — `TurnCallLogTest.whenLineIsDisabled_thenNothingIsLogged` passes against the
  no-op stub: it asserts nothing is logged.
- **RL14 (RI01):** hypothesis: `GroupingRepositoryAdapterTest`'s mocked-store failure test for `findCatalogue`
  stubs `findNonEmptyGroupingNames`, which the real read may stop calling — settled by that test after GI01.
- **RL15 (RU04):** hypothesis: the full-line test relies on the arguments' JSON keeping insertion order — settled
  by `TurnCallLogTest` going green.
- **RL16 (RI01):** plan defect — GI01's catalogue read uses `findCategoryEntriesForUser`, so
  `GroupingRepositoryAdapterTest.whenFindCatalogueHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionWrappingIt`
  stubbed a query nothing calls; RI01 gained an `update:` restubbing it, run by a red re-delegation.
  `CategoryEntityRepository.findNonEmptyGroupingNames` is left unused in production for the refactor pass.
- **RL17 (RU03):** plan defect — RU03's `7200 HUF` assumed a currency with no decimals; HUF records two, so
  `new Money(7200L, HUF)` is `72.00` and `LedgerTurnProtoMapperTest.whenEntryIs7200Huf_thenAmountIsWrittenWithNoExponent`
  failed against GU03's mapper. The scenario now reads `7200 JPY`; the test is re-delegated.
- **RL18 (GI05):** GI05, GI06 and GI07 needed no agent: the tools already wrote through `TurnCallLog` (ST08), and
  their three test classes passed once GU04 landed.
- **RL19 (RU03):** hypothesis: `CreateExpenseProposalMcpTool`'s description still offers "7200 HUF" as an example
  amount, which HUF's two recorded decimals may make misleading — settled by reading the description against
  `CurrencyCode.fractionDigits()` for HUF.
- **RL20 (GI02):** GI02 needed no agent: `AiConnectorIntentExtractionAdapterTest` passed once GU02 mapped the
  catalogue.
- **RL21 (RI04):** test defect — `LedgerTurnGrpcServiceTest.stored(...)` builds a PENDING `Expense` with an empty
  incoming message id, which `Expense.stored` refuses (`InvalidExpenseException`, `Expense.java:62`), so 8 happy-path
  tests failed before reaching the service GI04 implemented. Re-delegated to a red agent to give the fixture a
  reference.
- **RL22 (GI04):** hypothesis: `LedgerTurnGrpcService` picks the refused field of an `InvalidMoneyException` by
  whether its message mentions "currency code" — settled by reading it in the refactor pass.
- **RL23 (RI05):** four new display names broke `DisplayNameConventionsTest`'s 120-character limit
  (`IntentProtoMapperTest`, `IntentExtractionRequestTest` twice, `CreateExpenseProposalMcpToolTest`); two red
  re-delegations shortened them, and the rule passes.
- **RL24 (GS03):** ticked without an agent. In the focused run of `ReceiveTelegramMessageSystemTest`, the
  scenario's test passed and only the unrelated EUR-preference test of RL05 failed; the whole class passed in the
  full suite. GS01 and GS02 passed their focused runs without an agent.

## Suite Runs

- baseline · tree c119edf4d751a2791c835f67a7119b468397cb5e · total 1367 · skipped 0 · green · paths: ledger-service
- stabilization · tree 36938e0e491ba187099f816420ca8d5cb88fb133 · total 1368 · skipped 1 · green · paths: ledger-service

- green · tree bee1fad627160f9e63b88c1d556ff85dd7fa96b5 · total 1446 · skipped 0 · green · paths: ledger-service
## Stubs

Marker: `stub-intent:`

- `ledger-service/src/main/java/bot/finance/adapter/logging/TurnCallLog.java`
- `ledger-service/src/main/java/bot/finance/adapter/aiconnector/LedgerTurnProtoMapper.java`
- `ledger-service/src/main/java/bot/finance/adapter/aiconnector/LedgerTurnGrpcService.java`
- `ledger-service/src/main/java/bot/finance/adapter/security/TurnOperationsSecurityConfiguration.java`
