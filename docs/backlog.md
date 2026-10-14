# Backlog

**Format:** 2
**Last ids:** BB06 · BR01 · BT10 · BP00

Work still open from reviews: one row per bug not yet fixed, one row per `R` row whose owner still says
`open`. The owning `review/findings.md` holds the finding; this file only points at it. The `#` column is the
id to name when planning a bug fix or a rework — `B<n>` for a bug, `C<n>` for a refactoring candidate — and it
never changes once given.

## Bugs

| # | Raised by | Module | What | Where |
|---|-----------|--------|------|-------|

## Refactoring candidates

| #  | Raised by | Module                 | What                                                                                                  | Where                                                                                       |
|----|-----------|------------------------|-------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| C2 | task 32   | `ai-connector-service` | keep a hand-entered expense as a row of its own, with its own retention                               | [findings](implemented/32-the-connector-learns-what-became-of-a-message/review/findings.md) |
| C6 | task 40   | `ledger-service`       | loosen the log assertion pinning an SLF4J overload, and restore the class's own three-argument idiom  | [findings](implemented/40-set-a-default-currency/review/findings.md)                        |

## Open

### BB01 · `web-app` — asking after a filed text never stops on a 400, a 429 or a malformed answer

- **Kind** bug · **Priority** normal · **Size** S
- **Raised by** user, 2026-10-11
- **What** after a text is filed, the page asks after it every 5 seconds and keeps asking through a 400, a 429
  or an answer it cannot parse. A broken answer means it asks forever. The spec keeps asking only on a 503 or a
  network failure · `web-app/src/pages/useWrittenMessage.ts`
- **Why** found while archiving task 47, by reading the code; unverified by a test

### BB02 · `web-app` — a filed text is announced as filed while a category filter hides its entries

- **Kind** bug · **Priority** normal · **Size** S
- **Raised by** user, 2026-10-11
- **What** the rule for which days the listing can show checks the status and the period, never the category.
  Under a category filter a filed day is read back, holds no matching entry, and the filing is still announced
  as filed rather than out of view · `listingCanShow` in `web-app/src/components/expenseDays.ts`
- **Why** found while archiving task 47, by reading the code; unverified by a test

### BB03 · `web-app` — a filing answered with an empty or non-JSON body shows the browser's own error text

- **Kind** bug · **Priority** normal · **Size** S
- **Raised by** user, 2026-10-11
- **What** a filing answered with an empty body, or one that is not JSON, puts the browser's own error text
  beneath the write-in line instead of the line's fixed wording, unlike every other surface's malformed answer ·
  `fileMessage` in `web-app/src/api/messages.ts`
- **Why** found while archiving task 47, by reading the code; unverified by a test

### BB04 · `openapi` — the written-message 429 and 404 examples do not match the messages served

- **Kind** bug · **Priority** low · **Size** S
- **Raised by** user, 2026-10-11
- **What** the 429 example says "a text of yours is already waiting to be read", the ledger answers "user <id>
  already has a written message waiting or being read". The 404 example says "no message of yours carries that
  id", the ledger answers "no written message <id> for the caller" · `openapi/paths/messages.yaml`,
  `openapi/paths/message.yaml`
- **Why** found while archiving task 47, by comparing the schema with the code

### BB05 · `ledger-service` — the web turn's log lines print the message id as a record, not `web:<id>`

- **Kind** bug · **Priority** low · **Size** S
- **Raised by** user, 2026-10-11
- **What** the claim and settle lines print `IncomingMessageId[value=web:42]` where the plan asked for `web:42`.
  The sweep's warning already prints `web:42`. The Telegram turn logs the same record form ·
  `HandleWrittenMessageUseCase`, `ExtractionTurn`
- **Why** found while archiving task 47, by reading the code

### BB06 · `ledger-service` — spending queries remain after a failed cleanup

- **Kind** bug · **Priority** normal · **Size** TBD
- **Raised by** user, 2026-10-12
- **What** `ExtractionTurn.discardPeriods` catches a deletion failure and only logs a warning. The web message
  can then become `DONE`, so the worker never retries its cleanup. No scheduled retry or age-based purge removes
  the remaining `spending_query` rows. The Telegram path uses the same cleanup method ·
  [ExtractionTurn](../ledger-service/src/main/java/bot/finance/application/usecase/ExtractionTurn.java),
  [HandleWrittenMessageUseCase](../ledger-service/src/main/java/bot/finance/application/usecase/HandleWrittenMessageUseCase.java)
- **Expected** cleanup eventually retries after persistence recovers, without repeating extraction or summary
  delivery. Repeated cleanup is idempotent and does not remove queries belonging to active turns.
- **Acceptance** simulate a failed first deletion; finish the turn; restore persistence; verify that the leftover
  query is eventually removed and the turn is not executed again. Cover both web and Telegram paths and verify
  that an active turn's queries are preserved.
- **Why** found while reviewing the evaluation spending-question cases; confirmed by reading the implementation,
  not yet reproduced by a test

### BR01 · `ledger-service` — remove `CategoryEntityRepository.findNonEmptyGroupingNames`

- **Kind** refactoring candidate · **Priority** normal · **Size** S
- **Raised by** task 51, 2026-10-14 · [origin](implemented/51-the-ledger-takes-a-turns-writes-over-http/review/findings.md)
- **What** remove `CategoryEntityRepository.findNonEmptyGroupingNames`
- **Why** the catalogue read replaced it; 1 declaration and 0 callers in ledger-service/src, left by the refactor
  pass as outside its diff

### BT04 · `web-app` — discard a pending entry from the expenses page

- **Kind** deferred change · **Priority** normal · **Size** M
- **Raised by** task 49, 2026-10-12 · [origin](implemented/49-discard-chosen-proposals/design-log.md)
- **What** a person can discard chosen pending entries from the page, through `POST /api/v1/expenses/discards`
- **Why** task 49 ships the endpoint alone (its DN01); an entry filed as text from the web has no Telegram report,
  so nothing on the page throws it away

### BT05 · `ledger-service` — a Telegram report's text follows what was accepted and discarded

- **Kind** deferred change · **Priority** normal · **Size** M
- **Raised by** user, 2026-10-13
- **What** after any accept or discard, whether by id from the web or by a Telegram tap, the report is rewritten.
  While entries stay pending it lists only those and keeps its buttons. Once none is pending it lists what was
  accepted and loses its buttons. The text is rendered once at delivery and never stored: `proposal_report` holds
  only where it was sent, and the spending summary block cannot be rebuilt once the turn's `spending_query` rows
  are gone. A migration must keep the summary block and the outcome. Two resolutions in a row hand off two
  asynchronous rewrites, and the older can land last with stale text. Rewrites of one report must therefore run
  one at a time, each re-reading the entries just before its edit. A version check alone does not prevent this,
  because the Telegram edit sits outside the transaction and both checks can pass before either edit is sent ·
  `TurnReportRenderer`, `ClearEmptiedReportsUseCase`, `MessageDeliveryPort`
- **Why** a partly resolved report keeps listing entries already gone (task 49, AC12)

### BT06 · `ledger-service` — a failure opening or committing an acceptance's or a discard's transaction answers 503

- **Kind** deferred change · **Priority** normal · **Size** S
- **Raised by** task 49, 2026-10-13 · [origin](implemented/49-discard-chosen-proposals/review/findings.md)
- **What** a store failure while the transaction opens or commits answers 503, as a refused statement does, for
  both the acceptance and the discard
- **Why** `acceptByIds` and `discardByIds` catch only inside their `@Transactional` body, and
  `WebExceptionHandler` maps no transaction exception, so such a failure reaches `onUnexpected` and answers 500;
  deferred by the design as DF07

### BT08 · `ai-connector-service` — the tool-calling turn answers `list_categories` from the request's catalogue

- **Kind** deferred change · **Priority** normal · **Size** M
- **Raised by** task 51, 2026-10-13 · [origin](51-the-ledger-takes-a-turns-writes-over-http/design-log.md)
- **What** once the extraction request carries the whole catalogue, the connector answers the model's
  `list_categories` call itself instead of calling the ledger. The model still asks one grouping at a time, and the
  catalogue never enters its prompt. The evaluation case expecting a ledger-side `list_categories` call changes
  with it
- **Why** one network round trip per grouping asked about, for an answer the request already holds (task 51, DN01)

### BT09 · `eval-runner` — measure a classifier turn

- **Kind** deferred change · **Priority** normal · **Size** M
- **Raised by** task 52, 2026-10-13 · [origin](52-the-connector-files-with-a-classifier/design-log.md)
- **What** the runner and its cases measure a turn run with `EXTRACTION_MODE` at `classifier`: a case's expected
  calls hold for a turn that never asks for one grouping's categories, and a run records which mode it ran
- **Why** a classifier turn makes no `list_categories` call, which a case expects (task 52, DN04)

### BT10 · `ledger-service`, `ai-connector-service` — the turn becomes asynchronous

- **Kind** deferred change · **Priority** normal · **Size** L
- **Raised by** task 52, 2026-10-13 · [origin](52-the-connector-files-with-a-classifier/design-log.md)
- **What** the connector accepts a turn and answers at once; it tells the ledger the turn is done through an
  operation the ledger serves, and the ledger builds the report then. A turn the connector never finishes is
  settled by a sweep. Both modes change with it. The sweep retries a turn that failed as unavailable, so a
  filing becomes idempotent by message and the expense's position in it, and a retried turn never files an
  expense twice
- **Why** the ledger holds the call open for the whole turn, near five seconds on average (task 52, DN05). A
  turn that fails as unavailable is never retried today: the user gets a failed or partial report, or none when
  the store is down, and must resend, which files a partial turn's expenses again
  (`ledger-service/docs/contracts/out/ai-connector.md`)
