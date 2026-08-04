# Plan: A Grouping and a Category Are Different Types

**Affected Modules:** `ledger-service`, `ai-connector-service`
**Design:** [A Grouping and a Category Are Different Types](design.md)

## Step-by-Step Implementation Map (To-Do List)

### Stabilization

#### Interface-First / Build Stabilization

**Interface & Signature Sync**

- [x] ST01 · Add `domain/exception/InvalidGroupingException`, shaped exactly like the neighbouring
  `InvalidCategoryException`, which stays.
- [x] ST02 · Reduce `domain/value/Category` to `record Category(String name)`: keep the blank-name check throwing
  `InvalidCategoryException`, delete the child list, the null-child check, the `category tree exceeds two levels`
  check, and the `leaf`, `group`, `catchAllGroupingName` and `defaults` factories.
- [x] ST03 · Add `domain/value/Grouping` as `record Grouping(String name, List<Category> categories)`, its compact
  constructor rejecting a blank name, a null list and a null element with `InvalidGroupingException` and copying
  the list, carrying:
  ```java
  public static Grouping of(String name, String... categoryNames);
  public static String catchAllName();
  public static List<Grouping> defaults();
  ```
  `defaults()` is today's `Category.defaults()` catalogue verbatim — the same 20 groupings, the same categories, in
  the same order — with each `group(...)` written as `Grouping.of(...)` and the catch-all named by
  `catchAllName()`.
- [x] ST04 · Add `application/dto/StoredGrouping` as `record StoredGrouping(long id, String name)`, rejecting a
  non-positive id and a blank name with `InvalidGroupingException`.
- [x] ST05 · Reduce `application/dto/StoredCategory` to `(long id, String name)` — the `Optional<String> parentName`
  component and its check are deleted.
- [x] ST06 · Add `application/port/GroupingRepository` with `@throws PersistenceFailedException` javadoc on each
  operation:
  ```java
  Optional<StoredGrouping> findByUserIdAndName(long userId, String name);
  List<String> findCategoryNames(long userId, StoredGrouping grouping);
  List<String> findNamesWithCategories(long userId);
  ```
- [x] ST07 · Replace `application/port/CategoryRepository`'s three operations with two, same javadoc convention:
  ```java
  Optional<StoredCategory> findByGroupingAndName(long userId, StoredGrouping grouping, String name);
  boolean existsByUserIdAndName(long userId, String name);
  ```
- [x] ST08 · Change `application/port/UserRepository.create` to `User create(User user, List<Grouping> groupings)`
  and add `@throws InvalidGroupingException` beside the existing `@throws InvalidCategoryException`.
- [x] ST09 · Rename `application/dto/ListCategoriesCommand`'s second component to `groupingName` and move both its
  checks to `InvalidGroupingException`, with messages `list categories command has no userId` and
  `list categories command has no grouping name`.
- [x] ST10 · Rename `application/dto/CreateExpenseProposalCommand.parentCategoryName` to `groupingName`; its check
  keeps `InvalidExpenseProposalException` (D23) and its message becomes
  `new expense proposal has no grouping name`.
- [x] ST11 · Rename `adapter/persistence/CategoryEntity`'s factories for what they build, fields unchanged:
  ```java
  public static CategoryEntity grouping(long userId, Grouping grouping);
  public static CategoryEntity category(long userId, long groupingId, Category category);
  ```
- [x] ST12 · Replace `adapter/persistence/CategoryEntityRepository`'s derived queries so every lookup leads with
  `user_id`, and rename the `@Query` method with its SQL unchanged:
  ```java
  Optional<CategoryEntity> findByUserIdAndNameAndParentIdIsNull(Long userId, String name);
  Optional<CategoryEntity> findByUserIdAndParentIdAndName(Long userId, Long parentId, String name);
  List<CategoryEntity> findByUserIdAndParentIdOrderByName(Long userId, Long parentId);
  boolean existsByUserIdAndNameAndParentIdIsNotNull(Long userId, String name);
  List<String> findNonEmptyGroupingNames(@Param("userId") Long userId);
  ```
  `findByUserIdAndName` and `findByParentIdOrderByName` are deleted.
- [x] ST13 · Add `adapter/persistence/GroupingRepositoryAdapter`, a `@Component` over `CategoryEntityRepository`
  implementing `GroupingRepository`, with each method stubbed:
  ```java
  public Optional<StoredGrouping> findByUserIdAndName(long userId, String name) {
      // reads the caller's parentless row carrying that name and maps it onto StoredGrouping,
      // wrapping any framework exception in PersistenceFailedException
      return Optional.empty();
  }
  ```
  and the same shape for `findCategoryNames` (the grouping's categories' names, ordered by name) and
  `findNamesWithCategories` (delegates to `findNonEmptyGroupingNames`).
- [x] ST14 · Reduce `adapter/persistence/CategoryRepositoryAdapter` to the two `CategoryRepository` operations,
  stubbed the same way, and delete `toStoredCategory` and its per-row `findById`.
- [x] ST15 · Change `adapter/persistence/ColumnLimits.validateCategoryNames(List<Category>)` to
  `validateCatalogueNames(List<Grouping>)`, checking each grouping's name and each of its categories' against
  `CATEGORY_NAME` — `InvalidGroupingException` for a grouping's name, `InvalidCategoryException` for a category's.
- [x] ST16 · Update `adapter/persistence/UserRepositoryAdapter` to `create(User user, List<Grouping> groupings)`,
  calling `ColumnLimits.validateCatalogueNames`; `writeCategoryTree` keeps its insert order, its
  name-to-generated-id map and its two `READ COMMITTED` comments, and writes through `CategoryEntity.grouping(...)`
  and `CategoryEntity.category(...)`.
- [x] ST17 · Update the four use cases' constructors and call sites, keeping existing logic and marking the
  reshaped bodies with a `TODO` naming the green step that owns them:
    - `ListCategoriesUseCase` takes `UserRepository`, `GroupingRepository` and `CategoryRepository`; its
      absent-command guard becomes `InvalidGroupingException`, message unchanged;
    - `CreateExpenseProposalUseCase` takes `GroupingRepository` beside `CategoryRepository`;
    - `HandleIncomingMessageUseCase` takes `GroupingRepository` in place of `CategoryRepository` and reads
      `Grouping.catchAllName()`;
    - `InitializeUserUseCase` passes `Grouping.defaults()`.
- [x] ST18 · Rename the MCP tools' argument and wire the new exception into their catch chains:
    - `ListCategoriesMcpTool` — parameter `grouping`, description unchanged; `InvalidGroupingException` caught
      above `InvalidUserException` and rendered bare, as `InvalidCategoryException` is;
    - `ListCategoriesToolResponse` — `(String grouping, List<String> categories)`;
    - `CreateExpenseProposalMcpTool` — parameter `grouping`, described as *"the grouping the category is filed
      under, exactly as `list_categories` was asked for it"*; `InvalidGroupingException` caught beside
      `InvalidCategoryException`;
    - `CreateExpenseProposalToolRequest` — `parentCategory` becomes `grouping`;
    - `ExpenseProposalToolUtils` — the absent-or-blank check reads `request.grouping()` and refuses
      `expense proposal request has no grouping`, still as `InvalidExpenseProposalException` (D23).
- [x] ST19 · Update `adapter/config/UseCaseConfiguration`'s `handleIncomingMessagePort`,
  `createExpenseProposalPort` and `listCategoriesPort` bean methods to take the ports their use cases now use.
- [x] ST20 · In `ai-connector-service`, change line 5 of
  [`user-message.st`](../../ai-connector-service/src/main/resources/prompts/user-message.st): "sending that
  grouping as the parent category" becomes "sending that grouping as its grouping". No Java production file in
  that module changes (D11).

**Shared Test Infrastructure**

- [x] ST21 · Rename `bot.finance.common.CategoryRowUtils`'s row helpers for what they insert —
  `storedCategoryId(...)` (parentless row) becomes `storedGroupingId(...)`, `storedChildCategoryId(...)` becomes
  `storedCategoryId(...)` — and update its line in
  [Testing Conventions](../../ledger-service/docs/conventions/testing.md#package-structure). The rename reuses a
  surviving name at a different arity, so rewrite **every** reading class's call sites in this step:
  `CategoryRepositoryAdapterTest` and `UserRepositoryAdapterTest`, plus `ExpenseRepositoryAdapterTest` and
  `ExpenseProposalRepositoryAdapterTest` — including their own private wrappers of the same names — which no
  red-phase step owns and which ST24 would otherwise disable for good. `GroupingRepositoryAdapterTest` is written
  against the renamed helpers from the start.
- [x] ST22 · Rename the argument in `bot.finance.common.McpRequests` — `listCategories(String grouping)` and
  `createExpenseProposal(..., String grouping, ...)` — and the JSON key in both request bodies, from
  `parentCategory` to `grouping`.
- [x] ST23 · In `ai-connector-service`, update `bot.finance.ai.common.McpLedgerStubs`: the stubbed
  `create_expense_proposal` and `list_categories` tool schemas name `grouping` in their `properties` and
  `required` arrays, `stubListCategoriesAnswering(String grouping, List<String> categories)` takes that name, and
  the stubbed `list_categories` result body answers `{"grouping":"…","categories":[…]}`.

**Build Stabilization**

- [x] ST24 · Comment out the bodies of the test methods that cannot compile against the new signatures, keeping
  each method and adding `@Disabled("<RU/RI/RS id>: …")` naming the red-phase step that owns it, per
  [Testing Conventions](../../ledger-service/docs/conventions/testing.md#test-tooling) — never delete or comment
  out the method itself. Both modules compile, `ledger-service/gradlew` and `ai-connector-service/gradlew` builds
  are green.
- [x] ST25 · Confirm `bot.finance.architecture.CleanArchitectureTest` still passes — `Grouping`, `StoredGrouping`,
  `GroupingRepository` and `InvalidGroupingException` carry no external-system name and no new class lands in
  `domain/model` (D19).

### Red Phase

#### TDD Unit Red Phase

- [x] RU01 · `Category` · test: `CategoryTest` · covers: `Category(String)`
    - `Category(String)`:
        - given: a non-blank name
          when: the record is constructed
          then: `name()` reads it back unchanged
        - update: `whenNameIsAbsentEmptyOrOnlyWhitespace_thenThrowsInvalidCategoryException()` — construct with the
          one-component constructor
        - update: `whenChildListIsAbsent_thenThrowsInvalidCategoryException()` — delete; a category has no child
          list to be absent
        - update: `whenChildListCarriesANullElement_thenThrowsInvalidCategoryException()` — delete, same reason
        - update: `whenAChildCarriesChildrenOfItsOwn_thenThrowsInvalidCategoryException()` — delete; the two-level
          rule is unrepresentable rather than checked (D4)
        - update: `whenSourceListIsModifiedAfterConstruction_thenCategoryChildrenAreUnchanged()` — delete; the
          defensive copy moves to `GroupingTest`
        - update: `whenLeafIsCalled_thenReturnsCategoryWithThatNameAndNoChildren()` — delete; `leaf(...)` is gone
        - update: `whenGroupIsCalled_thenReturnsCategoryWithChildlessChildrenInOrder()` — delete; `group(...)`
          becomes `Grouping.of(...)`, covered by RU02
        - update: `whenCatchAllGroupingNameIsCalled_thenReturnsMiscellaneous()` — delete; moves to `GroupingTest`
        - update: `whenCatchAllGroupingNameIsComparedAgainstDefaultsNames_thenExactlyOneGroupingCarriesThatName()` —
          delete; moves to `GroupingTest`
        - update: `whenDefaultsIsCalled_thenReturnsThe20PredefinedGroupsByNameAndInOrder()` — delete; moves to
          `GroupingTest`
        - update: `whenDefaultsIsCalled_thenTheWholeTreeHolds97CategoriesOf77AreChildren()` — delete; moves to
          `GroupingTest`
        - update: `whenDefaultsIsCalled_thenHousingCarriesItsSevenChildrenInOrder()` — delete; moves to
          `GroupingTest`
        - update: `whenDefaultsIsCalled_thenNoGroupHasDuplicateChildrenAndNoTwoGroupsShareAName()` — delete; moves
          to `GroupingTest`
        - update: `whenDefaultsIsCalled_thenTravelIsPresentAsGroupAndAsChildOfInsurance()` — delete; moves to
          `GroupingTest`
        - update: `whenDefaultsIsCalled_thenTreeIsExactlyTwoLevels()` — delete; the types carry it now (D4)
- [x] RU02 · `Grouping` · test: `GroupingTest` · covers: `Grouping(String, List<Category>)`, `of()`,
  `catchAllName()`, `defaults()`
    - `Grouping(String, List<Category>)`:
        - given: a name that is absent, empty or only whitespace
          when: the record is constructed
          then: throws `InvalidGroupingException`
        - given: a null category list
          when: the record is constructed
          then: throws `InvalidGroupingException`
        - given: a category list carrying a null element
          when: the record is constructed
          then: throws `InvalidGroupingException`
        - given: a mutable category list handed to the constructor
          when: the source list is modified afterwards
          then: the grouping's categories are unchanged
    - `of()`:
        - given: a name and three category names
          when: `of()` is called
          then: returns a grouping with that name whose categories carry those names, in the order given
        - given: a name and no category names
          when: `of()` is called
          then: returns a grouping with that name and an empty category list
    - `catchAllName()`:
        - given: nothing
          when: `catchAllName()` is called
          then: returns `Miscellaneous`
        - given: the names `defaults()` answers
          when: they are filtered on `catchAllName()`
          then: exactly one grouping carries that name
    - `defaults()`:
        - given: nothing
          when: `defaults()` is called
          then: returns the 20 predefined groupings, by name and in order — `Housing`, `Groceries`, `Dining`,
          `Transportation`, `Utilities`, `Healthcare`, `Education`, `Shopping`, `Entertainment`, `Travel`, `Pets`,
          `Family & Children`, `Financial`, `Investments`, `Gifts & Donations`, `Work`, `Insurance`,
          `Personal Care`, `Subscriptions`, `Miscellaneous`
        - given: nothing
          when: `defaults()` is called
          then: the catalogue holds 20 groupings and 77 categories, 97 names in all
        - given: nothing
          when: `defaults()` is called
          then: the grouping named `Housing` carries exactly `Rent`, `Mortgage`, `HOA`, `Property Tax`,
          `Home Insurance`, `Repairs`, `Furniture`, in that order
        - given: nothing
          when: `defaults()` is called
          then: no grouping carries two categories of the same name and no two groupings share a name
        - given: nothing
          when: `defaults()` is called
          then: `Travel` is present both as a grouping and as a category under `Insurance`
- [x] RU03 · `StoredCategory` · test: `StoredCategoryTest` · covers: `StoredCategory(long, String)`
    - `StoredCategory(long, String)`:
        - given: a positive id and a non-blank name
          when: the record is constructed
          then: both components read back unchanged
        - update: `whenIdIsZeroOrNegative_thenThrowsInvalidCategoryException()` — construct with the two-component
          constructor
        - update: `whenNameIsAbsentEmptyOrWhitespace_thenThrowsInvalidCategoryException()` — same
        - update: `whenParentNameOptionalIsNull_thenThrowsInvalidCategoryException()` — delete; the component is
          gone
        - update: `whenIdNameAndEmptyParentNameAreValid_thenTheRecordCarriesThemUnchanged()` — delete; replaced by
          the scenario above
- [x] RU04 · `StoredGrouping` · test: `StoredGroupingTest` · covers: `StoredGrouping(long, String)`
    - `StoredGrouping(long, String)`:
        - given: an id that is zero or negative
          when: the record is constructed
          then: throws `InvalidGroupingException`
        - given: a name that is absent, empty or only whitespace
          when: the record is constructed
          then: throws `InvalidGroupingException`
        - given: a positive id and a non-blank name
          when: the record is constructed
          then: both components read back unchanged
- [x] RU05 · `ListCategoriesCommand` · test: `ListCategoriesCommandTest` ·
  covers: `ListCategoriesCommand(AuthenticatedUserId, String)`
    - `ListCategoriesCommand(AuthenticatedUserId, String)`:
        - update: `whenUserIdAndParentCategoryNameAreValid_thenBothComponentsReadBackUnchanged()` — read back the
          renamed `groupingName` component
        - update: `whenUserIdIsAbsent_thenThrowsInvalidCategoryException()` — expect `InvalidGroupingException`
          (D22), and rename the method and its `@DisplayName` to say so
        - update: `whenParentCategoryNameIsAbsentEmptyOrWhitespace_thenThrowsInvalidCategoryException()` — expect
          `InvalidGroupingException` for an absent, empty or whitespace-only `groupingName`, and rename the method
          and its `@DisplayName` for both the argument and the exception
- [x] RU06 · `CreateExpenseProposalCommand` · test: `CreateExpenseProposalCommandTest` ·
  covers: `CreateExpenseProposalCommand(...)`
    - `CreateExpenseProposalCommand(...)`:
        - update: `whenParentCategoryNameIsAbsentEmptyOrWhitespace_thenThrowsInvalidExpenseProposalException()` —
          the component is `groupingName`; the exception type is unchanged (D23)
        - update: `whenEveryFieldIsPresentAndMerchantIsNonBlank_thenTheRecordCarriesThemUnchanged()` — read back
          the renamed `groupingName` component
- [x] RU07 · `ListCategoriesUseCase` · test: `ListCategoriesUseCaseTest` · covers: `list()`
    - `list()`:
        - given: a stored user, and `groupingRepository.findByUserIdAndName` answering a grouping
          when: `list()` is called
          then: `groupingRepository.findCategoryNames` is called with that user's stored id and that grouping, its
          answer is returned unchanged, and `categoryRepository` is never touched
        - given: a stored user, `findByUserIdAndName` answering nothing, and
          `categoryRepository.existsByUserIdAndName` answering `true`
          when: `list()` is called
          then: throws `InvalidGroupingException` whose message names the name asked for and says it is a category,
          not a grouping; `existsByUserIdAndName` received the stored user's id, and `findCategoryNames` is never
          called
        - given: a stored user, `findByUserIdAndName` answering nothing, and `existsByUserIdAndName` answering
          `false`
          when: `list()` is called
          then: throws `InvalidGroupingException` whose message names the name asked for and says no grouping of
          that name is stored for this user; `existsByUserIdAndName` received the stored user's id
        - given: a stored user, `findByUserIdAndName` answering nothing, and `existsByUserIdAndName` raising
          `PersistenceFailedException`
          when: `list()` is called
          then: the exception reaches the caller unchanged
        - given: a stored user whose database id differs from the command's external id, and a grouping answered
          for it
          when: `list()` is called
          then: both reads receive that stored user's id, not the external id
        - update: `whenCommandIsAbsent_thenThrowsInvalidCategoryExceptionAndRepositoriesAreUntouched()` — expect
          `InvalidGroupingException`, message unchanged, assert both repositories untouched (D22), and rename the
          method and its `@DisplayName` to say `InvalidGroupingException`
        - update: `whenNoUserExistsForExternalId_thenThrowsEntityNotFoundExceptionAndCategoryRepositoryUntouched()`
          — assert the grouping repository is untouched as well
        - update: `whenFindByUserIdAndNameAnswersEmptyList_thenThrowsInvalidCategoryExceptionNamingGroupingAsUnstored()`
          — delete; replaced by the two refusal scenarios above
        - update: `whenAllCandidatesCarryAParentName_thenThrowsInvalidCategoryExceptionSayingCategoryNotGrouping()` —
          delete; the candidate filter is gone
        - update: `whenCandidatesIncludeOneParentlessAndOneWithParent_thenFindChildNamesReceivesParentlessCandidatesId()`
          — delete; `Travel` resolving to the parentless row is now the query's `parent_id IS NULL`, covered by
          RI01
        - update: `whenGroupingHasChildren_thenReturnsChildNamesExactlyAsRepositoryAnswered()` — read through
          `groupingRepository.findCategoryNames`
        - update: `whenStoredUsersIdDiffersFromExternalId_thenRepositoriesReceiveStoredUsersIdNotExternalId()` —
          delete; replaced by the scoping scenario above
        - update: `whenGroupingHasNoChildren_thenReturnsEmptyListRatherThanThrowing()` — stub
          `findCategoryNames` answering an empty list
        - update: `whenFindByUserIdAndNameRaisesPersistenceFailedException_thenExceptionPropagatesUnchanged()` —
          raise it from `groupingRepository.findByUserIdAndName`, assert `findCategoryNames` is never called
        - update: `whenFindChildNamesRaisesPersistenceFailedException_thenExceptionPropagatesUnchanged()` — raise
          it from `groupingRepository.findCategoryNames`
- [x] RU08 · `CreateExpenseProposalUseCase` · test: `CreateExpenseProposalUseCaseTest` · covers: `create()`
    - `create()`:
        - given: a stored user, a grouping answered for the command's grouping name, and a category answered under
          it
          when: `create()` is called
          then: the proposal handed to `expenseProposalRepository.create` carries that category's id, and
          `categoryRepository.findByGroupingAndName` received the user's stored id and that grouping
        - given: a stored user and `groupingRepository.findByUserIdAndName` answering nothing
          when: `create()` is called
          then: throws `InvalidGroupingException` naming the grouping as not stored for this user, and neither the
          category repository nor the proposal repository is touched
        - given: a stored user, a grouping answered, and `categoryRepository.findByGroupingAndName` answering
          nothing
          when: `create()` is called
          then: throws `InvalidCategoryException` naming both the category name and the grouping, and the proposal
          repository is untouched
        - given: a stored user and `groupingRepository.findByUserIdAndName` raising `PersistenceFailedException`
          when: `create()` is called
          then: the exception reaches the caller unchanged and the proposal repository is untouched
        - update: `whenUserExistsForExternalId_thenRepositoryStoresProposalWithResolvedUserIdAndClockInstant()` —
          stub both reads through the two ports
        - update: `whenNoUserExistsForExternalId_thenThrowsEntityNotFoundExceptionAndProposalRepositoryIsUntouched()`
          — assert the grouping repository is untouched as well
        - update: `whenCommandIsAbsent_thenThrowsInvalidExpenseProposalExceptionAndRepositoriesAreUntouched()` —
          assert the grouping repository is untouched as well
        - update: `whenProposalRepositoryRaisesPersistenceFailedException_thenExceptionPropagatesUnchanged()` —
          stub both reads through the two ports
        - update: `whenUserRepositoryRaisesPersistenceFailedException_thenExceptionPropagatesUnchangedAndProposalRepositoryUntouched()`
          — assert the grouping repository is untouched as well
        - update: `whenExactlyOneStoredCategoryMatchesNameWithParent_thenProposalRepositoryStoresProposalWithThatCategoryId()`
          — delete; replaced by the first scenario above
        - update: `whenNoStoredCategoryMatchesName_thenThrowsInvalidCategoryExceptionNamingUnknownNameAndProposalRepositoryUntouched()`
          — delete; `no category named <name> is stored for this user` merges into the under-a-grouping refusal
          (D9)
        - update: `whenOnlyMatchingCategoryIsAGrouping_thenThrowsInvalidCategoryExceptionAndProposalRepositoryUntouched()`
          — delete; the candidate filter is gone
        - update: `whenParentCategoryNameMatchesExactlyOneCandidate_thenProposalRepositoryStoresProposalWithThatCandidatesId()`
          — delete; narrowing is now the query's scope, covered by RI02
        - update: `whenParentCategoryNameMatchesNoCandidate_thenThrowsInvalidCategoryExceptionAndProposalRepositoryUntouched()`
          — delete; replaced by the unknown-category-under-the-grouping scenario above
        - update: `whenCommandCarriesMessageReference_thenProposalRepositoryReceivesProposalWithThatReference()` —
          stub both reads through the two ports
        - update: `whenCategoryRepositoryRaisesPersistenceFailedException_thenExceptionPropagatesUnchangedAndProposalRepositoryUntouched()`
          — raise it from `categoryRepository.findByGroupingAndName` after the grouping resolves
- [x] RU09 · `HandleIncomingMessageUseCase` · test: `HandleIncomingMessageUseCaseTest` · covers: `handle()`
    - `handle()`:
        - update: `stubKnownUserAndCategories()` — the shared fixture and `setUp()` mock `GroupingRepository` in
          place of `CategoryRepository`, stub `findNamesWithCategories`, and read `Grouping.catchAllName()`; the
          eight tests reading the helper inherit the change
        - update: `whenCommandIsNull_thenThrowsInvalidIncomingMessageExceptionAndLogsNothing()` — its
          `verifyNoInteractions` names the grouping repository
        - update: `whenInitializeThrowsPersistenceFailedException_thenExceptionPropagatesAndRemainingPortsUntouched()`
          — same
        - update: `whenHandleIsCalled_thenInitializeAndExtractionAndLookupCarryUserAndReference()` — the grouping
          names come from `groupingRepository.findNamesWithCategories`, and the catch-all asserted is
          `Grouping.catchAllName()`
        - update: `whenGroupingNamesIncludeCatchAllGroupingName_thenExtractionRequestCarriesThoseNamesAndThatNameAsCatchAll()`
          — same port and same catch-all accessor
        - update: `whenGroupingNamesExcludeCatchAllGroupingName_thenCatchAllGroupingMissingExceptionPropagates()` —
          same port and same catch-all accessor
        - update: `whenFindGroupingNamesReturnsEmptyList_thenCatchAllGroupingMissingExceptionPropagates()` — stub
          `findNamesWithCategories` answering an empty list
        - update: `whenFindGroupingNamesThrowsPersistenceFailedException_thenExceptionPropagatesAndExtractionPortUntouched()`
          — raise it from `findNamesWithCategories`
- [x] RU10 · `InitializeUserUseCase` · test: `InitializeUserUseCaseTest` · covers: `initialize()`
    - `initialize()`:
        - update: `whenNoUserExistsForExternalId_thenRepositoryCreatesUserWithDefaultsAndReturnsIt()` — the
          captured second argument is `Grouping.defaults()`
- [x] RU11 · `ExpenseProposalToolUtils` · test: `ExpenseProposalToolUtilsTest` · covers: `toCommand()`
    - `toCommand()`:
        - update: `whenRequestCarriesEveryArgumentAndAnIdentity_thenReturnsCommandCarryingThatIdentityAndFields()` —
          build the request with `grouping` and read back the command's `groupingName` component
        - update: `whenParentCategoryIsNullOrBlank_thenThrowsInvalidExpenseProposalException()` — the argument
          under test is `grouping`, the refusal message reads `expense proposal request has no grouping`, and its
          `@MethodSource("blankParentCategories")` provider is renamed with it
        - update: `whenParentCategoryIsAbsentAndAmountIsMalformed_thenThrowsForTheAmount()` — the absent argument is
          `grouping`; the amount still wins
        - update: `whenParentCategoryIsNonBlank_thenCommandParentCategoryNameIsThatName()` — a non-blank `grouping`
          becomes the command's `groupingName`

#### TDD Integration Red Phase

- [x] RI01 · `GroupingRepositoryAdapter` · test: `GroupingRepositoryAdapterTest` · covers: `findByUserIdAndName()`,
  `findCategoryNames()`, `findNamesWithCategories()`
    - `findByUserIdAndName()`:
        - given: a stored user with a parentless row named `Utilities`
          when: called with that user's id and `Utilities`
          then: answers a `StoredGrouping` carrying that row's id and name
        - given: a stored user with a category named `Supermarkets` under a grouping
          when: called with that user's id and `Supermarkets`
          then: answers nothing — a category is not a grouping
        - given: a stored user carrying `Travel` both as a parentless row and as a category under `Insurance`
          when: called with that user's id and `Travel`
          then: answers the parentless row's id, not the category's
        - given: two stored users each owning a parentless row of the same name
          when: called with the first user's id and that name
          then: answers only that user's row
        - given: a stored user with no row of that name
          when: called
          then: answers nothing
    - `findCategoryNames()`:
        - given: a stored user with a grouping holding three categories, stored out of alphabetical order
          when: called with that user's id and that grouping
          then: answers the three names, sorted by name
        - given: a stored user with a grouping holding nothing
          when: called with that user's id and that grouping
          then: answers an empty list
        - given: two stored users, each with a grouping of the same name holding its own categories
          when: called with the first user's id and the second user's grouping
          then: answers an empty list — the read is scoped to the caller (D21)
    - `findNamesWithCategories()`:
        - given: a stored user with three groupings stored out of alphabetical order, each holding a category
          when: called with that user's id
          then: answers exactly the three grouping names, sorted, with no category name among them
        - given: a stored user with one populated grouping and one holding nothing
          when: called with that user's id
          then: the empty grouping is absent
        - given: two stored users each owning a populated grouping
          when: called with the first user's id
          then: answers only that user's grouping name
        - given: a stored user with no rows at all
          when: called with that user's id
          then: answers an empty list
    - against a mocked `CategoryEntityRepository`, in a nested class of its own, as
      `CategoryRepositoryAdapterTest` already does:
        - given: the entity repository raising `QueryTimeoutException`
          when: each of the three methods is called
          then: each throws `PersistenceFailedException` carrying that exception as its cause
- [x] RI02 · `CategoryRepositoryAdapter` · test: `CategoryRepositoryAdapterTest` · covers:
  `findByGroupingAndName()`, `existsByUserIdAndName()`
    - `findByGroupingAndName()`:
        - given: a stored user with a category named `Supermarkets` under the grouping `Groceries`
          when: called with that user's id, that grouping and `Supermarkets`
          then: answers a `StoredCategory` carrying that row's id and name
        - given: a stored user with the same category name under two different groupings
          when: called with that user's id, the first grouping and that name
          then: answers the row filed under the first grouping
        - given: a stored user whose grouping holds no row of that name
          when: called
          then: answers nothing
        - given: two stored users, each with a grouping of the same name holding a category of the same name
          when: called with the first user's id and the second user's grouping
          then: answers nothing (D21)
        - given: a stored user with a parentless row named `Travel`
          when: called with that user's id, a grouping and `Travel`
          then: answers nothing — a grouping is not a category under another grouping
    - `existsByUserIdAndName()`:
        - given: a stored user with a category named `Supermarkets` under a grouping
          when: called with that user's id and `Supermarkets`
          then: answers `true`
        - given: a stored user with only a parentless row named `Groceries`
          when: called with that user's id and `Groceries`
          then: answers `false`
        - given: two stored users, the second owning a category of that name
          when: called with the first user's id and that name
          then: answers `false`
    - update: `whenCalledForAStoredChildCategory_thenReturnsItCarryingItsGroupingsNameAsParentName()` — delete;
      `parentName` is gone and the read is scoped to a grouping
    - update: `whenCalledForAStoredGrouping_thenReturnsItWithEmptyParentName()` — delete, same reason
    - update: `whenCalledForTwoCategoriesWithSameNameUnderDifferentGroupings_thenReturnsBothWithTheirOwnParentName()`
      — delete; replaced by the second `findByGroupingAndName()` scenario
    - update: `whenCalledForOneOfTwoUsersWithSameCategoryName_thenReturnsOnlyThatUsersCategory()` — delete;
      replaced by the cross-user scenario above
    - update: `whenNoCategoryOfThatNameExists_thenReturnsEmptyList()` — delete; replaced by the answers-nothing
      scenario
    - update: `whenCalledForAGroupingWithThreeChildren_thenReturnsTheThreeNames()` — delete; `findChildNames` moves
      to `GroupingRepositoryAdapterTest` (RI01)
    - update: `whenCalledForACategoryWithNoChildren_thenReturnsEmptyList()` — delete, same reason
    - update: `whenCalledForAStoredUserWithThreeGroupingsEachHoldingAChild_thenReturnsTheThreeGroupingNamesSorted()`
      — delete; `findGroupingNames` moves to RI01
    - update: `whenCalledForAStoredUserWithAChildlessGrouping_thenThatGroupingIsAbsent()` — delete, same reason
    - update: `whenCalledForOneOfTwoUsersEachOwningAGrouping_thenReturnsOnlyThatUsersGroupingName()` — delete, same
      reason
    - update: `whenCalledForAStoredUserWithNoCategories_thenReturnsEmptyList()` — delete, same reason
    - update: `whenFindByUserIdAndNameHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionCarryingFrameworkExceptionAsCause()`
      — cover `findByGroupingAndName()` instead
    - update: `whenFindChildNamesHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionCarryingFrameworkExceptionAsCause()`
      — cover `existsByUserIdAndName()` instead
    - update: `whenFindGroupingNamesHitsDatabaseFailure_thenThrowsPersistenceFailedExceptionCarryingFrameworkExceptionAsCause()`
      — delete; moves to RI01
- [x] RI03 · `UserRepositoryAdapter` · test: `UserRepositoryAdapterTest` · covers: `create()`
    - `create()`:
        - given: an unstored user and a catalogue whose grouping name is exactly 100 characters
          when: `create()` is called
          then: the tree is written and the grouping row carries the whole name
        - update: `whenCalledWithUnstoredUserAndDefaultCategories_thenUserRowWrittenAndReturnedUserCarriesGeneratedId()`
          — pass `Grouping.defaults()`
        - update: `whenCalledWithUnstoredUserAndDefaultCategories_thenCategoryTreeIsWrittenMatchingByName()` — pass
          `Grouping.defaults()`; the helper walks `Grouping.categories()` rather than `Category.children()`
        - update: `whenCalledWithAlreadyStoredExternalId_thenReturnsThatUserAndWritesNoCategories()` — pass
          `Grouping.defaults()`
        - update: `whenExternalIdIsExactly255Characters_thenUserIsWrittenAndReturnedWithGeneratedId()` — the empty
          catalogue is `List.<Grouping>of()`
        - update: `whenExternalIdIs256Characters_thenThrowsInvalidUserExceptionBeforeWritingAnything()` — same
        - update: `whenChildNameIsExactly100Characters_thenTreeIsWrittenAndChildRowCarriesWholeName()` — build the
          catalogue with `Grouping.of(...)`
        - update: `whenChildNameIs101Characters_thenThrowsInvalidCategoryExceptionBeforeWritingAnything()` — build
          with `Grouping.of(...)`; a category's name still raises `InvalidCategoryException`
        - update: `whenGroupNameIs101Characters_thenThrowsInvalidCategoryExceptionBeforeWritingAnything()` — build
          with `Grouping.of(...)`; a grouping's name now raises `InvalidGroupingException` (D15), so rename the
          method and its `@DisplayName` to say so
        - update: `whenCalledForTwoUsers_thenEachOwnsItsOwnCategoryRowsWithNoCrossReferences()` — pass
          `Grouping.defaults()`
        - update: `whenGroupOrderIsNotPreserved_thenChildrenStillPairToTheRightGroupByName()` — build the two
          catalogue entries with `Grouping.of(...)`
        - update: `whenTwoThreadsRaceOnTheSameExternalId_thenBothReturnTheSameUserAndOnlyOneRowSetIsWritten()` — in
          `UserRepositoryAdapterConcurrencyTest`; pass `Grouping.defaults()`
        - update: `whenTwoThreadsRaceOnDifferentExternalIds_thenBothUsersAreStoredEachOwningItsOwnCategoryRows()` —
          in `UserRepositoryAdapterConcurrencyTest`; pass `Grouping.defaults()`
- [x] RI04 · `ListCategoriesMcpTool` · test: `ListCategoriesMcpToolTest` · covers: `list_categories` via
  `POST /mcp` · mocks: `ListCategoriesPort`
    - Error Mapping:
        - given: the mocked port throws `InvalidGroupingException`
          when: the tool is called
          then: the tool error carries that exception's message bare, as `InvalidCategoryException`'s is (D10)
    - update: `whenListCategoriesIsCalled_thenPortReceivesTokenSubjectAndGroupingNameAndResultCarriesBoth()` — the
      command's `groupingName` component is asserted, and the result text carries `"grouping":"Groceries"`
    - update: `whenPortAnswersEmptyList_thenResultIsNonErrorCarryingEmptyCategoriesArray()` — the result text
      carries `"grouping":"Miscellaneous"`
    - update: `whenParentCategoryIsInvalid_thenToolErrorReturnedAndPortNeverCalled()` — the argument under test is
      `grouping`; its `@MethodSource` provider is renamed with it
    - update: `whenPortThrowsAnyFailure_thenWarnLineLogsFailureKindWithoutGroupingNameOrToken()` — unchanged in
      substance; confirm it still reads the renamed request builder
- [x] RI05 · `CreateExpenseProposalMcpTool` · test: `CreateExpenseProposalMcpToolTest` · covers:
  `create_expense_proposal` via `POST /mcp` · mocks: `CreateExpenseProposalPort`
    - Error Mapping:
        - given: the mocked port throws `InvalidGroupingException`
          when: the tool is called
          then: the tool error carries that exception's message bare, beside `InvalidCategoryException`'s (D10)
    - update: `whenCreateExpenseProposalIsCalled_thenPortReceivesTokenSubjectAndResultCarriesStoredProposal()` —
      the command's `groupingName` component is asserted
    - update: `whenParentCategoryAbsent_thenFrameworkSchemaRejectionNamesMissingParentCategoryAndPortNeverCalled()`
      — the missing argument the framework names is `grouping`
    - update: `whenParentCategoryBlank_thenToolErrorNamesInvalidRequestWithNoParentCategoryAndPortNeverCalled()` —
      the blank argument is `grouping` and the refusal reads `expense proposal request has no grouping`
    - update: `whenAmountCannotBeBoundToString_thenFrameworksOwnBindingFailureReportedAndPortNeverCalled()` — the
      inline JSON body names `grouping`
    - update: `whenAmountIsSentAsJsonNumber_thenToolErrorIsRefusedAndPortNeverCalled()` — same inline JSON body
- [x] RI06 · `AiExpenseRecordingAdapter` · test: `AiExpenseRecordingAdapterTest` · covers: `record()` against the
  stubbed provider and ledger
    - update: `whenCalledWithLabelsTextAndCurrency_thenRequestCarriesSystemPromptUserMessageAndToolSchema()` — the
      published tool schemas name `grouping`, and the user message says "sending that grouping as its grouping"
    - update: `lookupArguments()` — the shared `list_categories` argument helper sends `grouping`; it is the only
      place the lookup body names the argument, and
      `whenProviderListsCategoriesThenCreatesProposal_thenBothCallsReachLedgerAndLookupAnswerReachesProvider()`
      reads it
    - update: `whenLedgerRefusesFirstToolCallThenAcceptsCorrected_thenRefusalReachesProviderSecondCallMadeAndNoExceptionThrown()`
      — its hard-coded corrected `create_expense_proposal` arguments send `grouping`
    - ~~update: `whenNoAssumedCurrency_thenUserMessageSaysUnrecordedAndNamesNoCurrencyCode()`~~ — withdrawn as a
      plan defect (B3): that test asserts only the currency-substitution wording and never referenced the grouping
      line. The reworded line is asserted by
      `whenCalledWithLabelsTextAndCurrency_thenRequestCarriesSystemPromptUserMessageAndToolSchema()` instead.

#### TDD System Test Red Phase

- [x] RS01 · `ListCategoriesMcpToolSystemTest` · covers: `POST /mcp` — `tools/call list_categories`
    - Happy Path:
        - update: `whenToolCallNamesGrouping_thenResponseNamesGroupingAndListsChildrenSortedByName()` — the call
          sends `grouping`, the tool result's key is `grouping`, and the seed is `Grouping.defaults()`
    - Unhappy Path:
        - update: `whenToolCallNamesCategory_thenResponseIsToolErrorSayingItIsACategoryNotAGrouping()` — the call
          sends `grouping`; the refusal wording is unchanged (D8)
- [x] RS02 · `CreateExpenseProposalMcpToolSystemTest` · covers: `POST /mcp` —
  `tools/call create_expense_proposal`
    - Happy Path:
        - update: `whenToolCallNamesChildCategory_thenResponseCarriesStoredProposalAndRowIsWritten()` — the call
          sends `grouping`, and the seed is `Grouping.defaults()`
    - Unhappy Path:
        - update: `whenToolCallNamesGrouping_thenResponseIsToolErrorNamingChildrenAndNoRowIsWritten()` — the call
          sends `grouping`, and the asserted refusal text becomes `no category named Supermarkets under grouping
          Dining is stored for this user` (D9)
        - update: `whenToolCallHasNoParentCategory_thenResponseIsToolErrorAndNoRowIsWritten()` — the absent
          argument is `grouping` and the assertion names it
- [x] RS03 · `McpAuthenticationSystemTest` · covers: `POST /mcp` — `tools/list`
    - update: `whenToolsListIsPostedWithValidToken_thenEachPublishedToolIsListedWithItsArgumentsAndNoIdentityArgument()`
      — its `@MethodSource` argument lists carry `grouping` in place of `parentCategory`, for both tools and for
      the required sets
- [x] RS04 · `ReceiveTelegramMessageSystemTest` · covers: `HandleIncomingMessagePort.handle()` — the running
  Telegram poll loop
    - update: `whenRunningPollLoopPicksUpTextMessageUpdate_thenBatchIsConfirmedAndMessageIsPrinted()` — the
      expected row count, grouping names and grouping categories are derived from `Grouping.defaults()` and
      `Grouping.categories()`, and the catch-all asserted is `Grouping.catchAllName()`
- [x] RS05 · `ExtractIntentsSystemTest` · covers: `IntentExtractionService.extractIntents()` over the real gRPC
  channel
    - update: `whenTokenedRequestArrives_thenRpcAnswersEmptyResponseAndLedgerReceivesOneToolCallUnderToken()` — the
      stubbed tool-call arguments and the assertion read `grouping`
    - update: `whenTokenedRequestArrives_thenRpcAnswersEmptyResponseAndLedgerReceivesBothToolCallsUnderToken()` —
      both stubbed tool calls and both assertions read `grouping`

### Green Phase

#### TDD Unit Green Phase

- [x] GU01 · `Category` · test: `CategoryTest`
- [x] GU02 · `Grouping` · test: `GroupingTest` · after: GU01
- [x] GU03 · `StoredCategory` · test: `StoredCategoryTest`
- [x] GU04 · `StoredGrouping` · test: `StoredGroupingTest`
- [x] GU05 · `ListCategoriesCommand` · test: `ListCategoriesCommandTest`
- [x] GU06 · `CreateExpenseProposalCommand` · test: `CreateExpenseProposalCommandTest`
- [x] GU07 · `ListCategoriesUseCase` · test: `ListCategoriesUseCaseTest` · after: GU04, GU05
- [x] GU08 · `CreateExpenseProposalUseCase` · test: `CreateExpenseProposalUseCaseTest` · after: GU03, GU04, GU06
- [x] GU09 · `HandleIncomingMessageUseCase` · test: `HandleIncomingMessageUseCaseTest` · after: GU02
- [x] GU10 · `InitializeUserUseCase` · test: `InitializeUserUseCaseTest` · after: GU02
- [x] GU11 · `ExpenseProposalToolUtils` · test: `ExpenseProposalToolUtilsTest` · after: GU06

#### TDD Integration Green Phase

- [x] GI01 · `GroupingRepositoryAdapter` · test: `GroupingRepositoryAdapterTest` · after: GU04
- [x] GI02 · `CategoryRepositoryAdapter` · test: `CategoryRepositoryAdapterTest` · after: GU03, GU04
- [x] GI03 · `UserRepositoryAdapter` · test: `UserRepositoryAdapterTest` · after: GU01, GU02
- [x] GI04 · `ListCategoriesMcpTool` · test: `ListCategoriesMcpToolTest` · after: GU05
- [x] GI05 · `CreateExpenseProposalMcpTool` · test: `CreateExpenseProposalMcpToolTest` · after: GU06, GU11
- [x] GI06 · `AiExpenseRecordingAdapter` · test: `AiExpenseRecordingAdapterTest`

#### TDD System Test Green Phase

- [x] GS01 · `ListCategoriesMcpToolSystemTest` · covers: `POST /mcp` — `tools/call list_categories`
- [x] GS02 · `CreateExpenseProposalMcpToolSystemTest` · covers: `POST /mcp` —
  `tools/call create_expense_proposal`
- [x] GS03 · `McpAuthenticationSystemTest` · covers: `POST /mcp` — `tools/list`
- [x] GS04 · `ReceiveTelegramMessageSystemTest` · covers: `HandleIncomingMessagePort.handle()` — the running
  Telegram poll loop
- [x] GS05 · `ExtractIntentsSystemTest` · covers: `IntentExtractionService.extractIntents()` over the real gRPC
  channel

### Post-Implementation Steps

#### ADRs

- [x] P01 · Append one dated line to
  [ADR 0003](../../ledger-service/docs/adr/0003-a-category-is-unique-per-user-and-parent-not-per-user.md)'s
  **Consequences**: the two-level rule is now carried by the domain types rather than by a constructor check.
  `Status:` unchanged, no new ADR (D16). Authorized by **Q1**.

## Open Questions / Blockers

- **Q1:** D16 proposes appending one dated line to ADR 0003's **Consequences** — *the two-level rule is now
  carried by the domain types rather than by a constructor check* — with its `Status:` unchanged and no new ADR.
  Without it, that fact lives only in this task's design file, which is archived to `docs/implemented/`. Write the
  line?
- A: Yes, append the line. ADR 0003 keeps its `Status:`; the dated line records that the two-level rule is now
  carried by the domain types rather than by a constructor check. P01 stands.

- **B1 (plan gap, found in stabilization):** `HandleIncomingMessageFailureSystemTest` and
  `TelegramPollFailureRecoverySystemTest` are named by no red-phase step, but both drive the incoming-message flow
  end to end, which now reads `GroupingRepository.findNamesWithCategories`. Against ST13's stub that answers an
  empty list, `HandleIncomingMessageUseCase`'s catch-all check throws `CatchAllGroupingMissingException` before
  extraction runs, so both fail for a reason unrelated to their own scenario. Neither needs reworking — the
  grouping/category split changes nothing they assert.
- Resolution: **cleared.** Both were `@Disabled("GI01: …")` through the red phase, and the Red-phase exit check
  accordingly expected the baseline skip count plus these two. Once GI01 implemented
  `GroupingRepositoryAdapter.findNamesWithCategories` for real, both were re-enabled and pass unchanged — neither
  needed any rework, confirming the split changes nothing they assert. The ledger suite is back at **0 skipped**,
  the Stage 0 baseline.

- **B2 (stage violation, found in RU08):** stabilization was scoped to keep each reshaped use-case body intact
  behind a `TODO`, but it wrote the **finished** post-split logic into `ListCategoriesUseCase.resolveGrouping` and
  `CreateExpenseProposalUseCase.resolveCategoryId`. Verified against the source: both match the design's outcome
  tables exactly, including both refusal messages, and both still carry a stale `TODO(GU07)`/`TODO(GU08)`
  describing work already done. Consequence: RU07 and RU08 are not true RED steps — their tests pass on first
  write, so nothing has yet shown those tests are capable of failing.
- Resolution: the code is correct and reverting it only to have GU07/GU08 retype it is churn, so it stays. The
  proof was not skipped — at the Red-phase exit check both method bodies were temporarily reduced to stubs and the
  two classes re-run: `ListCategoriesUseCaseTest` **7 of 10 failed** (the 3 passing are the command-absent,
  unknown-user and empty-answer guards, which never reach `resolveGrouping`) and
  `CreateExpenseProposalUseCaseTest` **5 of 10 failed** (the 5 passing never reach `resolveCategoryId`). Both
  bodies were then restored and verified byte-identical to commit `94a81ab` by an empty `git diff`. The tests are
  therefore shown capable of failing. GU07 and GU08 are verification steps, and the stale `TODO` lines go in
  Stage 4.

- **B3 (plan defect, found in RI06):** RI06's `update:` bullet for
  `whenNoAssumedCurrency_thenUserMessageSaysUnrecordedAndNamesNoCurrencyCode()` claimed that test asserts the
  prompt's grouping line. Verified against the source: it asserts only `"unrecorded"` and the absence of a
  three-letter currency code, and never referenced that line before or after the rename. The bullet named a test
  needing no update.
- Resolution: bullet withdrawn from RI06 and struck through in place. The reworded prompt line is asserted by
  `whenCalledWithLabelsTextAndCurrency_thenRequestCarriesSystemPromptUserMessageAndToolSchema()`, which the step
  agent extended with a `contains("sending that grouping as its grouping")` check — so the coverage the bullet was
  reaching for exists, in the test that actually reads the user message.

- **B4 (follow-up, raised by the Stage 4 refactor):** the vocabulary migration stops short of the proposal read
  model. `ProposalSummary.parentCategoryName`, `ProposalSummaryProjection.parentName` and `ProposalReportUtils`'s
  read of it still name what this change calls a grouping — `ExpenseProposalRepositoryAdapterTest` asserts
  `summary.parentCategoryName()` equals `"Food"`, which is a grouping. No design decision covers these files and
  the plan never listed them, so the refactor stage correctly left them alone.
- Resolution: out of scope for this plan — renaming that component to `groupingName` is a clean follow-up change
  of its own. Recorded so the next reader finds it rather than rediscovering it.

- **B5 (wording, raised by the Stage 4 refactor):** `user-message.st` now reads "sending that grouping as its
  grouping" (ST20, per D11). It is accurate but reads oddly to a model; "sending that grouping as the `grouping`
  argument" would be plainer.
- Resolution: left as designed — the line is settled by D11 and is asserted text. Worth revisiting only if the
  model's grouping selection degrades in practice.

## Review Findings

- **F1:** ST21's helper rename reuses a surviving name at a different arity and named only two of the four reading
  test classes — `ExpenseRepositoryAdapterTest` and `ExpenseProposalRepositoryAdapterTest` would have been disabled
  by ST24 with no step to bring them back.
- Resolution: mechanical
- Action: applied — ST21 names every reading class and its private wrappers.

- **F2:** GU07 carried `after: GU03`, but the split removes `StoredCategory` from `ListCategoriesUseCase` entirely.
- Resolution: mechanical
- Action: applied — GU07 reads `after: GU04, GU05`.

- **F3:** RS02's new Unhappy Path scenario duplicated the existing `whenToolCallNamesGrouping_...`, which already
  sends a category filed under a different grouping.
- Resolution: mechanical
- Action: applied — dropped the scenario, kept the `update:`.

- **F4:** RS02's `update:` for that test omitted the refusal text D9 changes, leaving an assertion that could never
  pass.
- Resolution: mechanical
- Action: applied — the bullet names `no category named Supermarkets under grouping Dining is stored for this user`.

- **F5:** RS02 filed the happy-path test's `update:` under `Unhappy Path:`.
- Resolution: mechanical
- Action: applied — moved under a `Happy Path:` group.

- **F6:** RI06 named two `AiExpenseRecordingAdapterTest` tests that carry no `parentCategory` and omitted the shared
  `lookupArguments()` helper and `whenLedgerRefusesFirstToolCallThenAcceptsCorrected_...`, which do.
- Resolution: mechanical
- Action: applied — replaced both bullets with the helper and the refusal test.

- **F7:** RU09 left `HandleIncomingMessageUseCaseTest`'s `setUp()`, its `stubKnownUserAndCategories()` helper and
  two `verifyNoInteractions` sites — eleven tests the port swap breaks — with no owner.
- Resolution: mechanical
- Action: applied — added the fixture bullet and the two `verifyNoInteractions` bullets.

- **F8:** RU07 gave the new `existsByUserIdAndName` read no failure scenario and no scoping assertion.
- Resolution: mechanical
- Action: applied — added the `PersistenceFailedException` scenario and the stored-user-id assertion on both refusal
  paths.

- **F9:** Four `update:` bullets flipped the expected exception without renaming the test method, and RU11's omitted
  its `@MethodSource` provider.
- Resolution: mechanical
- Action: applied — RU05, RU07, RI03 and RU11 now state the rename.
