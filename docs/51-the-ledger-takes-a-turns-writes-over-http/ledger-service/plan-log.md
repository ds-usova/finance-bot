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

## Suite Runs

- baseline · tree c119edf4d751a2791c835f67a7119b468397cb5e · total 1367 · skipped 0 · green · paths: ledger-service
- stabilization · tree 36938e0e491ba187099f816420ca8d5cb88fb133 · total 1368 · skipped 1 · green · paths: ledger-service

## Stubs

Marker: `stub-intent:`

- `ledger-service/src/main/java/bot/finance/adapter/logging/TurnCallLog.java`
- `ledger-service/src/main/java/bot/finance/adapter/aiconnector/LedgerTurnProtoMapper.java`
- `ledger-service/src/main/java/bot/finance/adapter/aiconnector/LedgerTurnGrpcService.java`
- `ledger-service/src/main/java/bot/finance/adapter/security/TurnOperationsSecurityConfiguration.java`
