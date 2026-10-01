# Fix: a stored code the service cannot read is answered as nothing chosen

**Affected Module:** `ledger-service`
**Bug:** [a stored currency code the service cannot read is answered as a store outage](bug.md)
**In flight:**

## Steps

| #   | Kind      | What changes                                                     | Touches                             |
|-----|-----------|------------------------------------------------------------------|-------------------------------------|
| S01 | stabilize | Give the adapter's slice the logger factory bean it will need    | `UserPreferenceRepositoryAdapterTest` |
| R01 | red       | Enable the reproduction that an unreadable stored code is nothing | `UserPreferenceRepositoryAdapterTest` |
| G01 | green     | Answer nothing for a stored code the domain refuses, and warn    | `UserPreferenceRepositoryAdapter`   |

- [x] S01 · stabilize · import `Slf4jLoggerFactory` into the adapter's slice, so the adapter can take a
  `LoggerFactory` the way `LedgerEventOutbox` and `ReplicationCatalogue` already do
  - test-files:
    - `ledger-service/src/test/java/bot/finance/adapter/persistence/UserPreferenceRepositoryAdapterTest.java`

- [x] R01 · red · enable the disabled reproduction, so a row holding a code the JDK does not recognise is asked
  for and the answer is asserted to be nothing
  - test-files:
    - `ledger-service/src/test/java/bot/finance/adapter/persistence/UserPreferenceRepositoryAdapterTest.java`
  - reproduces: a person whose stored code this service cannot read is told the store is unavailable, instead of
    being answered no currency at all
  - runs: `UserPreferenceRepositoryAdapterTest$FindDefaultCurrency#whenRowHoldsUnrecognisedCode_thenNothingAnswered`
  - needs: S01

- [x] G01 · green · read the row inside the `try` and build the `CurrencyCode` outside it, answering nothing and
  warning where the domain refuses the stored code, so only the query is guarded as a store failure
  - files:
    - `ledger-service/src/main/java/bot/finance/adapter/persistence/UserPreferenceRepositoryAdapter.java`
    - `ledger-service/src/main/java/bot/finance/adapter/persistence/UserPreferenceEntity.java`
    - `ledger-service/src/main/java/bot/finance/application/port/UserPreferenceRepository.java`
  - fixes: R01
  - runs: `UserPreferenceRepositoryAdapterTest$FindDefaultCurrency#whenRowHoldsUnrecognisedCode_thenNothingAnswered`
  - docs: `ledger-service/docs/usecases/read-the-preferences.md`

## Attempts
