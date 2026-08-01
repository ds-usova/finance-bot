# Handle Incoming Message Extracts Intents

**Affected Modules:** `ledger-service`, `ai-connector-service`
**Design:** [Handle Incoming Message Extracts Intents](9-design-handle-incoming-message-extracts-intents.md)

## Step-by-Step Implementation Map (To-Do List)

### Stabilization

#### API Contract

- [x] ST01 · `proto/intent_extraction.proto` — replace the schema's body with the design's **The shared schema**:
  `known_categories` becomes `repeated KnownCategory` on field 2, the new `KnownCategory` message carries
  `name` and `parent_name`, `ExtractIntentsResponse` becomes empty, and `Intent`, `CategoryIntent`,
  `ExpenseIntent`, `Money` and `Operation` are deleted outright — not reserved (**D30**). Both modules pick the
  file up as an extra proto source directory, so this must be green before any other step compiles.

#### Interface-First / Build Stabilization

**Interface & Signature Sync**

- [x] ST02 · `ledger-service` — add `application/dto/KnownCategory.java`: `record KnownCategory(String name,
  String parentName)`, its compact constructor rejecting a null or blank value for either with
  `InvalidExtractionRequestException`.
- [x] ST03 · `ledger-service` — `application/port/CategoryRepository.java` gains
  `List<KnownCategory> findKnownCategories(long userId)`, javadoc'd `@throws PersistenceFailedException if the
  lookup fails`. Add `adapter/persistence/KnownCategoryProjection.java` —
  `record KnownCategoryProjection(String name, String parentName)` — and declare the design's `@Query`-annotated
  `List<KnownCategoryProjection> findKnownCategories(@Param("userId") Long userId)` on
  `adapter/persistence/CategoryEntityRepository.java`. Stub
  `CategoryRepositoryAdapter.findKnownCategories` returning `List.of()` with an inline comment: reads every
  category that hangs off a grouping in one statement, and wraps a store failure as its siblings do.
- [x] ST04 · `ledger-service` — `application/dto/IntentExtractionRequest.java`: `knownCategories` becomes
  `List<KnownCategory>` and the record gains `String userExternalId` as its last component, rejected when null
  or blank. The existing text, non-empty, no-null-element and `Optional` currency checks stay.
- [x] ST05 · `ledger-service` — `application/port/IntentExtractionPort.extract` returns `void`, its javadoc
  keeping `InvalidExtractionRequestException` and re-wording `IntentExtractionFailedException` to "when the turn
  does not complete". `AiConnectorIntentExtractionAdapter` returns `void`: drop the `toIntents` call and the
  empty-answer check, keep the null-request guard, the debug line and the `StatusRuntimeException` mapping, and
  add a `TODO` at the call site for minting and attaching the caller's token.
- [x] ST06 · `ledger-service` — delete the mirrored intent vocabulary and everything that only existed for it:
  `domain/value/Intent.java`, `CategoryIntent.java`, `ExpenseIntent.java`, `UnknownIntent.java`,
  `Operation.java`, `domain/exception/InvalidIntentException.java`, the response half of
  `adapter/aiconnector/IntentProtoUtils.java` (`toIntents` and every private member below it), and the tests
  bound to them: `domain/value/CategoryIntentTest.java`, `ExpenseIntentTest.java`, `UnknownIntentTest.java`, and
  the `ToIntents` group of `adapter/aiconnector/IntentProtoUtilsTest.java` — that class's `ToProtoRequest` group
  stays, covering the mapper that survives. `Money` and `CurrencyCode` stay (**D29**).
- [x] ST07 · `ledger-service` — `application/usecase/HandleIncomingMessageUseCase`'s constructor takes
  `InitializeUserPort`, `CategoryRepository`, `IntentExtractionPort` and `LoggerFactory`. Keep the null-command
  guard and put a `TODO` in `handle` for the resolve-read-dispatch body the design's steps 2–5 describe; the
  existing log line stays until GU03 replaces it. `adapter/config/UseCaseConfiguration.handleIncomingMessagePort`
  takes the three new beans and passes them through.
- [x] ST08 · `ledger-service` — `AiConnectorIntentExtractionAdapter`'s constructor takes `AccessTokenMinter`,
  held unused behind ST05's `TODO`.
- [x] ST09 · `ledger-service` — delete the domain pages for the deleted vocabulary (**D37**):
  `docs/domain/intent.md`, `expense-intent.md`, `category-intent.md`, `unknown-intent.md`, `operation.md`, and
  strip the links into them from the pages that survive: `docs/domain/money.md:16` and
  `docs/domain/category.md:19`. (`currency-code.md` links only to the contract page, which stays.)
- [x] ST26 · repo-root — add `docs/conventions/adr.md`, the ADR lifecycle rule this repository has never
  written down: an ADR is append-only, so it is never deleted and its Context and Decision are never rewritten;
  only `Status:` and Consequences grow. A table gives the three endings — a later decision **reverses** it (new
  ADR carrying `Supersedes: NNNN`, old one flipped to `Status: Superseded by ADR MMMM`), it stops mattering with
  nothing replacing it (`Status: Deprecated` and one line of why), or what it applied to **goes away** while the
  decision stands (status unchanged, one dated line appended to Consequences). Link it from the ADR bullet in
  both modules' `docs/conventions/orientation.md`, as `architecture.md` already links
  `docs/conventions/diagrams.md`.
- [x] ST27 · repo-root — mark [ADR 0005](adr/0005-the-ledger-mirrors-the-intent-vocabulary-in-its-own-domain.md)
  outdated under ST26's third ending: `Status:` stays `Accepted`, and one dated line joins its Consequences —
  since [plan 9](implemented/9-plan-handle-incoming-message-extracts-intents.md) no intent crosses into the
  ledger, so only the connector holds a mirror, and the title names the ledger for the boundary as it stood in
  July 2026. Context and Decision are not touched.
- [x] ST10 · `ai-connector-service` — add `application/dto/KnownCategory.java`: `record KnownCategory(String
  name, String parentName)`, both rejected when null or blank with `InvalidValueException`, plus
  `String label()` rendering `parentName + " > " + name`.
- [x] ST11 · `ai-connector-service` — `application/dto/ExtractIntentsCommand.knownCategories` becomes
  `List<KnownCategory>`; the non-empty and no-null-element checks stay, and the blank-element check moves into
  `KnownCategory` itself.
- [x] ST12 · `ai-connector-service` — `domain/value/ExpenseIntent` gains `Optional<String> parentCategoryName`
  as its last component, rejecting null as its siblings do, and its `CREATE` branch gains a `TODO` for the
  description requirement (**D32**). Update every construction site: `ExtractIntentsUseCase.buildExpenseIntent`
  passes `Optional.empty()` for now.
- [x] ST13 · `ai-connector-service` — add `application/dto/ProposedExpense.java`
  (`record ProposedExpense(String categoryName, Optional<String> parentCategoryName, String description, Money
  amount)`), `application/port/ExpenseProposalPort.java` (`void propose(ProposedExpense expense)`, javadoc'd
  `@throws ExpenseProposalFailedException if the proposal is refused or the ledger cannot be reached`), and
  `domain/exception/ExpenseProposalFailedException.java` carrying which of the two it is (**D35**).
- [x] ST14 · `ai-connector-service` — `application/port/ExtractIntentsPort.extractIntents` returns `void`.
  `ExtractIntentsUseCase` takes `ExpenseProposalPort` and `LoggerFactory` too, keeps its assembly loop, and
  ends with a `TODO` for the walk that proposes each `CREATE` expense and logs the rest.
  `adapter/config/UseCaseConfiguration` passes the two new beans.
- [x] ST15 · `ai-connector-service` — delete `adapter/grpc/IntentProtoUtils.java` and
  `adapter/grpc/IntentProtoUtilsTest.java` (**D43**). `IntentExtractionGrpcService` builds its command from
  `request.getKnownCategoriesList()`'s `KnownCategory` messages and answers
  `ExtractIntentsResponse.getDefaultInstance()`; leave a `TODO` for the blank name / blank parent name
  rejection. `adapter/grpc/GrpcStatusConfiguration`'s `GrpcExceptionHandler` gains a stub branch for
  `ExpenseProposalFailedException`, beside the `IntentInferenceException` one, with a `TODO` for the
  `FAILED_PRECONDITION` / `UNAVAILABLE` split (**D35**).
- [x] ST16 · `ai-connector-service` — add `adapter/grpc/CallerTokenInterceptor.java`, a `ServerInterceptor`
  registered as a `@Component`, and `adapter/grpc/CallerTokenUtils.java` with `static Optional<String>
  callerToken()`. Stub both with an inline comment: the interceptor reads `authorization` off the call's
  metadata into an `io.grpc.Context` key for the call's duration, and the utils class reads it back — the
  mirror of the ledger's `AuthenticatedCallerUtils`. The `UNAUTHENTICATED` refusal is scoped to
  `IntentExtractionService`; `grpc.health.v1.Health` stays open, since the ledger's `AiConnectorHealthIndicator`
  probes it with no token.
- [x] ST17 · `ai-connector-service` — add `adapter/ledger/` with `LedgerMcpProperties`
  (`@ConfigurationProperties(prefix = "ledger.mcp")`, one `String url()`) and `McpExpenseProposalAdapter`
  implementing `ExpenseProposalPort` as a `@Component`, its `propose` stubbed with an inline comment: opens an
  MCP client over Streamable HTTP against `LedgerMcpProperties.url()` carrying the caller's token, invokes
  `create_expense_proposal`, and closes it.
- [x] ST18 · `ai-connector-service` — `build.gradle` gains
  `implementation "org.springframework.ai:spring-ai-starter-mcp-client"`, and
  `architecture/CleanArchitectureTest` gains `io.modelcontextprotocol..` in the banned-package rule and `Mcp` in
  `coreTypesCarryNoExternalSystemName` (**D36**).

**Configuration**

- [x] ST19 · `ai-connector-service/src/main/resources/application.yaml` gains
  `ledger.mcp.url: ${LEDGER_MCP_URL:http://localhost:1000}`, and
  `infrastructure/docker-compose.yaml` gives the `ai-connector-service` service
  `LEDGER_MCP_URL: http://ledger-service:1000`, beside the ledger's existing `AI_CONNECTOR_GRPC_TARGET`
  (**D31**).

**Shared Test Infrastructure**

- [x] ST20 · `ledger-service` — `common/containers/GrpcStubServer` records the metadata of the last
  `ExtractIntents` call (a `ServerInterceptor` on the stub service, plus `lastExtractionMetadata()`, cleared by
  `reset()`), so a test can assert the `authorization` header the adapter attached.
- [x] ST21 · `ledger-service` — `common/AiConnectorAdapterTest` boots `AccessTokenMinter` alongside the adapter
  and carries `@EnableConfigurationProperties(AccessTokenProperties.class)`: a `@ConfigurationProperties` record
  is not registered by listing it in `@SpringBootTest(classes = …)`, and the `mcp.token.*` values it binds
  already come from `src/main/resources/application.yaml`. Delete `common/IntentFixtures` — nothing maps an
  intent on this side any more — and drop its entry from
  `ledger-service/docs/conventions/testing.md`'s Package Structure block.
- [x] ST22 · `ledger-service` — `common/AbstractSystemTest` points
  `spring.grpc.client.channel.ai-connector.target` at `GrpcStubServer.target()` through its
  `@DynamicPropertySource`, so the fully wired application reaches the stub connector rather than a dead
  address, and its `@AfterEach` calls `GrpcStubServer.reset()` beside `WireMockSupport.SERVER.resetAll()` —
  the stub server is JVM-wide, so RS02's forced failure would otherwise leak into every later class. Give
  `common/TelegramTestBot` a token constant for the new failure-path system test class (RS02), as the listener
  isolation rule requires.
- [x] ST23 · `ai-connector-service` — `common/RequestFixtures` builds `KnownCategory` messages
  (`DEFAULT_KNOWN_CATEGORIES` becomes label-bearing pairs), and `common/IntentFixtures` gains builders for an
  `ExpenseIntent` carrying a parent category name and a description.
- [x] ST24 · `ai-connector-service` — add `common/McpLedgerStubs`, one static method per outcome the MCP
  endpoint can produce (a session that accepts `create_expense_proposal` and answers a stored proposal, one
  that answers a tool result flagged `isError`, and one that fails the transport), registered through
  `WireMockSupport.SERVER`. Add `common/LedgerAdapterTest`, a composed annotation booting
  `McpExpenseProposalAdapter` and `LedgerMcpProperties` with `ledger.mcp.url` pointed at the stub server, plus
  a `common/LedgerAdapterContextTest` that carries the annotation, autowires the adapter and asserts nothing —
  the convention's boots-itself test. `common/AbstractSystemTest` points `ledger.mcp.url` at the stub server
  the same way it already points `spring.ai.openai.base-url`. List all three new classes in
  `ai-connector-service/docs/conventions/testing.md`'s Package Structure block.
- [x] ST25 · after stabilization, confirm `bot.finance.architecture.CleanArchitectureTest` and
  `bot.finance.ai.architecture.CleanArchitectureTest` both pass.

### Red Phase

#### TDD Unit Red Phase

- [x] RU01 · `KnownCategory` (`bot.finance.application.dto`) · test: `KnownCategoryTest` · covers:
  `KnownCategory(String, String)`
  - `KnownCategory(String, String)`:
    - given: a name and a parent name, both non-blank
      when: the record is constructed
      then: both components read back unchanged
    - given: a null or blank name, and a valid parent name
      when: the record is constructed
      then: `InvalidExtractionRequestException` is thrown
    - given: a valid name, and a null or blank parent name
      when: the record is constructed
      then: `InvalidExtractionRequestException` is thrown

- [x] RU02 · `IntentExtractionRequest` · test: `IntentExtractionRequestTest` · covers:
  `IntentExtractionRequest(String, List, Optional, String)`
  - `IntentExtractionRequest(String, List, Optional, String)`:
    - given: a valid text, one `KnownCategory`, an empty currency and a non-blank external id
      when: the record is constructed
      then: every component reads back unchanged and the category list is unmodifiable
    - given: a null or blank `userExternalId`, everything else valid
      when: the record is constructed
      then: `InvalidExtractionRequestException` is thrown
  - update: `whenKnownCategoriesContainsNullOrBlankEntry_thenThrowsInvalidExtractionRequestException()` — the
    blank case moves to `KnownCategory`, so this test and its `categoriesWithInvalidEntry` source now cover only
    a null element; rename accordingly.
  - update: `whenTextOneCategoryAndDefaultCurrencyAreValid_thenItHoldsAllThree()`,
    `whenTextIsNullOrBlank_thenThrowsInvalidExtractionRequestException()`,
    `whenKnownCategoriesIsNullOrEmpty_thenThrowsInvalidExtractionRequestException()`,
    `whenDefaultCurrencyIsNull_thenThrowsInvalidExtractionRequestException()`,
    `whenDefaultCurrencyIsEmpty_thenDefaultCurrencyComesBackEmpty()`,
    `whenKnownCategoriesListIsModifiedAfterConstruction_thenKnownCategoriesIsUnchanged()` — each construction
    site takes a `List<KnownCategory>` and the new `userExternalId` argument.

- [x] RU03 · `HandleIncomingMessageUseCase` · test: `HandleIncomingMessageUseCaseTest` · covers: `handle()`
  - `handle()`:
    - given: mocked ports where `initialize` answers a `User` with an id and an external id, and
      `findKnownCategories` answers two categories
      when: `handle` is called with a conversation id and text
      then: `initialize` is called with an `InitializeUserCommand` carrying the conversation id,
      `findKnownCategories` is called with the user's id, and `extract` is called with a request carrying that
      text, those two categories, an empty default currency and the user's external id
    - given: the same mocked ports
      when: `handle` completes
      then: an info line names the conversation id and does not carry the message text (**D15**)
    - given: `initializeUserPort.initialize` throws `PersistenceFailedException`
      when: `handle` is called
      then: the exception propagates and neither the repository nor the extraction port is called
    - given: `categoryRepository.findKnownCategories` throws `PersistenceFailedException`
      when: `handle` is called
      then: the exception propagates and the extraction port is never called
    - given: `intentExtractionPort.extract` throws `IntentExtractionFailedException`
      when: `handle` is called
      then: the exception propagates and no "handled" line is logged
  - note: `whenCommandCarriesConversationIdAndText_thenLogsBothAtInfoLevel` asserted the text is logged and was
    deleted in stabilization; the two scenarios above replace it.
  - update: `whenCommandIsNull_thenThrowsInvalidIncomingMessageExceptionAndLogsNothing()` — also assert none of
    the three ports is called.

- [x] RU04 · `KnownCategory` (`bot.finance.ai.application.dto`) · test: `KnownCategoryTest` · covers:
  `KnownCategory(String, String)`, `label()`
  - `KnownCategory(String, String)`:
    - given: a name and a parent name, both non-blank
      when: the record is constructed
      then: both components read back unchanged
    - given: a null or blank value for either component
      when: the record is constructed
      then: `InvalidValueException` is thrown
  - `label()`:
    - given: a category named `Travel` under a grouping named `Insurance`
      when: `label()` is called
      then: it renders `Insurance > Travel`

- [x] RU05 · `ExtractIntentsCommand` · test: `ExtractIntentsCommandTest` · covers:
  `ExtractIntentsCommand(String, List, Optional)`
  - update: `whenCategoryListContainsNullOrBlankElement_thenThrowsInvalidValueException()` — the blank case
    moves to RU04's `KnownCategory`; keep only the null element and rename accordingly.
  - update: `whenNonBlankTextAndCategoryList_thenCommandExposesBoth()`,
    `whenTextIsNullEmptyOrBlank_thenThrowsInvalidValueException()`,
    `whenCategoryListIsEmpty_thenThrowsInvalidValueException()`,
    `whenCategoryListIsNull_thenThrowsInvalidValueException()`,
    `whenMutableCategoryListModifiedAfterConstruction_thenCommandListUnchangedAndOwnListImmutable()`,
    `whenDefaultCurrencyPresent_thenCommandExposesCurrencyCode()`,
    `whenDefaultCurrencyOptionalIsNull_thenThrowsInvalidValueException()` — each construction site takes a
    `List<KnownCategory>`.

- [x] RU06 · `ExpenseIntent` · test: `ExpenseIntentTest` · covers: `ExpenseIntent(Operation, Optional,
  Optional, Optional, Optional)`
  - `ExpenseIntent(Operation, Optional, Optional, Optional, Optional)`:
    - given: a `CREATE` intent with an amount, a category, a description and a present parent category name
      when: the record is constructed
      then: `parentCategoryName` reads back with that value
    - given: a `CREATE` intent with an amount, a category and a description, and an empty parent category name
      when: the record is constructed
      then: the record is accepted — a category the same message created has no grouping (**D28**)
    - given: a `CREATE` intent with an amount and a category but an empty description
      when: the record is constructed
      then: `InvalidValueException` is thrown, naming the description (**D32**)
  - update: `nullOptionalPositions()` — add a fourth row for a null `parentCategoryName`, so the existing
    parameterized matrix covers it rather than a duplicate one-off.
  - update: `whenOperationAndAllOptionalFieldsPresent_thenIntentIsCreated()`,
    `whenOperationReadOrDeleteAndEveryOptionalFieldEmpty_thenIntentIsCreated()`,
    `whenOperationIsNull_thenThrowsInvalidValueException()`,
    `whenNullOptionalGivenInAnyOptionalPosition_thenThrowsInvalidValueException()`,
    `whenOperationCreateHasNoAmount_thenThrowsInvalidValueExceptionNamingOperationAndField()`,
    `whenOperationCreateHasNoCategory_thenThrowsInvalidValueExceptionNamingOperationAndField()` — each
    construction site passes the new `parentCategoryName` component, and every `CREATE` case that currently
    omits a description gains one.

- [x] RU07 · `ProposedExpense` · test: `ProposedExpenseTest` · covers:
  `ProposedExpense(String, Optional, String, Money)`
  - `ProposedExpense(String, Optional, String, Money)`:
    - given: a category name, a present parent category name, a description and a `Money`
      when: the record is constructed
      then: every component reads back unchanged
    - given: an empty parent category name, everything else present
      when: the record is constructed
      then: the record is accepted
    - given: a null or blank category name or description, or a null amount or parent `Optional`
      when: the record is constructed
      then: `InvalidValueException` is thrown

- [x] RU08 · `ExtractIntentsUseCase` · test: `ExtractIntentsUseCaseTest` · covers: `extractIntents()`
  - `extractIntents()`:
    - given: a command whose known categories are `Insurance > Travel` and `Food > Lunch`
      when: `extractIntents` is called
      then: `intentInferencePort.infer` is called with the command's text and the two rendered labels, in the
      command's order (**D27**)
    - given: the model answers a `CREATE` expense whose category name is the label `Insurance > Travel`
      when: `extractIntents` is called
      then: `expenseProposalPort.propose` is called once with a `ProposedExpense` carrying `Travel`, a present
      parent `Insurance`, the description and the amount
    - given: known categories `Insurance > Travel` and `Trips > Travel`, and the model answers a bare `Travel`
      when: `extractIntents` is called
      then: nothing is proposed, and an info line names the intent it did not act on — a bare name matching
      several entries is unknown (**D27**)
    - given: known categories `Insurance > Travel` and `Food > Lunch`, and the model answers a bare `Lunch`
      when: `extractIntents` is called
      then: the proposal carries `Lunch` with a present parent `Food`
    - given: the model answers a category-creation entry for `Trips` followed by a `CREATE` expense filed under
      `Trips`, carrying an amount and a description
      when: `extractIntents` is called
      then: the proposal carries `Trips` with an empty parent category name (**D28**)
    - given: the model answers a `CREATE` expense, a `READ` expense, a `CategoryIntent` and an unusable entry
      when: `extractIntents` is called
      then: `propose` is called exactly once — for the `CREATE` expense — and each of the other three is logged
      at info by target and operation (**D22**)
    - given: the model answers three `CREATE` expenses and `propose` throws
      `ExpenseProposalFailedException` on the second
      when: `extractIntents` is called
      then: the exception propagates, `propose` was called twice, and the third expense is never proposed
      (**D24**)
    - given: the model answers a `CREATE` expense with an amount and no description
      when: `extractIntents` is called
      then: nothing is proposed and the skipped entry is logged (**D32**)
    - given: the model answers nothing usable at all
      when: `extractIntents` is called
      then: no proposal is made and the call returns normally — a message asking for nothing this system does
      is not a failure (**D22**)
  - update: `whenPortReturnsOneRawExpenseAnswer_thenReturnsSingleExpenseIntentAndPortCalledWithTextAndCategories()`,
    `whenExpenseAnswerNamesCategoryNotInKnownCategories_thenUnknownIntentReasonNamesRejectedCategory()`,
    `whenExpenseAnswerNamesKnownCategoryInDifferentCase_thenExpenseIntentCarriesKnownCategorysSpelling()`,
    `whenCategoryCreationAnswerNamesCategoryAbsentFromKnownCategories_thenReturnsCategoryIntent()`,
    `whenPortReturnsOneRawCategoryDeleteAnswer_thenReturnsSingleCategoryIntentWithDeleteOperationAndName()`,
    `whenPortReturnsThreeRawAnswersExpenseCategoryExpense_thenResultMatchesSizeAndOrder()`,
    `whenPortReturnsCategoryThenUnknownCurrencyExpenseThenValidExpense_thenReturnsThreeIntentsInOrder()`,
    `whenAnswerTargetIsNullOrUnrecognized_thenUnknownIntentReasonNamesTarget()`,
    `whenAnswerOperationIsUnrecognized_thenUnknownIntentReasonNamesOperation()`,
    `whenExpenseAnswerAmountIsNotDecimal_thenUnknownIntentReasonIsRejectedValueExceptionMessage()`,
    `whenExpenseAnswerCreateHasNoAmount_thenUnknownIntentReasonNamesMissingAmount()`,
    `whenCommandHasDefaultCurrencyAndExpenseAnswerHasNoCurrency_thenExpenseIntentUsesDefaultCurrency()`,
    `whenCommandHasNoDefaultCurrencyAndExpenseAnswerHasNoCurrency_thenUnknownIntent()`,
    `whenCommandHasDefaultCurrencyAndExpenseAnswerNamesUsdExplicitly_thenExpenseIntentCarriesUsd()`,
    `whenPortReturnsEmptyList_thenReturnsExactlyOneUnknownIntentWithNonBlankReason()`,
    `whenPortReturnsNull_thenReturnsExactlyOneUnknownIntent()`,
    `whenPortReturnsListContainingNullElement_thenThatPositionHoldsUnknownIntentAndOthersUnaffected()`,
    `whenPortThrowsIntentInferenceException_thenExceptionPropagates()`,
    `whenCommandIsNull_thenThrowsInvalidValueExceptionAndPortNeverCalled()`,
    `whenCategoryCreationOfTravelPrecedesExpenseFiledUnderTravel_thenReturnsCategoryIntentThenExpenseIntentCarryingTravel()`,
    `whenExpenseFiledUnderTravelPrecedesCategoryCreationOfTravel_thenReturnsExpenseIntentCarryingTravelThenCategoryIntent()`,
    `whenCategoryCreationOfTravelPrecedesExpenseNamingTravelInDifferentCase_thenExpenseIntentCarriesTravel()`,
    `whenCategoryDeletionOfTravelPrecedesExpenseFiledUnderTravel_thenReturnsCategoryIntentThenExpenseIntentCarryingTravel()`,
    `whenCategoryCreationWithBlankNameFailsAssembly_thenBothItAndFollowingExpenseHoldUnknownIntent()` — the
    method now returns `void`, so each of these asserts on `expenseProposalPort` interactions and on the
    captured log lines instead of on a returned list, and each builds its command from `KnownCategory` values.
    Keep the assembly behaviour they cover; drop only the return-value assertions.

- [x] RU09 · `CallerTokenUtils` · test: `CallerTokenUtilsTest` · covers: `callerToken()`
  - `callerToken()`:
    - given: the context key holds `Bearer abc`
      when: `callerToken()` is called inside that context
      then: it answers a present `Bearer abc`
    - given: no context key is set
      when: `callerToken()` is called
      then: it answers an empty `Optional`

- [x] RU10 · `IntentProtoUtils` (`bot.finance.adapter.aiconnector`) · test: `IntentProtoUtilsTest` · covers:
  `toProtoRequest()`
  - `toProtoRequest()`:
    - given: a request carrying two `KnownCategory` values
      when: `toProtoRequest` is called
      then: the generated request holds two `KnownCategory` messages, each with its `name` and `parent_name`,
      in the request's order
  - update: `whenRequestCarriesTextThreeCategoriesAndDefaultCurrencyEur_thenGeneratedRequestCarriesThemAll()`
    and `whenRequestDefaultCurrencyIsEmpty_thenGeneratedRequestReportsHasDefaultCurrencyAsFalse()` — build
    their `IntentExtractionRequest` from `KnownCategory` values and the new `userExternalId`, and assert on the
    generated `KnownCategory` messages rather than on bare strings.

#### TDD Integration Red Phase

- [x] RI01 · `CategoryRepositoryAdapter` · test: `CategoryRepositoryAdapterTest` · covers:
  `findKnownCategories()`
  - `findKnownCategories()`:
    - given: a stored user with one grouping and two categories under it
      when: `findKnownCategories` is called with that user's id
      then: it answers exactly the two children, each carrying its own name and its grouping's name
    - given: a stored user with a grouping that has no children
      when: `findKnownCategories` is called
      then: the grouping itself is absent from the answer — the closed set is leaves only (**D3**)
    - given: two stored users each owning a category under a grouping
      when: `findKnownCategories` is called for one of them
      then: only that user's category is answered
    - given: a stored user with no categories at all
      when: `findKnownCategories` is called
      then: it answers an empty list
    - given: an adapter over a mocked `CategoryEntityRepository` whose `findKnownCategories` throws
      `QueryTimeoutException`
      when: `findKnownCategories` is called
      then: `PersistenceFailedException` is thrown carrying the framework exception as its cause

- [x] RI02 · `AiConnectorIntentExtractionAdapter` · test: `AiConnectorIntentExtractionAdapterTest` · covers:
  `extract()`
  - `extract()`:
    - given: the stub server answers an empty `ExtractIntentsResponse`
      when: `extract` is called with a request carrying text, two `KnownCategory` values, a default currency
      and an external id
      then: it returns without throwing, and the request the server received carries that text, those two
      categories with their parent names in order, and that default currency
    - given: the same stub
      when: `extract` is called
      then: the call's metadata carries `authorization: Bearer <jwt>`, whose `sub` claim is the request's
      `userExternalId` (**D19**)
  - note: `whenStubServerAnswersResponseWithNoEntries_thenThrowsIntentExtractionFailedException` was deleted in
    stabilization — an empty response is now the success shape.
  - update:
    `whenStubServerFailsCall_thenThrowsIntentExtractionFailedExceptionCarryingStatusRuntimeExceptionAsCauseAndNamingStatus()`
    — extend the enum source with `FAILED_PRECONDITION` and `UNAUTHENTICATED`, the statuses the connector can
    now answer with (**D35**).
  - update: `whenCalledWithNull_thenThrowsInvalidExtractionRequestExceptionAndServerIsNeverCalled()` — also
    assert no token was minted, i.e. nothing reached the server at all.
  - note: `whenStubServerAnswersTwoEntryResponse_thenDomainIntentsComeBackInOrderAndServerReceivedRequestFields`
    was deleted in stabilization; the first scenario above replaces it.

- [x] RI03 · `IntentExtractionGrpcService` · test: `IntentExtractionGrpcServiceTest` · covers:
  `ExtractIntents` · mocks: `ExtractIntentsPort`
  - Happy Path:
    - given: the mocked port returns normally
      when: a request carrying a text and two `KnownCategory` entries arrives
      then: the port is called with a command whose `knownCategories` hold both names and parent names in
      order, and the RPC answers an empty `ExtractIntentsResponse` (**D23**)
  - Error Mapping:
    - given: the mocked port throws `ExpenseProposalFailedException` for a refused proposal
      when: the RPC is called
      then: it fails `FAILED_PRECONDITION` (**D35**)
    - given: the mocked port throws `ExpenseProposalFailedException` for an unreachable ledger
      when: the RPC is called
      then: it fails `UNAVAILABLE`
  - Validation: `known_categories` — an entry with a blank `name`, an entry with a blank `parent_name`
    (**D41**); both `INVALID_ARGUMENT`, the port never called
  - note: `whenPortReturnsCreateExpenseIntent_thenResponseMapsItAndCommandCarriesTextAndCategories`,
    `whenPortReturnsCategoryThenExpenseIntent_thenResponseHoldsBothInOrder` and
    `whenPortReturnsUnknownIntent_thenRpcCompletesOkWithOperationUnknownAndReason` were deleted in stabilization
    — the port returns nothing and the response carries nothing; the Happy Path scenario above replaces them.
  - update: `whenRequestCarriesDefaultCurrencyInAnyCasing_thenCommandHoldsItAsPresentUpperCasedCurrencyCode()`
    and `whenRequestViolatesConstraint_thenFailsWithInvalidArgumentAndPortNeverCalled()` — build their requests
    from `KnownCategory` messages; the matrix's "known_categories containing a blank entry" case splits into
    the two Validation cases above.
  - update: `whenPortThrowsIntentInferenceException_thenFailsWithUnavailable()` and
    `whenPortThrowsUnrecognizedRuntimeException_thenFailsWithUnknownAndMessageAbsent()` — stub the port with
    `doThrow`, since it no longer returns a value. Every request in this class — surviving and new alike —
    goes through a stub carrying `authorization: Bearer <opaque text>` via
    `MetadataUtils.newAttachHeadersInterceptor`: `@GrpcAdapterTest` boots the whole application, so ST16's
    interceptor is registered and would refuse an unauthenticated call before the mocked port is reached.

- [x] RI04 · `CallerTokenInterceptor` · test: `CallerTokenInterceptorTest` · covers: `ExtractIntents` ·
  mocks: `ExtractIntentsPort`
  - Happy Path:
    - given: a stub carrying `authorization: Bearer abc` in the call's metadata
      when: the RPC is called
      then: the port is called, and `CallerTokenUtils.callerToken()` inside the port's invocation answers
      `Bearer abc` (**D21**)
  - Error Mapping:
    - given: a stub carrying no `authorization` metadata
      when: the RPC is called
      then: it fails `UNAUTHENTICATED` and the port is never called
    - given: a `HealthGrpc` stub carrying no `authorization` metadata
      when: `Check` is called
      then: it succeeds — the refusal is scoped to `IntentExtractionService`, and the ledger's health
      indicator probes with no token

- [x] RI05 · `McpExpenseProposalAdapter` · test: `McpExpenseProposalAdapterTest` · covers: `propose()`
  - `propose()`:
    - given: the stubbed ledger accepts `create_expense_proposal` and answers a stored proposal, and the
      caller's token is held in the gRPC context
      when: `propose` is called with a `ProposedExpense` carrying a present parent category name
      then: the tool call the ledger received carries `category`, `parentCategory`, `description`,
      `amountMinorUnits` and `currencyCode`, no `merchant`, and every request on the session carries
      `Authorization: Bearer <the caller's token>`
    - given: the same stub, and a `ProposedExpense` with an empty parent category name
      when: `propose` is called
      then: the tool call carries no `parentCategory`
    - given: the stubbed ledger answers a tool result flagged `isError`
      when: `propose` is called
      then: `ExpenseProposalFailedException` is thrown, reporting a refusal (**D35**)
    - given: the stubbed ledger fails the transport
      when: `propose` is called
      then: `ExpenseProposalFailedException` is thrown, reporting an unreachable ledger
    - given: no caller token is held in the context
      when: `propose` is called
      then: `ExpenseProposalFailedException` is thrown, reporting an unreachable ledger, and the ledger is
      never called
    - given: the stubbed ledger accepts two calls in a row
      when: `propose` is called twice
      then: each call performs its own MCP session and each carries its own token (**D34**)

#### TDD System Test Red Phase

- [x] RS01 · `ReceiveTelegramMessageSystemTest` · covers: `HandleIncomingMessagePort.handle()`
  - Happy Path:
    - update: `whenRunningPollLoopPicksUpTextMessageUpdate_thenBatchIsConfirmedAndMessageIsPrinted()` — the
      batch is still confirmed, but the assertion on the logged message text goes (**D15**). Instead: the user
      and their 97 categories exist in the database for the conversation id, the request the stub connector
      received carries the message text, that user's known categories with their parent names, and
      `authorization: Bearer <jwt>` whose `sub` is the conversation id, and the use case's info line names the
      conversation without the text.

- [x] RS02 · `HandleIncomingMessageFailureSystemTest` · covers: `HandleIncomingMessagePort.handle()`
  - Unhappy Path:
    - given: the stub connector fails `ExtractIntents` with `UNAVAILABLE`, and the poll loop is stubbed with
      one text-message update under this class's own bot token
      when: the loop picks the update up
      then: the failure is logged by `TelegramUpdateListener` and the batch is still confirmed with the
      follow-up `getUpdates` offset (**D6**)

- [x] RS03 · `ExtractIntentsSystemTest` · covers: `ExtractIntents`
  - Happy Path:
    - given: the provider answers one `CREATE` expense filed under `Insurance > Travel`, the stubbed ledger
      accepts the tool call, and the request carries a bearer token
      when: the RPC is called
      then: it answers an empty `ExtractIntentsResponse`, and the ledger received one
      `create_expense_proposal` call carrying `Travel`, `Insurance`, the description and the amount, under the
      request's own token
  - Unhappy Path:
    - given: the provider answers one `CREATE` expense and the stubbed ledger answers a tool result flagged
      `isError`
      when: the RPC is called
      then: it fails `FAILED_PRECONDITION` (**D35**)
    - given: a request arriving with no `authorization` metadata
      when: the RPC is called
      then: it fails `UNAUTHENTICATED` and the provider is never called (**D21**)
  - note: `whenTextNamesASimpleExpense_thenReturnsOneCreateExpenseIntent`,
    `whenTextCreatesACategoryAndAnExpenseInIt_thenExpenseUsesTheCategoryCreatedEarlierInMessage` and
    `whenProviderExtractsNothingActionable_thenReturnsOneUnknownIntent` were deleted in stabilization — nothing
    comes back over the wire any more; the Happy Path scenario above replaces them.
  - update: `whenTextNamesNoCategory_thenExpenseIsFiledUnderTheMatchingKnownCategory()` — keep the assertion
    that the model saw the full closed set, now as rendered labels; drop the response assertions, and attach a
    bearer token to the request.
  - update: `whenProviderRespondsWithServerError_thenFailsWithStatusUnavailable()` — attach a bearer token to
    the request, so the call reaches the provider at all.

### Green Phase

#### TDD Unit Green Phase

- [ ] GU01 · `KnownCategory` (`bot.finance.application.dto`) · test: `KnownCategoryTest`
- [ ] GU02 · `IntentExtractionRequest` · test: `IntentExtractionRequestTest` · after: GU01
- [ ] GU03 · `HandleIncomingMessageUseCase` · test: `HandleIncomingMessageUseCaseTest` · after: GU01, GU02
- [ ] GU04 · `KnownCategory` (`bot.finance.ai.application.dto`) · test: `KnownCategoryTest`
- [ ] GU05 · `ExtractIntentsCommand` · test: `ExtractIntentsCommandTest` · after: GU04
- [ ] GU06 · `ExpenseIntent` · test: `ExpenseIntentTest`
- [ ] GU07 · `ProposedExpense` · test: `ProposedExpenseTest`
- [ ] GU08 · `ExtractIntentsUseCase` · test: `ExtractIntentsUseCaseTest` · after: GU04, GU05, GU06, GU07
- [ ] GU09 · `CallerTokenUtils` · test: `CallerTokenUtilsTest`
- [ ] GU10 · `IntentProtoUtils` (`bot.finance.adapter.aiconnector`) · test: `IntentProtoUtilsTest` ·
  after: GU01, GU02

#### TDD Integration Green Phase

- [ ] GI01 · `CategoryRepositoryAdapter` · test: `CategoryRepositoryAdapterTest` · after: GU01
- [ ] GI02 · `AiConnectorIntentExtractionAdapter` · test: `AiConnectorIntentExtractionAdapterTest` ·
  after: GU01, GU02, GU10
- [ ] GI03 · `IntentExtractionGrpcService` · test: `IntentExtractionGrpcServiceTest` · after: GU04, GU05, GI04
- [ ] GI04 · `CallerTokenInterceptor` · test: `CallerTokenInterceptorTest` · after: GU09
- [ ] GI05 · `McpExpenseProposalAdapter` · test: `McpExpenseProposalAdapterTest` · after: GU07, GU09

#### TDD System Test Green Phase

- [ ] GS01 · `ReceiveTelegramMessageSystemTest` · covers: `HandleIncomingMessagePort.handle()`
- [ ] GS02 · `HandleIncomingMessageFailureSystemTest` · covers: `HandleIncomingMessagePort.handle()`
- [ ] GS03 · `ExtractIntentsSystemTest` · covers: `ExtractIntents`

## Open Questions / Blockers

- **Q1:** ADR candidate (**D29**): *the ledger no longer mirrors the intent vocabulary — an extracted intent
  stays inside the AI Connector Service, which acts on it through the ledger's MCP server*. It supersedes
  ADR 0005, whose decision still holds for the connector. Write it? If not, the fact lives in the rewritten
  `handle-incoming-message` use-case page and the intent-extraction contract, and ADR 0005 keeps standing
  unqualified against a wire that no longer carries intents.
- A: no, that's a business decision. 

- **Q2:** `spring-ai-starter-mcp-client` is assumed present in `spring-ai-bom` 2.0.0 (**D26**) but is not on
  any dependency list in this repository today, and the first build after adding it needs network access. If
  the coordinate does not resolve, ST18 blocks and every connector step behind it stalls — is a build with
  network access available for this run?
- A: yes

## Review Findings

- **F1:** RI03 attached no caller token, so `@GrpcAdapterTest`'s whole-application context would refuse every request `UNAUTHENTICATED`.
- Resolution: mechanical
- Action: applied — RI03's last `update:` bullet now attaches `authorization: Bearer <opaque text>` to every request in the class.

- **F2:** The interceptor as written also refused `grpc.health.v1.Health`, breaking `ActuatorHealthSystemTest` and the ledger's production health indicator.
- Resolution: mechanical
- Action: applied — ST16 scopes the refusal to `IntentExtractionService`, and RI04 gains a scenario asserting an unauthenticated health check still succeeds.

- **F3:** No step declared `findKnownCategories` on `CategoryEntityRepository`, so RI01's mocked-store scenario would not compile.
- Resolution: mechanical
- Action: applied — ST03 now declares the `@Query`-annotated repository method.

- **F4:** No step touched `GrpcStatusConfiguration`, where the design maps `ExpenseProposalFailedException`.
- Resolution: mechanical
- Action: applied — ST15 adds the stub `GrpcExceptionHandler` branch with a `TODO` for the status split.

- **F5:** RS03 kept `whenTextNamesNoCategory_thenExpenseIsFiledUnderTheMatchingKnownCategory()` without a bearer token.
- Resolution: mechanical
- Action: applied — its `update:` bullet now attaches one.

- **F6:** GI03's `after:` omitted GI04, the interceptor its tests enter through.
- Resolution: mechanical
- Action: applied — GI04 added to GI03's `after:`.

- **F7:** `GrpcStubServer` is JVM-wide and nothing reset it between ledger system-test classes, so RS02's forced failure would leak.
- Resolution: mechanical
- Action: applied — ST22 adds `GrpcStubServer.reset()` to `AbstractSystemTest`'s `@AfterEach`.

- **F8:** ST06 deleted `IntentProtoUtilsTest` whole, but its `ToProtoRequest` group covers `toProtoRequest`, which survives and changes shape.
- Resolution: decision
- Action: resolved — the ledger's testing conventions map a pure `*Utils` mapper to the unit layer, and `toProtoRequest` is the only public method left on `IntentProtoUtils`; ST06 now deletes only the `ToIntents` group, and RU10/GU10 cover the surviving mapper.

- **F9:** RU08's `Trips` scenario omitted a description, which D32 turns into an `UnknownIntent`.
- Resolution: mechanical
- Action: applied — the given now carries an amount and a description.

- **F10:** Two RU06 scenarios duplicated coverage `ExpenseIntentTest` already has, one of it a parameterized matrix.
- Resolution: mechanical
- Action: applied — both dropped; a fourth `nullOptionalPositions()` row replaces the null-`parentCategoryName` case.

- **F11:** RU05's only new scenario was already covered by two existing tests.
- Resolution: mechanical
- Action: applied — the scenario is gone; RU05 is now `update:` bullets only.

- **F12:** RU02 named `whenKnownCategoriesContainNullOrBlank_thenThrows`, which exists nowhere.
- Resolution: mechanical
- Action: applied — replaced with `whenKnownCategoriesContainsNullOrBlankEntry_thenThrowsInvalidExtractionRequestException()`.

- **F13:** Every `update:` bullet omitted `()`, so `plan.sh validate` parsed none of them and checked no method name.
- Resolution: mechanical
- Action: applied — every `update:` bullet re-emitted with `()`, and the blanket "every existing test method" bullets replaced by the actual names.

- **F14:** ST09 deleted five domain pages but left the links into them from the pages that survive.
- Resolution: mechanical
- Action: applied — ST09 now strips the references in `money.md`, `category.md` and `currency-code.md`.

- **F15:** Neither module's testing-conventions Package Structure block was updated for the added and deleted shared test infrastructure.
- Resolution: mechanical
- Action: applied — ST21 drops `IntentFixtures` from the ledger's block, ST24 lists the three new connector classes in its own.

- **F16:** ST21 claimed `application-test.yaml` needs the keystore properties; `application.yaml` already supplies them, and the annotation needs `@EnableConfigurationProperties` instead.
- Resolution: mechanical
- Action: applied — ST21 reworded, the `application-test.yaml` edit dropped.
