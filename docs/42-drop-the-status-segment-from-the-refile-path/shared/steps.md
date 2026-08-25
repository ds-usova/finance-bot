# Shared steps

**Affected Modules:** `ledger-service`, `web-app`
**Rework:** [An entry is refiled by its id alone](../rework.md)

## Steps

- [x] R01 · stabilize · `PATCH /api/v1/expenses/{status}/{id}` becomes `PATCH /api/v1/expenses/{id}`, and `status` leaves every signature between the path and the SQL: the controller, `toChangeExpenseCategoryCommand`, `ChangeExpenseCategoryCommand`, `ExpenseRepository.refile`, `ExpenseEntityRepository.refile` (the `:status` parameter and its `AND status = :status`), and `SpendingRowProjection.toExpenseEntry()`, which reads its own `status` field. The adapter derives the fact's type and the entry's status from the returned row (`ExpenseStatus.valueOf(row.status())`); the use case logs `entry.status()` where it logged `command.status()`. The mapper's `ExpenseStatus.valueOf(status)` block and its `status must be PENDING or RECORDED` refusal go with the parameter they parsed. `web-app`'s types are regenerated with `npm --prefix web-app run generate:api`, and `changeCategory` sends `${EXPENSES_PATH}/${entry.id}`. Ledger test call sites drop the argument; `WebSessionSystemTest` patches `/api/v1/expenses/<id>`; the `.http` file loses its status segment and its second request
  - files:
    - `openapi/ledger-api.yaml`
    - `openapi/paths/expense-category.yaml`
    - `ledger-service/src/main/java/bot/finance/adapter/web/ExpensesController.java`
    - `ledger-service/src/main/java/bot/finance/adapter/web/ExpenseWebMapper.java`
    - `ledger-service/src/main/java/bot/finance/application/dto/ChangeExpenseCategoryCommand.java`
    - `ledger-service/src/main/java/bot/finance/application/usecase/ChangeExpenseCategoryUseCase.java`
    - `ledger-service/src/main/java/bot/finance/application/port/ExpenseRepository.java`
    - `ledger-service/src/main/java/bot/finance/adapter/persistence/ExpenseRepositoryAdapter.java`
    - `ledger-service/src/main/java/bot/finance/adapter/persistence/ExpenseEntityRepository.java`
    - `ledger-service/src/main/java/bot/finance/adapter/persistence/SpendingRowProjection.java`
    - `ledger-service/docs/requests/expenses/change-expense-category.http`
    - `ledger-service/src/main/java/bot/finance/adapter/security/SecurityConfiguration.java`
    - `web-app/src/api/generated/ledger-api.d.ts`
    - `web-app/src/api/expenses.ts`
  - test-files:
    - `ledger-service/src/test/java/bot/finance/adapter/web/ExpensesControllerTest.java`
    - `ledger-service/src/test/java/bot/finance/adapter/web/ExpenseWebMapperTest.java`
    - `ledger-service/src/test/java/bot/finance/application/dto/ChangeExpenseCategoryCommandTest.java`
    - `ledger-service/src/test/java/bot/finance/application/usecase/ChangeExpenseCategoryUseCaseTest.java`
    - `ledger-service/src/test/java/bot/finance/adapter/persistence/ExpenseRepositoryAdapterTest.java`
    - `ledger-service/src/test/java/bot/finance/adapter/persistence/ExpenseRepositoryAdapterEventsTest.java`
    - `ledger-service/src/test/java/bot/finance/system/ChangeExpenseCategorySystemTest.java`
    - `ledger-service/src/test/java/bot/finance/system/WebSessionSystemTest.java`
    - `ledger-service/src/test/java/bot/finance/common/fixtures/ExpensePatches.java`
    - `web-app/src/api/expenses.test.ts`
  - disables: `ChangeExpenseCategorySystemTest$UnhappyPath#whenAnIdNamesNoEntryOfTheirsUnderThatStatus_then404NamingTheEntry` — cleared by R02
  - disables: `ExpenseRepositoryAdapterTest$Refile#whenIdNamesCallersPendingProposal_thenAnswerIsEmptyAndProposalRowUntouched` — cleared by R02
  - disables: `ChangeExpenseCategoryCommandTest$ChangeExpenseCategoryCommandConstructor#whenEveryFieldIsWithinItsBounds_thenTheRecordCarriesAllFourUnchanged` — cleared by R02
  - disables: `ChangeExpenseCategoryCommandTest$ChangeExpenseCategoryCommandConstructor#whenStatusIsAbsent_thenThrowsInvalidExpenseCategoryChangeExceptionNamingStatus` — cleared by R02
  - disables: `ExpenseWebMapperTest#whenDocumentReplacesCategoryIdUnderRecorded_thenCommandCarriesCallerRecordedIdAndCategory` — cleared by R02
  - disables: `ExpenseWebMapperTest#whenSameDocumentIsGivenUnderPending_thenCommandCarriesPendingStatus` — cleared by R02
  - disables: `ExpensesControllerTest$HappyPath#whenARecordedEntryIsPatchedToANewCategoryId_thenPortIsCalledAndResponseIs200WithTheEntry` — cleared by R02
  - disables: `ExpensesControllerTest$HappyPath#whenAPendingEntryIsPatchedToANewCategoryId_thenCommandCarriesPendingAndResponseStatusIsPending` — cleared by R02
  - disables: `ExpensesControllerTest$Validation#whenStatusOrIdPathSegmentIsRefused_thenResponseIs400AndPortNeverCalled` — cleared by R02
  - disables: `ChangeExpenseCategoryUseCaseTest$Change#whenCommandNamesPendingAndStoreAnswersRefiledEntry_thenRefileCalledOnceWithPendingAndEntryReturned` — cleared by R02
  - disables: `expenses.test.ts` › `patches the entry by its status and id, replacing /categoryId, with the CSRF header and the cookies` — cleared by R03
  - disables: `expenses.test.ts` › `carries the PENDING status in the path, so the two statuses are never confused for one id` — cleared by R03
  - docs: `ledger-service/docs/usecases/change-an-expense-category.md`
  - docs: `ledger-service/docs/domain/expense-status.md`
  - docs: `web-app/docs/contracts/out/ledger-browse-api.md`
