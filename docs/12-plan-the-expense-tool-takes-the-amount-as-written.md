# Plan: The Expense Tool Takes the Amount as the User Wrote It

**Affected Modules:** `ledger-service`, `ai-connector-service`
**Design:** [the expense tool takes the amount as written](12-design-the-expense-tool-takes-the-amount-as-written.md)

## Step-by-Step Implementation Map (To-Do List)

### Stabilization

#### Interface-First / Build Stabilization

**Interface & Signature Sync**

- [x] ST01 · `ledger-service` — add the new static factory `Money.ofMajorUnits(BigDecimal amount, CurrencyCode
  currencyCode)` beside `amount()` in `domain/value/Money`, as a temporary stub returning
  `new Money(0, currencyCode)`, with an inline comment stating the intent: reject a null amount; read the
  currency's `getDefaultFractionDigits()`; reject a currency whose fraction digits are negative; build the minor
  units as `amount.movePointRight(fractionDigits).setScale(0).longValueExact()`; catch the two
  `ArithmeticException`s and rethrow each as `InvalidMoneyException`. The canonical constructor and `amount()`
  are untouched.
- [x] ST02 · `ledger-service` — in `adapter/mcp/CreateExpenseProposalToolRequest`, replace the
  `Long amountMinorUnits` component with `String amount`.
- [x] ST03 · `ledger-service` — in `adapter/mcp/CreateExpenseProposalToolResponse`, replace the
  `long amountMinorUnits` component with `String amount`.
- [x] ST04 · `ledger-service` — in `adapter/mcp/CreateExpenseProposalMcpTool.createExpenseProposal`, the fifth
  parameter becomes `String amount`, still `required`, and its `@McpToolParam` description becomes exactly:
  `the amount exactly as the message writes it, in the currency's main unit - 7200 for 7200 HUF, 12.50 for 12.50
  EUR. Digits, and at most one dot for the decimals. Never convert it, never group the digits.` Pass it into
  `CreateExpenseProposalToolRequest` in the same position. No catch clause is added — `InvalidMoneyException` and
  `InvalidExpenseProposalException` are already caught and rendered as `invalid request: <message>`.
  · after: ST02
- [x] ST05 · `ledger-service` — in `adapter/mcp/ExpenseProposalToolUtils`, keep all existing logic and get back to
  build-green:
    - `toCommand` — replace the `amountMinorUnits` null check with the same check on `request.amount()`, and
      build the money as `Money.ofMajorUnits(new BigDecimal(request.amount().strip()),
      CurrencyCode.of(request.currencyCode()))`. Add a `TODO` at the null check describing what still has to land
      there: the message `expense proposal request has no amount`, and the `^\d{1,18}(\.\d{1,4})?$` check on the
      stripped text throwing `InvalidExpenseProposalException` with the message
      `amount must be digits with an optional dot, like 7200 or 12.50`.
    - `toResponse` — pass `proposal.money().amount().toPlainString()` for the response's `amount` component.
      · after: ST01, ST02, ST03

**Shared Test Infrastructure**

- [x] ST06 · `ledger-service` — in `common/McpRequests.createExpenseProposal`, the `Long amountMinorUnits`
  parameter becomes `String amount`, emitted through `jsonString(...)` under the argument name `amount`. Then get
  the module's test sources compiling again, leaving every assertion to the Red Phase:
    - `CreateExpenseProposalMcpToolTest`, `CreateExpenseProposalMcpToolSystemTest`, `McpAuthenticationSystemTest`
      — pass the text form of the amount each already sends.
    - `ReceiveTelegramMessageSystemTest` — drop `PROPOSAL_AMOUNT_MINOR_UNITS = 1230L` and pass the already-present
      `PROPOSAL_AMOUNT_TEXT` (`"12.30"`), which stores the same 1230 minor units and leaves the class's report
      assertion untouched. No Red Phase step follows.
    - `ExpenseProposalToolUtilsTest` — it constructs `CreateExpenseProposalToolRequest` and
      `CreateExpenseProposalToolResponse` directly rather than through `McpRequests`, so sync it here too:
      `"15.00"` for `1500L`, `"0"` for `0L`, `"-1"` for `-1L`, `null` for `null`. RU02 owns its assertions.
- [x] ST07 · `ai-connector-service` — in `common/McpLedgerStubs.stubToolsList`, publish
  `"amount": {"type": "string"}` in place of `"amountMinorUnits": {"type": "integer"}` and name `amount` in the
  schema's `required` array in place of `amountMinorUnits`.
- [x] ST08 · confirm `bot.finance.architecture.CleanArchitectureTest` (`ledger-service`) and
  `bot.finance.ai.architecture.CleanArchitectureTest` (`ai-connector-service`) still pass.

### Red Phase

#### TDD Unit Red Phase

- [x] RU01 · `Money` · test: `MoneyTest` · covers: `ofMajorUnits(BigDecimal, CurrencyCode)`
    - `ofMajorUnits(BigDecimal, CurrencyCode)`:
        - given: the amount `7200` and HUF, a currency ISO 4217 gives two fraction digits
          when: `ofMajorUnits` is called
          then: the returned money carries 720000 minor units and HUF
        - given: the amount `7200.50` and HUF
          when: `ofMajorUnits` is called
          then: the returned money carries 720050 minor units, so an amount within the currency's own precision
          is accepted rather than refused
        - given: the amount `12.50` and EUR
          when: `ofMajorUnits` is called
          then: the returned money carries 1250 minor units
        - given: the amount `12.5` and EUR, written with fewer decimals than the currency has
          when: `ofMajorUnits` is called
          then: the returned money carries 1250 minor units
        - given: the amount `1200` and JPY, a currency with no fraction digits
          when: `ofMajorUnits` is called
          then: the returned money carries 1200 minor units
        - given: the amount `0` and EUR
          when: `ofMajorUnits` is called
          then: the returned money carries zero minor units
        - given: a null amount and EUR
          when: `ofMajorUnits` is called
          then: it throws `InvalidMoneyException`
        - given: the amount `1.00` and XAU, a code `java.util.Currency` knows but whose
          `getDefaultFractionDigits()` is `-1`
          when: `ofMajorUnits` is called
          then: it throws `InvalidMoneyException` whose message names XAU and says an amount cannot be recorded
          in it
        - given: the amount `12.505` and EUR
          when: `ofMajorUnits` is called
          then: it throws `InvalidMoneyException` whose message names `12.505`, EUR and its two decimal places
        - given: an amount whose minor units exceed `Long.MAX_VALUE`, such as `999999999999999999` EUR
          when: `ofMajorUnits` is called
          then: it throws `InvalidMoneyException` saying the amount is too large to record, never an
          `ArithmeticException`
        - given: the amount `-1.00` and EUR
          when: `ofMajorUnits` is called
          then: it throws `InvalidMoneyException`, since the canonical constructor's non-negative rule still holds
- [x] RU02 · `ExpenseProposalToolUtils` · test: `ExpenseProposalToolUtilsTest` · covers: `toCommand()`,
  `toResponse()`
    - `toCommand()`:
        - given: a request whose `amount` is `"7200"` and whose `currencyCode` is `"HUF"`
          when: `toCommand` is called
          then: the returned command's money carries 720000 minor units and HUF
        - given: a request whose `amount` is `"  12.50  "`, with surrounding whitespace
          when: `toCommand` is called
          then: the returned command's money carries 1250 minor units, so the text is stripped before it is read
        - given: a request whose `amount` is a form the description never offered — `"12,50"`, `"7,200"`,
          `"-5.00"`, `"1e3"`, `"€12"`, `"12."`, `"twelve"`, `""`, `"   "`, nineteen digits, or `"1.23456"` with
          five decimals — as one parameterized case set
          when: `toCommand` is called
          then: it throws `InvalidExpenseProposalException` whose message names the accepted form, and no
          arithmetic runs
        - given: a request whose `amount` is `"0"` and whose `currencyCode` is `"EUR"`
          when: `toCommand` is called
          then: the returned command's money carries zero minor units
        - update: `whenAmountMinorUnitsIsAbsent_thenThrowsInvalidExpenseProposalException()` — build the request
          with a null `amount`, assert the message is `expense proposal request has no amount`, and rename the
          method and its `@DisplayName` for the renamed argument
        - update: `whenAmountMinorUnitsIsZero_thenReturnsCommandWithZeroMinorUnitsMoney()` — drop it; the `"0"`
          scenario above replaces it
        - update: `whenAmountMinorUnitsIsNegative_thenThrowsInvalidMoneyException()` — drop it; a leading `-`
          now fails the text check and is covered by the parameterized case set above
        - update: `whenRequestCarriesEveryArgumentAndAnIdentity_thenReturnsCommandCarryingThatIdentityAndFields()`,
          `whenRequestIdentityAndReferenceAreValid_thenReturnedCommandCarriesThatReference()`,
          `whenParentCategoryIsNullOrBlank_thenCommandParentCategoryNameIsEmpty()`,
          `whenMerchantIsNullOrBlank_thenCommandMerchantIsEmpty()` and
          `whenCurrencyCodeIsNotAnIso4217Code_thenThrowsInvalidMoneyException()` — build the request with
          `"15.00"` in place of `1500L`, and where the expected money is asserted, keep it as
          `new Money(1500L, CurrencyCode.of("EUR"))`
    - `toResponse()`:
        - update: `whenStoredProposalCarriesMerchantAndCategoryName_thenReturnsResponseCarryingThoseFields()` —
          expect the response's `amount` to be the string `"15.00"` in place of the `1500L` minor units, and
          reword the `@DisplayName` accordingly

#### TDD Integration Red Phase

- [x] RI01 · `CreateExpenseProposalMcpTool` · test: `CreateExpenseProposalMcpToolTest` · covers:
  `POST /mcp — tools/call create_expense_proposal` · mocks: `CreateExpenseProposalPort`
    - Validation: `amount` — the tool error names an invalid request and the port is never called for each of:
      a malformed text form (`"7,200"`), an amount more precise than its currency (`"12.505"` EUR, the message
      naming EUR's two decimal places), a currency with no minor unit (`"1.00"` XAU, the message naming XAU),
      and an amount too large for `long` minor units (`"999999999999999999"` EUR — eighteen digits, so it passes
      the text check and overflows at `longValueExact()`, and the message says it is too large)
    - update: `whenAmountMinorUnitsAbsent_thenToolErrorNamesInvalidRequestAndPortNeverCalled()` — send a null
      `amount`, keep the assertion that the error names the amount and the port is never called, and rename the
      method and its `@DisplayName` for the renamed argument
    - update: `whenAmountMinorUnitsCannotBeBound_thenFrameworksOwnBindingFailureReportedAndPortNeverCalled()` —
      keep it as the module's one test of
      [mcp.md](../ledger-service/docs/contracts/in/mcp.md)'s "an argument's value cannot be read as the type the
      schema declares" row, which the renamed argument would otherwise leave uncovered: the text-block body sends
      an object, `"amount": {"value": 7200}`, a value `String` cannot be read as. Assert the result is an error
      and the port is never called; rename the method and its `@DisplayName` for the renamed argument
    - Validation: a JSON **number** — given a text-block body sending `"amount": 7200` with
      `"currencyCode": "HUF"`, the result is a tool error and the port is never called: spring-ai-mcp validates
      the arguments against the published schema before the tool method runs, so a number under a
      `"type": "string"` field is refused there and never reaches Jackson's coercion (see the blocker under
      **Open Questions / Blockers**, and D13 as corrected). What the scenario pins is the invariant that
      survives: a model answering with a number stores no amount at all rather than a wrong one
    - update: every remaining test in the class — `postCreateExpenseProposal`'s amount parameter becomes a
      `String`, and each call site sends the text form of the amount it used to send in minor units (`1599L` →
      `"15.99"`, `500L` → `"5.00"`); in
      `whenCreateExpenseProposalIsCalled_thenPortReceivesTokenSubjectAndResultCarriesStoredProposal()` the
      response-body assertion expects `"15.99"` in place of `"1599"`
- [x] RI02 · `AiExpenseRecordingAdapter` · test: `AiExpenseRecordingAdapterTest` · covers: `record()`
    - `record()`:
        - update: `LUNCH_ARGUMENTS`, `CAB_ARGUMENTS`, `correctedArguments` and the two inline tool-call argument
          text blocks — send `"amount":"15.00"`, `"amount":"20.00"` and `"amount":"25.00"` in place of the
          `amountMinorUnits` numbers
        - update: `whenOneAcceptedToolCallWithCallerToken_thenLedgerReceivesItEveryRequestCarriesTokenAndNoExceptionThrown()`
          — assert `arguments.get("amount").asText()` is `"15.00"` in place of the `amountMinorUnits` long
        - update: the tool-schema assertion listing the published argument names — expect `amount` in place of
          `amountMinorUnits`

#### TDD System Test Red Phase

- [x] RS01 · `CreateExpenseProposalMcpToolSystemTest` · covers: `POST /mcp`
    - Happy Path:
        - update: `whenToolCallNamesChildCategory_thenResponseCarriesStoredProposalAndRowIsWritten()` — the class
          constants become `AMOUNT = "7200"` and `CURRENCY_CODE = "HUF"` in place of
          `AMOUNT_MINOR_UNITS = 1500L` / `"EUR"`; assert the tool result's `amount` is `"7200.00"` and the stored
          row's `amount_minor_units` is `720000`, so the objective — an amount written as the user wrote it is
          stored at the currency's own scale — is proved end to end; reword the `@DisplayName` accordingly
    - Unhappy Path:
        - update: `whenToolCallNamesGrouping_thenResponseIsToolErrorNamingChildrenAndNoRowIsWritten()` — follow
          the renamed constants; its assertions are otherwise unchanged
- [x] RS02 · `McpAuthenticationSystemTest` · covers: `POST /mcp`
    - Happy Path:
        - update:
          `whenToolsListIsPostedWithValidToken_thenCreateExpenseProposalToolIsListedWithSixArgumentsAndNoIdentityArgument()`
          — expect `amount` in place of `amountMinorUnits` among the published argument names, and additionally
          assert that `amount` is published with `"type": "string"` and is named in the schema's `required`
          array, so the contract the model reads is pinned
    - Unhappy Path:
        - update: `whenToolsCallIsPostedWithRejectedToken_thenUnauthorizedWithNoToolResultAndNoRowWritten()` —
          send `"10.00"` in place of `1000L`; its assertions are unchanged
- [x] RS03 · `ExtractIntentsSystemTest` · covers: `IntentExtractionService.ExtractIntents`
    - Happy Path:
        - update: the stubbed tool-call argument text block sends `"amount":"15.00"` in place of
          `"amountMinorUnits":1500`, and the assertion reads `arguments.get("amount").asText()` as `"15.00"`

### Green Phase

#### TDD Unit Green Phase

- [ ] GU01 · `Money` · test: `MoneyTest`
- [ ] GU02 · `ExpenseProposalToolUtils` · test: `ExpenseProposalToolUtilsTest` · after: GU01

#### TDD Integration Green Phase

- [ ] GI01 · `CreateExpenseProposalMcpTool` · test: `CreateExpenseProposalMcpToolTest` · after: GU01, GU02
- [ ] GI02 · `AiExpenseRecordingAdapter` · test: `AiExpenseRecordingAdapterTest`

#### TDD System Test Green Phase

- [ ] GS01 · `CreateExpenseProposalMcpToolSystemTest` · covers: `POST /mcp`
- [ ] GS02 · `McpAuthenticationSystemTest` · covers: `POST /mcp`
- [ ] GS03 · `ExtractIntentsSystemTest` · covers: `IntentExtractionService.ExtractIntents`

### Post-Implementation Steps

#### ADRs

- [ ] P01 · Write ADR: the major-to-minor conversion lives in the domain, as a `Money` factory, not in the
  adapter that reads the wire.

## Open Questions / Blockers

- **Q1:** `ledger-service`'s [Agent Configuration](../ledger-service/docs/conventions/agent.md) admits an ADR
  into **Post-Implementation Steps** only where the developer approves one. The candidate is D2, stated as a
  fact: *the major-to-minor conversion lives in the domain, as a `Money` factory, not in the adapter that reads
  the wire.* Without an ADR it is held by
  [money.md](../ledger-service/docs/domain/money.md) as a `Money` invariant and by nothing else — the reason for
  the placement is not recorded anywhere. Write it?
- A: Write the ADR — P01.

- **B1 (RI01, resolved in place):** D13 and the design's Context both describe argument binding as reaching
  `AbstractMcpToolMethodCallback.buildTypedArgument` → Jackson coercion, so the plan asked RI01 to pin a JSON
  number binding to its text as 720000 HUF. It cannot: spring-ai-mcp validates the incoming arguments against the
  generated JSON Schema first, and the call comes back
  `input validation failed: … [/amount: integer: string found, {2} expected]` with zero interactions on the port.
  No production change could turn that scenario green. Verified by running the class directly.
- Resolution: RI01's scenario moved from Happy Path to Validation and now asserts the rejection and the untouched
  port; D13's answer corrected in the design file. The invariant D13 exists for — *neither path stores a wrong
  amount* — is unaffected, and is what the scenario now pins.

## Review Findings

- **F1:** ST06 missed a fourth caller of `McpRequests.createExpenseProposal`, `ReceiveTelegramMessageSystemTest`.
- Resolution: mechanical
- Action: applied — ST06 now names it and passes its existing `PROPOSAL_AMOUNT_TEXT`.

- **F2:** No stabilization item synced `ExpenseProposalToolUtilsTest`, which builds the two changed records
  directly, so the test sources would not compile at the guardrail.
- Resolution: mechanical
- Action: applied — ST06 now carries its compile-only sync.

- **F3:** RI01's overflow scenario used nineteen digits, which the text check refuses first, so the overflow
  message it asserts is unreachable.
- Resolution: mechanical
- Action: applied — the scenario now uses eighteen digits.

- **F4:** RI01's JSON-number update asserted a disjunction covering every outcome, so it could not fail; and
  retyping the argument moved the class's only binding-failure test onto a path our own validation already covers.
- Resolution: decision
- Action: applied — both cases are now pinned separately. The existing test keeps
  [mcp.md](../ledger-service/docs/contracts/in/mcp.md)'s binding-failure row covered by sending an object under
  `amount`, and a new Happy Path scenario pins the JSON number binding to its text as 720000 HUF.

- **F5:** ST08 left `ai-connector-service`'s architecture-enforcement test unnamed.
- Resolution: mechanical
- Action: applied — ST08 now names `bot.finance.ai.architecture.CleanArchitectureTest`.
