# Bug: A Fractional Number in an Integer Field Is Truncated Instead of Refused

**Format:** 2
**Affected Modules:** `ledger-service`
**Source:** the request, 2026-10-13 — found while archiving task 49, whose schema declares `ids` items as `integer`
**Baseline:** `1d9413f0` · `ledger-service`: 1366 total, 1 skipped — the disabled reproduction
`ExpensesControllerTest$Validation#whenAnIntegerFieldCarriesAFractionalNumber_thenResponseIs400AndNoPortIsCalled`;
Docker running

## What happens

- **Given** a signed-in person with a session and a CSRF token
- **When** they post `{"ids":[1.5]}` to `/api/v1/expenses/acceptances` or `/api/v1/expenses/discards`, or patch
  an entry with `[{"op":"replace","path":"/categoryId","value":42.5}]`
- **Then** the request is refused with 400 and no write is attempted, since `openapi/ledger-api.yaml` declares
  each of those fields `type: integer`
- **Actual** the number is truncated and the write runs: id `1` is accepted or discarded, and the entry is
  refiled under category `42`

## How it reproduces

`tools/agent-test/agent-test.sh --module ledger-service --tests "bot.finance.adapter.web.ExpensesControllerTest"`,
with the reproduction enabled, failed 2 in 2 runs, each time on all three cases:

```
- ExpensesControllerTest$Validation > "an acceptance's id"
    type: org.mockito.exceptions.verification.NeverWantedButInvoked
    ... But invoked here: -> at bot.finance.adapter.web.ExpensesController.acceptExpenses(ExpensesController.java:57)
    with arguments: [AcceptExpensesCommand[userId=AuthenticatedUserId[userId=778899001], ids=ProposalIds[ids=[1]]]]

- ExpensesControllerTest$Validation > "a discard's id"
    ... But invoked here: -> at bot.finance.adapter.web.ExpensesController.discardExpenses(ExpensesController.java:64)
    with arguments: [DiscardExpensesCommand[userId=AuthenticatedUserId[userId=778899001], ids=ProposalIds[ids=[1]]]]

- ExpensesControllerTest$Validation > "a category change's value"
    ... But invoked here: -> at bot.finance.adapter.web.ExpensesController.changeExpenseCategory(ExpensesController.java:73)
    with arguments: [ChangeExpenseCategoryCommand[userId=AuthenticatedUserId[userId=778899001], entryId=7, categoryId=42]]
```

An entry-point test is the cheapest type that shows it: the truncation happens in the framework's body binding,
which a unit test of the controller or the mapper never reaches.

## Why it happens

1. Each of the three operations binds its JSON body through Spring MVC's Jackson message converter into a
   generated type whose field is a `Long` — `AcceptExpensesRequest.ids`, `DiscardExpensesRequest.ids`,
   `CategoryPatchOperation.value`. The reproduction shows the port receiving `1` and `42`, so binding succeeded.
2. Jackson's `DeserializationFeature.ACCEPT_FLOAT_AS_INT` is on unless switched off, and coerces a JSON float into
   an integer type by truncating it. The feature exists in the `tools.jackson.core:jackson-databind` 3.1.5 the
   module runs on (`tools/inspect-jar/inspect-jar.sh --javap jackson-databind-3.1.5
   tools.jackson.databind.DeserializationFeature`).
3. Nothing in `ledger-service` switches it off: `src/main/resources/application.yaml` sets no `spring.jackson`
   key, and no class builds or customizes the mapper MVC uses. Spring Boot's `JacksonProperties` takes a
   `deserialization` map keyed by that enum (`spring-boot-jackson` 4.1.1), so
   `spring.jackson.deserialization.accept-float-as-int` is where it is switched off — unverified until the green
   run.

These three fields are every integer field a request body of this service carries: `WrittenMessageFiling` and
`PreferencesUpdate` hold strings, `TelegramLoginPayload` is an untyped map, and every MCP tool parameter is a
`String`.

## What the fix must not break

- Whole-number ids and category ids still bind and reach their port — every happy-path case in
  `ExpensesControllerTest` and the acceptance, discard and category-change system tests.
- `ExpensesControllerTest#whenTheDiscardCarriesAFractionalId_thenItBindsTruncatedAndReachesThePort` pins the
  truncation as task 49 observed it. It protected nothing a caller relies on; the reproduction replaces it.
- The sign-in payload keeps every value as sent: it binds into a map, where no integer target exists.
- The MCP tools take only `String` parameters and use the shared `JsonMapper` only to write their answers.
- The change-capture and outbox renderers build their own `ObjectMapper`, untouched by the setting.
