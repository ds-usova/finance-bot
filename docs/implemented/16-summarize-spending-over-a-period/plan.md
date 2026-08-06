# Plan: Summarize Spending Over a Period

**Affected Modules:** `ledger-service`, `ai-connector-service`
**Design:** [Summarize Spending Over a Period](design.md)

## Step-by-Step Implementation Map (To-Do List)

### Stabilization

#### API Contract

- [x] ST01 · Add `current_date` to `ExtractIntentsRequest` in `proto/intent_extraction.proto`, at a fresh tag 5
  (D20):
  ```proto
  // The day the turn runs on, in UTC, as an ISO-8601 date. Every period the model reads out
  // of a relative phrase is anchored on it.
  string current_date = 5;
  ```

#### Database

- [x] ST02 · Add migration `ledger-service/src/main/resources/db/migration/V006__create_spending_query.sql`,
  exactly as the design's migration block states:
  ```sql
  CREATE TABLE spending_query (
      id                BIGSERIAL   PRIMARY KEY,
      user_id           BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
      message_reference UUID        NOT NULL,
      period_start      DATE        NOT NULL,
      period_end        DATE        NOT NULL,
      created_at        TIMESTAMPTZ NOT NULL,
      CONSTRAINT ck_spending_query_period CHECK (period_end >= period_start)
  );

  CREATE INDEX idx_spending_query_message_reference ON spending_query (user_id, message_reference);
  ```

#### Interface-First / Build Stabilization

New-method stubs carry a short inline comment describing the implementation intent; an existing method whose
signature changes keeps its logic and gains a `TODO` at the insertion point.

**Interface & Signature Sync**

- [x] ST03 · `ledger-service` — add `domain/exception/InvalidSpendingPeriodException` and
  `domain/exception/InvalidSpendingQueryException`, shaped like the neighbouring `InvalidGroupingException` and
  `InvalidExpenseProposalException`
- [x] ST04 · `ledger-service` — add `domain/value/SpendingPeriod`, the record
  `SpendingPeriod(LocalDate from, LocalDate to)` with a `TODO` in the compact constructor for the both-ends-present
  and `to >= from` invariants RU01 covers, and:
  ```java
  public static SpendingPeriod of(String from, String to) {
      // parses each written date as an ISO-8601 YYYY-MM-DD value, refusing an absent, blank or
      // unparseable one, and refuses a period whose last day precedes its first
      return null;
  }
  ```
- [x] ST05 · `ledger-service` — add `domain/model/SpendingQuery extends Entity`, holding `userId`,
  `SpendingPeriod period`, `MessageReference messageReference` and `Instant createdAt`, with `newQuery(...)` and
  `stored(...)` beside `ExpenseProposal`'s pair and a `TODO` in the private constructor for the invariants RU02
  covers
- [x] ST06 · `ledger-service` — add `application/dto/SummarizeSpendingCommand`, the record
  `(AuthenticatedUserId userId, MessageReference reference, String from, String to)`, with a `TODO` in the compact
  constructor for the self-validation RU03 covers. The name is fixed by the ArchUnit rule
  `inboundPortCommandsAreNamedAfterTheirUseCase`
- [x] ST07 · `ledger-service` — add the inbound port `application/port/SummarizeSpendingPort` with
  `SpendingPeriod summarize(SummarizeSpendingCommand command)` and `@throws` javadoc for
  `InvalidSpendingQueryException`, `InvalidSpendingPeriodException`, `EntityNotFoundException` and
  `PersistenceFailedException`
- [x] ST08 · `ledger-service` — add the outbound port `application/port/SpendingQueryRepository` with
  `SpendingQuery create(SpendingQuery query)` and
  `List<SpendingPeriod> findPeriodsByMessageReference(long userId, MessageReference reference)`, each with the
  `@throws PersistenceFailedException` javadoc `ExpenseProposalRepository` carries
- [x] ST09 · `ledger-service` — add `application/dto/CurrencyTotal`, the record `(Money total, int expenseCount)`,
  and `application/dto/SpendingSummary`, the record `(SpendingPeriod period, List<CurrencyTotal> totals)` with a
  `TODO` in its compact constructor for the copy-and-order-by-currency-code invariant RU05 covers
- [x] ST10 · `ledger-service` — add
  `List<CurrencyTotal> totalsByCurrency(long userId, SpendingPeriod period)` to `application/port/ExpenseRepository`,
  with the `@throws PersistenceFailedException` javadoc `countByMessageReference` carries
- [x] ST11 · `ledger-service` — stub `application/usecase/SummarizeSpendingUseCase implements SummarizeSpendingPort`,
  taking `UserRepository`, `SpendingQueryRepository` and a `Clock`:
  ```java
  public SpendingPeriod summarize(SummarizeSpendingCommand command) {
      // refuses an absent command, parses the written dates into a SpendingPeriod, resolves the token's
      // subject to a stored user, stores a SpendingQuery under the command's reference, and answers the
      // period it accepted
      return null;
  }
  ```
- [x] ST12 · `ledger-service` — rename `application/dto/ProposalReport` to `application/dto/TurnReport` and give it
  `List<SpendingSummary> summaries` beside `proposals`, copied the same way `proposals` already is (D5, D6); add
  `ANSWERED` to `application/dto/ReportOutcome` (D7)
- [x] ST13 · `ledger-service` — change `application/port/MessageDeliveryPort.deliver` to take a `TurnReport`,
  keeping its `@throws` javadoc, and update `adapter/telegram/TelegramMessageDeliveryAdapter.deliver`'s parameter
  type and the renderer calls inside it. In `TelegramMessageDeliveryAdapterTest`, the two private report factories
  build a `TurnReport` with an empty summary list and the text assertion calls `TurnReportRenderer.render`
- [x] ST14 · `ledger-service` — add `LocalDate currentDate` to `application/dto/IntentExtractionRequest`, with a
  `TODO` in the compact constructor for the not-null refusal RU07 covers
- [x] ST15 · `ledger-service` — in `application/usecase/HandleIncomingMessageUseCase`, take a `Clock`, a
  `SpendingQueryRepository` and an `ExpenseRepository` as new constructor arguments, put `LocalDate.now(clock)` on
  the `IntentExtractionRequest`, deliver a `TurnReport` carrying `List.of()` for its summaries, and add a `TODO`
  after the proposal read-back where the periods are read, totalled and folded into the outcome (GU08 implements
  it). Keep `outcomeFor`'s existing arms intact
- [x] ST16 · `ledger-service` — add `adapter/mcp/SummarizeSpendingToolResponse`, the record `(String from, String to)`
- [x] ST17 · `ledger-service` — stub `adapter/mcp/SummarizeSpendingMcpTool` as a `@Component` taking
  `SummarizeSpendingPort`, `JsonMapper` and `LoggerFactory`, declared exactly as the design's tool spec writes it:
  ```java
  @McpTool(
          name = "summarize_spending",
          description = "Answers the caller's question about what they spent over a period. The totals are put "
                  + "in front of the caller directly; they are not returned to you, and you never state an "
                  + "amount yourself.")
  public CallToolResult summarizeSpending(
          @McpToolParam(description = "the first day of the period, inclusive, as YYYY-MM-DD") String from,
          @McpToolParam(description = "the last day of the period, inclusive, as YYYY-MM-DD") String to) {
      // logs the call at debug, reads the caller and the message reference off the token, invokes
      // SummarizeSpendingPort, serializes SummarizeSpendingToolResponse, and renders every failure as an
      // isError result logged at warn
      return null;
  }
  ```
- [x] ST18 · `ledger-service` — add the persistence trio beside the proposal one: `adapter/persistence/SpendingQueryEntity`
  (`@Table("spending_query")`, `@Id Long id`, `userId`, `messageReference`, `periodStart`, `periodEnd`, `createdAt`,
  with `toDomain()` and `fromDomain(...)` truncating the instant to micros as `ExpenseProposalEntity` does),
  `adapter/persistence/SpendingQueryEntityRepository` carrying the design's read:
  ```sql
  SELECT DISTINCT ON (period_start, period_end) period_start, period_end, created_at
  FROM spending_query
  WHERE user_id = :userId AND message_reference = :messageReference
  ORDER BY period_start, period_end, created_at
  ```
  read into a `SpendingPeriodProjection`, and `adapter/persistence/SpendingQueryRepositoryAdapter` as a
  `@Component` implementing `SpendingQueryRepository` with both methods stubbed and commented for their intent
- [x] ST19 · `ledger-service` — add the totals query to `adapter/persistence/ExpenseEntityRepository`:
  ```sql
  SELECT currency_code, sum(amount_minor_units) AS total_minor_units, count(*) AS expense_count
  FROM expense
  WHERE user_id = :userId AND created_at >= :from AND created_at < :toExclusive
  GROUP BY currency_code
  ORDER BY currency_code
  ```
  declared over `Instant` bound parameters (D28 — no `DATE` is cast in SQL) and read into a new
  `adapter/persistence/CurrencyTotalProjection` record `(String currencyCode, long totalMinorUnits, int expenseCount)`
  with a `toCurrencyTotal()` beside `ProposalSummaryProjection.toSummary()`; stub
  `ExpenseRepositoryAdapter.totalsByCurrency` with its intent comment
- [x] ST20 · `ledger-service` — rename `adapter/telegram/ProposalReportRenderer` to
  `adapter/telegram/TurnReportRenderer`, take a `TurnReport` in `render` and `renderKeyboard`, add an `ANSWERED`
  arm to the `switch` returning the empty string for now, and add a `TODO` above `render` where the summary block
  and the D24 budget split go (GU10 implements them). `renderKeyboard`'s "only when a proposal is listed" rule is
  unchanged. `ProposalReportRendererTest` is renamed to `TurnReportRendererTest` in the same step, so RU10 has a
  class to extend
- [x] ST21 · `ledger-service` — in `adapter/aiconnector/IntentProtoMapper.toProtoRequest`, call
  `setCurrentDate(request.currentDate().toString())`
- [x] ST22 · `ledger-service` — in `adapter/config/UseCaseConfiguration`, add a `@Bean` returning
  `SummarizeSpendingPort` over `SummarizeSpendingUseCase` with `Clock.systemUTC()`, and pass `Clock.systemUTC()`,
  the `SpendingQueryRepository` and the `ExpenseRepository` into `handleIncomingMessagePort`
- [x] ST23 · `ai-connector-service` — add `LocalDate currentDate` to `application/dto/ExtractIntentsCommand`, with
  a `TODO` in the compact constructor for the not-null refusal RU11 covers
- [x] ST24 · `ai-connector-service` — add `LocalDate currentDate` to `application/port/ExpenseRecordingPort.record`,
  keeping its `@throws` javadoc, and pass `command.currentDate()` straight through in
  `application/usecase/ExtractIntentsUseCase`
- [x] ST25 · `ai-connector-service` — in `adapter/grpc/IntentExtractionGrpcService`, parse `getCurrentDate()` into a
  `LocalDate` for the command and add a `TODO` in `rejectIfInvalid` where the two new `INVALID_ARGUMENT` clauses go
  (RI05 covers them); leave the text, grouping and currency clauses intact
- [x] ST26 · `ai-connector-service` — in `adapter/ai/AiExpenseRecordingAdapter.record`, take the `currentDate`
  argument and render it into the template under the key `today`

**Configuration**

- [x] ST27 · `ai-connector-service` — in `src/main/resources/prompts/user-message.st`, add above the existing
  grouping paragraph:
  ```
  Today is {today} (UTC). A week starts on Monday.

  When the user asks what they spent over some period — "last week", "this month", "since Friday" — work the
  period out from today's date and call the summarize_spending tool once with its first and last day. The tool
  puts the totals in front of the user itself; it does not tell you the amounts, and you never state one.
  ```
- [x] ST28 · `ai-connector-service` — in `src/main/resources/prompts/record-expenses.st`, replace the closing
  paragraph ("Record expenses and nothing else…") with:
  ```
  Record expenses, and answer a question about what was spent by asking for a summary. You do not create, rename
  or delete categories, and you never state an amount back to the user yourself.
  ```

**Shared Test Infrastructure**

- [x] ST29 · `ledger-service` — add `summarizeSpending(String from, String to)` to `bot.finance.common.McpRequests`,
  a `tools/call` JSON-RPC body for `summarize_spending` shaped like the existing `listCategories` builder and using
  the same `jsonString` helper, so RI03, RS01 and RS03 share one body builder
- [x] ST30 · `ledger-service` — add `bot.finance.common.SpendingQueryRowUtils` with
  `spendingQueryRowsFor(JdbcAggregateTemplate, long userId)` and a `storedQuery(...)` insert, shaped like
  `ExpenseProposalRowUtils`, and list it in
  [testing.md](../../ledger-service/docs/conventions/testing.md)'s Package Structure. RI01, RS01 and RS03 all read
  back or seed `spending_query` rows
- [x] ST31 · `ledger-service` — add `storedExpense(JdbcAggregateTemplate, long userId, long categoryId, String
  description, String merchant, long amountMinorUnits, String currencyCode, UUID messageReference, Instant
  createdAt)` to `bot.finance.common.ExpenseRowUtils`, so a test can seed an `expense` row at a chosen instant.
  RI02 and RS03 both need expenses dated into and outside a period, and neither red-phase step is scoped to write
  shared infrastructure. Re-pad its entry in
  [testing.md](../../ledger-service/docs/conventions/testing.md)'s Package Structure to read "*reads back a
  user's stored expense rows, and stores one directly*"
- [x] ST32 · `ledger-service` — add a `SUMMARIZE_SPENDING_TOKEN` constant to `bot.finance.common.TelegramTestBot`,
  so RS03's new system test class gets its own bot token, its own context-cache entry and its own stub path (see
  [testing.md](../../ledger-service/docs/conventions/testing.md), *Isolating the long-polling listener*)
- [x] ST33 · `ai-connector-service` — in `bot.finance.ai.common.RequestFixtures`, add a `DEFAULT_CURRENT_DATE`
  constant and set `current_date` from it on every `request(...)` overload, plus a distinctly named
  `requestWithCurrentDate(String currentDate)` builder — a further `String` overload of `request(...)` would
  reproduce an existing signature — so RI05 can build the invalid cases
- [x] ST34 · `ai-connector-service` — in `bot.finance.ai.common.McpLedgerStubs`, publish `summarize_spending` in the
  stubbed `tools/list` result with `from` and `to` as its arguments, both required, and add a
  `stubSummarizeSpendingAccepted(String from, String to)` scenario answering a `summarize_spending` call with a
  non-error result carrying `{"from":…,"to":…}`, matched on `$.params.name` the way the sibling scenarios are

- [x] ST35 · Confirm both modules' architecture-enforcement tests still pass —
  `bot.finance.architecture.CleanArchitectureTest` and `bot.finance.ai.architecture.CleanArchitectureTest`

### Red Phase

#### TDD Unit Red Phase

- [x] RU01 · `SpendingPeriod` · test: `SpendingPeriodTest` · covers: the compact constructor, `of()`
    - `SpendingPeriod`:
        - given: two dates whose `to` equals the `from`
          when: the record is constructed
          then: both components read back unchanged — a one-day period is a period
        - given: a null `from`, or a null `to`
          when: the record is constructed
          then: throws InvalidSpendingPeriodException
        - given: a `to` one day before the `from`
          when: the record is constructed
          then: throws InvalidSpendingPeriodException
    - `of()`:
        - given: two ISO-8601 dates a week apart
          when: of() is called
          then: returns a period whose ends are those two dates
        - given: a `from` that is null, empty, or whitespace only
          when: of() is called
          then: throws InvalidSpendingPeriodException whose message names the first day as the one at fault
        - given: a `to` that is null, empty, or whitespace only
          when: of() is called
          then: throws InvalidSpendingPeriodException whose message names the last day as the one at fault
        - given: a written date that is not an ISO-8601 `YYYY-MM-DD` value — `27/07/2026`, `2026-7-27`,
          `last week`, `2026-02-30`
          when: of() is called
          then: throws InvalidSpendingPeriodException naming the value it could not read
        - given: a `to` before the `from`, both well-formed
          when: of() is called
          then: throws InvalidSpendingPeriodException saying the period ends before it starts
        - given: a period a decade wide, and one wholly in the future
          when: of() is called
          then: both are accepted — no span or future bound is imposed (D10)
- [x] RU02 · `SpendingQuery` · test: `SpendingQueryTest` · covers: `newQuery()`, `stored()`
    - `newQuery()`:
        - given: a positive user id, a period, a reference and an instant
          when: newQuery() is called
          then: every component reads back unchanged and the entity carries no id
        - given: a user id of zero or negative
          when: newQuery() is called
          then: throws InvalidSpendingQueryException
        - given: a null period, a null reference, or a null instant
          when: newQuery() is called
          then: throws InvalidSpendingQueryException
    - `stored()`:
        - given: an id beside otherwise valid components
          when: stored() is called
          then: the entity carries that id and every other component reads back unchanged
- [x] RU03 · `SummarizeSpendingCommand` · test: `SummarizeSpendingCommandTest` · covers: the compact constructor
    - `SummarizeSpendingCommand`:
        - given: an authenticated user id, a reference and two written dates
          when: the record is constructed
          then: every component reads back unchanged
        - given: a null authenticated user id
          when: the record is constructed
          then: throws InvalidSpendingQueryException
        - given: a null message reference
          when: the record is constructed
          then: throws InvalidSpendingQueryException
        - given: a `from` or a `to` that is null or blank
          when: the record is constructed
          then: the record is built and the value is carried through unchanged — the written dates are the
          period's to judge, not the command's (RU01 owns their refusal)
- [x] RU04 · `SummarizeSpendingUseCase` · test: `SummarizeSpendingUseCaseTest` · covers: `summarize()`
    - `summarize()`:
        - given: nothing stubbed
          when: summarize(null) is called
          then: throws InvalidSpendingQueryException and neither repository is touched
        - given: a command whose written dates do not make a period
          when: summarize() is called
          then: throws InvalidSpendingPeriodException and neither repository is touched — the period is parsed
          before the user is looked up
        - given: the user repository answers empty for the command's external id
          when: summarize() is called
          then: throws EntityNotFoundException and the spending query repository is never called
        - given: a stored user and a well-formed period
          when: summarize() is called
          then: the stored query carries that user's stored id, the command's reference, the parsed period and
          the fixed clock's instant, and the answer is that same period
        - given: a stored user whose id differs from the external id on the command
          when: summarize() is called
          then: the stored query carries that stored user's id — a caller records a period against no other user
        - given: the spending query repository throws PersistenceFailedException
          when: summarize() is called
          then: the exception propagates unchanged
- [x] RU05 · `SpendingSummary` · test: `SpendingSummaryTest` · covers: the compact constructor
    - `SpendingSummary`:
        - given: totals handed in out of currency-code order
          when: the record is constructed
          then: `totals()` reads back ordered by currency code
        - given: a mutable totals list handed to the constructor, modified afterwards
          when: totals() is read
          then: it is unchanged and unmodifiable
        - given: an empty totals list
          when: the record is constructed
          then: the summary is built and `totals()` is empty — a period holding nothing is a summary, not a
          failure (D11)
- [x] RU06 · `TurnReport` · test: `TurnReportTest` · covers: the compact constructor
    - `TurnReport`:
        - given: a conversation id, an inbound message id, an outcome, a proposal list, a summary list and a
          reference
          when: the record is constructed
          then: every component reads back unchanged and both lists are unmodifiable
        - given: a null proposals list, or a null summaries list
          when: the record is constructed
          then: that component reads back as an empty list rather than throwing
        - given: mutable proposal and summary lists handed to the constructor, modified afterwards
          when: proposals() and summaries() are read
          then: both are unchanged
- [x] RU07 · `IntentExtractionRequest` · test: `IntentExtractionRequestTest` · covers: the compact constructor
    - `IntentExtractionRequest`:
        - given: a null current date
          when: the record is constructed
          then: throws InvalidExtractionRequestException
        - given: an otherwise valid request carrying a current date
          when: the record is constructed
          then: `currentDate()` reads back unchanged
        - every remaining scenario in the class changes only its constructor arguments — a current date joins the
          list — and keeps the assertion it already makes; the affected methods are
          `whenTextOneCategoryAndDefaultCurrencyAreValid_thenItHoldsAllThree()`,
          `whenTextIsNullOrBlank_thenThrowsInvalidExtractionRequestException()`,
          `whenCategoryGroupingsIsNullOrEmpty_thenThrowsInvalidExtractionRequestException()`,
          `whenCategoryGroupingsContainsNullOrBlankElement_thenThrowsInvalidExtractionRequestException()`,
          `whenCatchAllGroupingIsNullOrBlank_thenThrowsInvalidExtractionRequestException()`,
          `whenCatchAllGroupingIsNotOneOfCategoryGroupings_thenThrowsInvalidExtractionRequestException()`,
          `whenDefaultCurrencyIsNull_thenThrowsInvalidExtractionRequestException()`,
          `whenDefaultCurrencyIsEmpty_thenDefaultCurrencyComesBackEmpty()`,
          `whenUserExternalIdIsNullOrBlank_thenThrowsInvalidExtractionRequestException()`,
          `whenGroupingListIsModifiedAfterConstruction_thenCategoryGroupingsIsUnchanged()`,
          `whenTextCategoriesCurrencyExternalIdAndMessageReferenceAreValid_thenMessageReferenceReadsBackUnchanged()`
          and `whenMessageReferenceIsNull_thenThrowsInvalidExtractionRequestException()`
- [x] RU08 · `HandleIncomingMessageUseCase` · test: `HandleIncomingMessageUseCaseTest` · covers: `handle()`
    - `handle()`:
        - given: a fixed clock and a stored user
          when: handle() is called
          then: the extraction request's `currentDate` is that clock's day in UTC (D29)
        - given: the periods read back name two distinct periods and each totals something
          when: handle() is called
          then: the delivered report carries one summary per period, in the order the read answered them, each
          holding the totals `ExpenseRepository` answered for it
        - given: the periods read back name one period the ledger holds nothing in
          when: handle() is called
          then: the delivered report carries that period as a summary with no totals, not an absent one (D11)
        - given: no proposal and one summary, extraction having completed
          when: handle() is called
          then: the report's outcome is `ANSWERED` (D7)
        - given: one proposal and one summary, extraction having completed
          when: handle() is called
          then: the report's outcome is `RECORDED` and the report carries both lists
        - given: no proposal and no summary, extraction having completed
          when: handle() is called
          then: the report's outcome is `NOTHING_IDENTIFIED`
        - given: extraction failed and one summary was recorded, with no proposal
          when: handle() is called
          then: the report's outcome is `PARTIAL` and it carries that summary (D15)
        - given: the period read-back throws PersistenceFailedException
          when: handle() is called
          then: the exception propagates and deliver is never called (D16)
        - given: the totals read throws PersistenceFailedException
          when: handle() is called
          then: the exception propagates and deliver is never called (D16)
        - given: a stored user whose id differs from the external id on the command
          when: handle() is called
          then: both the period read-back and every totals read carry that stored user's id and the reference the
          turn minted
        - update: `whenHandleIsCalled_thenInitializeAndExtractionAndLookupCarryUserAndReference()` — assert the
          extraction request now also carries a current date, and that the period read-back is made with the same
          user id and reference the proposal read-back is
        - update: `whenExtractionSucceedsWithSummaries_thenDeliverReceivesRecordedReport()` — the delivered type
          is a `TurnReport`; assert its summary list is empty when no period was recorded
        - update: `whenExtractionSucceedsWithNoSummaries_thenDeliverReceivesNothingIdentifiedReport()` — same type
          change; the outcome assertion holds only because no period was recorded either, so stub the period
          read-back empty explicitly
        - update: `whenExtractionFailsWithSummaries_thenDeliverReceivesPartialReport()` — same type change
        - update: `whenExtractionFailsWithNoSummaries_thenDeliverReceivesFailedReport()` — same type change, and
          stub the period read-back empty explicitly
        - update: `whenFindSummariesThrowsPersistenceFailedException_thenExceptionPropagatesAndDeliverUntouched()`
          — assert the period read-back and the totals read are untouched too
        - update: `whenDeliverThrowsMessageDeliveryFailedException_thenExceptionPropagates()` — stub the throw on
          the `TurnReport` overload
        - every remaining scenario in the class changes only how it builds the use case — the constructor gains a
          `Clock`, a `SpendingQueryRepository` and an `ExpenseRepository`, the two new mocks stubbed to answer
          empty by default — and keeps the assertion it already makes; the affected methods are
          `whenCommandIsNull_thenThrowsInvalidIncomingMessageExceptionAndLogsNothing()`,
          `whenGroupingNamesExcludeCatchAllGroupingName_thenCatchAllGroupingMissingExceptionPropagates()`,
          `whenFindNamesWithCategoriesReturnsEmptyList_thenCatchAllGroupingMissingExceptionPropagates()`,
          `whenGroupingNamesIncludeCatchAllGroupingName_thenExtractionRequestCarriesThoseNamesAndThatNameAsCatchAll()`,
          `whenFindNamesWithCategoriesThrowsPersistenceFailedException_thenExceptionPropagatesAndExtractionPortUntouched()`,
          `whenExtractionFails_thenErrorLineOmitsTheMessageText()`,
          `whenTurnSucceeds_thenInfoLineOmitsTheMessageText()`,
          `whenInitializeThrowsPersistenceFailedException_thenExceptionPropagatesAndRemainingPortsUntouched()` and
          `whenExtractionThrowsInvalidExtractionRequestException_thenExceptionPropagatesAndDeliverUntouched()`
- [x] RU09 · `IntentProtoMapper` · test: `IntentProtoMapperTest` · covers: `toProtoRequest()`
    - `toProtoRequest()`:
        - given: a request whose current date is a fixed day
          when: toProtoRequest() is called
          then: the generated request's `current_date` is that day's ISO-8601 text
        - update: `whenRequestCarriesTextThreeCategoriesAndDefaultCurrencyEur_thenGeneratedRequestCarriesThemAll()`
          — build the request with a current date and assert `current_date` beside the fields it already asserts
        - update: `whenRequestDefaultCurrencyIsEmpty_thenGeneratedRequestReportsHasDefaultCurrencyAsFalse()` —
          build the request with a current date; the currency assertion is unchanged
        - update:
          `whenGeneratedRequestDescriptorIsInspected_thenItDeclaresNoKnownCategoriesFieldAndFieldTwoIsGroupings()`
          — build the request with a current date and extend the descriptor assertion to field 5 being
          `current_date`
- [x] RU10 · `TurnReportRenderer` · test: `TurnReportRendererTest` · covers: `render()`, `renderKeyboard()`
    - `render()`:
        - given: an `ANSWERED` report carrying one summary with two currency totals, one of them a single expense
          when: render() is called
          then: the text is exactly the design's summary block — the `Between <first> and <last> you spent:`
          header and one `• <amount> <code> (<n> expense[s])` bullet per total, ordered by currency code, with
          the count singular for one expense
        - given: an `ANSWERED` report carrying a summary with no totals
          when: render() is called
          then: the text is exactly `Nothing is recorded between <first> and <last>.` (D11)
        - given: a summary whose period runs from 27 July 2026 to 2 August 2026, rendered with the JVM's default
          locale set to a non-English one
          when: render() is called
          then: the dates read `27 Jul 2026` and `2 Aug 2026` — the month name is English whatever the default
          locale is (D33)
        - given: an `ANSWERED` report carrying two summaries
          when: render() is called
          then: both blocks appear, in the report's own order, separated so a reader can tell them apart
        - given: a `RECORDED` report carrying one summary and two proposals
          when: render() is called
          then: the summary block comes first and the recorded-proposal header and its bullets follow it (D5)
        - given: a `PARTIAL` report carrying one summary and one proposal
          when: render() is called
          then: the summary block comes first, then the may-be-incomplete line and the proposal bullet (D15)
        - given: a `NOTHING_IDENTIFIED` report carrying one summary
          when: render() is called
          then: the summary block comes first and the no-expense-identified text follows it
        - given: a `FAILED` report carrying one summary
          when: render() is called
          then: the summary block comes first and the went-wrong text follows it
        - given: a `RECORDED` report whose summaries and proposals together exceed 4000 characters
          when: render() is called
          then: the text is at most 4000 characters, every summary line is intact, and the proposal list is the
          part that trims — ending with its `… and N more.` line whose count and the bullets present add up to
          the proposals given (D24)
        - given: an `ANSWERED` report whose summary blocks alone exceed 4000 characters
          when: render() is called
          then: the text is at most 4000 characters, whole periods are dropped from the oldest, and the text ends
          with `… and N more periods.` whose count and the blocks present add up to the summaries given (D24)
        - every existing scenario in the class changes only how it builds its report — `TurnReport` with an empty
          summary list in place of `ProposalReport` — and keeps the assertion it already makes; the affected
          methods are
          `whenRecordedReportCarriesTwoSummariesOneWithMerchantOneWithout_thenOpensWithPluralCountAndListsOneBulletPerSummaryInOrder()`,
          `whenRecordedReportCarriesExactlyOneSummary_thenOpensWithSingularCount()`,
          `whenNothingIdentifiedReportHasNoSummaries_thenRendersNoExpenseIdentifiedText()`,
          `whenFailedReportHasNoSummaries_thenRendersWentWrongText()`,
          `whenPartialReportCarriesOneSummary_thenOpensWithMayBeIncompleteTextAndCarriesBulletBelowIt()`,
          `whenRecordedReportExceeds4000Characters_thenTextIsCutAt4000CharactersAndEndsWithOmittedCountLine()`,
          `whenLastBulletIsShorterThanOmittedCountLineAndBulletsExceed4000Characters_thenTextIsStillCutAt4000Characters()`
          and
          `whenSummaryDescriptionContainsAsteriskAndUnderscore_thenThoseCharactersAppearLiterallyWithNoEscaping()`
    - `renderKeyboard()`:
        - given: an `ANSWERED` report carrying summaries and no proposal
          when: renderKeyboard() is called
          then: returns empty — the buttons resolve proposals, and there are none
        - given: a `RECORDED` report carrying both a summary and a proposal
          when: renderKeyboard() is called
          then: returns the same one-row, two-button markup it does today
        - every existing scenario in the class changes only how it builds its report — as above — and keeps its
          assertion; the affected methods are
          `whenRecordedReportCarriesTwoSummariesAndReference_thenReturnsOneRowOfConfirmAndDeleteButtons()`,
          `whenPartialReportCarriesOneSummaryAndReference_thenReturnsSameOneRowTwoButtonMarkup()`,
          `whenNothingIdentifiedAndFailedReportsHaveNoSummaries_thenReturnsEmpty()` and
          `whenRecordedReportSummaryListIsEmpty_thenReturnsEmpty()`
- [x] RU11 · `ExtractIntentsCommand` · test: `ExtractIntentsCommandTest` · covers: the compact constructor
    - `ExtractIntentsCommand`:
        - given: a null current date
          when: the record is constructed
          then: throws InvalidValueException
        - given: an otherwise valid command carrying a current date
          when: the record is constructed
          then: `currentDate()` reads back unchanged
        - every remaining scenario in the class changes only its constructor arguments — a current date joins the
          list — and keeps the assertion it already makes; the affected methods are
          `whenNonBlankTextAndCategoryList_thenCommandExposesBoth()`,
          `whenTextIsNullEmptyOrBlank_thenThrowsInvalidValueException()`,
          `whenCategoryListIsEmpty_thenThrowsInvalidValueException()`,
          `whenCategoryListIsNull_thenThrowsInvalidValueException()`,
          `whenCategoryListContainsNullElement_thenThrowsInvalidValueException()`,
          `whenCatchAllGroupingIsNullBlankOrNotAmongCategoryGroupings_thenThrowsInvalidValueException()`,
          `whenMutableCategoryListModifiedAfterConstruction_thenCommandListUnchangedAndOwnListImmutable()`,
          `whenDefaultCurrencyPresent_thenCommandExposesCurrencyCode()` and
          `whenDefaultCurrencyOptionalIsNull_thenThrowsInvalidValueException()`
- [x] RU12 · `ExtractIntentsUseCase` · test: `ExtractIntentsUseCaseTest` · covers: `extractIntents()`
    - `extractIntents()`:
        - given: a command carrying a current date
          when: extractIntents() is called
          then: the port receives that same date, unchanged
        - update: `whenCommandCarriesThreeGroupingNamesAndACatchAll_thenPortReceivesThemPassedThrough()` — assert
          the current date beside the arguments it already asserts
        - every remaining scenario in the class changes only how it builds its command and how it stubs or
          verifies the port — the two private `command(...)` helpers take a current date and the `record(...)`
          stubs and verifications take five arguments — and keeps its assertion; the affected methods are
          `whenCommandCarriesAssumedCurrency_thenPortReceivesThatCurrencyCode()`,
          `whenCommandIsNull_thenThrowsInvalidValueExceptionAndPortNeverCalled()`,
          `whenPortThrowsExpenseRecordingFailedException_thenExceptionPropagatesUnchanged()` and
          `whenPortReturnsNormally_thenOneInfoLineLoggedNamingCategoryCountAndCarryingNothingFromText()`

#### TDD Integration Red Phase

- [x] RI01 · `SpendingQueryRepositoryAdapter` · test: `SpendingQueryRepositoryAdapterTest` · covers: `create()`,
  `findPeriodsByMessageReference()`
    - `create()`:
        - given: a stored user and a query over a one-week period
          when: create() is called
          then: one `spending_query` row exists carrying that user, that reference, both period ends as dates and
          the query's instant, and the returned entity carries the generated id
        - given: a query whose user id names no stored user
          when: create() is called
          then: throws EntityNotFoundException naming the user, not a generic persistence failure
        - given: a mocked entity repository whose save throws a framework exception
          when: create() is called
          then: throws PersistenceFailedException carrying that exception as its cause
    - `findPeriodsByMessageReference()`:
        - given: two rows under one reference carrying the same period, written at different instants
          when: findPeriodsByMessageReference() is called
          then: that period is answered once (D8, D26)
        - given: three rows under one reference carrying three different periods, written out of order
          when: findPeriodsByMessageReference() is called
          then: all three are answered, oldest first by the instant they were written (D8)
        - given: rows under two different references for the same user
          when: findPeriodsByMessageReference() is called for one of them
          then: only that reference's periods are answered
        - given: two users each with a row under the same reference value
          when: findPeriodsByMessageReference() is called for one of them
          then: only that user's period is answered
        - given: a reference no row carries
          when: findPeriodsByMessageReference() is called
          then: returns an empty list
        - given: a mocked entity repository whose query throws a framework exception
          when: findPeriodsByMessageReference() is called
          then: throws PersistenceFailedException carrying that exception as its cause
- [x] RI02 · `ExpenseRepositoryAdapter` · test: `ExpenseRepositoryAdapterTest` · covers: `totalsByCurrency()`
    - `totalsByCurrency()`:
        - given: a stored user with four EUR expenses and one HUF expense inside the period
          when: totalsByCurrency() is called
          then: returns one total per currency, ordered by currency code, each carrying the summed minor units
          and the count of rows behind it, with no currency added to another
        - given: expenses at the period's first day 00:00:00 UTC and at its last day 23:59:59 UTC
          when: totalsByCurrency() is called
          then: both are counted — the bounds are inclusive of the whole first and last day (D4, D28)
        - given: expenses one microsecond before the period's first day and at 00:00:00 UTC on the day after its
          last
          when: totalsByCurrency() is called
          then: neither is counted
        - given: two stored users each with an expense inside the period
          when: totalsByCurrency() is called for one of them
          then: only that user's expense is counted
        - given: an `expense_proposal` row inside the period and no `expense` row
          when: totalsByCurrency() is called
          then: returns an empty list — a proposal awaiting confirmation counts towards nothing (D4)
        - given: a period the user has no expenses in
          when: totalsByCurrency() is called
          then: returns an empty list rather than throwing
        - given: a mocked entity repository whose query throws a framework exception
          when: totalsByCurrency() is called
          then: throws PersistenceFailedException carrying that exception as its cause
- [x] RI03 · `SummarizeSpendingMcpTool` · test: `SummarizeSpendingMcpToolTest` · covers:
  `tools/call summarize_spending` posted to `POST /mcp` · mocks: `SummarizeSpendingPort`
    - Happy Path:
        - given: the mocked port answers the period it was given
          when: the tool is called with a first and last day under a valid token carrying an `mrf` claim
          then: the port receives a command carrying the token's subject as its identity, the token's message
          reference, and both written days, and the result is a non-error whose text is the JSON
          `{"from":…,"to":…}` carrying the accepted period and no amount (D3)
    - Error Mapping:
        - given: the mocked port throws InvalidSpendingPeriodException
          when: the tool is called
          then: the result is a tool error carrying that exception's own message
        - given: the mocked port throws InvalidSpendingQueryException, or InvalidUserException
          when: the tool is called
          then: the result is a tool error naming an invalid request
        - given: the mocked port throws EntityNotFoundException naming an external id
          when: the tool is called
          then: the result is a tool error saying the user is unknown, carrying neither that id nor anything else
          from the exception
        - given: the mocked port throws PersistenceFailedException whose message names a table and a constraint
          when: the tool is called
          then: the result is a tool error saying the summary could not be recorded, naming neither
        - given: a token carrying no `mrf` claim
          when: the tool is called
          then: the result is the catch-all tool error saying the spending could not be summarized, and the port
          is never called (D25)
        - given: the mocked port throws a RuntimeException outside the failure table, with a secret message
          when: the tool is called
          then: the result is the catch-all tool error not carrying that message, rather than an exception
          reaching the transport
        - given: the mocked port throws any failure
          when: the tool is called
          then: a WARN line names the failure's class and message, and no line other than the debug received-call
          trace carries the token (D18)
    - Validation: `from` — blank; `to` — blank. Given the mocked port is stubbed to throw
      `InvalidSpendingPeriodException`, each case reaches the port carrying that written value unchanged and comes
      back as a tool error carrying the exception's own message — the tool reads neither day itself, unlike
      `ListCategoriesMcpTool`, whose command self-validates before the port
    - Validation: `from` — absent; `to` — absent. Both arguments are required in the published schema, so the
      framework refuses an absent one before the method is entered: the result is a tool error and the port is
      never called, the same way `CreateExpenseProposalMcpToolTest` covers its own required arguments
- [x] RI04 · `AiConnectorIntentExtractionAdapter` · test: `AiConnectorIntentExtractionAdapterTest` · covers:
  `extract()`
    - `extract()`:
        - given: the stub server answers an empty response and a request carrying a current date
          when: extract() is called
          then: the request the server received carries that date as `current_date` in ISO-8601 text
        - every existing scenario in the class changes only how it builds its `IntentExtractionRequest` — a
          current date joins the arguments — and keeps the assertion it already makes
- [x] RI05 · `IntentExtractionGrpcService` · test: `IntentExtractionGrpcServiceTest` · covers:
  `IntentExtractionService.ExtractIntents` · mocks: `ExtractIntentsPort`
    - Happy Path:
        - given: a tokened request whose `current_date` is a valid ISO-8601 date
          when: the RPC is called
          then: the port receives a command holding that date as a `LocalDate` and the RPC answers an empty
          response
    - Validation: `current_date` — blank, and a value that is not an ISO-8601 date (`27/07/2026`, `2026-13-01`).
      Each fails with INVALID_ARGUMENT — `Current date must not be blank` and `Current date must be an ISO-8601
      date` — and the port is never called
    - update: `invalidRequests()` — add the two `current_date` cases above; the existing text, grouping,
      catch-all and currency cases stay, taking their `current_date` from the ST33 fixture
    - update: `whenRequestCarriesDefaultCurrencyInAnyCasing_thenCommandHoldsItAsPresentUpperCasedCurrencyCode()` —
      same fixture change; the currency assertion is unchanged
- [x] RI06 · `AiExpenseRecordingAdapter` · test: `AiExpenseRecordingAdapterTest` · covers: `record()`
    - `record()`:
        - given: the provider is stubbed to call `summarize_spending` and the ledger answers it
          when: record() is called
          then: that tool call reaches the ledger under the turn's caller token, carrying the first and last day
          the provider asked for
    - update: `whenCalledWithLabelsTextAndCurrency_thenRequestCarriesSystemPromptUserMessageAndToolSchema()` —
      assert the user message states the current date it was called with, and add `summarize_spending` to the
      exhaustive tools-array assertion — ST34 publishes a third tool, so the existing
      `containsExactlyInAnyOrder` over the two names stops holding — asserting its arguments are `from` and `to`
    - update: `whenNoAssumedCurrency_thenUserMessageSaysUnrecordedAndNamesNoCurrencyCode()` — its assertion that
      no three-letter uppercase word appears now reads the new `Today is … (UTC).` line, which carries one;
      exclude that line from the search rather than weakening the assertion, so it still proves no currency code
      was rendered
    - update: `whenNoCallerTokenHeld_thenThrowsExpenseRecordingFailedException()` — calls `adapter.record`
      directly; add the current-date argument
    - every remaining scenario in the class changes only how it calls the port — the private `record(...)` helper
      and its `recordInEuros(...)` sibling pass a current date through the changed signature — and keeps its
      assertion

#### TDD System Test Red Phase

- [x] RS01 · `SummarizeSpendingMcpToolSystemTest` · covers: `POST /mcp`
    - Happy Path:
        - given: a user seeded through the wired repositories, and a valid token for them carrying a message
          reference
          when: `tools/call summarize_spending` is posted with a first and last day
          then: 200 with a non-error result carrying that period and no amount, and one `spending_query` row for
          that user under that reference holding both days
    - Unhappy Path:
        - given: the same seeded user
          when: `tools/call summarize_spending` is posted with a last day before the first
          then: 200 with a tool error saying the period ends before it starts, and no `spending_query` row for
          that user
- [x] RS02 · `McpAuthenticationSystemTest` · covers: `POST /mcp`
    - update: `publishedTools()` — add a `summarize_spending` entry whose arguments are `from` and `to`, both
      required, so
      `whenToolsListIsPostedWithValidToken_thenEachPublishedToolIsListedWithItsArgumentsAndNoIdentityArgument()`
      proves the new tool publishes no identity argument either (D12)
- [x] RS03 · `SummarizeSpendingReplySystemTest` · covers: `HandleIncomingMessagePort.handle()` — the running
  Telegram poll loop
    - Happy Path:
        - given: a user seeded with expenses in two currencies dated inside a period and one dated outside it,
          the stub connector armed to call `summarize_spending` for that period over `/mcp`, and the Telegram Bot
          API stubbed under this class's own bot token
          when: the poll loop picks up a text message asking what was spent
          then: the batch is confirmed, the extraction request carried today's date, and the reply text carries
          one line per currency with the totals of the in-period expenses only and no button markup — the whole
          loop from the model's tool call to the user's answer, which no lower layer proves
- [x] RS04 · `ReceiveTelegramMessageSystemTest` · covers: `HandleIncomingMessagePort.handle()` — the running
  Telegram poll loop
    - update: `whenRunningPollLoopPicksUpTextMessageUpdate_thenBatchIsConfirmedAndMessageIsPrinted()` — assert the
      extraction request carries a `current_date` that parses as today's UTC date, beside the grouping assertions
      it already makes
- [x] RS05 · `ExtractIntentsSystemTest` · covers: `IntentExtractionService.ExtractIntents`
    - Happy Path:
        - given: the ledger stub answers a `summarize_spending` call and the provider is stubbed to make one
          when: a tokened request carrying a current date arrives
          then: the RPC answers an empty response and the ledger received that call under the request's own
          token, carrying the days the provider asked for
    - update: `whenTokenedRequestArrives_thenRpcAnswersEmptyResponseAndLedgerReceivesOneToolCallUnderToken()` —
      build its request through the ST33 fixture so it carries a current date
    - update: `whenTokenedRequestArrives_thenRpcAnswersEmptyResponseAndLedgerReceivesBothToolCallsUnderToken()` —
      same fixture change

### Green Phase

#### TDD Unit Green Phase

- [x] GU01 · `SpendingPeriod` · test: `SpendingPeriodTest`
- [x] GU02 · `SpendingQuery` · test: `SpendingQueryTest` · after: GU01
- [x] GU03 · `SummarizeSpendingCommand` · test: `SummarizeSpendingCommandTest`
- [x] GU04 · `SummarizeSpendingUseCase` · test: `SummarizeSpendingUseCaseTest` · after: GU01, GU02, GU03
- [x] GU05 · `SpendingSummary` · test: `SpendingSummaryTest` · after: GU01
- [x] GU06 · `TurnReport` · test: `TurnReportTest` · after: GU05
- [x] GU07 · `IntentExtractionRequest` · test: `IntentExtractionRequestTest`
- [x] GU08 · `HandleIncomingMessageUseCase` · test: `HandleIncomingMessageUseCaseTest` · after: GU01, GU05, GU06,
  GU07
- [x] GU09 · `IntentProtoMapper` · test: `IntentProtoMapperTest` · after: GU07
- [x] GU10 · `TurnReportRenderer` · test: `TurnReportRendererTest` · after: GU01, GU05, GU06
- [x] GU11 · `ExtractIntentsCommand` · test: `ExtractIntentsCommandTest`
- [x] GU12 · `ExtractIntentsUseCase` · test: `ExtractIntentsUseCaseTest` · after: GU11

#### TDD Integration Green Phase

- [x] GI01 · `SpendingQueryRepositoryAdapter` · test: `SpendingQueryRepositoryAdapterTest` · after: GU01, GU02
- [x] GI02 · `ExpenseRepositoryAdapter` · test: `ExpenseRepositoryAdapterTest` · after: GU01
- [x] GI03 · `SummarizeSpendingMcpTool` · test: `SummarizeSpendingMcpToolTest` · after: GU03
- [x] GI04 · `AiConnectorIntentExtractionAdapter` · test: `AiConnectorIntentExtractionAdapterTest` · after: GU07,
  GU09
- [x] GI05 · `IntentExtractionGrpcService` · test: `IntentExtractionGrpcServiceTest` · after: GU11
- [x] GI06 · `AiExpenseRecordingAdapter` · test: `AiExpenseRecordingAdapterTest`

#### TDD System Test Green Phase

- [x] GS01 · `SummarizeSpendingMcpToolSystemTest` · covers: `POST /mcp`
- [x] GS02 · `McpAuthenticationSystemTest` · covers: `POST /mcp`
- [x] GS03 · `SummarizeSpendingReplySystemTest` · covers: `HandleIncomingMessagePort.handle()` — the running
  Telegram poll loop
- [x] GS04 · `ReceiveTelegramMessageSystemTest` · covers: `HandleIncomingMessagePort.handle()` — the running
  Telegram poll loop
- [x] GS05 · `ExtractIntentsSystemTest` · covers: `IntentExtractionService.ExtractIntents`

### Post-Implementation Steps

None. The one ADR candidate was raised as Q1 and declined; D28's rule lands in
[database.md](../../ledger-service/docs/contracts/out/database.md) when `archive-knowledge` rewrites it (D23).

## Open Questions / Blockers

- **Q1:** Record D28 as an ADR? The decision, stated as a fact: *a date-bounded read converts its period to
  instants in the adapter, in Java, and binds them as parameters — no `DATE` is cast to a timestamp in SQL, where
  the session's time zone would decide the result.* If no ADR is written,
  [database.md](../../ledger-service/docs/contracts/out/database.md) is where the rule has to be spelled out, and
  it is stated nowhere in the module today.
- A: No ADR — state it in `database.md` instead. **Post-Implementation Steps** therefore holds no ADR item, and
  the rule lands on the out-contract page when `archive-knowledge` rewrites it (D23).

- **Q2:** The design names three types the tree calls something else — `IntentProtoUtils` (the module has
  `IntentProtoMapper`), `CategoryRepository.findGroupingNames` (it is `GroupingRepository.findNamesWithCategories`)
  and a `ProposalReportTest` (no such class exists; `ProposalReportRendererTest` does). The steps above use the
  repository's names. Confirm that is right rather than the design's, or say which name should win.
- A: The repository's names win. The design's are drift from change 13, not a decision; no production type is
  renamed and the design file is left as it stands.

- **Blocker (refactor, 2026-08-05):** `TurnReportRenderer.render` can exceed its own 4000-character cap when the
  summaries fill the budget and the report also carries proposals, so D24's bound does not hold in that corner.
  `renderSummaries` budgets against the full `MAX_LENGTH` and may return up to 4000 characters; `render` then
  computes `remainingBudget` as what is left, which can reach zero or go negative, while `renderList` seeds its
  header before consulting the budget and always appends its `… and N more.` line. Worked case: a `RECORDED`
  report with ~150 single-day summaries plus three proposals renders 4026 characters. No test covers it, and
  Telegram's own limit is 4096, so nothing breaks for a user today. Left unfixed — the refactor phase may not
  change behaviour. Needs a decision: fix it here as a follow-up, or fold it into the next change.
- A: Archive now and fix it as a follow-up. The defect is unreachable for a user at 4026 against Telegram's own
  4096 limit, and a correct fix wants a red test pinning the bound with summaries and proposals together, which
  is its own small design-and-plan cycle rather than a wrap-up edit.

- **Note (refactor, 2026-08-05):** `SummarizeSpendingMcpToolTest`'s WARN scenario carries a `filteredOn(...)`
  assertion strictly weaker than the unfiltered one below it, plus the `RECEIVED_CALL_PREFIX` constant that
  exists only to serve it — leftovers from an earlier version of the scenario. Removing an assertion was outside
  the refactor phase's remit, so both stayed.

- **Note (run, 2026-08-05):** RI03's Validation group listed `from`/`to` *absent* as cases reaching the port,
  which is unreachable: both arguments are required in the published schema, so the MCP framework refuses an
  absent one before the method is entered. GI03 first made them `required = false` to satisfy those scenarios,
  which dropped both days from the published `required` array and failed `McpAuthenticationSystemTest`. Resolved
  in favour of the contract — the design's tool spec, RS02 and `CreateExpenseProposalMcpTool` all say required —
  and RI03's Validation group above was split into the blank cases (which reach the port) and the absent cases
  (which the framework refuses, port never called).

- **Note (run, 2026-08-05):** stabilization wrote RU09's first scenario early, as
  `IntentProtoMapperTest.whenRequestCarriesCurrentDate_thenGeneratedRequestCarriesItAsCurrentDate()`. It passes
  rather than failing red, because ST21's `setCurrentDate` is a one-line pass-through that left nothing stubbed.
  The test is correct and was kept; RU09 owns its three `update:` bullets, and GU09 has nothing left to implement.

## Review Findings

- **F1:** RI03's `Validation:` group claimed the four absent/blank cases come back as the error `SpendingPeriod.of`
  raises, which a mocked port never reaches.
- Resolution: mechanical
- Action: applied — restated the group as a stubbed `InvalidSpendingPeriodException`, proving the tool passes both
  written days through unread.

- **F2:** RU08's "every remaining scenario" list omitted two methods that exist in
  `HandleIncomingMessageUseCaseTest`.
- Resolution: mechanical
- Action: applied — added both names.

- **F3:** No step owned the test side of the two renames — `TelegramMessageDeliveryAdapterTest`'s report factories
  and `ProposalReportRendererTest`'s own rename, which RU10 assumed had happened.
- Resolution: mechanical
- Action: applied — extended ST13 with the adapter test's `TurnReport` and `TurnReportRenderer` uses, and ST20 with
  the test-class rename.

- **F4:** RI06's `update:` bullet left an exhaustive two-name tools-array assertion standing while ST34 publishes a
  third tool.
- Resolution: mechanical
- Action: applied — the bullet now names `summarize_spending` joining that list.

- **F5:** RS03, RS04, GS03 and GS04 wrote `covers:` in neither form the step formats allow.
- Resolution: mechanical
- Action: applied — all four now read `HandleIncomingMessagePort.handle()` with the poll loop as a trailing gloss.

- **F6:** ST33's proposed extra `String` overload of `request(...)` would reproduce a signature `RequestFixtures`
  already declares.
- Resolution: mechanical
- Action: applied — named the builder `requestWithCurrentDate(String currentDate)`.

- **F7:** ST31 extended `ExpenseRowUtils` without the Package Structure entry the module's testing conventions
  require.
- Resolution: mechanical
- Action: applied — added the entry update, worded as `ExpenseProposalRowUtils`'s is.
