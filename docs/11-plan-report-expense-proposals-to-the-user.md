# Plan: Identify the User by Telegram User Id and Report the Proposals Back

**Affected Modules:** `ledger-service`
**Design:** [Identify the User by Telegram User Id and Report the Proposals Back](11-design-report-expense-proposals-to-the-user.md)

## Step-by-Step Implementation Map (To-Do List)

### Stabilization

#### Database

- [x] ST01 · Add `ledger-service/src/main/resources/db/migration/V004__add_expense_proposal_message_reference.sql`
  exactly as the design's **Migration** section states: add the nullable `message_reference UUID` column, backfill
  every existing row with `gen_random_uuid()`, `SET NOT NULL`, and create
  `idx_expense_proposal_message_reference` on `(user_id, message_reference)`. Never edit `V001`–`V003`.

#### Interface-First / Build Stabilization

**Interface & Signature Sync**

- [x] ST02 · Add `domain/value/MessageReference` — a record wrapping a `UUID`, rejecting an absent value in its
  compact constructor with `InvalidIncomingMessageException`, plus `newReference()` and `of(String)`. Stub the two
  factories with an inline comment stating the intent (`newReference` mints a fresh random UUID; `of` parses the
  string and throws `InvalidIncomingMessageException` when it is absent, blank or unparseable).
- [x] ST03 · `domain/model/ExpenseProposal` — add a `MessageReference messageReference` field, a `messageReference()`
  accessor, and the parameter to the private constructor and to both `newExpenseProposal(...)` and `stored(...)`.
  Keep every existing assertion; add a `TODO` at the insertion point for the presence check the RED step covers.
  Update every call site (`CreateExpenseProposalUseCase`, `ExpenseProposalEntity.toDomain`, and each test that
  constructs one) to compile.
- [x] ST04 · `application/dto/HandleIncomingMessageCommand` — become
  `(String userExternalId, String conversationId, String inboundMessageId, String text)`; keep the existing
  blank checks for `conversationId` and `text` and add a `TODO` for the two new ones.
- [x] ST05 · `application/dto/IntentExtractionRequest` and `application/dto/CreateExpenseProposalCommand` — add a
  `MessageReference messageReference` component to each, with a `TODO` at the insertion point for its presence
  check. Update every call site, production and test, to pass a reference — `IntentProtoUtilsTest` constructs
  `IntentExtractionRequest` at lines 25, 48 and 63 and no other step names it.
- [x] ST06 · Add `application/dto/ProposalSummary`
  `(String categoryName, String parentCategoryName, String description, Optional<String> merchant, Money money)`,
  `application/dto/ReportOutcome` (`RECORDED`, `NOTHING_IDENTIFIED`, `PARTIAL`, `FAILED`), and
  `application/dto/ProposalReport`
  `(String conversationId, String inboundMessageId, ReportOutcome outcome, List<ProposalSummary> proposals)`.
  Plain read models — no validation beyond copying the list defensively.
- [x] ST07 · Add `domain/exception/MessageDeliveryFailedException` and `application/port/MessageDeliveryPort` with
  `void deliver(ProposalReport report)`, documenting that exception as `@throws` — and
  `InvalidIncomingMessageException` for an absent report (F9).
- [x] ST08 · `application/port/ExpenseProposalRepository` — add
  `List<ProposalSummary> findSummariesByMessageReference(long userId, MessageReference reference)`, documenting
  `PersistenceFailedException`. Add `adapter/persistence/ProposalSummaryProjection`
  `(String categoryName, String parentName, String description, String merchant, long amountMinorUnits,
  String currencyCode)`; add the `@Query` from the design's **Adapters** section to
  `ExpenseProposalEntityRepository` returning it; add `messageReference` to `ExpenseProposalEntity` and to its
  `toDomain`/`fromDomain`; stub the adapter method with an inline comment (map each projection row onto a
  `ProposalSummary`, translating a runtime failure into `PersistenceFailedException`, as `findKnownCategories`
  does in `CategoryRepositoryAdapter`).
- [x] ST09 · `adapter/security/AccessTokenMinter` — change `mint(String userExternalId)` to
  `mint(String userExternalId, MessageReference reference)`, keeping every existing claim and adding a `TODO` for
  the `mrf` claim. `adapter/security/AuthenticatedCallerUtils` — add a stubbed `messageReference()` with an inline
  comment (read `mrf` off the validated token and throw `InvalidIncomingMessageException` when it is absent or
  unparseable).
- [x] ST10 · Sync the remaining call sites so the module compiles: `adapter/telegram/TelegramUpdateUtils` (build
  the four-component command, `TODO` for the `from` handling); `adapter/mcp/ExpenseProposalToolUtils` and
  `CreateExpenseProposalMcpTool` (read the reference through `AuthenticatedCallerUtils` and pass it to
  `toCommand`); `adapter/aiconnector/AiConnectorIntentExtractionAdapter` (mint with the request's reference);
  `application/usecase/CreateExpenseProposalUseCase` (pass the command's reference into the entity);
  `application/usecase/HandleIncomingMessageUseCase` (take `MessageDeliveryPort` and
  `ExpenseProposalRepository` as constructor parameters, `TODO` for the new flow); and
  `adapter/config/UseCaseConfiguration` (wire both new dependencies). `IntentProtoUtils` and
  `proto/intent_extraction.proto` are unchanged — the reference travels in the token.
- [x] ST11 · Add `adapter/telegram/ProposalReportUtils` (a static `render(ProposalReport)` stub with an inline
  comment naming the four outcome wordings and the 4000-character cut) and
  `adapter/telegram/TelegramMessageDeliveryAdapter` — a `@Component` implementing `MessageDeliveryPort`, taking the
  existing `TelegramBot` bean and `LoggerFactory`, with a stubbed `deliver` body carrying an inline comment (send a
  `SendMessage(String conversationId, String renderedText)` whose reply target is set through
  `replyParameters(new ReplyParameters(Integer.valueOf(report.inboundMessageId())).allowSendingWithoutReply(true))`
  — pengrad 10.1.0 has no `replyToMessageId` and sends this as a `reply_parameters` form param carrying JSON, and
  `allowSendingWithoutReply` is what makes the Bot API send unthreaded rather than fail when the reply target is
  gone, in the same request — and translate a non-OK response or a client exception into
  `MessageDeliveryFailedException`).

**Shared Test Infrastructure**

- [x] ST12 · `bot.finance.common.McpTokens` — `tokenFor(...)` takes a `MessageReference` and mints through the
  application's minter with it; the hand-signed `expiredToken`/`wrongAudienceToken`/`overTtlToken` builders carry
  an `mrf` claim too, so a token that is meant to fail validation fails on validation and not on a missing claim.
  Add a `malformedReferenceToken(...)` and a `noReferenceToken(...)` for the RED steps that need them. Update every
  existing caller — `McpAuthenticationSystemTest:87`, `CreateExpenseProposalMcpToolSystemTest:92` and `:138`.
- [x] ST13 · `bot.finance.common.TelegramFixtures` — add a `from` object to `textMessageUpdate(...)`, taking the
  user id as a new parameter; add `textMessageUpdateWithoutFrom(int updateId, long chatId, String text)`; expose
  `MESSAGE_ID` publicly so a test can assert the reply target; add `sendMessageResponse()` and
  `sendMessageError(int errorCode, String description)` envelopes. Update every existing caller.
- [x] ST14 · `bot.finance.common.WireMockStubs` — add `telegramAcceptsSendMessage(String token)` and
  `telegramFailsSendMessage(String token, int errorCode, String description)`.
  `bot.finance.common.TelegramTestBot` — add `recordedSendMessages(String token)` reading back the recorded
  `/bot<token>/sendMessage` posts, and a `DELIVERY_TOKEN` constant owned by `TelegramMessageDeliveryAdapterTest`.
- [x] ST15 · `bot.finance.common.containers.GrpcStubServer` — add a mode in which `extractIntents` calls back into
  the ledger's own `/mcp` endpoint with a `tools/call create_expense_proposal` body, forwarding the received
  `authorization` header verbatim, before answering. This is the only way a system test can reach the `RECORDED`
  outcome, since the reference is minted inside the use case and no test can seed a row under it. The callback
  needs the booted application's random port, so the mode is armed per test through a static setter that takes the
  base URL and the tool arguments. Ship it with a test that boots it, per the module's testing conventions.
- [x] ST16 · After stabilization, confirm `bot.finance.architecture.CleanArchitectureTest` still passes — in
  particular `coreTypesCarryNoExternalSystemName` against the new `domain`/`application` types
  (`MessageReference`, `ProposalSummary`, `ProposalReport`, `ReportOutcome`, `MessageDeliveryPort`) and the ban on
  `com.pengrad..` outside the adapter layer.

### Red Phase

#### TDD Unit Red Phase

- [x] RU01 · `MessageReference` · test: `MessageReferenceTest` · covers: `newReference()`, `of(String)`, `value()`
  - `newReference()`:
    - given: nothing
      when: `newReference()` is called twice
      then: both carry a non-null UUID and the two are not equal
  - `of(String)`:
    - given: the canonical text of a UUID
      when: `of` is called with it
      then: the returned reference's value equals that UUID, and `of(reference.value().toString())` equals the
      original reference
    - given: null, an empty string, a blank string, and a string that is not a UUID
      when: `of` is called with each
      then: `InvalidIncomingMessageException` is thrown
  - `value()`:
    - given: a reference built from a known UUID
      when: `value()` is read
      then: it returns that UUID
    - given: a null UUID
      when: the canonical constructor is called with it
      then: `InvalidIncomingMessageException` is thrown

- [x] RU02 · `ExpenseProposal` · test: `ExpenseProposalTest` · covers: `newExpenseProposal(...)`, `stored(...)`,
  `messageReference()`
  - `newExpenseProposal(...)`:
    - given: every field valid and a message reference
      when: the factory is called
      then: `messageReference()` returns that reference
    - given: a null message reference and every other field valid
      when: the factory is called
      then: `InvalidExpenseProposalException` is thrown
    - update: `whenAllFieldsAreGiven_thenReturnsProposalCarryingThemWithNoDatabaseIdAndBothTimestampsEqualToInstant()`
      — pass a reference and assert `messageReference()` alongside the other fields
  - `stored(...)`:
    - given: a database id, a message reference and every other field valid
      when: the factory is called
      then: `messageReference()` returns that reference unchanged
    - given: a database id, a null message reference and every other field valid
      when: the factory is called
      then: `InvalidExpenseProposalException` is thrown
    - update: `whenDatabaseIdAndEveryOtherFieldAreGiven_thenReturnsProposalCarryingAllWithTimestampsUnchanged()` —
      pass a reference and assert `messageReference()` alongside the other fields

- [x] RU03 · `HandleIncomingMessageCommand` · test: `HandleIncomingMessageCommandTest` · covers: the compact
  constructor
  - compact constructor:
    - given: a non-blank user external id, conversation id, inbound message id and text
      when: the record is constructed
      then: all four components read back unchanged
    - update: `whenConversationIdOrTextIsNullOrBlank_thenThrowsInvalidIncomingMessageException()` — widen the
      `invalidComponents` matrix to all four components, each null, empty and blank, with the other three valid,
      and rename it for the four-component shape
    - update: `whenConversationIdAndTextAreNonBlank_thenBothComponentsAreReadableUnchanged()` — drop it, replaced
      by the four-component scenario above

- [x] RU04 · `HandleIncomingMessageUseCase` · test: `HandleIncomingMessageUseCaseTest` · covers: `handle(command)`
  - `handle(command)`:
    - given: a command, a stored user, known categories, an extraction that returns normally, and a repository
      returning two summaries under the minted reference
      when: `handle` is called
      then: `initialize` receives an `InitializeUserCommand` carrying `command.userExternalId()` (not the
      conversation id); the `IntentExtractionRequest` carries a non-null message reference; and
      `findSummariesByMessageReference` is called with the user's id and that same reference
    - given: the same, with the extraction returning normally and summaries present
      when: `handle` is called
      then: `deliver` receives a `ProposalReport` whose outcome is `RECORDED`, whose `conversationId` and
      `inboundMessageId` come from the command, and whose proposals are those summaries in order
    - given: the extraction returns normally and the repository returns an empty list
      when: `handle` is called
      then: `deliver` receives a report whose outcome is `NOTHING_IDENTIFIED` and whose proposals are empty
    - given: the extraction throws `IntentExtractionFailedException` and the repository returns two summaries
      when: `handle` is called
      then: no exception escapes, and `deliver` receives a report whose outcome is `PARTIAL` carrying those
      summaries
    - given: the extraction throws `IntentExtractionFailedException` and the repository returns an empty list
      when: `handle` is called
      then: no exception escapes, and `deliver` receives a report whose outcome is `FAILED`
    - given: the extraction throws `IntentExtractionFailedException`
      when: `handle` is called
      then: an error line is logged carrying the message reference and the outcome, and not the message text
    - given: a successful turn
      when: `handle` is called
      then: an info line is logged carrying the message reference and the user's external id, and not the message
      text
    - given: `findSummariesByMessageReference` throws `PersistenceFailedException`
      when: `handle` is called
      then: that exception propagates and `deliver` is never called
    - given: `deliver` throws `MessageDeliveryFailedException`
      when: `handle` is called
      then: that exception propagates
    - given: the extraction throws `InvalidExtractionRequestException`
      when: `handle` is called
      then: that exception propagates past the catch and `deliver` is never called
    - update: `whenCommandIsNull_thenThrowsInvalidIncomingMessageExceptionAndLogsNothing()` — add the repository
      and the delivery port to the `verifyNoInteractions` set
    - update: `whenHandleIsCalled_thenPortsAreCalledInOrderWithExpectedArguments()` — drop it, replaced by the
      first scenario above
    - update: `whenHandleCompletes_thenInfoLineNamesConversationIdAndOmitsText()` — drop it, replaced by the
      info-line scenario above
    - update: `whenExtractThrowsIntentExtractionFailedException_thenExceptionPropagatesAndNoHandledLineLogged()` —
      drop it: the exception no longer propagates, and the `PARTIAL`/`FAILED` scenarios above replace it
    - update: `whenInitializeThrowsPersistenceFailedException_thenExceptionPropagatesAndRemainingPortsUntouched()`
      and `whenFindKnownCategoriesThrowsPersistenceFailedException_thenExceptionPropagatesAndExtractionPortUntouched()`
      — add the delivery port to the untouched-port assertions

- [x] RU05 · `TelegramUpdateUtils` · test: `TelegramUpdateUtilsTest` · covers:
  `toHandleIncomingMessageCommand(Update)`
  - `toHandleIncomingMessageCommand(Update)`:
    - given: an update whose message carries a `from` id, a chat id, a message id and non-blank text
      when: the mapper is called
      then: it returns a command whose `userExternalId` is the `from` id as a string, whose `conversationId` is the
      chat id as a string, whose `inboundMessageId` is the message id as a string, and whose text is that text
    - given: an update whose message carries text and a chat but no `from`
      when: the mapper is called
      then: it returns an empty `Optional`
    - update: `whenUpdateCarriesChatIdAndNonBlankText_thenReturnsCommandWithChatIdAsConversationIdAndThatText()` —
      drop it, replaced by the four-component scenario above
    - update: `skippableUpdates()` — add the no-`from` case to the matrix and adapt every existing argument to
      `TelegramFixtures`' new signatures

- [x] RU06 · `ProposalReportUtils` · test: `ProposalReportUtilsTest` · covers: `render(ProposalReport)`
  - `render(ProposalReport)`:
    - given: a `RECORDED` report carrying two summaries, one with a merchant and one without
      when: `render` is called
      then: the text opens `Noted 2 expenses, pending your confirmation:` and carries one bullet per summary in the
      list's order, each as `• <category> (<parent>) — <description>[, <merchant>]: <amount> <currency>` with the
      amount rendered through `Money.amount()`
    - given: a `RECORDED` report carrying exactly one summary
      when: `render` is called
      then: the text opens `Noted 1 expense, pending your confirmation:` — singular
    - given: a `NOTHING_IDENTIFIED` report with no summaries
      when: `render` is called
      then: the text is `No expense was identified in that message.`
    - given: a `FAILED` report with no summaries
      when: `render` is called
      then: the text is `Something went wrong and nothing was noted — please try again.`
    - given: a `PARTIAL` report carrying one summary
      when: `render` is called
      then: the text opens `Something went wrong, so this may be incomplete. What I could read:` and carries that
      summary's bullet below it
    - given: a `RECORDED` report carrying enough summaries that the bullets would exceed 4000 characters
      when: `render` is called
      then: the text is at most 4000 characters, carries only the bullets that fit, and ends with `… and N more.`
      naming the number omitted
    - given: a summary whose description contains `*` and `_`
      when: `render` is called
      then: those characters appear literally, with no escaping applied

- [x] RU07 · `ExpenseProposalToolUtils` · test: `ExpenseProposalToolUtilsTest` · covers:
  `toCommand(request, userId, reference)`
  - `toCommand(request, userId, reference)`:
    - given: a valid request, an identity and a message reference
      when: `toCommand` is called
      then: the returned command carries that reference
    - update: `whenRequestCarriesEveryArgumentAndAnIdentity_thenReturnsCommandCarryingThatIdentityAndFields()` —
      pass a reference and include it in the expected `CreateExpenseProposalCommand`
    - update: every other scenario in `ToCommand` — pass a reference so the calls compile against the new
      signature; assertions are otherwise unchanged

- [x] RU08 · `IntentExtractionRequest` · test: `IntentExtractionRequestTest` · covers: the compact constructor
  - compact constructor:
    - given: a valid text, categories, currency and external id, and a message reference
      when: the record is constructed
      then: `messageReference()` reads back unchanged
    - given: a null message reference and every other component valid
      when: the record is constructed
      then: `InvalidExtractionRequestException` is thrown
    - update: every existing scenario — pass a reference so the constructions compile; assertions are otherwise
      unchanged

- [x] RU09 · `CreateExpenseProposalCommand` · test: `CreateExpenseProposalCommandTest` · covers: the compact
  constructor
  - compact constructor:
    - given: every component valid and a message reference
      when: the record is constructed
      then: `messageReference()` reads back unchanged
    - given: a null message reference and every other component valid
      when: the record is constructed
      then: `InvalidExpenseProposalException` is thrown
    - update: every existing scenario — pass a reference so the constructions compile; assertions are otherwise
      unchanged

- [x] RU10 · `CreateExpenseProposalUseCase` · test: `CreateExpenseProposalUseCaseTest` · covers: `create(command)`
  - `create(command)`:
    - given: a stored user, a stored child category, and a command carrying a message reference
      when: `create` is called
      then: the proposal handed to `ExpenseProposalRepository.create` carries that same reference
    - update: `newExpenseProposal(...)` (the test's own command builder) — give it a message reference so every
      scenario compiles
    - update: the fixed-clock scenario at `CreateExpenseProposalUseCaseTest:85` — assert the created proposal's
      `messageReference()` alongside the fields it already asserts

- [x] RU11 · `AuthenticatedCallerUtils` · test: `AuthenticatedCallerUtilsTest` · covers: `messageReference()`
  - `messageReference()`:
    - given: the security context holds a validated token whose `mrf` claim is a UUID's canonical text
      when: `messageReference()` is called
      then: it returns a `MessageReference` carrying that UUID
    - given: the security context holds a validated token with no `mrf` claim
      when: `messageReference()` is called
      then: `InvalidIncomingMessageException` is thrown
    - given: the security context holds a validated token whose `mrf` claim is not a UUID
      when: `messageReference()` is called
      then: `InvalidIncomingMessageException` is thrown
    - given: the security context holds no authentication, or one that is not a validated token
      when: `messageReference()` is called
      then: `InvalidUserException` is thrown, as `authenticatedUserId()` does for the same context

- [x] RU12 · `AccessTokenMinter` · test: `AccessTokenMinterTest` · covers: `mint(String, MessageReference)`
  - `mint(String, MessageReference)`:
    - given: an external id and a message reference
      when: the minted token is parsed
      then: its `mrf` claim is that reference's UUID in canonical text form
    - given: the same external id and two different references
      when: both tokens are parsed
      then: their `mrf` claims differ
    - update: `whenMintIsCalledAndTheTokenIsParsed_thenItCarriesTheExpectedClaims()`,
      `whenMintIsCalledTwiceForTheSameExternalId_thenTheTwoTokensCarryDifferentJtiValues()` and
      `whenMintIsCalledAndTheTokenHeaderIsRead_thenTheAlgorithmIsRs256AndTheSignatureVerifies()` — pass a reference
      so the calls compile; assertions are otherwise unchanged

#### TDD Integration Red Phase

- [x] RI01 · `ExpenseProposalRepositoryAdapter` · test: `ExpenseProposalRepositoryAdapterTest` · covers:
  `findSummariesByMessageReference(long, MessageReference)`, `create(ExpenseProposal)`
  - `findSummariesByMessageReference(long, MessageReference)`:
    - given: a stored user, a child category under a stored parent, and three proposals written under the same
      reference at increasing `created_at`
      when: the method is called with that user id and reference
      then: it returns three summaries oldest first, each carrying the category name, the parent's name, the
      description, the merchant and a `Money` built from the row's minor units and currency code
    - given: a stored user with proposals under two different references
      when: the method is called with one of them
      then: only that reference's proposals come back
    - given: two stored users whose proposals share a reference value
      when: the method is called with one user's id and that reference
      then: only that user's proposals come back
    - given: a stored user and a reference nothing was written under
      when: the method is called
      then: it returns an empty list
    - given: a stored proposal whose merchant column is null
      when: the method is called
      then: that summary's merchant is `Optional.empty()`
    - given: a mocked `ExpenseProposalEntityRepository` whose query throws a `QueryTimeoutException` (the
      `WithAMockedStore` nested class already sets this shape up)
      when: the method is called
      then: `PersistenceFailedException` is thrown carrying the framework exception as its cause
  - `create(ExpenseProposal)`:
    - given: a stored user, a stored category and a proposal carrying a message reference
      when: `create` is called
      then: the written row's `message_reference` column equals that reference's UUID
    - update: every existing `Create` scenario and both `WithAMockedStore` scenarios — build the proposal with a
      message reference so the calls compile; assertions are otherwise unchanged

- [x] RI02 · `TelegramMessageDeliveryAdapter` · test: `TelegramMessageDeliveryAdapterTest` · covers:
  `deliver(ProposalReport)`
  - `deliver(ProposalReport)`:
    - given: WireMock accepts `sendMessage` for `DELIVERY_TOKEN`, and a `RECORDED` report with two summaries
      when: `deliver` is called on an adapter wrapping `TelegramTestBot.forToken(DELIVERY_TOKEN)`
      then: exactly one `sendMessage` is recorded, whose `chat_id` form param is the report's `conversationId`,
      whose `reply_parameters` form param carries its `inboundMessageId` as the JSON `message_id` and
      `allow_sending_without_reply` as `true` — so a deleted reply target is sent unthreaded by the Bot API rather
      than retried here — whose `text` is what `ProposalReportUtils.render` returns for that report, and which sets
      no `parse_mode`
    - given: WireMock answers `sendMessage` with a non-OK envelope
      when: `deliver` is called
      then: `MessageDeliveryFailedException` is thrown
    - given: the bot is pointed at an address that refuses the connection
      when: `deliver` is called
      then: `MessageDeliveryFailedException` is thrown carrying the client exception as its cause
    - given: a null report
      when: `deliver` is called
      then: `InvalidIncomingMessageException` is thrown and nothing is sent

- [x] RI03 · `AiConnectorIntentExtractionAdapter` · test: `AiConnectorIntentExtractionAdapterTest` · covers:
  `extract(IntentExtractionRequest)`
  - `extract(IntentExtractionRequest)`:
    - given: the stub server answers an empty response and a request carrying a known message reference
      when: `extract` is called
      then: the bearer token in the call's metadata carries an `mrf` claim equal to that reference's UUID text,
      and the `ExtractIntentsRequest` the server received carries no field for it
    - update: `whenStubServerAnswersEmptyResponse_thenReturnsAndServerReceivedRequestFields()`,
      `whenExtractIsCalled_thenMetadataCarriesBearerTokenWithSubClaimAsUserExternalId()` and
      `whenStubServerFailsCall_thenThrowsIntentExtractionFailedExceptionCarryingStatusRuntimeExceptionAsCauseAndNamingStatus()`
      — build the request with a message reference so the calls compile; assertions are otherwise unchanged

- [x] RI04 · `CreateExpenseProposalMcpTool` · test: `CreateExpenseProposalMcpToolTest` · covers:
  `tools/call create_expense_proposal` via `POST /mcp` · mocks: `CreateExpenseProposalPort`
  - Happy Path:
    - given: the port returns a stored proposal, and the caller token carries a known `mrf` claim
      when: the tool is called
      then: the command the port receives carries a `MessageReference` equal to that claim, alongside the token's
      subject as its identity
  - Error Mapping:
    - given: the caller token carries no `mrf` claim
      when: the tool is called
      then: the result is a tool error and the port is never called
    - given: the caller token's `mrf` claim is not a UUID
      when: the tool is called
      then: the result is a tool error and the port is never called
  - Validation: unchanged — the tool's parameters do not change, so the existing matrix stands
  - update: `token(String)` (the test's own helper) and every scenario in `HappyPath`, `ErrorMapping` and
    `Validation` — mint through `McpTokens` with a message reference so the calls compile and the tool finds a
    claim; assertions are otherwise unchanged

- [x] RI05 · `TelegramUpdateListener` · test: `TelegramUpdateListenerTest` · covers: the pengrad `getUpdates` poll
  loop · mocks: `HandleIncomingMessagePort`
  - Happy Path:
    - given: the stub server serves one text-message update carrying a `from` id, a chat id and a message id
      when: the loop polls it
      then: the port receives a command whose `userExternalId` is the `from` id, whose `conversationId` is the chat
      id, whose `inboundMessageId` is the message id, and whose text is the message text, and the batch is
      confirmed
  - Error Mapping: unchanged — the existing scenario already proves a rejected update still confirms the batch
  - Validation:
    - given: the stub server serves an update whose message carries text and a chat but no `from`
      when: the loop polls it
      then: the port is never called and the batch is still confirmed
  - update: `whenTextMessageUpdateIsPolled_thenPortHandlesMappedCommandAndBatchIsConfirmed()` and
    `whenBatchMixingTextAndVoiceUpdateIsPolled_thenOnlyTextUpdateIsHandledAndWholeBatchIsConfirmed()` — assert the
    four components rather than two, and adapt to `TelegramFixtures`' new signatures

#### TDD System Test Red Phase

- [x] RS01 · `ReceiveTelegramMessageSystemTest` · covers: `HandleIncomingMessagePort.handle()`
  - Happy Path:
    - given: `WireMockStubs.telegramAcceptsSendMessage(TOKEN)` is registered alongside the `getUpdates` catch-all in
      the same `@BeforeEach`, the stub Telegram server serves one text-message update whose `from` id and chat id
      differ, and the gRPC stub connector is armed to record one expense proposal back through `/mcp` with the
      token it receives
      when: the running poll loop picks the update up
      then: a user is stored under the `from` id — not the chat id; one `expense_proposal` row exists whose
      `message_reference` is non-null and equals the `mrf` claim parsed off the bearer token in
      `GrpcStubServer.lastExtractionMetadata()` — the same token the existing test already parses for its `sub`
      assertion; and one `sendMessage` is recorded whose `chat_id` is the chat id, whose `reply_parameters` carries
      the update's message id, and whose text opens `Noted 1 expense, pending your confirmation:` and names that
      proposal's category and amount
  - update: `whenRunningPollLoopPicksUpTextMessageUpdate_thenBatchIsConfirmedAndMessageIsPrinted()` — the stored
    user's external id is now the `from` id rather than `CONVERSATION_ID`, the bearer token's `sub` follows it, and
    the delivered report replaces the log-line assertion as the observable outcome

- [x] RS02 · `HandleIncomingMessageFailureSystemTest` · covers: `HandleIncomingMessagePort.handle()`
  - Unhappy Path:
    - given: `WireMockStubs.telegramAcceptsSendMessage(HANDLE_MESSAGE_FAILURE_TOKEN)` is registered alongside the
      `getUpdates` catch-all in the same `@BeforeEach`, the stub Telegram server serves one text-message update, and
      the gRPC stub connector fails the extraction with `UNAVAILABLE`
      when: the running poll loop picks the update up
      then: one `sendMessage` is recorded whose text is
      `Something went wrong and nothing was noted — please try again.`, sent to the chat id with the update's
      message id in `reply_parameters`, no `expense_proposal` row exists for that user, and the batch is still
      confirmed
  - update: `whenLoopPicksUpdateUp_thenFailureIsLoggedAndBatchIsStillConfirmed()` — the listener no longer sees the
    failure, since the use case catches it and reports `FAILED`; replace the `TelegramUpdateListener` log-capture
    assertion with the delivered-report assertion above

### Green Phase

#### TDD Unit Green Phase

- [x] GU01 · `MessageReference` · test: `MessageReferenceTest`
- [x] GU02 · `ExpenseProposal` · test: `ExpenseProposalTest` · after: GU01
- [x] GU03 · `HandleIncomingMessageCommand` · test: `HandleIncomingMessageCommandTest`
- [x] GU08 · `IntentExtractionRequest` · test: `IntentExtractionRequestTest` · after: GU01
- [x] GU09 · `CreateExpenseProposalCommand` · test: `CreateExpenseProposalCommandTest` · after: GU01
- [x] GU04 · `HandleIncomingMessageUseCase` · test: `HandleIncomingMessageUseCaseTest` · after: GU01, GU03, GU08
- [x] GU05 · `TelegramUpdateUtils` · test: `TelegramUpdateUtilsTest` · after: GU03
- [x] GU06 · `ProposalReportUtils` · test: `ProposalReportUtilsTest`
- [x] GU07 · `ExpenseProposalToolUtils` · test: `ExpenseProposalToolUtilsTest` · after: GU01, GU02, GU09
- [x] GU10 · `CreateExpenseProposalUseCase` · test: `CreateExpenseProposalUseCaseTest` · after: GU02, GU09
- [x] GU11 · `AuthenticatedCallerUtils` · test: `AuthenticatedCallerUtilsTest` · after: GU01
- [x] GU12 · `AccessTokenMinter` · test: `AccessTokenMinterTest` · after: GU01

#### TDD Integration Green Phase

- [x] GI01 · `ExpenseProposalRepositoryAdapter` · test: `ExpenseProposalRepositoryAdapterTest` · after: GU01, GU02
- [x] GI02 · `TelegramMessageDeliveryAdapter` · test: `TelegramMessageDeliveryAdapterTest` · after: GU06
- [x] GI03 · `AiConnectorIntentExtractionAdapter` · test: `AiConnectorIntentExtractionAdapterTest` · after: GU08,
  GU12
- [x] GI04 · `CreateExpenseProposalMcpTool` · test: `CreateExpenseProposalMcpToolTest` · after: GU07, GU11, GU12
- [x] GI05 · `TelegramUpdateListener` · test: `TelegramUpdateListenerTest` · after: GU05

#### TDD System Test Green Phase

- [x] GS01 · `ReceiveTelegramMessageSystemTest` · covers: `HandleIncomingMessagePort.handle()`
- [x] GS02 · `HandleIncomingMessageFailureSystemTest` · covers: `HandleIncomingMessagePort.handle()`

### Post-Implementation Steps

- [ ] P01 · Write ADR: a per-message correlation reference reaches the MCP tool as the `mrf` claim on the caller
  token the ledger already mints, rather than as a field on `ExtractIntentsRequest`, so the connector forwards it
  verbatim without reading it and `intent_extraction.proto` is untouched.

## Open Questions / Blockers

- **Q1:** Should an ADR record that a per-message correlation reference reaches the MCP tool as a claim on the
  caller token the ledger already mints, rather than as a field on `ExtractIntentsRequest` — so the connector
  neither reads nor forwards it and the proto contract is untouched (D4)? If no ADR is written, the fact lives in
  this design file and in `AccessTokenMinter`/`AuthenticatedCallerUtils` alone, and the next change to the
  extraction contract has nothing to consult.
- A: Yes, write the ADR. It is item P01.

- **Q2:** ST15 extends `GrpcStubServer` so the stub connector can call back into the ledger's own `/mcp` endpoint,
  because the `RECORDED` outcome is otherwise unreachable end-to-end: the reference is minted inside the use case,
  so no test can seed a proposal row under it beforehand. The alternative is to leave the stub connector as it is
  and have RS01 assert the `NOTHING_IDENTIFIED` report instead, leaving the recorded-proposals path proven only by
  RU04 and RI01. Which should RS01 prove?
- A: `RECORDED`, via the `/mcp` callback. ST15 stays as written.

- **B1 (baseline, 2026-08-02):** The Stage 0 baseline was red on three tests.
  `CreateExpenseProposalMcpToolTest.whenPortThrowsAnyFailure_thenWarnLineLogsFailureKindWithoutArgumentsOrToken()`
  failed on the tool's own `Received create_expense_proposal call: {}` debug line, which logs the request record;
  the user chose to loosen the assertion rather than the log line, and it was fixed and committed before Stage 1.
  The other two — `HandleIncomingMessageUseCaseTest.whenHandleCompletes_thenInfoLineNamesConversationIdAndOmitsText()`
  and `…whenExtractThrowsIntentExtractionFailedException_thenExceptionPropagatesAndNoHandledLineLogged()` — assert
  an `info` line the use case logs at `debug`; RU04 deletes both, so they are expected red until GU04 and are
  subtracted by hand from the Stage 1 and Stage 2 guardrails.

- **B3 (Stage 1, 2026-08-03):** ST02, ST09 and part of ST10 asked for stubs, but a throwing stub for
  `MessageReference.newReference()`/`of(String)`, the `mrf` claim in `AccessTokenMinter`, or
  `AuthenticatedCallerUtils.messageReference()` would have broken dozens of already-passing tests once the
  mandatory signature changes landed — the stabilization guardrail "existing suite still green" wins over "stub
  everything". All five pieces are trivial, non-branching plumbing and were implemented outright. Consequence:
  **RU01, RU07, RU11 and RU12 will pass on arrival rather than failing red**, and their green steps GU01, GU07,
  GU11 and GU12 are verification rather than implementation. RU07 joins them because ST10's call-site sync had to
  pass the reference straight through `ExpenseProposalToolUtils.toCommand` for the module to compile, and a
  pass-through parameter has no behaviour left to stub. This is an expected pass, not a false red, at the Stage 2 exit
  check. Every genuinely new behaviour — `ProposalReportUtils.render`, `TelegramMessageDeliveryAdapter.deliver`,
  `ExpenseProposalRepositoryAdapter.findSummariesByMessageReference`, the use case's new flow, and
  `TelegramUpdateUtils`' `from` handling — is a true stub or `TODO`, so RU02–RU10, RI01–RI05 and RS01–RS02 fail
  red as planned.

- **B4 (Stage 2, 2026-08-03):** RU05 asks for the no-`from` skip both as its own scenario and as a case added to
  `skippableUpdates()`, so `TelegramUpdateUtilsTest` now covers it twice — which the testing conventions forbid
  ("Never duplicate a case as both a parameterized entry and a one-off test"). A plan defect, not the step agent's.
  Stage 4's refactor pass collapses it to the parameterized case alone.

- **B5 (Stage 3, 2026-08-03):** The green-batch guardrail caught a regression in
  `TelegramPollFailureRecoverySystemTest`, which no step in this plan names. It drives a message end to end and
  asserts the use case logs the conversation id; once GU04 made every handled message end in a delivery, its
  missing `sendMessage` stub made delivery throw before the log line fired. This is finding F4 in a third system
  test — the review found the gap in RS01 and RS02, but nobody searched for other classes that drive a message
  through. Fixed by the orchestrator: `telegramAcceptsSendMessage(POLL_RECOVERY_TOKEN)` registered alongside the
  other stubs in its `@BeforeEach`, no assertion changed. The plan should have carried an `update:` bullet for it.

- **B2 (baseline, 2026-08-02):** `spotlessCheck` fails across 48 pre-existing files in `ledger-service`, unrelated
  to this plan. It is not part of `test`, so no guardrail in this run depends on it; running `spotlessApply` would
  sweep 48 unrelated files into this plan's diff, so it was left alone.

## Review Findings

- **F1:** ST11, ST14 and RI02 named `replyToMessageId`, which pengrad 10.1.0 does not have — the reply target goes
  through `replyParameters(ReplyParameters)` and travels as a `reply_parameters` form param.
- Resolution: mechanical
- Action: applied — ST11, ST14, RI02, RS01 and RS02 now name `replyParameters` / `reply_parameters`.

- **F2:** The send-then-retry pair RI02 and ST14 described is one request in this pengrad version —
  `ReplyParameters.allowSendingWithoutReply(Boolean)` makes the Bot API send unthreaded when the reply target is
  gone.
- Resolution: decision
- Action: applied — the user chose `allowSendingWithoutReply`; ST11 sets it, ST14 drops
  `telegramRejectsReplyTargetThenAccepts`, and RI02's retry scenario is folded into the happy path.

- **F3:** GI04's `after:` omitted GU12, though every RI04 scenario needs the minter's `mrf` claim first.
- Resolution: mechanical
- Action: applied — GI04 is now `after: GU07, GU11, GU12`.

- **F4:** Neither RS01 nor RS02 armed a `sendMessage` stub, so WireMock would 404 the delivery.
- Resolution: mechanical
- Action: applied — both steps' `given:` lines now register `telegramAcceptsSendMessage` alongside the `getUpdates`
  catch-all.

- **F5:** RS01 asserted the row carries "that turn's `message_reference`", a value no system test can know.
- Resolution: mechanical
- Action: applied — RS01 now reads the expected value off the `mrf` claim in
  `GrpcStubServer.lastExtractionMetadata()`.

- **F6:** ST12 changed `McpTokens.tokenFor(...)`'s signature without naming its three existing callers.
- Resolution: mechanical
- Action: applied — ST12 now names them and says to update every caller.

- **F7:** ST05 added a component to `IntentExtractionRequest` while `IntentProtoUtilsTest`'s three constructions
  were named by no step.
- Resolution: mechanical
- Action: applied — ST05 now covers every call site, production and test, naming `IntentProtoUtilsTest`.

- **F8:** RU06 pinned only the plural `RECORDED` opener while RS01 asserts the singular one.
- Resolution: mechanical
- Action: applied — added a one-summary scenario to RU06.

- **F9:** RI02 had `deliver(null)` throw `MessageDeliveryFailedException`, which no decision settles and which
  reads against the module's habit of answering a null argument with a domain validation exception.
- Resolution: decision
- Action: applied — the user chose `InvalidIncomingMessageException`; RI02's scenario and
  `MessageDeliveryPort`'s `@throws` in ST07 now say so.
