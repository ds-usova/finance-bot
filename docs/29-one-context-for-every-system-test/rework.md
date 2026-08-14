# Rework: the system tests share one booted application

**Affected Modules:** `ledger-service`
**Source:** docs/implemented/23-broadcast-ledger-changes-to-redis/review/findings.md R1
**Baseline:** c3d2475c9c858ab7a96478b55d312b8dfcd79350

## The fix

**One booted application for all fifteen system tests.** A scenario is told apart by the data it carries, not
by a context of its own.

The chain that has to be cut, from the symptom back:

| The step                                                              | Why it holds today                                                     |
|-------------------------------------------------------------------------|--------------------------------------------------------------------------|
| Fifteen classes boot fifteen applications                              | `@DirtiesContext(AFTER_CLASS)`, plus eight distinct `telegram.bot.token` values |
| Eight classes declare a token                                          | the token is the WireMock path, so it is the only thing separating two scenarios' stubs and recordings |
| A scenario needs a stub path of its own                                | its update-bearing stub matches `offset absent()`, which every poll loop satisfies until it confirms a batch |
| That matcher is how a batch is delivered exactly once                  | nothing else expresses "once"                                          |

So the cut is at the bottom. **WireMock already has a way to say "once": a scenario that answers in state
`STARTED` and sets itself to `DELIVERED`.** It works on any poll of any loop, at any offset. Swap the matcher
for it and the whole chain falls:

- the update-bearing stub no longer needs a poll loop that has never confirmed anything (R01);
- so a scenario no longer needs a path of its own — it needs an update id, a Telegram user and a conversation
  of its own, which is data (R02);
- so the token can go back to the one the `test` profile declares (R03);
- so all fifteen classes share one configuration, and `@DirtiesContext` is what is left stopping them from
  sharing one context (R04);
- so the heap and the connection bound sized for fifteen live contexts come off (R05).

## What the code does now

| What                                                                | Where                                                            | What is wrong with it                                                                                                       |
|---------------------------------------------------------------------|-------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------|
| A batch is delivered on the poll carrying no `offset`                | `common/stubs/WireMockStubs.java:64`                             | "once" is expressed as "before this loop ever confirmed anything", which only a fresh poll loop provides                     |
| The first poll fails, keyed on the same absent `offset`              | `common/stubs/WireMockStubs.java:90`                             | the scenario already sequences the two answers; the `offset` matcher adds the fresh-loop requirement for nothing             |
| Eight classes declare `telegram.bot.token`                           | the eight `@TestPropertySource` lines in `bot.finance.system`     | a differing property is what defeats the context cache, so each buys a booted application to get a poll loop or a stub path  |
| Every poll-loop scenario uses `update_id` 42                         | six system test classes                                          | distinct only because each runs against a loop of its own                                                                    |
| `ReceiveTelegramMessage` and `ResolveProposals` both act as user 777 | `ReceiveTelegramMessageSystemTest.java:56`, `ResolveProposalsSystemTest.java:45` | `PostgresContainers` is already a JVM-wide singleton, so the two already share a database and are kept apart only by class order |
| Replies are read back as every `sendMessage` for the token           | five system test classes                                         | a turn still running when the next test starts lands in that test's journal once the token no longer separates them          |
| `@DirtiesContext(classMode = AFTER_CLASS)`                           | `common/boot/AbstractSystemTest.java:22`                         | discards a context that the next class would otherwise reuse, so identical configurations still boot once each               |
| `maxHeapSize = "2g"`                                                 | `build.gradle:151`                                               | sized for the cache holding a dozen booted applications                                                                     |
| `max_connections=300`                                                | `common/containers/PostgresContainers.java:36`                   | five pooled connections times the number of live contexts                                                                    |

## What must stay true

- A batch reaches the poll loop exactly once. A second delivery re-runs the turn, and the counts every scenario
  asserts would double.
- A scenario's own reply is what its assertions read. With one poll loop and one stub path, an unfinished turn
  from the previous test is the only thing that can add a `sendMessage` to the journal, and it would be caught
  only by the `chat_id` it carries.
- No two scenarios act as the same Telegram user. A user's first message seeds their whole category tree, and a
  user who already exists is not seeded again.
- The poll loop keeps running for the classes that never stub Telegram. It polls a path with no stub and gets
  404s, which is what happens today between tests.

## Steps

| #   | What changes                                                                | What proves it                                       |
|-----|-------------------------------------------------------------------------------|--------------------------------------------------------|
| R01 | the update-bearing stubs deliver once by scenario state, not by absent offset  | the six poll-loop system tests, still each on its own context |
| R02 | each poll-loop scenario takes its own update id, Telegram user and conversation | the six poll-loop system tests                        |
| R03 | the eight classes drop their bot token and share the profile's                 | the system tests, and the distinct-configuration count |
| R04 | the full-application context is no longer discarded after each class           | the module's suite, and the count of booted applications |
| R05 | the container's connection bound is dropped and the test heap is halved        | the module's suite                                    |

- [x] R01 · tests · the two update-bearing Telegram stubs deliver once through a WireMock scenario
  - test-files:
    - `ledger-service/src/test/java/bot/finance/common/stubs/WireMockStubs.java`
    - `ledger-service/src/test/java/bot/finance/system/ReceiveTelegramMessageSystemTest.java`
    - `ledger-service/src/test/java/bot/finance/system/SummarizeSpendingReplySystemTest.java`
    - `ledger-service/src/test/java/bot/finance/system/ResolveProposalsSystemTest.java`
    - `ledger-service/src/test/java/bot/finance/system/ResolveUnknownProposalsSystemTest.java`
    - `ledger-service/src/test/java/bot/finance/system/HandleIncomingMessageFailureSystemTest.java`
    - `ledger-service/src/test/java/bot/finance/adapter/telegram/TelegramUpdateListenerTest.java`
  - survives: a batch is delivered to the poll loop exactly once · `ReceiveTelegramMessageSystemTest`, `SummarizeSpendingReplySystemTest`, `ResolveProposalsSystemTest`, `ResolveUnknownProposalsSystemTest`, `HandleIncomingMessageFailureSystemTest`
  - survives: a failed poll is followed by a good one carrying the same batch · `TelegramPollFailureRecoverySystemTest`
  - measures: system test classes whose delivery depends on a virgin poll loop 6 -> 0

- [x] R02 · tests · every poll-loop scenario is identified by its own data rather than by its own context
  - test-files:
    - `ledger-service/src/test/java/bot/finance/common/stubs/TelegramTestBot.java`
    - `ledger-service/src/test/java/bot/finance/system/ReceiveTelegramMessageSystemTest.java`
    - `ledger-service/src/test/java/bot/finance/system/SummarizeSpendingReplySystemTest.java`
    - `ledger-service/src/test/java/bot/finance/system/ResolveProposalsSystemTest.java`
    - `ledger-service/src/test/java/bot/finance/system/ResolveUnknownProposalsSystemTest.java`
    - `ledger-service/src/test/java/bot/finance/system/HandleIncomingMessageFailureSystemTest.java`
    - `ledger-service/src/test/java/bot/finance/system/TelegramPollFailureRecoverySystemTest.java`
    - `ledger-service/src/test/java/bot/finance/common/fixtures/TelegramFixtures.java`
  - survives: a text message is turned into a reported proposal · `ReceiveTelegramMessageSystemTest`
  - survives: a spending question is answered in the chat · `SummarizeSpendingReplySystemTest`
  - survives: an accept tap records the proposals and acknowledges the tap · `ResolveProposalsSystemTest`
  - survives: a tap naming an unknown report is answered without recording anything · `ResolveUnknownProposalsSystemTest`
  - survives: a failed extraction still reports back and confirms the batch · `HandleIncomingMessageFailureSystemTest`
  - survives: a failed poll costs the user nothing · `TelegramPollFailureRecoverySystemTest`
  - needs: a scenario is delivered once whatever the loop's offset already is
  - measures: poll-loop scenarios sharing a Telegram user with another scenario 2 -> 0

- [x] R03 · tests · the eight classes declaring a bot token take the test profile's own
  - test-files:
    - `ledger-service/src/test/java/bot/finance/common/stubs/TelegramTestBot.java`
    - `ledger-service/src/test/java/bot/finance/system/ReceiveTelegramMessageSystemTest.java`
    - `ledger-service/src/test/java/bot/finance/system/SummarizeSpendingReplySystemTest.java`
    - `ledger-service/src/test/java/bot/finance/system/ResolveProposalsSystemTest.java`
    - `ledger-service/src/test/java/bot/finance/system/ResolveUnknownProposalsSystemTest.java`
    - `ledger-service/src/test/java/bot/finance/system/HandleIncomingMessageFailureSystemTest.java`
    - `ledger-service/src/test/java/bot/finance/system/TelegramPollFailureRecoverySystemTest.java`
    - `ledger-service/src/test/java/bot/finance/system/AcceptExpensesSystemTest.java`
    - `ledger-service/src/test/java/bot/finance/system/WebSessionSystemTest.java`
  - survives: a cleared report has its buttons taken off · `AcceptExpensesSystemTest`
  - survives: a Login Widget payload signed with the configured token is accepted · `WebSessionSystemTest`
  - survives: a payload signed with another token is refused · `WebSessionSystemTest`
  - needs: each scenario's delivery and each scenario's assertions no longer depend on owning a stub path
  - measures: distinct full-application context configurations among the system tests 15 -> 1
  - docs: `ledger-service/docs/conventions/testing.md`

- [x] R04 · pin · the full-application context survives the class that used it
  - test-files:
    - `ledger-service/src/test/java/bot/finance/common/boot/AbstractSystemTest.java`
  - needs: the fifteen classes share one configuration, so the cache has something to hand back
  - proves: nothing to undo — the claim is that the suite is green without it, 1164 passed either way, and the system tests fell from 200.6s to 82.6s with the whole suite at 318.6s -> 204.8s

- [x] R05 · pin · the container's connection bound is dropped and the test heap is halved
  - test-files:
    - `ledger-service/build.gradle`
    - `ledger-service/src/test/java/bot/finance/common/containers/PostgresContainers.java`
  - needs: the suite no longer holds fifteen booted applications
  - proves: nothing to undo — the claim is that the suite is green without them, 1164 passed with `max_connections` back at the server's default and `maxHeapSize` at `1g`. Dropping the heap setting altogether was tried first and refused: the run died part-way with `OutOfMemoryError: Java heap space` at 1146 of 1164 tests, so Gradle's default worker heap is below what the remaining contexts need.

## Open Questions

- **Q1:** R05 drops two settings the finding calls symptoms. Six contexts remain after R04 — four capture
  classes with a slot each, and `CaptureDisabledSystemTest`'s two — so the suite still holds more than one
  booted application. Drop `maxHeapSize` and `max_connections` entirely, lower them to a value that reflects
  six contexts, or leave them alone and report the headroom?
  - A: Drop both entirely — Gradle's default worker heap and the container's default `max_connections`. A red
    run reverts the step and reports what the suite actually needs.
- **Q2:** The four `@CdcCaptureTest` classes and `CaptureDisabledSystemTest` keep a context each, and this
  rework does not touch them. Leave them out and report them as remaining, or widen R04 to look at whether the
  four capture classes can share one slot?
  - A: Leave them out. A slot name against the shared Postgres singleton is a real constraint rather than an
    isolation trick, so they are reported as what remains open.
- **Q3:** `TelegramTestBot`'s javadoc and the **Isolating the long-polling listener** section of
  `ledger-service/docs/conventions/testing.md` both state that a class isolates itself with its own bot token.
  R03 makes that false. Rewrite the section to say a scenario is isolated by its update id and its Telegram
  user, or record the change as an ADR as well?
  - A: Rewrite the section, and no ADR. This reverses a convention rather than settling a new decision.
