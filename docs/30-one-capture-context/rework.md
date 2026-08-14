# Rework: the capture tests share one engine

**Affected Modules:** `ledger-service`
**Source:** the session that finished docs/implemented/29-one-context-for-every-system-test
**Baseline:** 14dc70046ba37bd41359681a2bb53d13592564e9

## The fix

**One booted application for all five capture classes**, on the slot and stream key the application defaults to.

Rework 29 left the capture classes alone. Each declares its own `cdc.slot-name` and `cdc.stream-key`, and each
differing property is a context.

The slot name looks load-bearing and is circular. Postgres allows one active consumer per slot, so two live
engines collide. They are both live only because the properties differ. One context is one engine on one slot,
which is the shape production runs in.

The stream key is the trick the bot token was. Every capture class creates its own user and reads back through
`ChangeStreamEntries.entriesOnFor(key, table, userId)`, so the user already separates their assertions. The
javadoc there claims otherwise, on a collision only `CaptureDisabledSystemTest.HappyPath` can cause: it boots a
database of its own and mints `user_id` values another class published under. It publishes nothing, and keeps a
key of its own for that reason.

## What the code does now

| What                                                       | Where                                                                     | What is wrong with it                                                                               |
|------------------------------------------------------------|---------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------|
| `cdc.slot-name` per class                                  | the four `@TestPropertySource` lines, and `CdcCaptureContextTest.java:15` | keeps five engines apart that only need keeping apart because they are five                         |
| `cdc.stream-key` per class                                 | the four `@TestPropertySource` lines                                      | the user id already separates what each class reads back, and every one of them filters by it       |
| `cdc.recovery-secret=recover-slot-system-test-secret`      | `RecoverSlotSystemTest.java:37`                                           | `application-test.yaml` already gives every context a secret, and a differing property is a context |
| "a class needing its own slot declares its own …"          | `CdcCaptureTest.java:28`                                                  | states the per-class slot as the rule rather than the exception it now is                           |
| "Every capture test gives itself its own `cdc.stream-key`" | `ChangeStreamEntries.java:24`                                             | true of the class with its own database, wrong about the four sharing one                           |
| `@CdcCaptureTest` plus one slot override                   | `CdcCaptureContextTest.java:15`                                           | a booted application to prove the annotation boots, when the context the four share proves it       |

## What must stay true

- The engine is `STREAMING` when a capture class starts. Only `RecoverSlotSystemTest` leaves it otherwise,
  driving the slot to `lost` on purpose. It would be noticed as every later class's await timing out.
- A recovered slot resumes from the current write-ahead position, so a change written before the recovery and
  not yet published is never captured. Inside one context that window belongs to the class that opened it. It
  would be noticed as a missing entry in a class that made its change before another class's recovery.
- Each class reads back only its own user's entries. They share one stream once this lands, so the user id is
  the only thing keeping one class's assertions off another's rows.
- Cutting Redis at the Toxiproxy singleton cuts it for the one engine every capture class shares. Three classes
  do it and each restores it. A restore that stopped happening would be noticed as every later class reading
  `DOWN`.

## Steps

| #   | What changes                                                            | What proves it                              |
|-----|-------------------------------------------------------------------------|---------------------------------------------|
| R01 | three capture classes fall back to the application's own slot and stream | the three classes, on one context           |
| R02 | the recovery class joins them, on the profile's own secret               | `RecoverSlotSystemTest` and the other three |
| R03 | the annotation's own context test joins them                            | `CdcCaptureContextTest`                     |

- [x] R01 · tests · three capture classes drop their slot and stream overrides
  - test-files:
    - `ledger-service/src/test/java/bot/finance/common/boot/CdcCaptureTest.java`
    - `ledger-service/src/test/java/bot/finance/common/fixtures/ChangeStreamEntries.java`
    - `ledger-service/src/test/java/bot/finance/system/BroadcastLedgerChangesSystemTest.java`
    - `ledger-service/src/test/java/bot/finance/system/AcceptedProposalChangeStreamSystemTest.java`
    - `ledger-service/src/test/java/bot/finance/system/ChangeStreamMetersSystemTest.java`
  - survives: a refiled expense reaches the stream naming both categories · `BroadcastLedgerChangesSystemTest`
  - survives: nothing publishes while Redis refuses, and the held change lands once it returns · `BroadcastLedgerChangesSystemTest`
  - survives: an accepted proposal's delete and insert share one transaction id · `AcceptedProposalChangeStreamSystemTest`
  - survives: the scrape carries the published, failure, lag, slot and state meters · `ChangeStreamMetersSystemTest`
  - survives: the scrape is refused on the service port · `ChangeStreamMetersSystemTest`
  - measures: booted applications behind these three classes 3 -> 1
  - docs: `ledger-service/docs/conventions/testing.md`

- [x] R02 · tests · the recovery class takes the shared slot and the profile's own secret
  - test-files:
    - `ledger-service/src/test/java/bot/finance/system/RecoverSlotSystemTest.java`
  - survives: an invalidated slot is recovered and streaming resumes · `RecoverSlotSystemTest`
  - survives: health and metrics answer on the management port with no secret · `RecoverSlotSystemTest`
  - needs: the three classes already run on the application's default slot
  - measures: booted applications behind the four capture classes 2 -> 1

- [ ] R03 · tests · the annotation's own context test stops booting an application of its own
  - test-files:
    - `ledger-service/src/test/java/bot/finance/common/boot/CdcCaptureContextTest.java`
  - survives: `@CdcCaptureTest` boots a context whose engine and Redis wiring come up together · `CdcCaptureContextTest`
  - needs: the capture classes share one context
  - measures: booted applications carrying `@CdcCaptureTest` 2 -> 1

## Open Questions

- **Q1:** `RecoverSlotSystemTest` destroys and repairs the slot the other three now share. JUnit's class order is
  deterministic but nobody chose it, so which classes run before the repair is accidental. Pin the capture
  classes' order with a JUnit `ClassOrderer`, `RecoverSlotSystemTest` last, or leave the order alone?
  - A: Leave the order alone. The repair is the test's own last assertion, and an orderer would hide a repair
    that stopped working behind a rule no one sees when reading a test.
- **Q2:** `CaptureDisabledSystemTest` keeps two contexts. `HappyPath` needs a `wal_level=replica` container of
  its own. `UnhappyPath` needs `cdc.enabled=false` plus a `cdc.slot-name` no engine opens, which is what its
  gauge assertion reads, so it can join neither the capture context nor the base one. Leave both, or widen this
  rework to `UnhappyPath`?
  - A: Leave both. Each holds a real constraint rather than an isolation trick.
