docs/49-discard-chosen-proposals · spec.md: 13 scenarios

| ID   | Verdict | Evidence |
|------|---------|----------|
| AC01 | covered | RU01 `DiscardExpensesUseCaseTest` (ledger-service/src/test/java/bot/finance/application/usecase/DiscardExpensesUseCaseTest.java) · RI01 `ExpenseRepositoryAdapterTest` (ledger-service/src/test/java/bot/finance/adapter/persistence/ExpenseRepositoryAdapterTest.java) · RI03 `ExpensesControllerTest` (ledger-service/src/test/java/bot/finance/adapter/web/ExpensesControllerTest.java) · RS01 `DiscardExpensesSystemTest` (ledger-service/src/test/java/bot/finance/system/DiscardExpensesSystemTest.java) |
| AC02 | covered | RI02 `ExpenseRepositoryAdapterEventsTest` (ledger-service/src/test/java/bot/finance/adapter/persistence/ExpenseRepositoryAdapterEventsTest.java) |
| AC03 | covered | RU01 `DiscardExpensesUseCaseTest` (ledger-service/src/test/java/bot/finance/application/usecase/DiscardExpensesUseCaseTest.java) · RI01 `ExpenseRepositoryAdapterTest` (ledger-service/src/test/java/bot/finance/adapter/persistence/ExpenseRepositoryAdapterTest.java) · RI02 `ExpenseRepositoryAdapterEventsTest` (ledger-service/src/test/java/bot/finance/adapter/persistence/ExpenseRepositoryAdapterEventsTest.java) |
| AC04 | covered | RI01 `ExpenseRepositoryAdapterTest` (ledger-service/src/test/java/bot/finance/adapter/persistence/ExpenseRepositoryAdapterTest.java) |
| AC05 | covered | RI01 `ExpenseRepositoryAdapterTest` (ledger-service/src/test/java/bot/finance/adapter/persistence/ExpenseRepositoryAdapterTest.java) · RI02 `ExpenseRepositoryAdapterEventsTest` (ledger-service/src/test/java/bot/finance/adapter/persistence/ExpenseRepositoryAdapterEventsTest.java) |
| AC06 | covered | RI03 `ExpensesControllerTest` (ledger-service/src/test/java/bot/finance/adapter/web/ExpensesControllerTest.java) |
| AC07 | covered | RI03 `ExpensesControllerTest` (ledger-service/src/test/java/bot/finance/adapter/web/ExpensesControllerTest.java) |
| AC08 | covered | RI03 `ExpensesControllerTest` (ledger-service/src/test/java/bot/finance/adapter/web/ExpensesControllerTest.java) |
| AC09 | covered | RU01 `DiscardExpensesUseCaseTest` (ledger-service/src/test/java/bot/finance/application/usecase/DiscardExpensesUseCaseTest.java) · RS01 `DiscardExpensesSystemTest` (ledger-service/src/test/java/bot/finance/system/DiscardExpensesSystemTest.java) |
| AC10 | covered | RU01 `DiscardExpensesUseCaseTest` (ledger-service/src/test/java/bot/finance/application/usecase/DiscardExpensesUseCaseTest.java) · RI03 `ExpensesControllerTest` (ledger-service/src/test/java/bot/finance/adapter/web/ExpensesControllerTest.java) |
| AC11 | covered | RU01 `DiscardExpensesUseCaseTest` (ledger-service/src/test/java/bot/finance/application/usecase/DiscardExpensesUseCaseTest.java) · RS01 `DiscardExpensesSystemTest` (ledger-service/src/test/java/bot/finance/system/DiscardExpensesSystemTest.java) |
| AC12 | held    | AC12 is held by `ClearEmptiedReportsUseCaseTest`'s case "when the one message still has a pending proposal". |
| AC13 | held    | AC13 is held by `ResolveProposalsUseCaseTest`'s case "when the resolution and the count both answer 0". |

every scenario is covered or held
