# `ledger-service` steps

**Affected Module:** `ledger-service`
**Rework:** [An entry is refiled by its id alone](../rework.md)

## Steps

- [x] R02 · tests · every test R01 disabled is re-enabled in its id-alone shape: the system test's unhappy path patches an id that names no entry while a stored one stays untouched; the adapter test refiles a pending proposal by its id and finds it refiled and still pending; the controller's two happy-path tests become one asserting the command (caller, id, category) and the answered entry, and its path-violation matrix keeps only the id rows; the mapper's two tests become one asserting caller, id and category; the command test's all-fields case drops its status assertion and the `status is absent` case is deleted; the use case's PENDING case is deleted, its RECORDED sibling proving the one call
  - test-files:
    - `ledger-service/src/test/java/bot/finance/system/ChangeExpenseCategorySystemTest.java`
    - `ledger-service/src/test/java/bot/finance/adapter/persistence/ExpenseRepositoryAdapterTest.java`
    - `ledger-service/src/test/java/bot/finance/adapter/web/ExpensesControllerTest.java`
    - `ledger-service/src/test/java/bot/finance/adapter/web/ExpenseWebMapperTest.java`
    - `ledger-service/src/test/java/bot/finance/application/dto/ChangeExpenseCategoryCommandTest.java`
    - `ledger-service/src/test/java/bot/finance/application/usecase/ChangeExpenseCategoryUseCaseTest.java`
  - survives: an id naming no entry of theirs answers 404 naming the entry, and a stored entry keeps its category · `ChangeExpenseCategorySystemTest` against the wired application
  - survives: a pending proposal named by its id is refiled and stays pending · `ExpenseRepositoryAdapterTest` against the containerized database
  - survives: a patched entry answers 200 in listing shape, and the port receives the caller, the id and the category · `ExpensesControllerTest` on the MockMvc slice with the port mocked
  - survives: an id segment of 0 or not a number answers 400 and the port is never called · `ExpensesControllerTest` on the MockMvc slice with the port mocked
  - survives: a document replacing `/categoryId` maps to a command carrying the caller, the id and the category · `ExpenseWebMapperTest`, plain JUnit
  - survives: a command within bounds carries its fields unchanged · `ChangeExpenseCategoryCommandTest`, plain JUnit
  - survives: the use case calls `refile` once with the caller, the id, the category and the clock's instant, and answers the entry · `ChangeExpenseCategoryUseCaseTest` with the ports mocked
  - measures: tests skipped in this module 10 -> 0
  - needs: R01
