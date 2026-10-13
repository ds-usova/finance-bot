# Fix: Refuse a Fractional Number Where a Request Body Expects an Integer

**Format:** 2
**Affected Module:** `ledger-service`
**Bug:** [A fractional number in an integer field is truncated instead of refused](bug.md)

## Steps

| #    | Kind  | What changes                                                        | Touches                  |
|------|-------|---------------------------------------------------------------------|--------------------------|
| FR01 | red   | enable the reproduction and drop the test pinning the truncation    | `ExpensesControllerTest` |
| FG01 | green | switch off Jackson's float-to-integer coercion for the service      | `application.yaml`       |

- [x] FR01 · red · enable the fractional-number reproduction, and delete the test that asserts the truncation
  - test-files:
    - `ledger-service/src/test/java/bot/finance/adapter/web/ExpensesControllerTest.java`
  - reproduces: a fractional number in an integer field of a request body — `1.5` among the ids of an acceptance
    or a discard, `42.5` as a category change's value — is truncated and reaches the port instead of being
    refused with 400
  - runs: `ExpensesControllerTest$Validation#whenAnIntegerFieldCarriesAFractionalNumber_thenResponseIs400AndNoPortIsCalled`

- [ ] FG01 · green · a request body carrying a fractional number for an integer field is refused with 400
  - files:
    - `ledger-service/src/main/resources/application.yaml`
  - fixes: FR01
  - runs: `ExpensesControllerTest$Validation#whenAnIntegerFieldCarriesAFractionalNumber_thenResponseIs400AndNoPortIsCalled`
