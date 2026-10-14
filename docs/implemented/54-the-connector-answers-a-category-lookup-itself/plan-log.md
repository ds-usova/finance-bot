# Plan Log: The Connector Answers a Category Lookup Itself

## Review Findings

- **RF01:** RU01's `getToolDefinition()` scenario passed before Green, since ST04 completes the definition.
  - Resolution: mechanical
  - Action: applied — dropped the scenario and `getToolDefinition()` from RU01's `covers:`; RI02 checks the
    definition through the adapter.

- **RF02:** RI02's prompt scenario passed before Green, since ST02 already builds the prompt from the groupings.
  - Resolution: mechanical
  - Action: applied — dropped the scenario; a coverage note holds AC09 by ST02 and the existing prompt test.

- **RF03:** The spec and AC11 say `accepted` where ST03, RI01 and RS01 tag the meter `outcome="answered"`.
  - Resolution: decision
  - Action: resolved — the tag stays `answered`, the word `MicrometerTurnMeters` already uses for a turn's and a
    classifier call's outcome; the spec's "accepted" names the outcome, not the tag value.

- **RF04:** ST05 edits `AiExpenseRecordingAdapter.java` after ST02 without saying so.
  - Resolution: mechanical
  - Action: applied — ST05 carries `after: ST02, ST03`.

- **RF05:** RI02's tool-schema scenario overlapped the existing test asserting the three names and the argument.
  - Resolution: mechanical
  - Action: applied — narrowed to the ledger's real description against the stub's shorter one.

- **RF06:** RU01's `food` scenario ran the `Travel` path; exact matching is only the artifact's rule.
  - Resolution: mechanical
  - Action: applied — kept to pin exact matching, which the artifact's answers table states.

- **RF07:** RU01's refusals did not say the cause must be a `RuntimeException`, which the failure processor needs.
  - Resolution: mechanical
  - Action: applied — ST04 and RU01 name a `RuntimeException` cause carrying the refusal text.

- **RF08:** Architecture Decisions hid the lookup's new dependency on `LedgerToolFailureProcessor`.
  - Resolution: mechanical
  - Action: applied — the diagram and the Placement sentence name it.

- **RF09:** No scenario tests DF05, one turn's catalogue never answering another turn's lookup.
  - Resolution: decision
  - Action: resolved — RI02 gained a two-turn scenario; DF05 is a settled finding, so pinning it is coverage, not
    a new behaviour.

- **RF10:** Arguments the callback cannot read had no settled behaviour.
  - Resolution: decision
  - Action: resolved — refused with the no-grouping text and the turn goes on, as `ledger-mcp.md` says the ledger
    refuses an argument's type; written as DF13, with RU01 scenarios for non-JSON, a non-string and a `null`
    `grouping`.

## Run Log

- **RL01 (RU01):** hypothesis: a lenient JSON reader may coerce `{"grouping":5}` to `"5"` and refuse it as not
  stored rather than with the no-grouping text — settled by running `CategoryLookupToolCallbackTest` against the
  implementation.
- **RL02 (RU01):** the case-differing scenario asserts `no grouping named food is stored for this user`, the
  `Travel` scenario's text; the refusal cause is asserted as exactly `RuntimeException`.
- **RL03 (RI02):** hypothesis: the AC01 and AC07 tests fail red because the ledger stub no longer answers
  `list_categories`, so the turn fails with `ExpenseRecordingFailedException` — settled by the wave run's failure
  messages (the red run showed that exception for both).
- **RL04 (RI02):** the full suite after the unit + integration green batch failed only on
  `DisplayNameConventionsTest.aTestMethodsDisplayNameStaysUnderTheLimit`: four `@DisplayName`s RI02 wrote in
  `AiExpenseRecordingAdapterTest` and one RS01 wrote in `ExtractIntentsSystemTest` exceed 120 characters. The
  focused red and green runs exclude the architecture tests, so the wave runs did not see it. Sent back to the red
  agents to shorten the names; 569 tests, 568 passed, 0 skipped.
- **RL05 (RS01):** the rerun cleared the length rule but RS01's shortened name broke
  `aTestMethodsDisplayNameNamesOneConditionAndOneOutcome` (no `when … - then …` shape); sent back a second time.
- **RL06 (GS01):** `ExtractIntentsSystemTest` passed once the unit and integration green steps landed, so no
  system green agent was spawned; the full suite ran green at 569 tests, 0 skipped.
- **RL07 (GI02):** unrelated failure during the refactor pass: one full-suite run failed 8
  `AiExpenseRecordingAdapterTest` tests after a Java heap `OutOfMemoryError` killed the ledger MCP HTTP client's
  selector thread (`IOException: selector manager closed`); an unchanged rerun passed. Log:
  `ai-connector-service/build/agent-runs/suite-20261014-171054-98780/console.log`. hypothesis: the test JVM's
  heap is near its limit over the full suite — settled by comparing heap use across full-suite runs.
- **RL08 (GI02):** the refactor pass left, outside this plan's diff, `EXAMPLES_HEADING` in
  `AiExpenseRecordingAdapter` and JSON argument strings in `ExtractIntentsSystemTest` and
  `AiExpenseRecordingAdapterTest` still joined with `+` rather than text blocks.
- **RL09 (PI02):** `docs/eval/implementation/demo-plan.md` is git-ignored (`.gitignore:36`), so its edit stays in
  the working tree and reaches no commit.

## Suite Runs

- baseline · tree 3160bbf96b59a6af275ce5d9e98f33d380192065 · total 552 · skipped 0 · green · paths: ai-connector-service
- stabilization · tree e30635e8be46fb3f8e3a6d1761b7018910f1cf6a · total 552 · skipped 0 · green · paths: ai-connector-service

- green-unit-integration · tree 3713773a65e595a5197e09f7c8646d589e8ea60a · total 569 · skipped 0 · red · paths: ai-connector-service
- green · tree e46b788429259be28010a38eb78372e814a24647 · total 569 · skipped 0 · green · paths: ai-connector-service
- refactor · tree 3741080c860ff17f0c4e69a70669aeaf50ac9db0 · total 569 · skipped 0 · green · paths: ai-connector-service
- wrap-up · tree 3741080c860ff17f0c4e69a70669aeaf50ac9db0 · total 569 · skipped 0 · green · paths: ai-connector-service
## Stubs

Marker: `stub-intent:`

- `ai-connector-service/src/main/java/bot/finance/ai/adapter/metrics/MicrometerTurnMeters.java`
- `ai-connector-service/src/main/java/bot/finance/ai/adapter/ai/CategoryLookupToolCallback.java`
