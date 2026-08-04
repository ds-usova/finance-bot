# Plan: Accept or Discard a Reported Proposal from the Chat

**Affected Modules:** `ledger-service`
**Design:** [Accept or Discard a Reported Proposal from the Chat](design.md)

## Step-by-Step Implementation Map (To-Do List)

### Stabilization

#### Database

- [x] ST01 · Add migration `ledger-service/src/main/resources/db/migration/V005__add_expense_message_reference.sql`,
  as designed:
  ```sql
  ALTER TABLE expense
      ADD COLUMN message_reference UUID;

  CREATE INDEX idx_expense_message_reference ON expense (user_id, message_reference);
  ```

#### Interface-First / Build Stabilization

New-method stubs carry a short inline comment describing the implementation intent.

**Interface & Signature Sync**

- [x] ST02 · Add `application/dto/ProposalResolution` — enum `ACCEPT`, `DISCARD`
- [x] ST03 · Add `application/dto/ResolutionOutcome` — enum `ACCEPTED`, `DISCARDED`, `ALREADY_ACCEPTED`,
  `NOTHING_TO_RESOLVE`
- [x] ST04 · Add record `application/dto/ResolveProposalsCommand(String userExternalId, String conversationId,
  String reportMessageId, String interactionId, MessageReference reference, ProposalResolution resolution)` with an
  empty compact constructor carrying `// TODO RU01: reject a blank string, an absent reference and an absent
  resolution with InvalidIncomingMessageException` — the checks themselves are RU01/GU01's
- [x] ST05 · Add record `application/dto/ResolutionAcknowledgement(String conversationId, String reportMessageId,
  String interactionId, ResolutionOutcome outcome, int count)` — no validation, mirroring `ProposalReport`
- [x] ST06 · Add `MessageReference reference` to `application/dto/ProposalReport`, as its last component. In
  `HandleIncomingMessageUseCase.handle`, pass `null` for it with `// TODO GU07: pass the reference minted above`,
  keeping every other line intact — the wiring is GU07's. Fix the nine existing constructions in
  `ProposalReportUtilsTest` and `TelegramMessageDeliveryAdapterTest` so the module compiles
- [x] ST07 · Add inbound port `application/port/ResolveProposalsPort` with `void resolve(ResolveProposalsCommand
  command)`, documenting the runtime exceptions it throws as `@throws` javadoc
- [x] ST08 · Add `void acknowledge(ResolutionAcknowledgement ack)` to `application/port/MessageDeliveryPort`,
  documenting `InvalidIncomingMessageException` and `MessageDeliveryFailedException`
- [x] ST09 · Add `int accept(long userId, MessageReference reference, Instant now)` and `int discard(long userId,
  MessageReference reference)` to `application/port/ExpenseProposalRepository`, documenting
  `PersistenceFailedException`
- [x] ST10 · Add `int countByMessageReference(long userId, MessageReference reference)` to
  `application/port/ExpenseRepository`, documenting `PersistenceFailedException`
- [x] ST11 · Add `application/usecase/ResolveProposalsUseCase implements ResolveProposalsPort`, taking
  `UserRepository`, `ExpenseProposalRepository`, `ExpenseRepository`, `MessageDeliveryPort`, `Clock` and
  `LoggerFactory` by constructor, with the stub:
  ```java
  public void resolve(ResolveProposalsCommand command) {
      // looks the user up by external id; accepts or discards that user's proposals under the command's
      // reference; when nothing moved, counts the expenses already stored under it; maps the two counts onto a
      // ResolutionOutcome and acknowledges through MessageDeliveryPort
  }
  ```
- [x] ST12 · Add `adapter/telegram/ProposalCallbackData` — a final class with a private constructor, a nested
  `public record ParsedCallback(ProposalResolution resolution, MessageReference reference)`, and the stubs
  `public static String render(ProposalResolution resolution, MessageReference reference)` (`// renders
  accept:<uuid> or discard:<uuid>`, returning `""`) and `public static Optional<ParsedCallback> parse(String data)`
  (`// splits the verb from the uuid and checks both itself before calling MessageReference.of, which throws`,
  returning `Optional.empty()`)
- [x] ST13 · Add stub `public static Optional<InlineKeyboardMarkup> renderKeyboard(ProposalReport report)` to
  `adapter/telegram/ProposalReportUtils` (`// one row of a Confirm and a Delete button carrying the payloads
  ProposalCallbackData renders, and empty for a report with no proposals`), returning `Optional.empty()`
- [x] ST14 · Add stub `public static Optional<ResolveProposalsCommand> toResolveProposalsCommand(Update update)` to
  `adapter/telegram/TelegramUpdateUtils` (`// reads the callback query's from, chat, message id, id and data, and
  skips an update missing any of them`), returning `Optional.empty()`
- [x] ST15 · Add `adapter/telegram/ResolutionAcknowledgementUtils` — a final class with a private constructor and
  the stub `public static String render(ResolutionAcknowledgement ack)` (`// one line per outcome, pluralised by
  the count`), returning `""`
- [x] ST16 · Add `acknowledge(ResolutionAcknowledgement ack)` to
  `adapter/telegram/TelegramMessageDeliveryAdapter` with the stub body `// sends AnswerCallbackQuery with the
  wording, then EditMessageReplyMarkup with no markup, attempting the second even when the first failed and
  throwing the first failure`, and add a `TODO` at the point in `deliver` where the keyboard is attached — keep
  every existing line of `deliver` intact
- [x] ST17 · Add two `@Modifying @Query` methods to `adapter/persistence/ExpenseProposalEntityRepository`, each
  returning `int`, with the statements the design gives:
  ```sql
  WITH accepted AS (
      DELETE FROM expense_proposal
      WHERE user_id = :userId AND message_reference = :messageReference
      RETURNING user_id, category_id, description, merchant,
                amount_minor_units, currency_code, message_reference
  )
  INSERT INTO expense (user_id, category_id, description, merchant,
                       amount_minor_units, currency_code, message_reference, created_at, updated_at)
  SELECT user_id, category_id, description, merchant,
         amount_minor_units, currency_code, message_reference, :now, :now
  FROM accepted
  ```
  ```sql
  DELETE FROM expense_proposal
  WHERE user_id = :userId AND message_reference = :messageReference
  ```
- [x] ST18 · Stub `ExpenseProposalRepositoryAdapter.accept()` and `.discard()`, both `@Transactional`:
  ```java
  public int accept(long userId, MessageReference reference, Instant now) {
      // moves every proposal under the reference into expense in one statement, stamping both timestamps with
      // now truncated to microseconds, and classifies a RuntimeException as PersistenceFailedException
      return 0;
  }
  ```
  ```java
  public int discard(long userId, MessageReference reference) {
      // removes every proposal under the reference, classifying a RuntimeException as PersistenceFailedException
      return 0;
  }
  ```
- [x] ST19 · Add `UUID messageReference` to `adapter/persistence/ExpenseEntity`, positioned after `currencyCode` so
  it mirrors `ExpenseProposalEntity`; `fromDomain` passes `null` and `toDomain` does not pass the column on (D39).
  Update `ExpenseRepositoryAdapter.truncatedToMicros`, which re-emits every component
- [x] ST20 · Add a `@Query` count method returning `int` to `adapter/persistence/ExpenseEntityRepository`:
  ```sql
  SELECT count(*) FROM expense
  WHERE user_id = :userId AND message_reference = :messageReference
  ```
- [x] ST21 · Stub `ExpenseRepositoryAdapter.countByMessageReference()`:
  ```java
  public int countByMessageReference(long userId, MessageReference reference) {
      // counts the user's expenses under the reference, wrapping a RuntimeException in PersistenceFailedException
      return 0;
  }
  ```
- [x] ST22 · Add `callback_query` to `TelegramLongPollingSubscriber`'s `allowedUpdates`, beside the existing
  `message`
- [x] ST23 · Add `ResolveProposalsPort` as a second constructor parameter of `TelegramUpdateListener` and route
  each update: the message mapping first, the callback mapping second, both inside the existing
  swallow-and-log-per-update `try`. Update the one call site, `TelegramUpdateListenerTest`'s `setUp()`, to pass a
  `mock(ResolveProposalsPort.class)`
- [x] ST24 · Add a `resolveProposalsPort` `@Bean` to `adapter/config/UseCaseConfiguration`, taking
  `UserRepository`, `ExpenseProposalRepository`, `ExpenseRepository`, `MessageDeliveryPort` and `LoggerFactory` as
  parameters and passing `Clock.systemUTC()`, wired like `createExpensePort`

**Shared Test Infrastructure**

- [x] ST25 · Add to `bot.finance.common.TelegramFixtures`: `callbackQueryUpdate(int updateId, long userId,
  long chatId, int messageId, String data)`, `callbackQueryUpdateWithoutFrom(int updateId, long chatId, String
  data)` and `callbackQueryUpdateWithoutMessage(int updateId, long userId, String data)`, plus two response
  envelopes in the shapes pengrad's own request types declare: `answerCallbackQueryResponse()` is a bare
  `BaseResponse` — `{"ok":true,"result":true}` — while `editMessageReplyMarkupResponse()` is `Message`-shaped like
  the existing `sendMessageResponse()`, since `EditMessageReplyMarkup(Object, int)` registers `SendResponse` and a
  `"result": true` body would fail to deserialize before any assertion runs. Leave the existing
  no-argument-beyond-`updateId` `callbackQueryUpdate(int)` untouched — `TelegramUpdateUtilsTest` uses it as its
  "no message at all" case
- [x] ST26 · Add to `bot.finance.common.TelegramTestBot`: `answerCallbackQueryPath(token)`,
  `editMessageReplyMarkupPath(token)`, `recordedAnswerCallbackQueries(token)`,
  `recordedEditMessageReplyMarkups(token)`, and `recordedBotApiMethods(token)` returning the Bot API method names
  the stub server received for that token in arrival order — RI03 asserts the answer precedes the edit (D8). Add
  the token constants `RESOLVE_PROPOSALS_TOKEN` and `RESOLVE_UNKNOWN_PROPOSALS_TOKEN` for RS01 and RS02
- [x] ST27 · Add to `bot.finance.common.WireMockStubs`: `telegramAcceptsAnswerCallbackQuery(token)`,
  `telegramFailsAnswerCallbackQuery(token, errorCode, description)`,
  `telegramAcceptsEditMessageReplyMarkup(token)` and
  `telegramFailsEditMessageReplyMarkup(token, errorCode, description)`, registered through
  `WireMockSupport.SERVER` like every other helper there
- [x] ST28 · Move `ExpenseProposalRepositoryAdapterTest`'s private `storedProposal(...)` helper onto
  `bot.finance.common.ExpenseProposalRowUtils` as `storedProposal(JdbcAggregateTemplate, long userId, long
  categoryId, String description, String merchant, long amountMinorUnits, String currencyCode, UUID
  messageReference, Instant createdAt)`, and point the test class at it — RI01 and RS01 both seed proposal rows,
  and neither red step owns a shared fixture
- [x] ST29 · List every helper ST25–ST28 added under
  [Package Structure](../../ledger-service/docs/conventions/testing.md#package-structure) in the module's testing
  conventions
- [x] ST30 · Compile the module green, then confirm `bot.finance.architecture.CleanArchitectureTest` still passes

### Red Phase

#### TDD Unit Red Phase

- [x] RU01 · `ResolveProposalsCommand` · test: `ResolveProposalsCommandTest` · covers: compact constructor
    - compact constructor:
        - given: a non-blank user external id, conversation id, report message id and interaction id, a present
          reference and a present resolution
          when: the record is constructed
          then: it is constructed and each component reads back what was passed
        - given: a null or blank `userExternalId`
          when: the record is constructed
          then: throws `InvalidIncomingMessageException`
        - given: a null or blank `conversationId`
          when: the record is constructed
          then: throws `InvalidIncomingMessageException`
        - given: a null or blank `reportMessageId`
          when: the record is constructed
          then: throws `InvalidIncomingMessageException`
        - given: a null or blank `interactionId`
          when: the record is constructed
          then: throws `InvalidIncomingMessageException`
        - given: a null `reference`
          when: the record is constructed
          then: throws `InvalidIncomingMessageException`
        - given: a null `resolution`
          when: the record is constructed
          then: throws `InvalidIncomingMessageException`
- [x] RU02 · `ResolveProposalsUseCase` · test: `ResolveProposalsUseCaseTest` · covers: `resolve()`
    - `resolve()`:
        - given: nothing stubbed
          when: `resolve(null)` is called
          then: throws `InvalidIncomingMessageException` and none of the four ports is touched
        - given: a stored user and `accept` answering 2, for an `ACCEPT` command
          when: `resolve` is called
          then: `accept` is called with the user's id, the command's reference and `Instant.now(clock)`, and
          `acknowledge` receives a `ResolutionAcknowledgement` carrying `ACCEPTED`, a count of 2 and the command's
          conversation id, report message id and interaction id
        - given: a stored user and `discard` answering 3, for a `DISCARD` command
          when: `resolve` is called
          then: `acknowledge` receives `DISCARDED` with a count of 3, `accept` is never called and
          `ExpenseRepository` is never touched
        - given: a stored user, `accept` answering 0 and `countByMessageReference` answering 2, for an `ACCEPT`
          command
          when: `resolve` is called
          then: `countByMessageReference` is called with the user's id and the command's reference, and
          `acknowledge` receives `ALREADY_ACCEPTED` with a count of 2
        - given: a stored user, `discard` answering 0 and `countByMessageReference` answering 2, for a `DISCARD`
          command
          when: `resolve` is called
          then: `acknowledge` receives `ALREADY_ACCEPTED` with a count of 2
        - given: a stored user, the resolution answering 0 and `countByMessageReference` answering 0
          when: `resolve` is called
          then: `acknowledge` receives `NOTHING_TO_RESOLVE` with a count of 0
        - given: `findByExternalId` answering empty
          when: `resolve` is called
          then: `acknowledge` receives `NOTHING_TO_RESOLVE` with a count of 0, and neither
          `ExpenseProposalRepository` nor `ExpenseRepository` is touched
        - given: a stored user and `accept` throwing `PersistenceFailedException`
          when: `resolve` is called
          then: that exception propagates and `acknowledge` is never called
        - given: a stored user, `discard` answering 2 and `acknowledge` throwing
          `MessageDeliveryFailedException`
          when: `resolve` is called
          then: that exception propagates
        - given: a stored user and `accept` answering 2
          when: `resolve` is called
          then: one `info` line is logged whose arguments carry a `MessageReference`, the `ProposalResolution`,
          the `ResolutionOutcome` and the count — the outcome leaves no other trace once the tap is answered
          (D26, D37)
- [x] RU03 · `ProposalCallbackData` · test: `ProposalCallbackDataTest` · covers: `render()`, `parse()`
    - `render()`:
        - given: `ACCEPT` and a reference
          when: `render` is called
          then: returns `accept:` followed by that reference's canonical UUID text
        - given: `DISCARD` and a reference
          when: `render` is called
          then: returns `discard:` followed by that reference's canonical UUID text
        - given: either resolution and a reference
          when: `render` is called
          then: the result is at most 64 bytes, the Bot API's `callback_data` limit (D3)
    - `parse()`:
        - given: the payload `render(ACCEPT, reference)` produced
          when: `parse` is called
          then: returns a `ParsedCallback` carrying `ACCEPT` and that same reference
        - given: the payload `render(DISCARD, reference)` produced
          when: `parse` is called
          then: returns a `ParsedCallback` carrying `DISCARD` and that same reference
        - given: `accept:not-a-uuid`
          when: `parse` is called
          then: returns empty and throws nothing — the payload is checked before `MessageReference.of`, which
          throws (D19)
        - given: a payload whose verb is neither `accept` nor `discard` — `resolve:<uuid>`, `ACCEPT:<uuid>`
          when: `parse` is called
          then: returns empty
        - given: `null`, `""`, `"   "`, a payload with no colon, and a bare `accept:`
          when: `parse` is called
          then: returns empty
- [x] RU04 · `ProposalReportUtils` · test: `ProposalReportUtilsTest` · covers: `renderKeyboard()`
    - `renderKeyboard()`:
        - given: a `RECORDED` report carrying two summaries and a reference
          when: `renderKeyboard` is called
          then: returns a markup of exactly one row of two buttons, the first labelled `Confirm` carrying
          `ProposalCallbackData.render(ACCEPT, reference)` and the second labelled `Delete` carrying
          `ProposalCallbackData.render(DISCARD, reference)` (D18)
        - given: a `PARTIAL` report carrying one summary and a reference
          when: `renderKeyboard` is called
          then: returns the same one-row, two-button markup (D1)
        - given: a `NOTHING_IDENTIFIED` report and a `FAILED` report, both with no summaries
          when: `renderKeyboard` is called
          then: returns empty (D1)
        - given: a `RECORDED` report whose summary list is empty
          when: `renderKeyboard` is called
          then: returns empty — the outcome does not decide it, the proposal list does (D1)
- [x] RU05 · `ResolutionAcknowledgementUtils` · test: `ResolutionAcknowledgementUtilsTest` · covers: `render()`
    - `render()`:
        - given: an `ACCEPTED` acknowledgement with a count of 2, and one with a count of 1
          when: `render` is called
          then: returns `Confirmed 2 expenses.` and `Confirmed 1 expense.` (D42)
        - given: a `DISCARDED` acknowledgement with a count of 2, and one with a count of 1
          when: `render` is called
          then: returns `Deleted 2 expenses.` and `Deleted 1 expense.` (D42)
        - given: an `ALREADY_ACCEPTED` acknowledgement with a count of 2, and one with a count of 1
          when: `render` is called
          then: returns `Already confirmed: 2 expenses.` and `Already confirmed: 1 expense.` (D42)
        - given: a `NOTHING_TO_RESOLVE` acknowledgement with a count of 0
          when: `render` is called
          then: returns `There is nothing left to resolve.` (D42)
        - given: every `ResolutionOutcome`, each with a count of 999
          when: `render` is called
          then: the result is at most 200 characters, the limit `answerCallbackQuery` imposes on its text (D9)
- [x] RU06 · `TelegramUpdateUtils` · test: `TelegramUpdateUtilsTest` · covers: `toResolveProposalsCommand()`
    - `toResolveProposalsCommand()`:
        - given: an update carrying a callback query with an id, a `from`, a message with a chat and a message id,
          and the data `accept:<uuid>`
          when: `toResolveProposalsCommand` is called
          then: returns a command whose `userExternalId` is the `from` id, `conversationId` the chat id,
          `reportMessageId` the message id, `interactionId` the callback query id, `reference` that UUID and
          `resolution` `ACCEPT` — the tapper comes from `from`, never from the payload (D7)
        - given: the same update carrying the data `discard:<uuid>`
          when: `toResolveProposalsCommand` is called
          then: the returned command's resolution is `DISCARD`
        - given: `null`, a text-message update with no callback query, a callback query with no `from`, a callback
          query with no message, and a callback query whose data is `noop`
          when: `toResolveProposalsCommand` is called
          then: returns empty for each (D11)
- [x] RU07 · `HandleIncomingMessageUseCase` · test: `HandleIncomingMessageUseCaseTest` · covers: `handle()`
    - `handle()`:
        - update: `whenExtractionSucceedsWithSummaries_thenDeliverReceivesRecordedReport()` — assert the delivered
          report's new reference component is the same `MessageReference` the captured `IntentExtractionRequest`
          carried; without it the new component is delivered untested

#### TDD Integration Red Phase

- [x] RI01 · `ExpenseProposalRepositoryAdapter` · test: `ExpenseProposalRepositoryAdapterTest` · covers:
  `accept()`, `discard()`
    - `accept()`:
        - given: a stored user with a stored grouping and category, two proposal rows under one reference (one
          carrying a merchant, one not) and a third under a different reference
          when: `accept(userId, reference, now)` is called
          then: returns 2; the user's `expense` rows are exactly two, each carrying the category id, description,
          merchant, minor units, currency code and `message_reference` of the proposal it came from, with both
          timestamps equal to `now`; the two proposal rows are gone and the third survives
        - given: a proposal row whose `merchant` column is null
          when: `accept` is called
          then: the written `expense` row's `merchant` column is null
        - given: a stored user and a reference nothing was written under
          when: `accept` is called
          then: returns 0 and no `expense` row is written (D5)
        - given: two stored users each holding one proposal row under the same reference value
          when: `accept` is called for the first user
          then: returns 1, the first user's proposal is gone and the second user's survives untouched (D7)
        - given: a `now` carrying nanosecond precision
          when: `accept` is called
          then: both written timestamps equal that instant truncated to microseconds, as every other write in this
          module stores an `Instant`
    - `discard()`:
        - given: a stored user with two proposal rows under one reference and a third under a different reference
          when: `discard(userId, reference)` is called
          then: returns 2, only the third proposal row survives, and no `expense` row is written (D14)
        - given: a stored user and a reference nothing was written under
          when: `discard` is called
          then: returns 0
        - given: two stored users each holding one proposal row under the same reference value
          when: `discard` is called for the first user
          then: returns 1 and the second user's row survives (D7)
        - given: an adapter over a mocked `ExpenseProposalEntityRepository` whose `accept` and whose `discard`
          each throw a `QueryTimeoutException` — the failure the healthy containerized Postgres cannot be made to
          raise, in the nested class that already exists for exactly that
          when: each adapter method is called
          then: throws `PersistenceFailedException` carrying the framework exception as its cause
- [x] RI02 · `ExpenseRepositoryAdapter` · test: `ExpenseRepositoryAdapterTest` · covers:
  `countByMessageReference()`. Every scenario below seeds its `expense` rows by inserting an `ExpenseEntity`
  carrying the reference through `JdbcAggregateTemplate`, the way `ExpenseProposalRowUtils.storedProposal` seeds a
  proposal row — `adapter.create` cannot produce one, since it writes the column as `null` (D39)
    - `countByMessageReference()`:
        - given: a stored user with two `expense` rows under one reference and one under a different reference
          when: `countByMessageReference(userId, reference)` is called
          then: returns 2
        - given: a stored user and a reference nothing was written under
          when: `countByMessageReference` is called
          then: returns 0 (D30)
        - given: a stored user whose `expense` rows all carry a null `message_reference`
          when: `countByMessageReference` is called
          then: returns 0 — a row no message produced is counted for no reference (D28)
        - given: two stored users each holding one `expense` row under the same reference value
          when: `countByMessageReference` is called for the first user
          then: returns 1 (D7)
        - given: an adapter over a mocked `ExpenseEntityRepository` whose count throws a `QueryTimeoutException`,
          in the nested mocked-store class
          when: `countByMessageReference` is called
          then: throws `PersistenceFailedException` carrying the framework exception as its cause, and never
          `EntityNotFoundException` — a count raises no constraint violation
        - update: `whenCalledWithMerchant_thenRowWrittenWithGivenFieldsAndReturnedExpenseCarriesGeneratedId()` —
          `ExpenseEntity` gained `messageReference`; assert the written row's `messageReference()` is null, since
          `create` builds no reference (D39)
- [x] RI03 · `TelegramMessageDeliveryAdapter` · test: `TelegramMessageDeliveryAdapterTest` · covers: `deliver()`,
  `acknowledge()`
    - `deliver()`:
        - given: a `RECORDED` report carrying two summaries and a reference, over a bot WireMock accepts
          `sendMessage` for
          when: `deliver` is called
          then: the recorded `sendMessage` carries a `reply_markup` form param holding one row of two buttons
          whose texts are `Confirm` and `Delete` and whose `callback_data` values are what
          `ProposalCallbackData.render` produces for that reference
        - given: a `NOTHING_IDENTIFIED` report with no summaries
          when: `deliver` is called
          then: the recorded `sendMessage` carries no `reply_markup` form param (D1)
        - update: `whenCalledWithRecordedReportAndSendMessageAccepted_thenExactlyOneSendMessageIsRecordedAsTheReportSays()`
          — `ProposalReport` gained `reference`; pass one from the fixture and leave the existing text, chat and
          reply-parameter assertions as they are
    - `acknowledge()`:
        - given: an `ACCEPTED` acknowledgement, over a bot WireMock accepts both `answerCallbackQuery` and
          `editMessageReplyMarkup` for
          when: `acknowledge` is called
          then: exactly one `answerCallbackQuery` is recorded carrying the acknowledgement's interaction id as
          `callback_query_id` and the text `ResolutionAcknowledgementUtils.render` produces, and exactly one
          `editMessageReplyMarkup` is recorded carrying the conversation id as `chat_id`, the report message id as
          `message_id` and no `reply_markup` form param (D6)
        - given: the same acknowledgement over a bot whose stubs record both calls
          when: `acknowledge` is called
          then: the two recorded Bot API calls, in arrival order, are `answerCallbackQuery` then
          `editMessageReplyMarkup` (D8)
        - given: a bot whose `answerCallbackQuery` is answered with a non-OK envelope and whose
          `editMessageReplyMarkup` is accepted
          when: `acknowledge` is called
          then: throws `MessageDeliveryFailedException` and the `editMessageReplyMarkup` is still recorded (D34)
        - given: a bot whose `answerCallbackQuery` is accepted and whose `editMessageReplyMarkup` is answered with
          a non-OK envelope
          when: `acknowledge` is called
          then: throws `MessageDeliveryFailedException` (D35)
        - given: a bot answering both calls with a non-OK envelope, each carrying a distinguishable description
          when: `acknowledge` is called
          then: the thrown `MessageDeliveryFailedException`'s message names the `answerCallbackQuery` failure's
          description and not the edit's — the first failure is the one thrown (D34)
        - given: a bot WireMock accepts both calls for
          when: `acknowledge(null)` is called
          then: throws `InvalidIncomingMessageException` and nothing is sent, as `deliver(null)` already does
        - given: a bot pointed at an address that refuses the connection
          when: `acknowledge` is called
          then: throws `MessageDeliveryFailedException` carrying the client exception as its cause
- [x] RI04 · `TelegramUpdateListener` · test: `TelegramUpdateListenerTest` · covers: a polled `callback_query`
  update · mocks: `HandleIncomingMessagePort`, `ResolveProposalsPort`
    - Happy Path:
        - given: `ResolveProposalsPort` mocked, and the loop started with `allowed_updates` carrying both `message`
          and `callback_query`, over a stub answering the first poll with a `callback_query` update carrying
          `accept:<uuid>`
          when: the loop picks it up
          then: `resolve` is called with a command carrying the `from` id, the chat id, the message id, the
          callback query id and that reference with `ACCEPT`; `HandleIncomingMessagePort` is never called; and a
          follow-up poll carrying the next offset confirms the batch
    - Error Mapping:
        - given: `ResolveProposalsPort.resolve` throwing `PersistenceFailedException`, over a stub answering the
          first poll with a `callback_query` update carrying `accept:<uuid>`
          when: the loop picks it up
          then: a follow-up poll carrying the next offset still confirms the batch, so the loop is not stalled
          (D12)
    - Validation: an update whose callback query carries an unrecognised `data` — neither port is called and the
      batch is still confirmed (D11); a batch pairing a text-message update with a `callback_query` update — each
      port is called exactly once and the whole batch is confirmed
- [x] RI05 · `TelegramLongPollingSubscriber` · test: `TelegramLongPollingSubscriberTest` · covers: `start()`
    - `start()`:
        - update: `whenStartIsCalled_thenPollsGetUpdatesWithConfiguredParametersAndReportsRunning()` — assert the
          recorded poll's `allowed_updates` form param carries `callback_query` alongside the `message` it already
          asserts; without it no tap ever reaches the listener (D2)

#### TDD System Test Red Phase

- [x] RS01 · `ResolveProposalsSystemTest` · covers: `ResolveProposalsPort.resolve()`
    - Happy Path:
        - given: a stored user under the `from` id with a stored grouping and category and two `expense_proposal`
          rows under one reference; and, registered in this order, the catch-all `getUpdates` stub, the
          `answerCallbackQuery` and `editMessageReplyMarkup` stubs, then the stub answering the first poll with a
          `callback_query` update carrying `accept:<that reference>`
          when: the running poll loop picks the update up
          then: the batch is confirmed by a follow-up poll carrying the next offset; no `expense_proposal` row
          remains for that user; exactly two `expense` rows exist for them, each carrying that reference; one
          `answerCallbackQuery` is recorded naming the callback query id and reporting two confirmed expenses; and
          one `editMessageReplyMarkup` is recorded for the report message with no `reply_markup`
- [x] RS02 · `ResolveUnknownProposalsSystemTest` · covers: `ResolveProposalsPort.resolve()`
    - Unhappy Path:
        - given: a stored user under the `from` id and no `expense_proposal` or `expense` row under the tapped
          reference; and the same stub registration order as RS01, answering the first poll with a
          `callback_query` update carrying `discard:<an unknown uuid>`
          when: the running poll loop picks the update up
          then: the batch is confirmed; no `expense` row is written for that user; one `answerCallbackQuery`
          reports there is nothing left to resolve; and one `editMessageReplyMarkup` is still recorded, since
          every outcome strips the keyboard (D36)
- [x] RS03 · `ReceiveTelegramMessageSystemTest` · covers: `HandleIncomingMessagePort.handle()`
    - Happy Path:
        - update: `whenRunningPollLoopPicksUpTextMessageUpdate_thenBatchIsConfirmedAndMessageIsPrinted()` — assert
          the recorded `sendMessage` carries a `reply_markup` holding two buttons whose `callback_data` values
          carry the same UUID as the bearer token's `mrf` claim the test already reads; the report the buttons
          attach to is what this change makes resolvable
- [x] RS04 · `HandleIncomingMessageFailureSystemTest` · covers: `HandleIncomingMessagePort.handle()`
    - Unhappy Path:
        - update: `whenLoopPicksUpdateUp_thenFailureIsLoggedAndBatchIsStillConfirmed()` — assert the recorded
          `sendMessage` carries no `reply_markup` form param, since a `FAILED` report has nothing to resolve (D1)

### Green Phase

#### TDD Unit Green Phase

- [ ] GU01 · `ResolveProposalsCommand` · test: `ResolveProposalsCommandTest`
- [ ] GU02 · `ResolveProposalsUseCase` · test: `ResolveProposalsUseCaseTest` · after: GU01
- [ ] GU03 · `ProposalCallbackData` · test: `ProposalCallbackDataTest`
- [ ] GU04 · `ProposalReportUtils` · test: `ProposalReportUtilsTest` · after: GU03
- [ ] GU05 · `ResolutionAcknowledgementUtils` · test: `ResolutionAcknowledgementUtilsTest`
- [ ] GU06 · `TelegramUpdateUtils` · test: `TelegramUpdateUtilsTest` · after: GU01, GU03
- [ ] GU07 · `HandleIncomingMessageUseCase` · test: `HandleIncomingMessageUseCaseTest`

#### TDD Integration Green Phase

- [ ] GI01 · `ExpenseProposalRepositoryAdapter` · test: `ExpenseProposalRepositoryAdapterTest`
- [ ] GI02 · `ExpenseRepositoryAdapter` · test: `ExpenseRepositoryAdapterTest`
- [ ] GI03 · `TelegramMessageDeliveryAdapter` · test: `TelegramMessageDeliveryAdapterTest` · after: GU04, GU05
- [ ] GI04 · `TelegramUpdateListener` · test: `TelegramUpdateListenerTest` · after: GU06
- [ ] GI05 · `TelegramLongPollingSubscriber` · test: `TelegramLongPollingSubscriberTest`

#### TDD System Test Green Phase

- [ ] GS01 · `ResolveProposalsSystemTest` · covers: `ResolveProposalsPort.resolve()`
- [ ] GS02 · `ResolveUnknownProposalsSystemTest` · covers: `ResolveProposalsPort.resolve()`
- [ ] GS03 · `ReceiveTelegramMessageSystemTest` · covers: `HandleIncomingMessagePort.handle()`
- [ ] GS04 · `HandleIncomingMessageFailureSystemTest` · covers: `HandleIncomingMessagePort.handle()`

### Post-Implementation Steps

#### ADRs

- [ ] P01 · Write ADR: a set of rows is moved between tables by a single data-modifying CTE — a
  `DELETE … RETURNING` feeding an `INSERT` — so two concurrent resolutions contend on the same rows and only the
  transaction that deletes them writes anything

## Open Questions / Blockers

- **Q1:** Should an ADR record that a set of rows is moved between tables by a single data-modifying CTE
  (`DELETE … RETURNING` feeding an `INSERT`), so two concurrent resolutions contend on the same rows and only one
  of them writes? It is a Postgres-specific technique this module has not used before, and D5 is the only place it
  is written down. Without an ADR, D5 in this design and the query on `ExpenseProposalEntityRepository` hold it.
- A: Yes — record this one. It is P01 above.

- **Q2:** D34 sets a rule this module has never needed: when one port operation makes two Bot API calls, the
  second is attempted even after the first failed and the *first* failure is the one thrown. Should that be an ADR
  too, or is the port's javadoc plus RI03's scenarios enough?
- A: No ADR. Only the D5 candidate is recorded; D34 in the design, `MessageDeliveryPort`'s javadoc and RI03's
  scenarios hold this one.

## Review Findings

- **F1:** ST11's constructor list and ST24's `@Bean` omitted `MessageDeliveryPort`, which the use case's own stub
  comment and every RU02 scenario require.
- Resolution: mechanical
- Action: applied — added it to both, and to the design's `ResolveProposalsUseCase` bullet.

- **F2:** ST23 changed `TelegramUpdateListener`'s constructor but named no call site, so
  `TelegramUpdateListenerTest` would stop compiling and ST30 could not pass.
- Resolution: mechanical
- Action: applied — ST23 now updates that construction to pass a mocked `ResolveProposalsPort`.

- **F3:** ST06 wired the report's new reference fully instead of stubbing it, making RU07's `update:` assertion
  green before its red step ran and GU07 a no-op.
- Resolution: mechanical
- Action: applied — ST06 passes `null` with a `TODO GU07`, leaving the wiring to GU07.

- **F4:** ST25 left the response-envelope shapes unstated, and a `"result": true` body fails to deserialize for
  `EditMessageReplyMarkup`, which declares `SendResponse`.
- Resolution: mechanical
- Action: applied — ST25 names both shapes, `Message` for the edit and bare `BaseResponse` for the answer.

- **F5:** RI02 needed `expense` rows carrying a reference, which `adapter.create` cannot produce and no helper
  seeds.
- Resolution: mechanical
- Action: applied — RI02 now says the rows are inserted as an `ExpenseEntity` through `JdbcAggregateTemplate`.

- **F6:** Two ADR candidates were asked where the conventions say one or none, P01 was worded as a condition
  rather than a fact, and Q2 had no item at all.
- Resolution: decision
- Action: resolved — the user chose the D5 candidate (2026-08-05). P01 is now a single item stating the decision
  as a fact; Q2 is answered "no ADR" and carries none.

- **F7:** The design's **Adapters** bullet for `ExpenseEntity` contradicted D39 on whether the column maps onto
  the domain type.
- Resolution: decision
- Action: resolved — D39 wins and the bullet was corrected to match it. D39 is a `decided` entry recording the
  user's own choice of "the column and the entity mapping over the full domain change" (2026-08-04); the
  **Adapters** bullet is prose that predates it, and `Expense`, its two factories and `CreateExpenseUseCase` are
  untouched by every step in this plan. ST19 already implemented D39 and needed no change.
