# Design Log: The Ledger Stops Waiting for a Turn

## Concerns

Grilled (2026-10-14): grill-design, twice — the second pass after the outbox (DN06)

| Concern             | Verdict                                                                 | Why                                                                                 |
|---------------------|-------------------------------------------------------------------------|-------------------------------------------------------------------------------------|
| Failure modes       | a refusal of the request settles at once; anything else stays queued    | AC03, AC18, AC25 (DN06); the run outlives the accepting call (DF02)                 |
| Idempotency & retry | the hand-over and the end are retried; one run per turn in either memory setting | AC11, AC12, AC17, AC26; DN02, DN06; a restart forgets runs (DF13); a window (DF16)  |
| Concurrency         | one look at a time; settle-if-unsettled, and only the settler reports   | AC12, AC19 (DF17); the web path's guard today (`docs/domain/written-message.md`)    |
| Recovery            | a connector restart drops its runs; the sweep settles them by what is filed | AC15, AC16, AC27 (DF06, DF15)                                                   |
| Data                | one row per Telegram turn in flight, deleted on settle; a text queues as `WAITING` | DN07, DN10; text bound and day (DF20, DF21)                               |
| Contract compat     | the empty answer changes meaning; busy is a new status                  | DF08, DF09; both services ship together                                             |
| Lifecycle           | queued, then handed over; leaves once, by refusal, end or sweep         | `a-turn-awaiting-its-end.md`; DN07                                                  |
| Authorization       | the end settles only the turn of the person and message it names        | RQ07, AC22 (DF07)                                                                   |
| Security            | the only new input is one closed value; the credential stays the connector's | DF07; DN05                                                                     |
| Observability       | a swept counter and an oldest-queued-age gauge, by channel; unreported counted where a settle delivers | DN08, DN12 (DF10, DF22)                              |
| Limits              | the connector's runs capped; queued turns bounded by DN09; one web text per person | DN01, DN09 (DF18); neither module's testing conventions measure performance |
| Business invariants | a Failed report means nothing was noted; a turn runs on the day it was kept | DN06, AC20, AC27, AC31 (DF15, DF21)                                             |
| Stack-neutral       | pass, after build wiring and threads were taken out                     | DF11                                                                                |

## Findings

| #    | Question                                                       | Answer                                                                                   | Evidence                                                                                          |
|------|----------------------------------------------------------------|------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------|
| DF01 | Does a swept Telegram turn get a report? (was DN04)            | Yes: Partial if anything is under the message, else Failed                               | `ledger-service/docs/usecases/handle-incoming-message.md` ("exactly one of these"), `HandleIncomingMessageUseCase.java` |
| DF02 | Does the run survive the call that accepted it, as built?      | No; the credential and the deadline live in that call's context, so the run carries its own (AC05) | `ai-connector-service/.../adapter/grpc/CallerTokenContext.java`, `CallerTokenLedgerTurnInterceptor.java` |
| DF03 | What bounds the hand-over?                                     | A named setting, above the connector's key-set timeout, replacing the 60 s whole-turn deadline | `ledger-service/src/main/resources/application.yaml`, `ledger-service/docs/contracts/out/ai-connector.md` |
| DF04 | Is the per-call deadline of the ledger turn operations still the hand-over's? | No; each call ends at `LEDGER_TURN_TIMEOUT` or the run's bound                | `ai-connector-service/docs/contracts/out/ledger-turn.md` ("Deadlines")                            |
| DF05 | What does a Telegram batch delivered again do?                 | A message whose turn awaits is not handed over again, and an info line says so (AC17)    | `ledger-service/docs/contracts/in/telegram-updates.md` ("Failures")                               |
| DF06 | What of turns running when the connector stops?                | No end is sent; the sweep settles them after the turn's longest life                     | `docs/adr/0017-the-connector-verifies-its-caller-token-and-keeps-the-message-it-names.md`        |
| DF07 | Whose turn may an end settle?                                  | Only the one stored under both the credential's person and its message                   | `ledger-service/.../adapter/grpc/LedgerTurnGrpcService.java`, `ledger-service/docs/contracts/out/database.md` |
| DF08 | Does the runner's ledger-call count stay true?                 | Yes, with the end served as a service of its own and writing no turn call line           | `eval-runner/.../observe/TargetMetrics.java`, `ledger-service/docs/contracts/in/operations.md`   |
| DF09 | Does the tools mode reach the end's address?                   | Yes; compose already sets it, and it becomes required in both modes                      | `infrastructure/docker-compose.yaml`                                                              |
| DF10 | How are settled turns counted today?                           | A delivered report under its outcome; a swept web text not at all                        | `ledger-service/docs/contracts/in/operations.md` ("Meters")                                       |
| DF11 | What did the stack-neutral check change?                       | "Both services build from both schema files" goes to the plan; "threads" became "concurrently" | `the-turn-in-two-halves.md`                                                                |
| DF12 | Is draining in-flight runs on shutdown this design's?          | Deferred to the plan, up to the run's bound (DN03)                                       | `ai-connector-service/docs/conventions/architecture.md`                                           |
| DF13 | Does a hand-over sent again after a connector restart run twice? | It can: the connector remembers runs in flight only while it runs, so a filing may repeat until task 56 | `ai-connector-service/docs/contracts/out/ledger-turn.md` ("No call is retried"), `ledger-service/docs/contracts/in/turn-operations.md` |
| DF14 | Where does a web text queue?                                   | In its own row; it has no hand-over stamp, so `WAITING` stands for queued (DN10)         | `ledger-service/src/main/resources/db/migration/V012__*.sql`, `WrittenMessageEntityRepository.java` |
| DF15 | How long may a queued turn's last attempt still be running?    | Up to the turn's longest life, so the sweep never settles it sooner, and reads what is under it | `ledger-service/docs/contracts/in/turn-operations.md`, `ledger-service/docs/usecases/sweep-written-messages.md` |
| DF16 | Can a turn settled between the look's read and its send run again? | Yes, in that window; its filings stand. Deferred to task 56's idempotent filing     | `ai-connector-service/docs/contracts/out/ledger-turn.md` ("No call is retried")                   |
| DF17 | Where do the look and the sweep run?                           | One at a time, on the reader's schedule; a Telegram message wakes it as a filing does    | `ledger-service/.../adapter/async/WrittenMessageReader.java`                                      |
| DF18 | What does a person face filing a second text while one queues? | The 429 they get today, for as long as the first is unsettled                            | `ledger-service/docs/contracts/in/web-session-api.md`, V012's one-unsettled-per-user index         |
| DF19 | What of a Telegram turn whose person cannot be prepared?       | No catch-all: removed, unreported, as today; a store failure keeps it queued (AC28, AC29) | `ledger-service/docs/usecases/handle-incoming-message.md`, `handle-written-message.md` ("Outcomes") |
| DF20 | What bounds a kept Telegram text?                              | Telegram's 4096 UTF-16 units, checked in the persistence adapter                        | `ledger-service/docs/adr/0004-column-widths-are-checked-in-the-persistence-adapter.md`            |
| DF21 | Which day does a queued turn run on?                           | The day it was kept or filed; the caller chooses it                                      | `ai-connector-service/docs/contracts/in/intent-extraction.md` ("What is kept of a call"), `ExtractionTurn.java` |
| DF22 | Where is an undelivered report counted now?                    | Wherever a settle delivers: the end, the refusal and the sweep                           | `ledger-service/docs/contracts/in/operations.md` ("Meters"), `TelegramUpdateListener.java`         |
| DF23 | At the cap, is a hand-over for a run in flight busy?           | No: one already running is accepted whatever the cap, since it starts nothing           | `the-turn-in-two-halves.md` ("The connector's answer to a hand-over"), AC26                       |
| DF24 | Who logs a turn that ended failed or unavailable?              | The ledger, at error, when the end arrives, as it logs a failed call today; the connector adds none | `ledger-service/.../usecase/HandleIncomingMessageUseCase.java`, `HandleWrittenMessageUseCase.java` |
| DF25 | How is a queued text swept with only a period under it?        | `FAILED`, by today's text sweep rule, which counts entries only                          | `ledger-service/.../usecase/SweepWrittenMessagesUseCase.java`                                     |
| DF26 | What if recording an attempt or the acceptance fails in the store? | The failure leaves the look and is logged by the reader; the turn stays queued        | `ledger-service/.../adapter/async/WrittenMessageReader.java`, `handle-written-message.md` ("Look failed") |
| DF27 | Does a swept Telegram turn whose report fails count twice?     | Yes: swept counts the settle, unreported counts the delivery; the meters are independent | `ledger-service/docs/contracts/in/operations.md` ("Meters")                                       |

## Decision Bases

- **DN01:** The user chose a cap with a busy refusal over a queue inside the connector or no cap. The refused
  turn is not failed: it stays in the ledger's queue (DN06). Today the single Telegram collector and the single
  web reader cap it at two by accident (`TelegramUpdateListener.java`, `HandleWrittenMessageUseCase.java`).
- **DN02:** The user chose retrying the end over leaving it to the sweep. The end is idempotent (AC11, AC12), and
  "no call is retried" in `ai-connector-service/docs/contracts/out/ledger-turn.md` holds because filing is not.
- **DN03:** The user chose a connector setting over the credential's lifetime alone. The token's expiry cannot be
  read with memory off (`ai-connector-service/docs/configuration.md`, `MEMORY_ENABLED`), and the lifetime refuses
  every ledger effect after it (`ledger-service/docs/contracts/out/ai-connector.md`, "Timing").
- **DN05:** The user chose three values over two, so task 57 retries on "unavailable" without a schema change.
  The wire already tells unavailable from unknown (`ai-connector-service/docs/contracts/in/intent-extraction.md`).
- **DN06:** The user chose an outbox over settling an unknown hand-over at once or leaving it to the sweep. A
  Failed report above entries the turn later files breaks what Failed means
  (`ledger-service/docs/usecases/handle-incoming-message.md`), and the outbox already carries the ledger's facts
  (`docs/adr/0019-the-ledger-publishes-facts-through-a-transactional-outbox-rather-than-its-own-row-changes.md`).
- **DN07:** The user chose deleting the row over keeping it with a settled instant: the table holds only turns in
  flight, needs no retention, and the delete's row count is the settle-once guard. Deduplication across settled
  turns is task 56's.
- **DN08:** The user chose a new counter over a tag on `ledger_turns_total` or the warning alone. Existing meters
  are the dashboard's contract (`ledger-service/docs/contracts/in/operations.md`, "Compatibility").
- **DN09:** The user chose a setting counted from when the turn was kept over giving up after a number of failed
  attempts, under which a long busy spell would hold turns without limit. The floor since the last attempt stays,
  since that attempt may have been accepted (DF15).
- **DN10:** The user chose `WAITING` as queued over a new hand-over column: no migration, and the page shows a
  text not yet accepted as waiting. The cost is a settle that accepts `WAITING`, since the end can arrive before
  the acceptance is recorded (`ledger-service/docs/domain/written-message.md`, V012).
- **DN11:** The user chose reading the two claims with memory off over AC26 holding only with memory on. It
  amends ADR 0017's single switch for token reading
  (`docs/adr/0017-the-connector-verifies-its-caller-token-and-keeps-the-message-it-names.md`); the ledger's own
  check stays the only verification that gates an effect (`ledger-service/docs/contracts/in/turn-operations.md`).
- **DN12:** The user chose a gauge of the oldest queued turn's age over a counter of failed hand-overs: one
  threshold covers a connector down, busy or unanswering (`ledger-service/docs/contracts/in/operations.md`).
