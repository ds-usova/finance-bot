# Plan: MCP Adapter with a Create Expense Proposal Tool

**Affected Modules:** `ledger-service`
**Design:** [MCP Adapter with a Create Expense Proposal Tool](8-design-mcp-adapter-create-expense-proposal.md)

## Step-by-Step Implementation Map (To-Do List)

### Stabilization

#### Interface-First / Build Stabilization

**Interface & Signature Sync**

- [x] ST01 · Add `springAiVersion=2.0.0` to `ledger-service/gradle.properties` and, to
  `ledger-service/build.gradle`, `implementation platform("org.springframework.ai:spring-ai-bom:${springAiVersion}")`
  plus `implementation "org.springframework.ai:spring-ai-starter-mcp-server-webmvc"`, beside the existing starters
- [x] ST02 · Resolve the MCP annotation and result types the tool class uses from the dependency the previous step
  adds, with `tools/inspect-jar/inspect-jar.sh` — the annotation that declares a tool, the one that describes a
  single argument, and the type a tool method returns to signal a tool error. Every later step names them from what
  this step found; no step guesses an import
- [x] ST03 · Add `domain/value/AuthenticatedUserId` as `record AuthenticatedUserId(String externalId)` with a stub
  compact constructor:
  ```java
  public AuthenticatedUserId {
      // rejects an absent, empty or whitespace-only external id with InvalidUserException
  }
  ```
- [x] ST04 · Change `application/dto/CreateExpenseProposalCommand` to
  `record CreateExpenseProposalCommand(AuthenticatedUserId userId, String categoryName,
  Optional<String> parentCategoryName, String description, Optional<String> merchant, Money money)`. Keep the
  existing description, merchant and money checks and the blank-merchant normalization intact; drop the
  `categoryId` check; add a `TODO` at the top of the compact constructor for the new ones:
  ```java
  public CreateExpenseProposalCommand {
      // TODO: reject an absent userId with InvalidUserException; reject an absent, empty or
      // whitespace-only categoryName and an absent parentCategoryName Optional with
      // InvalidExpenseProposalException; normalize a present-but-blank parentCategoryName to
      // Optional.empty()
      ...existing checks unchanged...
  }
  ```
- [x] ST05 · Add `application/dto/StoredCategory` as
  `record StoredCategory(long id, String name, Optional<String> parentName)` — **written complete**, with a compact
  constructor rejecting a non-positive id, a blank name and an absent `parentName` Optional with
  `InvalidCategoryException`. An absent `parentName` means the category is a grouping
- [x] ST06 · Add `application/port/CategoryRepository` with
  `List<StoredCategory> findByUserIdAndName(long userId, String name)` and
  `List<String> findChildNames(long categoryId)`, each documenting `PersistenceFailedException` as `@throws`
  javadoc
- [x] ST07 · Update `application/port/CreateExpenseProposalPort`'s `@throws` javadoc: `EntityNotFoundException` now
  means the command's identity names no stored user, and `InvalidCategoryException` is added for a category name
  that is unknown, a grouping, or ambiguous
- [x] ST08 · Update `application/usecase/CreateExpenseProposalUseCase` — add `CategoryRepository` to the
  constructor, read the identity as `command.userId().externalId()`, and keep every existing step of `create()`
  intact, with a `TODO` at the point the category id is needed:
  ```java
  // TODO: resolve command.categoryName() (narrowed by command.parentCategoryName() when present)
  // through categoryRepository among this user's categories, and throw InvalidCategoryException when
  // the name is unknown, names a grouping (message naming that grouping's children), or matches
  // several (message naming the candidates' groupings)
  long categoryId = 1L;
  ```
- [x] ST09 · Add `adapter/persistence/CategoryEntityRepository extends CrudRepository<CategoryEntity, Long>` —
  **written complete** — with derived queries `List<CategoryEntity> findByUserIdAndName(Long userId, String name)`
  and `List<CategoryEntity> findByParentId(Long parentId)`
- [x] ST10 · Add `adapter/persistence/CategoryRepositoryAdapter` as a `@Component` implementing
  `CategoryRepository`, taking `CategoryEntityRepository` on its constructor, with stubbed methods:
  ```java
  @Override
  public List<StoredCategory> findByUserIdAndName(long userId, String name) {
      // reads every one of that user's categories carrying the name, resolving each row's parent
      // name, and translates every runtime exception into PersistenceFailedException
      return List.of();
  }

  @Override
  public List<String> findChildNames(long categoryId) {
      // reads the names of the categories whose parent is the given one, translating every runtime
      // exception into PersistenceFailedException
      return List.of();
  }
  ```
- [x] ST11 · Add `adapter/security/AccessTokenProperties` as
  `@ConfigurationProperties("mcp.token")` over `keystore`, `keystorePassword`, `keyAlias`, `issuer`, `audience`
  and `Duration ttl` — **written complete** — and enable it with `@EnableConfigurationProperties` from
  `SecurityConfiguration`
- [x] ST12 · Add `adapter/security/AccessTokenMinter` as a `@Component` taking `AccessTokenProperties` and a
  `ResourceLoader`, loading the PKCS#12 keystore's key pair once at construction, with a stubbed method:
  ```java
  public String mint(String userExternalId) {
      // signs an RS256 JWT carrying sub, iss, aud, iat, exp at the configured ttl and a random jti
      return null;
  }
  ```
  plus a `public RSAPublicKey publicKey()` and a `public String keyId()` the JWKS endpoint reads, both written
  complete from the loaded keystore
- [x] ST13 · Add `adapter/security/SecurityConfiguration` — **written complete** — declaring the module's first
  `SecurityFilterChain` (`/mcp/**` authenticated, `/actuator/**` and `/.well-known/jwks.json` permitted, everything
  else denied, CSRF disabled for the stateless token flow) and a `JwtDecoder` built with
  `NimbusJwtDecoder.withJwkSetUri(...)` against this service's own JWKS, pinned to RS256, validating timestamp,
  issuer and audience, and rejecting a token whose `exp - iat` exceeds `AccessTokenProperties.ttl`
- [x] ST14 · Add `adapter/security/JwksController` as a `@RestController` serving
  `GET /.well-known/jwks.json`, taking `AccessTokenMinter`, with a stubbed method:
  ```java
  public Map<String, Object> jwks() {
      // returns the JWK Set carrying this service's RS256 public key, its key id and use=sig
      return Map.of();
  }
  ```
- [x] ST15 · Add `adapter/security/AuthenticatedCallerUtils` as a static `*Utils` class with a private
  constructor and a stubbed method:
  ```java
  public static AuthenticatedUserId authenticatedUserId() {
      // reads the validated token from the security context and returns its subject as an
      // AuthenticatedUserId; throws InvalidUserException when the context carries no validated token
      return null;
  }
  ```
- [x] ST16 · Add `adapter/mcp/CreateExpenseProposalToolRequest` as
  `record CreateExpenseProposalToolRequest(String category, String parentCategory, String description,
  String merchant, Long amountMinorUnits, String currencyCode)` — **written complete** — each component carrying
  the argument-description annotation ST02 named, with the wording from the design's argument table. No identity
  component
- [x] ST17 · Add `adapter/mcp/CreateExpenseProposalToolResponse` as
  `record CreateExpenseProposalToolResponse(long id, String category, String description, String merchant,
  long amountMinorUnits, String currencyCode, Instant createdAt)` — **written complete**. No `userId` component
- [x] ST18 · Add `adapter/mcp/ExpenseProposalToolUtils` as a static `*Utils` class with a private constructor and
  two stubbed methods:
  ```java
  public static CreateExpenseProposalCommand toCommand(
          CreateExpenseProposalToolRequest request, AuthenticatedUserId userId) {
      // builds the command from the request and the caller's identity, lifting a null or blank
      // parentCategory and merchant to Optional.empty() and building Money from amountMinorUnits and
      // CurrencyCode; rejects an absent request before constructing the command
      return null;
  }

  public static CreateExpenseProposalToolResponse toResponse(
          ExpenseProposal proposal, String categoryName) {
      // maps the stored proposal onto the tool's result record
      return null;
  }
  ```
- [x] ST19 · Add `adapter/mcp/CreateExpenseProposalMcpTool` as a `@Component` taking `CreateExpenseProposalPort`
  and `LoggerFactory`, with one method annotated as a tool named `create_expense_proposal` (the annotation ST02
  named), taking `CreateExpenseProposalToolRequest` and returning the framework's tool-result type carrying
  `CreateExpenseProposalToolResponse` as its success payload — a plain record return cannot express the tool error
  the **Failures** table requires — stubbed:
  ```java
  // reads the caller's identity through AuthenticatedCallerUtils, maps the request through
  // ExpenseProposalToolUtils, calls CreateExpenseProposalPort, and converts every failure into a tool
  // error result per the design's Failures table, logging each rejection at WARN with the failure kind
  // and neither the arguments nor the token
  return null;
  ```
- [x] ST20 · Update `ledger-service/docs/conventions/architecture.md`: add `mcp` and `security` to the package
  tree, each with a few words on what it holds, and bring **Architecture Enforcement** in step with ST25 —
  `io.modelcontextprotocol..` on the banned-packages bullet, `Mcp` and `Jwt` on the forbidden-names bullet, and
  `authenticatedUserIdIsConstructedOnlyBySecurityAdapter` in the rules list

**Configuration**

- [x] ST21 · Add the `spring.ai.mcp.server` and `mcp.token` blocks to
  `ledger-service/src/main/resources/application.yaml`, verbatim from the design's **Build and configuration**
  section
- [x] ST22 · Generate the local development keystore
  `ledger-service/src/main/resources/local-mcp-signing.p12` with `keytool` — an RS256 key pair under alias
  `mcp-signing`, password `changeit` — matching the defaults ST21 writes (**Q2**)
- [x] ST23 · Wire `CategoryRepository` into the `createExpenseProposalPort` bean in
  `adapter/config/UseCaseConfiguration`
- [x] ST24 · Add every `MCP_*` variable — `MCP_ENABLED`, `MCP_JWT_KEYSTORE`, `MCP_JWT_KEYSTORE_PASSWORD`,
  `MCP_JWT_KEY_ALIAS`, `MCP_JWT_TTL` — to the table in `ledger-service/docs/configuration.md`, with a note that
  the committed keystore is a development default a deployment replaces from its secret store
- [x] ST25 · Extend `bot.finance.architecture.CleanArchitectureTest` with the three rules from the design's
  **Architecture enforcement** section: `io.modelcontextprotocol..` added to the banned packages, `Mcp` and `Jwt`
  added to `coreTypesCarryNoExternalSystemName`, and a new
  `authenticatedUserIdIsConstructedOnlyBySecurityAdapter` rule. `@AnalyzeClasses(packages = "bot.finance")` scans
  test classes too, so the new rule excludes classes whose top-level name ends with `Test` and those in
  `bot.finance.common`, which construct the type as a fixture

**Shared Test Infrastructure**

- [x] ST26 · Extend `bot.finance.common.CategoryRowUtils` with
  `static long storedChildCategoryId(JdbcAggregateTemplate jdbcAggregateTemplate, long userId, long parentId,
  String name)`, beside the existing `storedCategoryId`, and keep its entry in the package-structure tree in
  `ledger-service/docs/conventions/testing.md` accurate
- [x] ST27 · Add `bot.finance.common.McpRequests` with static methods building the JSON-RPC bodies the system
  tests post to `/mcp` — an `initialize` body and a `tools/call` body for `create_expense_proposal` taking the six
  arguments — as text-block builders, and list it in the package-structure tree in
  `ledger-service/docs/conventions/testing.md`
- [x] ST28 · Add `bot.finance.common.McpTokens` with a static method minting a token for a given external id
  through the application's own `AccessTokenMinter`, and static methods producing, from the same keystore, an
  expired token, a wrong-audience token, and a token whose `exp - iat` exceeds the configured TTL, and list it in
  the same tree
- [x] ST30 · Add the composed annotation `bot.finance.common.McpAdapterTest`, shaped like
  `ai-connector-service`'s `GrpcAdapterTest`: it boots the application over a random HTTP port so the MCP endpoint
  is reachable, with the containerized Postgres wired as `AbstractSystemTest` wires it (the context needs a
  datasource), and leaves isolation to `@MockitoBean` in the test class. Ship the throwaway class that boots it
  and autowires one bean, per `testing.md`'s rule for infrastructure that only proves itself at runtime, and list
  it in the package-structure tree in `ledger-service/docs/conventions/testing.md`

**Close-out**

- [x] ST29 · Compile the module and confirm `bot.finance.architecture.CleanArchitectureTest` passes ·
  after: ST01, ST02, ST03, ST04, ST05, ST06, ST07, ST08, ST09, ST10, ST11, ST12, ST13, ST14, ST15, ST16, ST17,
  ST18, ST19, ST20, ST21, ST22, ST23, ST24, ST25, ST26, ST27, ST28, ST30

### Red Phase

#### TDD Unit Red Phase

- [x] RU01 · `AuthenticatedUserId` · test: `AuthenticatedUserIdTest` · covers: `AuthenticatedUserId()`
    - `AuthenticatedUserId()`:
        - given: an external id that is absent, empty, or only whitespace
          when: the record is constructed
          then: throws InvalidUserException
        - given: a non-blank external id
          when: the record is constructed
          then: the record carries it unchanged
- [x] RU02 · `StoredCategory` · test: `StoredCategoryTest` · covers: `StoredCategory()`
    - `StoredCategory()`:
        - given: an id of zero or negative
          when: the record is constructed
          then: throws InvalidCategoryException
        - given: a name that is absent, empty, or only whitespace
          when: the record is constructed
          then: throws InvalidCategoryException
        - given: an absent parentName Optional
          when: the record is constructed
          then: throws InvalidCategoryException — absence is `Optional.empty()`, never null
        - given: an id, a name and an empty parentName
          when: the record is constructed
          then: the record carries them unchanged, its parentName empty
- [x] RU03 · `CreateExpenseProposalCommand` · test: `CreateExpenseProposalCommandTest` · covers:
  `CreateExpenseProposalCommand()`
    - `CreateExpenseProposalCommand()`:
        - given: an absent userId
          when: the record is constructed
          then: throws InvalidUserException
        - given: a category name that is absent, empty, or only whitespace
          when: the record is constructed
          then: throws InvalidExpenseProposalException
        - given: an absent parentCategoryName Optional
          when: the record is constructed
          then: throws InvalidExpenseProposalException
        - given: a parentCategoryName that is present but empty or only whitespace
          when: the record is constructed
          then: the record's parentCategoryName is `Optional.empty()`
        - update: `whenUserExternalIdIsAbsentEmptyOrWhitespace_thenThrowsInvalidUserException()` — the blank-external-id
          matrix now belongs to `AuthenticatedUserId` (RU01); replace it with the absent-userId scenario above
        - update: `whenCategoryIdIsZeroOrNegative_thenThrowsInvalidExpenseProposalException()` — there is no
          category id; replace it with the blank-category-name scenario above
        - update: `whenDescriptionIsAbsentEmptyOrWhitespace_thenThrowsInvalidExpenseProposalException()`,
          `whenMerchantOptionalIsAbsent_thenThrowsInvalidExpenseProposalException()`,
          `whenMerchantIsPresentButBlank_thenTheRecordsMerchantIsEmpty()`,
          `whenMoneyIsAbsent_thenThrowsInvalidExpenseProposalException()` — carry the new component list into each
          construction call; the assertions are unchanged
        - update: `whenEveryFieldIsPresentAndMerchantIsNonBlank_thenTheRecordCarriesThemUnchanged()` — assert the
          new `userId`, `categoryName` and `parentCategoryName` components instead of `userExternalId` and
          `categoryId`
- [x] RU04 · `CreateExpenseProposalUseCase` · test: `CreateExpenseProposalUseCaseTest` · covers: `create()`
    - `create()`:
        - given: a stored user, and one stored category with that name carrying a parent
          when: create() is called
          then: the proposal repository is asked to store a proposal carrying that category's id, and the stored
          proposal is returned
        - given: a stored user, and no category of theirs carrying that name
          when: create() is called
          then: throws InvalidCategoryException naming the unknown name, and the proposal repository is untouched
        - given: a stored user, and one stored category with that name carrying no parent
          when: create() is called
          then: throws InvalidCategoryException whose message names that grouping's child names, read through
          `findChildNames`, and the proposal repository is untouched
        - given: a stored user, and several stored categories with that name, and a command carrying no
          parentCategoryName
          when: create() is called
          then: throws InvalidCategoryException whose message names the candidates' groupings, and the proposal
          repository is untouched
        - given: a stored user, several stored categories with that name, and a command whose parentCategoryName
          matches exactly one of them
          when: create() is called
          then: the proposal repository is asked to store a proposal carrying that candidate's id
        - given: a stored user, stored categories with that name, and a command whose parentCategoryName matches
          none of them
          when: create() is called
          then: throws InvalidCategoryException, and the proposal repository is untouched
        - given: a category repository that raises PersistenceFailedException while resolving the name
          when: create() is called
          then: the exception reaches the caller unchanged and the proposal repository is untouched
        - update: `whenUserExistsForExternalId_thenRepositoryStoresProposalWithResolvedUserIdAndClockInstant()` —
          build the command from an `AuthenticatedUserId` and a category name, stub `CategoryRepository` to return
          one child category, and assert the proposal carries that category's id
        - update: `whenNoUserExistsForExternalId_thenThrowsEntityNotFoundExceptionAndProposalRepositoryIsUntouched()`,
          `whenCommandIsAbsent_thenThrowsInvalidExpenseProposalExceptionAndRepositoriesAreUntouched()`,
          `whenProposalRepositoryRaisesPersistenceFailedException_thenExceptionPropagatesUnchanged()`,
          `whenUserRepositoryRaisesPersistenceFailedException_thenExceptionPropagatesUnchangedAndProposalRepositoryUntouched()`
          — carry the new command shape and the added `CategoryRepository` constructor argument; the assertions are
          unchanged, and the identity lookup is still asserted to run before the category one
- [x] RU05 · `AuthenticatedCallerUtils` · test: `AuthenticatedCallerUtilsTest` · covers: `authenticatedUserId()`
    - `authenticatedUserId()`:
        - given: a security context holding a validated token whose subject is an external id
          when: authenticatedUserId() is called
          then: returns an AuthenticatedUserId carrying that subject
        - given: a security context holding no authentication
          when: authenticatedUserId() is called
          then: throws InvalidUserException
        - given: a security context holding an authentication that is not a validated token
          when: authenticatedUserId() is called
          then: throws InvalidUserException
        - given: a security context holding a validated token with a blank subject
          when: authenticatedUserId() is called
          then: throws InvalidUserException
- [x] RU06 · `AccessTokenMinter` · test: `AccessTokenMinterTest` · covers: `mint()`, `publicKey()`
    - The test constructs the minter itself, with a `new DefaultResourceLoader()` and an `AccessTokenProperties`
      pointing at the classpath development keystore — no Spring context and no infrastructure, which is what puts
      this class in the unit layer despite living in an adapter package
    - `mint()`:
        - given: the classpath development keystore and a user's external id
          when: mint() is called and the token is parsed
          then: it carries that external id as `sub`, the configured issuer as `iss`, the configured audience as
          `aud`, an `iat`, an `exp` exactly the configured ttl after the `iat`, and a `jti`
        - given: the same properties
          when: mint() is called twice for the same external id
          then: the two tokens carry different `jti` values
        - given: the classpath development keystore
          when: mint() is called and the token's header is read
          then: the algorithm is RS256 and the signature verifies against the keystore's public key
    - `publicKey()`:
        - given: the classpath development keystore
          when: publicKey() is called
          then: it returns the RSA public key matching the private key `mint()` signs with
- [x] RU07 · `ExpenseProposalToolUtils` · test: `ExpenseProposalToolUtilsTest` · covers: `toCommand()`,
  `toResponse()`
    - `toCommand()`:
        - given: a request carrying every argument and an AuthenticatedUserId
          when: toCommand() is called
          then: returns a command carrying that identity, the category name, the parent category name, the
          description, the merchant, and a Money built from the minor units and the currency code
        - given: a request whose parentCategory is null or blank
          when: toCommand() is called
          then: the command's parentCategoryName is `Optional.empty()`
        - given: a request whose merchant is null or blank
          when: toCommand() is called
          then: the command's merchant is `Optional.empty()`
        - given: a request whose currencyCode is not an ISO 4217 code
          when: toCommand() is called
          then: throws InvalidMoneyException
        - given: a request whose amountMinorUnits is absent
          when: toCommand() is called
          then: throws InvalidExpenseProposalException, so an absent amount is never read as zero
        - given: a request whose amountMinorUnits is zero
          when: toCommand() is called
          then: returns a command whose Money carries zero minor units
        - given: a request whose amountMinorUnits is negative
          when: toCommand() is called
          then: throws InvalidMoneyException
        - given: an absent request
          when: toCommand() is called
          then: throws InvalidExpenseProposalException
    - `toResponse()`:
        - given: a stored proposal carrying a merchant and the category name it was filed under
          when: toResponse() is called
          then: returns a response carrying the proposal's id, that category name, the description, the merchant,
          the minor units, the currency code and the created-at instant
        - given: a stored proposal with no merchant
          when: toResponse() is called
          then: the response's merchant is null
#### TDD Integration Red Phase

- [x] RI01 · `CategoryRepositoryAdapter` · test: `CategoryRepositoryAdapterTest` · covers:
  `findByUserIdAndName()`, `findChildNames()`
    - `findByUserIdAndName()`:
        - given: a stored user with one grouping and one child category under it, and the child's name
          when: findByUserIdAndName() is called
          then: returns exactly that child, carrying its id, its name and its grouping's name as `parentName`
        - given: a stored user with a grouping, and the grouping's name
          when: findByUserIdAndName() is called
          then: returns exactly that grouping, its `parentName` empty
        - given: a stored user with two categories of the same name under two different groupings
          when: findByUserIdAndName() is called
          then: returns both, each carrying its own grouping's name
        - given: two stored users each owning a category with the same name
          when: findByUserIdAndName() is called for one of them
          then: returns only that user's category
        - given: a stored user with no category of that name
          when: findByUserIdAndName() is called
          then: returns an empty list
        - given: an adapter over a mocked `CategoryEntityRepository` whose query raises a database failure
          when: findByUserIdAndName() is called
          then: throws PersistenceFailedException carrying the framework exception as its cause, so no framework
          type crosses the port
    - `findChildNames()`:
        - given: a stored grouping with three children
          when: findChildNames() is called
          then: returns the three names
        - given: a stored category with no children
          when: findChildNames() is called
          then: returns an empty list
        - given: an adapter over a mocked `CategoryEntityRepository` whose query raises a database failure
          when: findChildNames() is called
          then: throws PersistenceFailedException carrying the framework exception as its cause
- [x] RI02 · `JwksController` · test: `JwksControllerTest` · covers: `GET /.well-known/jwks.json` ·
  mocks: `AccessTokenMinter`
    - Happy Path:
        - given: a `@WebMvcTest` slice importing `SecurityConfiguration`, and a mocked minter reporting an RSA
          public key and a key id
          when: the endpoint is requested with no token
          then: 200 with a JWK Set whose single key carries `kty=RSA`, `alg=RS256`, `use=sig`, that key id, and the
          key's modulus and exponent — and no private material
- [x] RI03 · `CreateExpenseProposalMcpTool` · test: `CreateExpenseProposalMcpToolTest` · covers: `POST /mcp` ·
  mocks: `CreateExpenseProposalPort`
    - The class carries `@McpAdapterTest` (ST30) and posts JSON-RPC `tools/call` bodies built by `McpRequests`,
      authenticated with a token from `McpTokens` — the adapter is entered through its own protocol with its
      inbound port mocked, as `IntentExtractionGrpcServiceTest` enters the gRPC adapter
    - Happy Path:
        - given: a token minted for an external id, a valid arguments object, and a port returning a stored
          proposal
          when: `create_expense_proposal` is called
          then: the port receives a command carrying the token's subject as its identity — never a value taken from
          the arguments — and the result carries the stored proposal's id, category, description, merchant, minor
          units, currency code and created-at
    - Error Mapping:
        - given: a port that throws InvalidExpenseProposalException
          when: the tool is called
          then: a tool error result whose message names the field at fault and nothing about the store
        - given: a port that throws InvalidUserException
          when: the tool is called
          then: a tool error result naming an invalid request
        - given: arguments whose currency code is unusable, so mapping throws InvalidMoneyException
          when: the tool is called
          then: a tool error result naming an invalid request, and the port is untouched
        - given: a port that throws InvalidCategoryException
          when: the tool is called
          then: a tool error result carrying that exception's message, so the model can retry with a category the
          message names
        - given: a port that throws EntityNotFoundException
          when: the tool is called
          then: a tool error result saying the user is unknown
        - given: a port that throws PersistenceFailedException
          when: the tool is called
          then: a tool error result saying the proposal could not be stored, naming no table, constraint or stack
          frame
        - given: a port that throws a RuntimeException outside the failure table
          when: the tool is called
          then: a generic tool error result rather than an exception reaching the transport
        - given: a port that throws any of the above
          when: the tool is called
          then: a WARN line is logged carrying the failure kind and neither the arguments nor the token
    - Validation: `amountMinorUnits` — absent, and a value that cannot be bound at all (`"twelve"`): the first is a
      tool error naming an invalid request, the second the framework's own binding failure (D17), and neither
      reaches the port

#### TDD System Test Red Phase

- [x] RS01 · `CreateExpenseProposalMcpToolSystemTest` · covers: `POST /mcp`
    - The user and their category tree are seeded through the wired `UserRepository` with `Category.defaults()` —
      the only writer of the tree — not through a `bot.finance.common` row helper
    - Happy Path:
        - given: a stored user with the default category tree and a token minted for that user's external id
          when: `tools/call create_expense_proposal` is posted to `/mcp` with a child category's name, a
          description, a merchant, an amount and a currency code
          then: the response carries the stored proposal, and exactly one `expense_proposal` row exists for that
          user carrying that category's id, the description, the merchant, the minor units and the currency code
    - Unhappy Path:
        - given: a stored user with the default category tree and a token minted for them
          when: the same call is posted naming a grouping — `Groceries` — as the category
          then: the response is a tool error naming that grouping's children, and no `expense_proposal` row exists
          for that user
- [x] RS02 · `McpAuthenticationSystemTest` · covers: `POST /mcp`
    - Happy Path:
        - given: a stored user and a token minted for them
          when: `tools/list` is posted to `/mcp`
          then: 200, and the listed tools include `create_expense_proposal` with its six arguments and no identity
          argument, proving the tool is registered on the wire
    - Unhappy Path:
        - given: no token, an expired token, a token carrying a wrong audience, and a token whose `exp - iat`
          exceeds the configured TTL
          when: `tools/call create_expense_proposal` is posted to `/mcp` with each in turn
          then: 401 each time, with no tool result and no `expense_proposal` row written
        - given: the same running application and no token
          when: `GET /actuator/health` is requested
          then: 200 — the module's first filter chain leaves the actuator contract reachable

### Green Phase

#### TDD Unit Green Phase

- [x] GU01 · `AuthenticatedUserId` · test: `AuthenticatedUserIdTest`
- [x] GU02 · `StoredCategory` · test: `StoredCategoryTest`
- [x] GU03 · `CreateExpenseProposalCommand` · test: `CreateExpenseProposalCommandTest` · after: GU01
- [x] GU04 · `CreateExpenseProposalUseCase` · test: `CreateExpenseProposalUseCaseTest` · after: GU01, GU02, GU03
- [x] GU05 · `AuthenticatedCallerUtils` · test: `AuthenticatedCallerUtilsTest` · after: GU01
- [x] GU06 · `AccessTokenMinter` · test: `AccessTokenMinterTest`
- [x] GU07 · `ExpenseProposalToolUtils` · test: `ExpenseProposalToolUtilsTest` · after: GU01, GU03

#### TDD Integration Green Phase

- [x] GI01 · `CategoryRepositoryAdapter` · test: `CategoryRepositoryAdapterTest` · after: GU02
- [x] GI02 · `JwksController` · test: `JwksControllerTest`
- [x] GI03 · `CreateExpenseProposalMcpTool` · test: `CreateExpenseProposalMcpToolTest` · after: GU05, GU06, GU07,
  GI02

#### TDD System Test Green Phase

- [x] GS01 · `CreateExpenseProposalMcpToolSystemTest` · covers: `POST /mcp`
- [x] GS02 · `McpAuthenticationSystemTest` · covers: `POST /mcp`

### Post-Implementation Steps

#### ADRs

- [ ] P01 · Write ADR: the caller's identity reaches an MCP tool as a signed token on the transport and never as a
  tool argument, and the Ledger Service mints and validates that token itself with an RS256 key pair from a
  keystore (**Q1**)

## Open Questions / Blockers

- **Q1:** ADR candidate — *the caller's identity reaches an MCP tool as a signed token on the transport, never as a
  tool argument, and the Ledger Service mints and validates that token itself with an RS256 key pair from a
  keystore*. Record it as an ADR in `ledger-service/docs/adr/`? If not, the decision is carried only by design 8,
  which is archived with this plan, and by the shape of `CreateExpenseProposalToolRequest` — neither records the
  rejected alternative of a `userExternalId` tool argument.
- A: yes, it's an adr

- **Q2:** ST22 commits a development private key — `local-mcp-signing.p12`, password `changeit` — to the
  repository. Withdrawn: D23 already settles it ("the repository ships a local-development keystore for the
  default; a deployment supplies its own from its secret store").
- A: withdrawn — answered by D23, and ST22 follows it.
- **RI03 blocked:** GI03 also depends on GI02: until JwksController serves a real JWK Set, NimbusJwtDecoder resolves no signing key, so every token-bearing call to /mcp is rejected 401 before reaching the tool. RI03's tests are written and compile, but they currently fail at the transport rather than on the tool stub. Plan amended: GI03 now carries after: GU05, GU06, GU07, GI02.
- **RS02 blocked:** RS02 needed two additions the plan did not list: a tools/list body added to McpRequests (ST27 produced only initialize and tools/call), and a class-scoped @DynamicPropertySource pointing spring.grpc.client.channel.ai-connector.target at GrpcStubServer, because AbstractSystemTest leaves it unwired and AiConnectorHealthIndicator then reports DOWN, making /actuator/health answer 503. Both are scoped to this run; the health-indicator wiring gap is pre-existing and unrelated to this plan.
- **GI03 blocked:** SecurityConfiguration.jwtDecoder builds its JWKS URI from server.port, which stays 0 under @SpringBootTest(RANDOM_PORT) - only local.server.port carries the bound port. Every token-bearing call therefore 401s in McpAdapterTest and system tests. Defect in ST13's class, found by GI03; fixed under ST13's ownership by resolving local.server.port with server.port as the fallback.
- **GS01 blocked:** Refactor-phase findings, recorded not fixed: (1) CreateExpenseProposalUseCaseTest carries two tests for one scenario - whenUserExistsForExternalId_... and whenExactlyOneStoredCategoryMatchesNameWithParent_... stub identically and the second's assertions are a subset of the first's; deleting one is a test-count change the refactor phase may not make. (2) CategoryRepositoryAdapter.findByUserIdAndName issues one findById per matching row to resolve the parent name (N+1); correct and tested, but a shape the tests are silent on. (3) Pre-existing and unrelated: gradlew spotlessCheck fails at the pre-plan baseline on CRLF-vs-LF across 30+ files, so a .gitattributes / core.autocrlf decision is owed separately.

## Review Findings

- **F1:** RU08/GU08 placed the MCP tool — an inbound adapter whose whole content is protocol-level error mapping —
  at the unit layer, on a false reading of the `TelegramUpdateListener` precedent.
- Resolution: decision
- Action: resolved against the repository — `ai-connector-service` tests its inbound gRPC adapter through its own
  protocol under the composed annotation `GrpcAdapterTest` with `@MockitoBean` on the inbound port
  (`IntentExtractionGrpcServiceTest`), which is the same shape this adapter needs. RU08/GU08 became RI03/GI03,
  entered by posting JSON-RPC to `/mcp` with `CreateExpenseProposalPort` mocked; ST30 adds the `McpAdapterTest`
  annotation that boots it, and the preamble is gone.

- **F2:** RU06/GU06 placed `AccessTokenMinter` at the unit layer without saying how a keystore-loading adapter
  class satisfies `testing.md`'s unit mapping.
- Resolution: decision
- Action: resolved — kept at the unit layer, and RU06 now states the test constructs the minter itself with a
  plain `DefaultResourceLoader` and properties pointing at the classpath keystore: no Spring context and no
  infrastructure, which is what the mapping asks for.

- **F3:** GI02's `after: GU06` named a mocked collaborator.
- Resolution: mechanical
- Action: applied — dropped.

- **F4:** RI02's second scenario tested `SecurityConfiguration` through an endpoint outside its slice, duplicating
  RS02.
- Resolution: mechanical
- Action: applied — dropped, and the remaining happy path now states that the slice imports
  `SecurityConfiguration`.

- **F5:** D24's over-TTL rejection, `/actuator/**` staying permitted, and deny-by-default had no coverage.
- Resolution: mechanical
- Action: applied — ST28 also produces an over-TTL token, and RS02's Unhappy Path covers it and asserts
  `GET /actuator/health` still answers 200 with no token.

- **F6:** RU07's `toCommand()` matrix omitted a negative `amountMinorUnits`, which `Money` rejects.
- Resolution: mechanical
- Action: applied — added the scenario.

- **F7:** ST25 changed `CleanArchitectureTest` with nothing updating `architecture.md`'s **Architecture
  Enforcement** list.
- Resolution: mechanical
- Action: applied — ST20 now updates that list as well as the package tree.

- **F8:** ST19 and the design disagreed on the tool method's return type.
- Resolution: decision
- Action: resolved against the design — the **Failures** table and D5 require a tool error result, which a plain
  record cannot express, so ST19 now returns the framework's tool-result type carrying
  `CreateExpenseProposalToolResponse` as its success payload. Both design statements hold.

- **F9:** RS01's "default category tree" fixture exists in no `bot.finance.common` helper.
- Resolution: mechanical
- Action: applied — RS01 states the tree is seeded through the wired `UserRepository` with `Category.defaults()`.

- **F10:** Q2 re-opened D23, which already settles the committed development keystore.
- Resolution: decision
- Action: resolved — Q2 withdrawn against D23; ST22 follows it.
