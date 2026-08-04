# Plan: The Model Looks Up a Grouping's Categories

**Affected Modules:** `ledger-service`, `ai-connector-service`
**Design:** [The Model Looks Up a Grouping's Categories](design.md)

## Step-by-Step Implementation Map (To-Do List)

### Stabilization

#### API Contract

- [x] ST01 · Reshape `ExtractIntentsRequest` in `proto/intent_extraction.proto` and delete the `KnownCategory`
  message, exactly as the design's schema block states:
  ```proto
  message ExtractIntentsRequest {
    string text = 1;
    reserved 2;
    reserved "known_categories";
    // ISO 4217 code applied when the user states an amount but no currency. Absent means
    // an amount without a currency is not acted on.
    optional string default_currency = 3;
    // The groupings the caller's categories are filed under; must be non-empty. An expense is
    // filed under a category the `list_categories` tool answers for one of these, never under
    // the grouping itself.
    repeated string category_groupings = 4;
    // The grouping to fall back on when no other fits; must be non-blank and one of
    // `category_groupings`, so a fit always exists.
    string catch_all_grouping = 5;
  }
  ```

#### Interface-First / Build Stabilization

New-method stubs carry a short inline comment describing the implementation intent; an existing method whose
signature changes keeps its logic and gains a `TODO` at the insertion point.

**Interface & Signature Sync**

- [x] ST02 · `ledger-service` — add `public static String catchAllGroupingName()` to `domain/value/Category`,
  answering `"Miscellaneous"`, and build the `Miscellaneous` grouping in `defaults()` from that same constant
- [x] ST03 · `ledger-service` — delete `application/dto/KnownCategory`,
  `adapter/persistence/KnownCategoryProjection`, and their tests `application/dto/KnownCategoryTest`; delete
  `ai-connector-service`'s `application/dto/KnownCategory` and `application/dto/KnownCategoryTest`. One `git rm`
  naming all five files
- [x] ST04 · `ledger-service` — on `application/port/CategoryRepository`, replace
  `List<KnownCategory> findKnownCategories(long userId)` with `List<String> findGroupingNames(long userId)`,
  keeping the `@throws PersistenceFailedException` javadoc. `findByUserIdAndName` and `findChildNames` are
  untouched
- [x] ST05 · `ledger-service` — reshape `application/dto/IntentExtractionRequest` to
  `(String text, List<String> categoryGroupings, String catchAllGrouping, Optional<CurrencyCode> defaultCurrency,
  String userExternalId, MessageReference messageReference)`, keeping the existing text / currency / external-id /
  reference checks and adding a `TODO` where the grouping and catch-all invariants go (RU02 implements them);
  keep `List.copyOf` on the grouping list so the record still compiles and copies
- [x] ST06 · `ledger-service` — add the inbound port `application/port/ListCategoriesPort` with
  `List<String> list(ListCategoriesCommand command)` and `@throws` javadoc for `InvalidCategoryException`,
  `EntityNotFoundException` and `PersistenceFailedException`
- [x] ST07 · `ledger-service` — add `application/dto/ListCategoriesCommand`, the record
  `(AuthenticatedUserId userId, String parentCategoryName)`, with a `TODO` in the compact constructor for the
  self-validation RU03 covers. The name is fixed by the ArchUnit rule
  `inboundPortCommandsAreNamedAfterTheirUseCase`
- [x] ST08 · `ledger-service` — stub `application/usecase/ListCategoriesUseCase implements ListCategoriesPort`,
  taking `UserRepository`, `CategoryRepository` and `LoggerFactory`:
  ```java
  public List<String> list(ListCategoriesCommand command) {
      // refuses an absent command, resolves the token's subject to a stored user, finds the caller's
      // categories carrying the name, refuses when none is a grouping, and answers that grouping's
      // child names ordered by name
      return null;
  }
  ```
- [x] ST09 · `ledger-service` — in `adapter/persistence/CategoryEntityRepository`, replace the
  `findKnownCategories` projection query with
  ```sql
  SELECT c.name
  FROM category c
  WHERE c.user_id = :userId AND c.parent_id IS NULL
  ORDER BY c.name
  ```
  declared as `List<String> findGroupingNames(@Param("userId") Long userId)`, and rename `findByParentId` to
  `findByParentIdOrderByName`
- [x] ST10 · `ledger-service` — in `adapter/persistence/CategoryRepositoryAdapter`, replace
  `findKnownCategories` with `findGroupingNames` delegating to the new query inside the existing
  `PersistenceFailedException` wrapper, and point `findChildNames` at `findByParentIdOrderByName`
- [x] ST11 · `ledger-service` — in `application/usecase/HandleIncomingMessageUseCase`, call `findGroupingNames`,
  add a `TODO` where the catch-all is resolved (`Category.catchAllGroupingName()` when the groupings read carry
  it, the first grouping otherwise — GU05 implements it), and pass both new components into
  `IntentExtractionRequest` so the module compiles
- [x] ST12 · `ledger-service` — in `adapter/aiconnector/IntentProtoUtils.toProtoRequest`, call
  `addAllCategoryGroupings(...)` and `setCatchAllGrouping(...)`, and delete the private `toProtoKnownCategory`
- [x] ST13 · `ledger-service` — add `adapter/mcp/ListCategoriesToolResponse`, the record
  `(String parentCategory, List<String> categories)`
- [x] ST14 · `ledger-service` — stub `adapter/mcp/ListCategoriesMcpTool` as a `@Component` taking
  `ListCategoriesPort`, `JsonMapper` and `LoggerFactory`, declared exactly as the design's tool spec writes it:
  ```java
  @McpTool(
          name = "list_categories",
          description = "Lists the categories filed under one of the caller's groupings. An expense is filed "
                  + "under one of these, never under the grouping itself.")
  public CallToolResult listCategories(
          @McpToolParam(description = "the grouping's name, exactly as it was offered") String parentCategory) {
      // logs the call at debug, reads the caller off the token, invokes ListCategoriesPort, serializes
      // ListCategoriesToolResponse, and renders every failure as an isError result logged at warn
      return null;
  }
  ```
- [x] ST15 · `ledger-service` — on `adapter/mcp/CreateExpenseProposalMcpTool`, drop `required = false` from
  `parentCategory` and describe it as *"the grouping the category is filed under, exactly as `list_categories`
  was asked for it"*; drop "never a grouping" from the `category` description
- [x] ST16 · `ledger-service` — register `ListCategoriesUseCase` as a `@Bean` returning `ListCategoriesPort` in
  `adapter/config/UseCaseConfiguration`
- [x] ST17 · `ai-connector-service` — reshape `application/dto/ExtractIntentsCommand` to
  `(String text, List<String> categoryGroupings, String catchAllGrouping, Optional<CurrencyCode> defaultCurrency)`,
  keeping the text and currency checks and adding a `TODO` for the grouping and catch-all invariants RU08 covers
- [x] ST18 · `ai-connector-service` — change `application/port/ExpenseRecordingPort.record` to
  `record(String text, List<String> categoryGroupings, String catchAllGrouping,
  Optional<CurrencyCode> assumedCurrency)`, keeping its `@throws` javadoc
- [x] ST19 · `ai-connector-service` — in `application/usecase/ExtractIntentsUseCase`, pass
  `command.categoryGroupings()` and `command.catchAllGrouping()` straight through, delete the label mapping, and
  count groupings in the info line
- [x] ST20 · `ai-connector-service` — in `adapter/grpc/IntentExtractionGrpcService`, read
  `getCategoryGroupingsList()` and `getCatchAllGrouping()` into the command and add a `TODO` in `rejectIfInvalid`
  where the four new `INVALID_ARGUMENT` clauses go (RI05 covers them); leave the text and currency clauses intact
- [x] ST21 · `ai-connector-service` — in `adapter/ai/AiExpenseRecordingAdapter`, rename the port parameters and
  render the template with `categoryGroupings` (the names joined by `", "`), `catchAllGrouping`,
  `assumedCurrency` and `text`

**Configuration**

- [x] ST23 · `ai-connector-service` — replace `src/main/resources/prompts/user-message.st` with:
  ```
  Category groupings the user has: {categoryGroupings}

  Every expense is filed under a category inside one of these, never under a grouping itself. Pick the grouping
  first, then call the list_categories tool for it and file the expense under one of the categories it answers,
  sending that grouping as the parent category. If no grouping fits the expense, use {catchAllGrouping}. Never
  invent a name.

  Currency to assume when the user states an amount without one: {assumedCurrency}

  Message: {text}
  ```
- [x] ST24 · `ai-connector-service` — two edits to `src/main/resources/prompts/record-expenses.st`: replace
  "from the message and the categories you were given" with "from the message and the categories the tools answer
  for the groupings you were given", and replace the retry paragraph with:
  ```
  A refused recording call answers with an error saying what to retry with. Correct that call and try the same
  expense once more; if it is refused again, leave that expense and go on to the next one. A refused category
  lookup costs the expense nothing — correct the grouping's name and ask again.
  ```

**Shared Test Infrastructure**

- [x] ST25 · `ledger-service` — add `listCategories(String parentCategory)` to `bot.finance.common.McpRequests`,
  a `tools/call` JSON-RPC body for `list_categories` shaped like the existing `createExpenseProposal` builder and
  using the same `jsonString` helper, so RI02 and RS01 share one body builder
- [x] ST26 · `ai-connector-service` — rebuild `bot.finance.ai.common.RequestFixtures` around the new request:
  a `DEFAULT_CATEGORY_GROUPINGS` list of plain names, a `DEFAULT_CATCH_ALL` that is one of them, and `request(…)`
  overloads setting `category_groupings` and `catch_all_grouping`. Drop `knownCategory(...)` and
  `DEFAULT_KNOWN_CATEGORIES`. Keep every default grouping name clear of an all-caps three-letter word, which
  `AiExpenseRecordingAdapterTest`'s no-currency assertion would read as a currency code
- [x] ST27 · `ai-connector-service` — in `bot.finance.ai.common.McpLedgerStubs`: publish `list_categories`
  alongside `create_expense_proposal` in the stubbed `tools/list` result, with `parentCategory` in
  `create_expense_proposal`'s `required` array, and add a `stubListCategoriesAnswering(...)` scenario that
  answers a `list_categories` call with a categories list and a `stubListCategoriesRefused()` that answers an
  `isError` result. The existing scenarios keep matching `create_expense_proposal` on `$.params.name`
- [x] ST28 · `ai-connector-service` — widen `bot.finance.ai.common.CapturedRequestUtils` so a caller can read
  back the MCP requests for a named tool: add `toolCallRequests(String toolName)` and have the existing
  no-argument `toolCallRequests()` delegate to it with `create_expense_proposal`. RI06 and RS05 both read back
  `list_categories` calls, and neither step is scoped to write a shared reader

**Documentation**

- [x] ST29 · `ledger-service` — in [code-style.md](../../ledger-service/docs/conventions/code-style.md)'s Domain
  section, extend the line "A reference to something that is not stored raises a not-found domain exception" with
  the split D33 states: a not-found exception is raised for an **id**, while a **name the caller chose** that
  resolves to nothing raises that concept's invalid-argument exception. Re-pad nothing else on the page

- [x] ST22 · Confirm both modules' architecture-enforcement tests still pass —
  `bot.finance.architecture.CleanArchitectureTest` and `bot.finance.ai.architecture.CleanArchitectureTest`

### Red Phase

#### TDD Unit Red Phase

- [x] RU01 · `Category` · test: `CategoryTest` · covers: `catchAllGroupingName()`, `defaults()`
    - `catchAllGroupingName()`:
        - given: nothing
          when: catchAllGroupingName() is called
          then: returns "Miscellaneous"
        - given: the default tree
          when: catchAllGroupingName() is compared against the names defaults() returns
          then: exactly one grouping carries that name, so the designated catch-all always exists in a fresh
          catalogue
    - `defaults()`:
        - update: `whenDefaultsIsCalled_thenReturnsThe20PredefinedGroupsByNameAndInOrder()` — leave the twenty
          names as they are; the grouping formerly written as the literal `"Miscellaneous"` is now built from
          `catchAllGroupingName()`, and the assertion must keep proving the rendered name is unchanged
- [x] RU02 · `IntentExtractionRequest` · test: `IntentExtractionRequestTest` · covers: the compact constructor
    - `IntentExtractionRequest`:
        - given: a text, two grouping names, a catch-all that is one of them, an empty currency, an external id
          and a reference
          when: the record is constructed
          then: every component reads back unchanged and `categoryGroupings()` is unmodifiable
        - given: a category-groupings list that is null, or empty
          when: the record is constructed
          then: throws InvalidExtractionRequestException
        - given: a category-groupings list carrying a null element, or a blank element
          when: the record is constructed
          then: throws InvalidExtractionRequestException
        - given: a catch-all grouping that is null, or blank
          when: the record is constructed
          then: throws InvalidExtractionRequestException
        - given: a catch-all grouping that is not one of the category groupings
          when: the record is constructed
          then: throws InvalidExtractionRequestException
        - given: a mutable grouping list handed to the constructor, modified afterwards
          when: categoryGroupings() is read
          then: it is unchanged
        - update: `whenKnownCategoriesIsNullOrEmpty_thenThrowsInvalidExtractionRequestException()` — replace with
          the grouping-list scenarios above; the parameterized source `nullOrEmptyCategories` goes with it
        - update: `whenKnownCategoriesContainsNullEntry_thenThrowsInvalidExtractionRequestException()` — replace
          with the null/blank-element scenario above
        - update: `whenKnownCategoriesListIsModifiedAfterConstruction_thenKnownCategoriesIsUnchanged()` — assert
          the copy on the `categoryGroupings` component
        - update: `whenTextOneCategoryEmptyCurrencyAndUserExternalIdAreValid_thenEveryComponentReadsBackUnchangedAndKnownCategoriesIsUnmodifiable()`
          — assert the unmodifiability of the `categoryGroupings` component and read back the `catchAllGrouping`
          one
        - every remaining scenario in the class changes only its constructor arguments — grouping names and a
          catch-all in place of `KnownCategory` values — and keeps the assertion it already makes
- [x] RU03 · `ListCategoriesCommand` · test: `ListCategoriesCommandTest` · covers: the compact constructor
    - `ListCategoriesCommand`:
        - given: an authenticated user id and a non-blank parent category name
          when: the record is constructed
          then: both components read back unchanged
        - given: a null authenticated user id
          when: the record is constructed
          then: throws InvalidCategoryException
        - given: a parent category name that is null, empty, or whitespace only
          when: the record is constructed
          then: throws InvalidCategoryException
- [x] RU04 · `ListCategoriesUseCase` · test: `ListCategoriesUseCaseTest` · covers: `list()`
    - `list()`:
        - given: nothing stubbed
          when: list(null) is called
          then: throws InvalidCategoryException and neither repository is touched
        - given: the user repository answers empty for the command's external id
          when: list() is called
          then: throws EntityNotFoundException and the category repository is never called
        - given: a stored user whose findByUserIdAndName answers an empty list for the name
          when: list() is called
          then: throws InvalidCategoryException whose message names the grouping asked for and says none is
          stored for this user
        - given: a stored user whose findByUserIdAndName answers only candidates carrying a parent name
          when: list() is called
          then: throws InvalidCategoryException saying that name is a category, not a grouping, and
          findChildNames is never called
        - given: a stored user whose findByUserIdAndName answers two candidates for the name — one with a parent
          name and one without — as `Travel` resolves in the default catalogue
          when: list() is called
          then: findChildNames is called with the parentless candidate's id and its answer is returned
        - given: a stored user whose grouping has children
          when: list() is called
          then: returns those child names, ordered by name, exactly as the repository answered them
        - given: a stored user whose id differs from the external id on the command
          when: list() is called
          then: findByUserIdAndName receives that stored user's id and the command's name, and findChildNames
          receives the id of the candidate that read answered — so a caller reaches no other user's categories
        - given: a stored user whose grouping has no children
          when: list() is called
          then: returns an empty list rather than throwing
        - given: findByUserIdAndName throws PersistenceFailedException
          when: list() is called
          then: the exception propagates unchanged and findChildNames is never called
        - given: findChildNames throws PersistenceFailedException
          when: list() is called
          then: the exception propagates unchanged
- [x] RU05 · `HandleIncomingMessageUseCase` · test: `HandleIncomingMessageUseCaseTest` · covers: `handle()`
    - `handle()`:
        - given: a stored user whose grouping names include `Category.catchAllGroupingName()`
          when: handle() is called
          then: the extraction request carries exactly those grouping names and that name as its catch-all
        - given: a stored user whose grouping names do not include `Category.catchAllGroupingName()`
          when: handle() is called
          then: the extraction request's catch-all is the first grouping read, so the request's own invariant
          holds for a catalogue that never carried the designated name
        - given: a stored user whose findGroupingNames answers an empty list
          when: handle() is called
          then: InvalidExtractionRequestException propagates from the request's own constructor and the
          extraction port is never called
        - update: `stubKnownUserAndCategories()` — stub `findGroupingNames` with grouping names rather than
          `findKnownCategories` with `KnownCategory` values, and return that list
        - update: `whenHandleIsCalled_thenInitializeAndExtractionAndLookupCarryUserAndReference()` — verify
          `findGroupingNames` and assert the request's `categoryGroupings` and `catchAllGrouping` components
        - update: `whenFindKnownCategoriesThrowsPersistenceFailedException_thenExceptionPropagatesAndExtractionPortUntouched()`
          — stub the failure on `findGroupingNames`, renaming the method for the port it now names
- [x] RU06 · `IntentProtoUtils` · test: `IntentProtoUtilsTest` · covers: `toProtoRequest()`
    - `toProtoRequest()`:
        - given: a request carrying three grouping names and a catch-all
          when: toProtoRequest() is called
          then: the generated request's `category_groupings` holds the three names in order and
          `catch_all_grouping` holds the catch-all
        - given: any request
          when: the generated request's descriptor is inspected
          then: it declares no `known_categories` field, so the reserved tag is never filled
        - update: `whenRequestCarriesTextThreeCategoriesAndDefaultCurrencyEur_thenGeneratedRequestCarriesThemAll()`
          — assert grouping names and the catch-all beside the text and `default_currency`
        - update: `whenRequestCarriesTwoKnownCategories_thenGeneratedRequestHoldsTwoKnownCategoryMessagesInOrder()`
          — replace with the ordering assertion on `category_groupings` above
        - update: `whenRequestDefaultCurrencyIsEmpty_thenGeneratedRequestReportsHasDefaultCurrencyAsFalse()` —
          build the request from grouping names; the currency assertion is unchanged
- [x] RU07 · `ExpenseProposalToolUtils` · test: `ExpenseProposalToolUtilsTest` · covers: `toCommand()`
    - `toCommand()`:
        - given: a request whose parentCategory is null, empty, or whitespace only
          when: toCommand() is called
          then: throws InvalidExpenseProposalException with the message "expense proposal request has no parent
          category"
        - given: a request whose parentCategory is absent and whose amount is also malformed
          when: toCommand() is called
          then: the amount's own failure is raised — the parent-category check sits beside the amount check and
          neither reaches a lookup
        - given: a request carrying a non-blank parentCategory
          when: toCommand() is called
          then: the command's parentCategoryName is that name, present
        - update: `whenParentCategoryIsNullOrBlank_thenCommandParentCategoryNameIsEmpty()` — replace with the
          refusal scenario above; its `blankParentCategories()` source becomes the refusal's parameter source
- [x] RU08 · `ExtractIntentsCommand` · test: `ExtractIntentsCommandTest` · covers: the compact constructor
    - `ExtractIntentsCommand`:
        - given: a text, grouping names, a catch-all that is one of them and an empty currency
          when: the record is constructed
          then: every component reads back unchanged and `categoryGroupings()` is unmodifiable
        - given: a category-groupings list that is null, empty, carries a null element, or carries a blank element
          when: the record is constructed
          then: throws InvalidValueException
        - given: a catch-all that is null, blank, or not among the category groupings
          when: the record is constructed
          then: throws InvalidValueException
        - update: `whenNonBlankTextAndCategoryList_thenCommandExposesBoth()` — read back the
          `categoryGroupings` and `catchAllGrouping` components
        - update: `whenCategoryListIsEmpty_thenThrowsInvalidValueException()` — assert on the grouping list
        - update: `whenCategoryListIsNull_thenThrowsInvalidValueException()` — assert on the grouping list
        - update: `whenCategoryListContainsNullElement_thenThrowsInvalidValueException()` — assert on the
          grouping list, and extend to a blank element
        - update: `whenMutableCategoryListModifiedAfterConstruction_thenCommandListUnchangedAndOwnListImmutable()`
          — assert the copy and the unmodifiability on the `categoryGroupings` component
        - every remaining scenario in the class changes only its constructor arguments — grouping names and a
          catch-all in place of `KnownCategory` values, with the class's `KNOWN_CATEGORIES` constant becoming a
          grouping-name list and a catch-all — and keeps the assertion it already makes
- [x] RU09 · `ExtractIntentsUseCase` · test: `ExtractIntentsUseCaseTest` · covers: `extractIntents()`
    - `extractIntents()`:
        - given: a command carrying three grouping names and a catch-all
          when: extractIntents() is called
          then: the port receives that text, those names in the command's order with no `>` composed into any of
          them, that catch-all, and the command's currency
        - update: `whenTextThreeKnownCategoriesAndNoAssumedCurrency_thenPortCalledOnceWithTextLabelsAndEmptyCurrency()`
          — replace with the pass-through scenario above
        - update: `whenKnownCategoriesShareNameUnderDifferentGroupings_thenBothLabelsReachPortDistinctAndInOrder()`
          — delete; groupings are unique per user, so a name collision no longer reaches this class
        - update: `whenPortReturnsNormally_thenOneInfoLineLoggedNamingCategoryCountAndCarryingNothingFromText()` —
          assert the count of groupings offered
        - every remaining scenario in the class changes only how it builds its command and how it stubs or
          verifies the port — the two private `command(...)` helpers take grouping names and a catch-all, and the
          `record(...)` stubs and verifications take four arguments — and keeps the assertion it already makes

#### TDD Integration Red Phase

- [x] RI01 · `CategoryRepositoryAdapter` · test: `CategoryRepositoryAdapterTest` · covers: `findGroupingNames()`,
  `findChildNames()`
    - `findGroupingNames()`:
        - given: a stored user with three groupings stored out of alphabetical order, each holding a child
          when: findGroupingNames() is called
          then: returns exactly the three grouping names, sorted by name, with no child name among them
        - given: a stored user with a grouping that has no children
          when: findGroupingNames() is called
          then: that grouping is present — a childless grouping is still a grouping
        - given: two stored users each owning a grouping
          when: findGroupingNames() is called for one of them
          then: only that user's grouping name is returned
        - given: a stored user with no categories at all
          when: findGroupingNames() is called
          then: returns an empty list
        - given: a mocked entity repository whose query throws a framework exception
          when: findGroupingNames() is called
          then: throws PersistenceFailedException carrying that exception as its cause
        - update: `whenFindKnownCategoriesHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionCarryingFrameworkExceptionAsCause()`
          — replace with the mocked-store scenario above, stubbing the new query method
        - update: `whenCalledForAStoredUserWithOneGroupingAndTwoChildren_thenReturnsBothChildrenWithTheirGroupingsName()`
          — delete with the rest of the `FindKnownCategories` nested class; `findGroupingNames`' own nested class
          replaces it
        - update: `whenCalledForAStoredUserWithAChildlessGrouping_thenTheGroupingItselfIsAbsent()` — delete, its
          inverse is the childless-grouping scenario above
        - update: `whenCalledForOneOfTwoUsersEachOwningACategory_thenReturnsOnlyThatUsersCategory()` — delete,
          covered by the two-user scenario above
        - update: `whenCalledForAStoredUserWithNoCategories_thenReturnsEmptyList()` — delete, covered above
    - `findChildNames()`:
        - update: `whenCalledForAGroupingWithThreeChildren_thenReturnsTheThreeNames()` — assert
          `containsExactly` in name order rather than `containsExactlyInAnyOrder`, and store the three children
          out of alphabetical order so the ordering is proved
- [x] RI02 · `ListCategoriesMcpTool` · test: `ListCategoriesMcpToolTest` · covers: `tools/call list_categories`
  posted to `POST /mcp` · mocks: `ListCategoriesPort`
    - Happy Path:
        - given: the mocked port answers three category names
          when: the tool is called with a grouping's name under a valid token
          then: the port receives a command carrying the token's subject as its identity and that name, and the
          result is a non-error whose text is the JSON `{"parentCategory":…,"categories":[…]}` carrying both
        - given: the mocked port answers an empty list
          when: the tool is called
          then: the result is a non-error carrying an empty `categories` array, never a tool error
        - given: a token carrying no `mrf` claim
          when: the tool is called
          then: the categories are still answered — the tool reads no message reference
    - Error Mapping:
        - given: the mocked port throws InvalidCategoryException
          when: the tool is called
          then: the result is a tool error carrying that exception's own message
        - given: the mocked port throws InvalidUserException
          when: the tool is called
          then: the result is a tool error naming an invalid request
        - given: the mocked port throws EntityNotFoundException naming an external id
          when: the tool is called
          then: the result is a tool error saying the user is unknown, carrying neither that id nor anything
          else from the exception
        - given: the mocked port throws PersistenceFailedException whose message names a table and a constraint
          when: the tool is called
          then: the result is a tool error saying the categories could not be read, naming neither
        - given: the mocked port throws a RuntimeException outside the failure table, with a secret message
          when: the tool is called
          then: the result is a generic tool error not carrying that message, rather than an exception reaching
          the transport
        - given: the mocked port throws any failure
          when: the tool is called
          then: a WARN line names the failure's class and message, and no line other than the debug
          received-call trace carries the grouping name or the token
    - Validation: `parentCategory` — absent, blank; each is a tool error and the port is never called
- [x] RI03 · `CreateExpenseProposalMcpTool` · test: `CreateExpenseProposalMcpToolTest` · covers:
  `tools/call create_expense_proposal` posted to `POST /mcp` · mocks: `CreateExpenseProposalPort`
    - Validation: `parentCategory` — absent and blank are each a tool error naming the missing parent category,
      with the port never called
    - update: `postCreateExpenseProposal()` — every existing scenario passes `null` for `parentCategory`; give
      each a real grouping name so it keeps testing what it was written for rather than failing on the new
      refusal
    - update: `whenAmountCannotBeBoundToString_thenFrameworksOwnBindingFailureReportedAndPortNeverCalled()` —
      its inline JSON body sends `"parentCategory": null`; give it a grouping name
    - update: `whenAmountIsSentAsJsonNumber_thenToolErrorIsRefusedAndPortNeverCalled()` — same inline body fix
- [x] RI04 · `AiConnectorIntentExtractionAdapter` · test: `AiConnectorIntentExtractionAdapterTest` · covers:
  `extract()`
    - `extract()`:
        - given: the stub server answers an empty response and a request carrying two grouping names and a
          catch-all
          when: extract() is called
          then: the request the server received carries `category_groupings` in order and `catch_all_grouping`,
          and its `known_categories` field does not exist on the descriptor
        - update: `whenStubServerAnswersEmptyResponse_thenReturnsAndServerReceivedRequestFields()` — replace its
          `KnownCategory` assertions with the grouping-name and catch-all assertions above
        - update: `whenExtractIsCalled_thenMetadataCarriesBearerTokenWithSubClaimAsUserExternalId()` — build the
          request from grouping names; the token assertion is unchanged
        - every remaining scenario in the class changes only how it builds its `IntentExtractionRequest` —
          grouping names and a catch-all in place of `KnownCategory` values — and keeps the assertion it already
          makes
- [x] RI05 · `IntentExtractionGrpcService` · test: `IntentExtractionGrpcServiceTest` · covers:
  `IntentExtractionService.ExtractIntents` · mocks: `ExtractIntentsPort`
    - Happy Path:
        - given: a tokened request carrying a text, two grouping names and a catch-all that is one of them
          when: the RPC is called
          then: the port receives a command holding those names in order and that catch-all, and the RPC answers
          an empty response
    - Validation: `category_groupings` — empty, an entry that is blank; `catch_all_grouping` — blank, and a
      value that is not among the category groupings. Each fails with INVALID_ARGUMENT and the port is never
      called
    - update: `whenRequestCarriesTextAndTwoKnownCategories_thenPortReceivesOrderedCategoriesAndResponseIsEmpty()`
      — replace with the happy-path scenario above
    - update: `invalidRequests()` — replace the two `known_categories` entries with the four grouping and
      catch-all cases above; the text and currency cases stay, reading `DEFAULT_CATEGORY_GROUPINGS` and
      `DEFAULT_CATCH_ALL` in place of the dropped `DEFAULT_KNOWN_CATEGORIES`
    - update: `whenRequestCarriesDefaultCurrencyInAnyCasing_thenCommandHoldsItAsPresentUpperCasedCurrencyCode()`
      — same fixture swap; the currency assertion is unchanged
- [x] RI06 · `AiExpenseRecordingAdapter` · test: `AiExpenseRecordingAdapterTest` · covers: `record()`
    - `record()`:
        - given: a turn where the provider first calls `list_categories`, then `create_expense_proposal`, and the
          ledger answers both
          when: record() is called
          then: both tool calls reach the ledger under the turn's caller token, and the lookup's answer reaches
          the provider as that call's result
        - given: the ledger answers a `list_categories` call with an isError result, and the provider then
          corrects the grouping and records the expense
          when: record() is called
          then: the call returns without throwing and the create call still reaches the ledger — a refused lookup
          ends nothing
        - update: `record()` — the private helper now passes grouping names and a catch-all through the changed
          port signature; rename its `KNOWN_CATEGORY_LABELS` constant to a grouping-name list with no `>` in it
        - update: `recordInEuros()` — same signature change
        - update: `whenCalledWithLabelsTextAndCurrency_thenRequestCarriesSystemPromptUserMessageAndToolSchema()` —
          assert the user message carries the grouping names and the catch-all name and no `>`; assert the tool
          schema names both `create_expense_proposal` and `list_categories`, the latter with `parentCategory` as
          its only argument
        - update: `whenNoAssumedCurrency_thenUserMessageSaysUnrecordedAndNamesNoCurrencyCode()` — its assertion
          that no three-letter uppercase word appears now also reads the rendered grouping names, so it must use
          the fixture's grouping names from ST26
        - update: `whenNoCallerTokenHeld_thenThrowsExpenseRecordingFailedException()` — calls `adapter.record`
          directly; update the argument list

#### TDD System Test Red Phase

- [x] RS01 · `ListCategoriesMcpToolSystemTest` · covers: `POST /mcp`
    - Happy Path:
        - given: a user seeded through the wired `UserRepository` with `Category.defaults()`, and a valid token
          for them
          when: `tools/call list_categories` is posted naming `Groceries`
          then: 200 with a non-error result whose text names `Groceries` and carries exactly its three seeded
          children, sorted by name
    - Unhappy Path:
        - given: the same seeded user
          when: `tools/call list_categories` is posted naming a category rather than a grouping —
          `Supermarkets`
          then: 200 with a tool error saying it is a category, not a grouping
- [x] RS02 · `CreateExpenseProposalMcpToolSystemTest` · covers: `POST /mcp`
    - Unhappy Path:
        - given: a user seeded with `Category.defaults()`
          when: `tools/call create_expense_proposal` is posted with no `parentCategory`
          then: 200 with a tool error naming the missing parent category, and no `expense_proposal` row for that
          user
    - update: `whenToolCallNamesChildCategory_thenResponseCarriesStoredProposalAndRowIsWritten()` — send
      `Groceries` as `parentCategory` beside `Supermarkets`
    - update: `whenToolCallNamesGrouping_thenResponseIsToolErrorNamingChildrenAndNoRowIsWritten()` — with
      `parentCategory` required the grouping branch is unreachable through the tool, so re-aim the scenario at the
      parent-mismatch refusal: post `Supermarkets` under `parentCategory` `Dining` and assert the tool error reads
      "no category named Supermarkets under parent Dining is stored for this user", with no `expense_proposal` row
      for that user
- [x] RS03 · `McpAuthenticationSystemTest` · covers: `POST /mcp`
    - update: `whenToolsListIsPostedWithValidToken_thenCreateExpenseProposalToolIsListedWithSixArgumentsAndNoIdentityArgument()`
      — assert `parentCategory` is in `create_expense_proposal`'s `required` array beside `amount`, and that
      `list_categories` is listed with `parentCategory` as its only argument and no identity argument
    - update: `whenToolsCallIsPostedWithRejectedToken_thenUnauthorizedWithNoToolResultAndNoRowWritten()` — its
      body sends `null` for `parentCategory`; give it a grouping name so the 401 is proved by the filter chain
      rather than by a malformed body
- [x] RS04 · `ReceiveTelegramMessageSystemTest` · covers: the running Telegram poll loop
    - update: `whenRunningPollLoopPicksUpTextMessageUpdate_thenBatchIsConfirmedAndMessageIsPrinted()` — assert
      the extraction request carries the twenty grouping names `Category.defaults()` seeds, sorted by name, and
      `Category.catchAllGroupingName()` as its catch-all, in place of the child-and-parent `KnownCategory` list
    - update: `stubTelegram()` — the armed MCP callback posts a `create_expense_proposal` body with `null` for
      `parentCategory`; send `Groceries` so the callback still records a proposal
- [x] RS05 · `ExtractIntentsSystemTest` · covers: `IntentExtractionService.ExtractIntents`
    - Happy Path:
        - given: the ledger stub answers a `list_categories` call and then a `create_expense_proposal` call, and
          the provider is stubbed to make both in that order
          when: a tokened request carrying grouping names and a catch-all arrives
          then: the RPC answers an empty response, the ledger received both calls under the request's own token,
          and the lookup carried the grouping name the provider asked for
    - update: `whenTokenedRequestArrives_thenRpcAnswersEmptyResponseAndLedgerReceivesOneToolCallUnderToken()` —
      its create call must now carry `parentCategory`, since the stubbed tool list declares it required

### Green Phase

#### TDD Unit Green Phase

- [ ] GU01 · `Category` · test: `CategoryTest`
- [ ] GU02 · `IntentExtractionRequest` · test: `IntentExtractionRequestTest`
- [ ] GU03 · `ListCategoriesCommand` · test: `ListCategoriesCommandTest`
- [ ] GU04 · `ListCategoriesUseCase` · test: `ListCategoriesUseCaseTest` · after: GU03
- [ ] GU05 · `HandleIncomingMessageUseCase` · test: `HandleIncomingMessageUseCaseTest` · after: GU01, GU02
- [ ] GU06 · `IntentProtoUtils` · test: `IntentProtoUtilsTest` · after: GU02
- [ ] GU07 · `ExpenseProposalToolUtils` · test: `ExpenseProposalToolUtilsTest`
- [ ] GU08 · `ExtractIntentsCommand` · test: `ExtractIntentsCommandTest`
- [ ] GU09 · `ExtractIntentsUseCase` · test: `ExtractIntentsUseCaseTest` · after: GU08

#### TDD Integration Green Phase

- [ ] GI01 · `CategoryRepositoryAdapter` · test: `CategoryRepositoryAdapterTest`
- [ ] GI02 · `ListCategoriesMcpTool` · test: `ListCategoriesMcpToolTest` · after: GU03
- [ ] GI03 · `CreateExpenseProposalMcpTool` · test: `CreateExpenseProposalMcpToolTest` · after: GU07
- [ ] GI04 · `AiConnectorIntentExtractionAdapter` · test: `AiConnectorIntentExtractionAdapterTest` · after: GU02,
  GU06
- [ ] GI05 · `IntentExtractionGrpcService` · test: `IntentExtractionGrpcServiceTest` · after: GU08
- [ ] GI06 · `AiExpenseRecordingAdapter` · test: `AiExpenseRecordingAdapterTest`

#### TDD System Test Green Phase

- [ ] GS01 · `ListCategoriesMcpToolSystemTest` · covers: `POST /mcp`
- [ ] GS02 · `CreateExpenseProposalMcpToolSystemTest` · covers: `POST /mcp`
- [ ] GS03 · `McpAuthenticationSystemTest` · covers: `POST /mcp`
- [ ] GS04 · `ReceiveTelegramMessageSystemTest` · covers: the running Telegram poll loop
- [ ] GS05 · `ExtractIntentsSystemTest` · covers: `IntentExtractionService.ExtractIntents`

### Post-Implementation Steps

None. The one ADR candidate was raised as Q1 and declined; its rule lands in `code-style.md` at ST29 instead.

## Open Questions / Blockers

- **Q1:** Record D33 as an ADR? The decision, stated as a fact: *a caller-supplied name that resolves to nothing
  raises the invalid-argument domain exception for that concept, while the not-found exception is raised only for
  an id.* If no ADR is written,
  [code-style.md](../../ledger-service/docs/conventions/code-style.md)'s existing "a reference to something that
  is not stored raises a not-found domain exception" line is where the id/name split has to be spelled out.
- A: No ADR — code-style.md already owns the rule and this only spells out an id/name split inside it.
  **Post-Implementation Steps** therefore holds no ADR item.

- **Q2:** If Q1 is answered `no`, where does that `code-style.md` edit happen? The module's
  [agent.md](../../ledger-service/docs/conventions/agent.md) restricts **Post-Implementation Steps** to ADRs and
  nothing else, and `archive-knowledge` owns use-case, contract and ADR pages rather than conventions — so a
  conventions edit has no slot in this plan as the conventions currently define it. Fold it into the
  stabilization group as a documentation item, leave it to a follow-up change, or widen `agent.md`'s
  post-implementation list?
- A: As a Stabilization documentation item — ST29 — so the rule lands in the same change that makes
  `ListCategoriesUseCase` depend on it. `agent.md` is not widened.

## Review Findings

- **F1:** RS02 told the system test to send `Groceries` as both arguments and keep asserting the "is a grouping,
  retry with one of its children" refusal, which `parentCategory` being required (D10) puts out of reach.
- Resolution: decision
- Action: applied — re-aimed the scenario at the parent-mismatch refusal (`Supermarkets` under `Dining`), the
  user's choice: it still originates below the inbound adapter, which the missing-parentCategory case does not.

- **F2:** RI06 and RS05 both read back `list_categories` calls, but `CapturedRequestUtils.toolCallRequests()`
  filters on a hard-coded `create_expense_proposal` and no step owned widening it.
- Resolution: decision
- Action: resolved — two steps needing one reader is what `plan-task.md` defines as **Shared Test
  Infrastructure** ("more than one upcoming Red Phase step will need"), and every red-phase agent is scoped
  against writing shared fixtures, so the fix is not a choice between two defensible options. Added ST28
  widening `CapturedRequestUtils` with `toolCallRequests(String toolName)`, the existing no-argument method
  delegating to it.

- **F3:** RU09 named no `update:` for the two `ExtractIntentsUseCaseTest` scenarios and the two private
  `command(...)` helpers that ST18's port signature breaks.
- Resolution: mechanical
- Action: applied — added RU02's blanket sentence, naming the helpers and the four-argument `record` stubs.

- **F4:** RU08 named no `update:` for the `ExtractIntentsCommandTest` scenarios and its `KNOWN_CATEGORIES`
  constant that ST17's reshaped record breaks without changing their assertions.
- Resolution: mechanical
- Action: applied — added the blanket sentence, naming the constant.

- **F5:** RI04 named no `update:` for the two `AiConnectorIntentExtractionAdapterTest` scenarios that build an
  `IntentExtractionRequest` from `KnownCategory` values and stop compiling after ST05.
- Resolution: mechanical
- Action: applied — added the blanket sentence.

- **F6:** RI05 named no `update:` for
  `whenRequestCarriesDefaultCurrencyInAnyCasing_thenCommandHoldsItAsPresentUpperCasedCurrencyCode()`, which reads
  the `DEFAULT_KNOWN_CATEGORIES` constant ST26 drops.
- Resolution: mechanical
- Action: applied — added the bullet and pointed `invalidRequests()`'s surviving cases at the new fixtures.

- **F7:** RU04 pinned nothing about D32 — no scenario proved *which* user id `findByUserIdAndName` was called
  with.
- Resolution: mechanical
- Action: applied — added a scenario asserting both repository calls carry ids that came out of the token
  subject's own read.

- **F8:** ST22, the architecture-enforcement confirmation, sat inside **Interface & Signature Sync** rather than
  closing the Stabilization group.
- Resolution: mechanical
- Action: applied — moved it after **Shared Test Infrastructure**, ID unchanged.
