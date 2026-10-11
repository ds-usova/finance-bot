# Design Log: File Expenses as Text from the Web

## Concerns

Grilled (2026-10-01): grill-design, grill-frontend, each twice.

| Concern             | Verdict                                                           | Why                                                                                                 |
|---------------------|-------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------|
| Failure modes       | every branch ends in an outcome; 429 only from its own index      | DF01; the sweep settles the rest (DN07); a unique violation is classified, not a 503 (DF30)         |
| Idempotency & retry | a refiled text is a second text                                   | DN11; double submission is closed by the held line and DN15's one-at-a-time index                   |
| Concurrency         | one reader per text; the sweep never overtakes a live turn        | DF03; the life is timed from the stamped hand-over, not the claim (DF28); writes only over `READING` |
| Recovery            | waiting texts are read after a restart; reading ones are swept    | DN07, DN20, AC20 to AC22, AC33 to AC35                                                              |
| Data                | new table, constrained shape                                      | DF04; length counted in UTF-16 code units on both sides (DF32)                                      |
| Contract compat     | additive                                                          | a new tag and three new operations; no existing schema changes                                      |
| Lifecycle           | rows removed only with their user                                 | DF02 cascade; retention is deferred (DF10)                                                          |
| Authorization       | every read scoped to the caller, 404 either way                   | AC11, AC12; the new paths join the filter chain (DF05); the turn runs as the stored person (DF02)   |
| Security            | text bounded and escaped                                          | DN09; it reaches the model as a Telegram text does, under the person's own credential               |
| Observability       | a line at claim and settle, a warning per swept text              | DF08, DF31; a failed pass never stops the reader (DF29); the gauge is deferred (DF11)               |
| Limits              | one unsettled text per person, 1000 code units each               | DN15, DN09; the ledger's tests measure no performance (DF27)                                        |
| Business invariants | one text, one turn, one outcome that agrees with its days         | DN20; the store checks `outcome` against `DONE`; `web:<id>` never collides (DF09)                   |
| Empty & extreme     | bounded height, clamped reading line, unbroken strings wrap       | DN17, DF12                                                                                          |
| Default state       | the line held until the load-time ask answers; days opened        | DN21, DN13; days render collapsed today (DF15)                                                      |
| Layout stability    | the hint row always stands; one item beneath, replaced on filing  | DF13, DF33; the jump of a reading line arriving on load is checked by eye                           |
| Consistency         | the shipped field, day header and tick, not the mockup's          | DF14, DF15                                                                                          |
| Colour system       | refusal red only for refusals; notes in pencil                    | DN04, DN24, DF16                                                                                    |
| Motion              | the reading mark stands still under reduced motion                | DF17                                                                                                |
| Third-party UI      | none added                                                        | DF18                                                                                                |
| Library reach       | no package added                                                  | a native multi-line field; growth to 8 lines is the plan's mechanism (DN17)                         |
| Input & locale      | days named by the ledger in UTC; Enter respects composition       | DN12, DF20, DF32                                                                                    |
| Person's state      | reload and 429 resume; transient failures keep asking; 404 ends   | DN08, DN21, DN22, AC29, AC30; a lost draft on 401 is deferred (DF19)                                |
| Reachability        | focus stays in the held line; hint hidden on touch; outcome told  | DN23, DN18, DF21                                                                                    |
| Stack-neutral       | pass                                                              | the artifacts name paths, fields, statuses, columns and SQL semantics only                          |

## Findings

| #    | Question                                                   | Answer                                                                         | Evidence                                                                                              |
|------|------------------------------------------------------------|--------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------|
| DF01 | The turn throws before or after the connector?             | Before: `FAILED`. After: left `READING` for the sweep                           | `ledger-service/src/main/java/bot/finance/application/usecase/HandleIncomingMessageUseCase.java`      |
| DF02 | How does the reader resolve the person?                    | By the stored id; the row cascades with its user                               | `ledger-service/src/main/java/bot/finance/adapter/persistence/UserRepository.java`, `V011` FK         |
| DF03 | How is a text claimed, and where does the reader run?      | One claim statement skipping locked rows; its own work, not report clearing     | `IncomingMessageEntityRepository.java` `claimUnembedded`, `ReportClearingConfiguration.java`          |
| DF04 | The stored shape's constraints?                            | status CHECK; outcome present iff DONE; times from the injected clock           | `ledger-service/src/main/resources/db/migration/V009__merge_expense_proposal_into_expense.sql`        |
| DF05 | Does an authenticated call reach the new paths?            | Only once listed; the chain denies the rest                                     | `ledger-service/src/main/java/bot/finance/adapter/security/SecurityConfiguration.java`               |
| DF06 | Asking answers 404?                                        | The reading ends as `FAILED` does                                               | `rest-api-update.md`: a 404 never turns into anything else                                            |
| DF07 | Does a question-only turn store anything?                  | Its periods, then discards them; no pending entry                               | `HandleIncomingMessageUseCase.java` `discardReportedPeriods`                                         |
| DF08 | What proves a web turn ran?                                | A line at claim and at settle naming `web:<id>` and the person; the turn meter counts it | `ledger-service/docs/conventions/code-style.md`, `ledger-service/docs/contracts/in/operations.md` |
| DF09 | Does `web:<id>` fit an incoming message id?                | Yes, under 56 bytes; the domain page is reworded to cover it                    | `ledger-service/docs/domain/incoming-message-id.md`, ADR 0015                                         |
| DF10 | What removes a settled row?                                | Nothing yet; returns with a history view or a retention rule                    | `ai-connector-service/docs/usecases/purge-messages.md`, the precedent                                 |
| DF11 | A gauge for the oldest unsettled text?                     | Deferred; returns if the sweep is ever seen to lag                              | `ledger-service/docs/contracts/in/operations.md`                                                      |
| DF12 | An unbroken string in the reading line?                    | Wraps anywhere                                                                  | `web-app/src/components/ExpenseDaySection.tsx`, which truncates only one-line cells                   |
| DF13 | Does the hint row stay while nothing can be filed?         | Yes, its control disabled                                                       | `web-app/src/components/ExpenseFilters.tsx`, a row that comes and goes moves every control under it   |
| DF14 | Focus, size and padding of the field?                      | As the shipped field: underline turns focus colour, 16px on a phone             | `web-app/src/components/ui/input.tsx`, `DESIGN.md` "Inputs / Fields"                                  |
| DF15 | The mockup's day rule and tick?                            | Not used; the shipped header and tick stand                                     | `web-app/src/components/ExpenseDaySection.tsx`, `web-app/src/components/expenseDays.ts`               |
| DF16 | Ink of the partial and failed notes?                       | Partial in pencil; failed as the refusal banner                                 | `web-app/src/pages/ExpensesPage.tsx`, `web-app/src/components/ErrorBanner.tsx`                        |
| DF17 | The reading mark under reduced motion?                     | Still; the global rule does not stop an infinite animation                      | `web-app/src/styles.css`                                                                              |
| DF18 | What does the module not style?                            | The resize grip and the phone's return key                                      | `web-app/package.json`, unchanged                                                                     |
| DF19 | A typed text when filing answers 401?                      | Lost, as every form's; a draft store returns it                                 | `web-app/src/pages/ExpensesPage.tsx` `report`                                                         |
| DF20 | Enter while an input method composes?                      | Confirms the composition, never files                                           | `the-write-in-line.md`, the field table                                                               |
| DF21 | How does assistive technology learn the outcome?           | A polite status region                                                          | `web-app/src/auth/RequireAuth.tsx`, the one `role="status"`                                           |
| DF22 | A day the answer names but the filter excludes?            | Not merged in                                                                   | `web-app/src/pages/ExpensesPage.tsx` `readSpan`, read under the filter on screen                      |
| DF23 | Outcome arriving with no listing on screen?                | Nothing to merge; the note still shows                                          | `web-app/src/pages/ExpensesPage.tsx` `rereadTouchedDays`                                              |
| DF24 | A refiled text after a lost 202?                           | A second turn; an idempotency key returns it                                    | `docs/backlog.md` BT02                                                                                |
| DF25 | Discarding a wrong pending entry from the page?            | Not possible yet; a later task adds it                                          | `docs/backlog.md` BT01                                                                                |
| DF26 | Totals for a text that only asks?                          | Not shown; a later change shows them                                            | `docs/backlog.md` BT03                                                                                |
| DF27 | Is a latency figure owed?                                  | No; the module's tests measure none                                             | `ledger-service/docs/conventions/testing.md`                                                          |
| DF28 | When has a turn's longest life passed?                     | Credential life plus validator leeway plus margin, from the stamped hand-over   | `SecurityConfiguration.java` `JwtTimestampValidator`, `application.yaml` `mcp.token.ttl`              |
| DF29 | A pass of the reader that throws?                          | Caught and logged; the next interval runs                                       | `ledger-service/src/main/java/bot/finance/adapter/cdc/ReplicationSlotMonitor.java` `readSlotSafely`   |
| DF30 | How does the one-at-a-time index become 429?               | Its violation is classified by name, as foreign-key violations are              | `ledger-service/src/main/java/bot/finance/adapter/persistence/ForeignKeyViolations.java`              |
| DF31 | What records a swept text?                                 | A warning naming `web:<id>`, the person and the outcome                         | `ledger-service/docs/conventions/code-style.md`                                                       |
| DF32 | What does one character of DN09 count?                     | A UTF-16 code unit, in the field and the ledger alike                           | ADR 0004, `HandleIncomingMessageCommand.java`                                                         |
| DF33 | When does a note beneath the line clear?                   | The next filing replaces it                                                     | `web-app/src/pages/ExpensesPage.tsx`, the acceptance's `missingMessage`                               |
| DF34 | Which days can a merged day land on?                       | One on the page, or any the first page's filter includes                        | `web-app/src/components/expenseDays.ts` `mergeDay`, which ignores the offset                          |
| DF35 | Does asking for the texts being read ever answer 404?      | When the session outlives its user, as on every browse path; the line opens    | `ledger-service/src/main/java/bot/finance/adapter/web/WebExceptionHandler.java` `onEntityNotFound`    |
| DF36 | How does an unsettled answer carry `outcome` and `days`?   | Both present: `outcome` null, `days` empty, as the generated server writes them | `openapi/ledger-api.yaml` `Expense.merchant`, present and nullable; chosen by the user, 2026-10-02    |

## Decision Bases

- **DN01:** The user proposed asking every 5 seconds over a push channel; nothing reaches the browser but its own
  requests today (`web-app/src/api/client.ts`), and the ledger's change stream feeds the connector alone
  (`ledger-service/src/main/java/bot/finance/adapter/redis/RedisChangeStreamWriter.java`).
- **DN02:** The user chose storing and answering at once over holding the request through the turn; one turn may
  take as long as `spring.grpc.client.channel.ai-connector.default.deadline`
  (`ledger-service/src/main/resources/application.yaml`), and `HandleIncomingMessageUseCase` runs it in one piece.
- **DN03:** The user chose an inline line under the filters over one above them and over a dialog; the mockup
  draws it inline (`mockup.html`), and the page renders filters then days (`web-app/src/pages/ExpensesPage.tsx`).
- **DN04:** The user chose returning the text to the line with a pencil note over a dismissible line on the page;
  the connector answers no reason (`proto/intent_extraction.proto`), so the mockup's reason has nothing behind it,
  and red is kept for refusals (`DESIGN.md`).
- **DN05:** The user chose treating a question like nothing found over showing totals, which would add a field and
  a view; Telegram renders them today (`ledger-service/.../TurnReportRenderer.java`).
- **DN06:** The user chose no Telegram report over sending one; the request names the page as a replacement for
  the chat, and every report today goes through `MessageDeliveryPort`, implemented only for Telegram.
- **DN07:** The user chose settling by what was stored over reading again, which may store spending twice since
  `expense` has no unique key on `incoming_message_id`, and over failing outright, which asks a refile of what was
  already stored. The credential's life bounds every tool call (`mcp.token.ttl`, `application.yaml`).
- **DN08:** The user chose resuming a text being read after a reload over losing it, and chose not to keep an
  unseen outcome, which would need a seen mark; entries found are in the listing either way.
- **DN09:** The user chose 1000 characters over 4096, Telegram's limit, and 500; nothing in the repository bounds a
  message's length (`HandleIncomingMessageCommand.java` checks only that it is not blank).
- **DN10:** The user chose a separate task over adding discard here and over none at all; the browser API has no
  discard (`openapi/ledger-api.yaml`), and Delete exists only under a Telegram report, which DN06 rules out.
- **DN11:** The user chose no idempotency key over a client-sent one; the page never retries on its own
  (`web-app/src/api/client.ts`), so only a manual refile after a lost answer duplicates.
- **DN12:** The user chose the answer naming the days over the page reading a span from filing to now; entries
  are stamped when stored, which may cross UTC midnight (`web-app/src/components/expenseDays.ts`).
- **DN13:** The user chose opening the day without ticking over the mockup's ticked entries and over leaving the
  day as it is; the page never pre-ticks (`web-app/src/pages/ExpensesPage.tsx`), and every day renders collapsed
  (`ExpenseDaySection.tsx`).
- **DN14:** The user chose one text at a time over stacked reading lines; nothing in the page tracks several
  calls at once.
- **DN15:** The user chose one over five and over no cap; texts are read one after another, so one person's
  backlog delays everyone's, and nothing in the repository caps it.
- **DN16:** The user chose disabling the whole line over disabling only its control, which would have let a
  returning text meet a new one; with DN14 and DN15 the line holds one text at a time.
- **DN17:** The user chose growing from 3 to 8 lines with a 3-line reading line over fixed heights;
  `web-app/src/components/ui/` holds no multi-line field to follow.
- **DN18:** The user chose hiding the hint on touch devices over showing it everywhere; a touch keyboard has no
  Shift+Enter.
- **DN19:** The user accepted the proposed wording over writing their own; `web-app/src/i18n/en.ts` holds no key
  for any of it, and the existing refusal key names the listing, not filing (`web-app/src/pages/errorText.ts`).
- **DN20:** The user chose counting accepted entries over counting pending ones only; the Telegram read-back
  filters `PENDING` (`ExpenseEntityRepository.java`), so an acceptance from another tab would read as nothing found
  and invite a duplicate refile.
- **DN21:** The user chose holding the line until the load-time ask answers over leaving it editable, which would
  strand a draft in a held line; DN15's index catches a wrong guess after a failed ask.
- **DN22:** The user chose resuming the text behind a 429 over only saying so, which would leave nothing to watch
  until a reload.
- **DN23:** The user chose a read-only, focusable line over a disabled one with focus restored later; a disabled
  field drops focus to the document, and the page's only focus move is to a day header
  (`web-app/src/components/ExpenseDaySection.tsx`).
- **DN24:** The user chose a pencil note over resetting the filter and the page; the page already reads back
  under the filter on screen (`web-app/src/pages/ExpensesPage.tsx` `readSpan`).
- **DN25:** The user chose returning the line quietly over a refusal saying a text is already being read; DN22's
  ask either finds the text or finds it already ended, so the wording had no case left to name.
- **DN26:** The user chose the incomplete-list note over the out-of-view one; only one note stands beneath the
  line (`the-write-in-line.md`, Order on the page), and the partial reading is the one a refile may answer.
- **DN27:** The user chose keeping the person's new text over replacing it with the returned one; the line holds
  one text (DN16), and the returned text is already the other tab's.

## Caveats

- **CV01:** `design.sh` refused at design-task's first `validate` — the project hook does not allow the plugin
  path, and the allowlisted `C:/Users/rieic/Projects/tdd-sdlc/scripts/design/design.sh` is absent. `validate`,
  `settled` and `approved` were answered by reading the files; no `**Approved:**` line is written.
