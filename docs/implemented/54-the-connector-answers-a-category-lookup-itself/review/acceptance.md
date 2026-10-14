docs/54-the-connector-answers-a-category-lookup-itself · spec.md: 12 scenarios

| ID   | Verdict | Evidence |
|------|---------|----------|
| AC01 | covered | RU01 `CategoryLookupToolCallbackTest` (ai-connector-service/src/test/java/bot/finance/ai/adapter/ai/CategoryLookupToolCallbackTest.java) · RI02 `AiExpenseRecordingAdapterTest` (ai-connector-service/src/test/java/bot/finance/ai/adapter/ai/AiExpenseRecordingAdapterTest.java) · RS01 `ExtractIntentsSystemTest` (ai-connector-service/src/test/java/bot/finance/ai/system/ExtractIntentsSystemTest.java) |
| AC02 | covered | RU01 `CategoryLookupToolCallbackTest` (ai-connector-service/src/test/java/bot/finance/ai/adapter/ai/CategoryLookupToolCallbackTest.java) · RI02 `AiExpenseRecordingAdapterTest` (ai-connector-service/src/test/java/bot/finance/ai/adapter/ai/AiExpenseRecordingAdapterTest.java) |
| AC03 | covered | RU01 `CategoryLookupToolCallbackTest` (ai-connector-service/src/test/java/bot/finance/ai/adapter/ai/CategoryLookupToolCallbackTest.java) |
| AC04 | covered | RU01 `CategoryLookupToolCallbackTest` (ai-connector-service/src/test/java/bot/finance/ai/adapter/ai/CategoryLookupToolCallbackTest.java) |
| AC05 | covered | RU01 `CategoryLookupToolCallbackTest` (ai-connector-service/src/test/java/bot/finance/ai/adapter/ai/CategoryLookupToolCallbackTest.java) |
| AC06 | covered | RI02 `AiExpenseRecordingAdapterTest` (ai-connector-service/src/test/java/bot/finance/ai/adapter/ai/AiExpenseRecordingAdapterTest.java) |
| AC07 | covered | RI02 `AiExpenseRecordingAdapterTest` (ai-connector-service/src/test/java/bot/finance/ai/adapter/ai/AiExpenseRecordingAdapterTest.java) · RS01 `ExtractIntentsSystemTest` (ai-connector-service/src/test/java/bot/finance/ai/system/ExtractIntentsSystemTest.java) |
| AC08 | held    | AC08 is held by the existing `AiExpenseRecordingAdapterTest` test that drives `summarize_spending` to the ledger |
| AC09 | held    | AC09 is held by ST02, which builds the prompt from the catalogue's groupings alone, and the existing `AiExpenseRecordingAdapterTest` test naming every grouping in the user message. |
| AC11 | covered | RU01 `CategoryLookupToolCallbackTest` (ai-connector-service/src/test/java/bot/finance/ai/adapter/ai/CategoryLookupToolCallbackTest.java) · RI01 `MicrometerTurnMetersTest` (ai-connector-service/src/test/java/bot/finance/ai/adapter/metrics/MicrometerTurnMetersTest.java) · RS01 `ExtractIntentsSystemTest` (ai-connector-service/src/test/java/bot/finance/ai/system/ExtractIntentsSystemTest.java) |
| AC12 | held    | AC12 and AC13 are held by ST01 and the runner's existing oracle, which checks only the tools a case names under `mcpCalls` |
| AC13 | held    | AC12 and AC13 are held by ST01 and the runner's existing oracle, which checks only the tools a case names under `mcpCalls` |

every scenario is covered or held
