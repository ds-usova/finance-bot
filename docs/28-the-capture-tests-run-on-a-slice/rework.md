# Rework: the capture adapter tests boot the capture adapter, not the application

**Affected Modules:** `ledger-service`
**Source:** R3 of [task 23's findings](../implemented/23-broadcast-ledger-changes-to-redis/review/findings.md)
**Baseline:** `24f25d1`

## What the code does now

| What | Where | What is wrong with it |
|------|-------|-----------------------|
| `@CdcCaptureTest`, which is `@SpringBootTest(classes = LedgerServiceApplication.class)` | `ChangeStreamReaderTest`, `ChangeStreamRecoveryTest`, `ReplicationSlotMonitorTest` | Each scenario starts the Telegram poll loop, the web layer, the MCP server, the gRPC client to the AI connector and both security chains. None of the three touches any of them. |
| One annotation serving both the adapter tests and the four system tests | `common/boot/CdcCaptureTest` | The system tests set the width, and the adapter tests inherit it. |

The three classes are classified integration-outbound, which
[Testing](../../ledger-service/docs/conventions/testing.md#test-layers) wires only the adapter under test for.

## The transaction the slice would otherwise wrap

R3's row names "the database slice", and `@DataJdbcTest` is it — but it wraps each test in a transaction and
rolls it back. A capture scenario writes a row and waits for the embedded engine, on its own replication
connection, to read it back out of the log, and nothing rolled back is ever committed. Left alone, every
capture scenario would time out against an empty stream.

`@Transactional(propagation = NOT_SUPPORTED)` on the composed annotation switches that wrapper off, so the
slice is reached without `@Commit` on every scenario. Each class cleans up after itself instead, which the
three already do — they drop their own slot.

## What must stay true

- A scenario keeps the real Redis it runs against. The outage group keeps reaching Redis through Toxiproxy, so
  cutting the connection still proves the position is not committed.
- A row written by a test is committed by the time the engine could read it. This is what the propagation
  setting buys, and it would be noticed as every capture scenario timing out with an empty stream.
- Each capture test class keeps its own slot. Two classes opening one slot against the shared Postgres
  singleton fight over it, which is why `CdcCaptureTest` leaves the slot name to the class.

## Steps

- [x] R01 · tests · a capture-adapter slice, and the slot monitor's test moves onto it
  - test-files:
    - `ledger-service/src/test/java/bot/finance/common/boot/CdcAdapterTest.java`
    - `ledger-service/src/test/java/bot/finance/common/boot/CdcAdapterContextTest.java`
    - `ledger-service/src/test/java/bot/finance/common/containers/RedisContainers.java`
    - `ledger-service/src/test/java/bot/finance/adapter/cdc/ReplicationSlotMonitorTest.java`
  - survives: a slot no engine in this JVM holds is still read and recorded · the real containerized Postgres,
    with the slot created by raw SQL
  - survives: a healthy slot kept moving by the heartbeat retains near nothing over an idle period · the real
    containerized Postgres and the application's own heartbeat
  - measures: application components this class starts and never touches — the Telegram poll loop, the web
    layer, the MCP server, the gRPC client, the security chains 5 -> 0
  - docs: `ledger-service/docs/conventions/testing.md`

- [x] R02 · tests · the recovery test moves onto the slice
  - test-files:
    - `ledger-service/src/test/java/bot/finance/adapter/cdc/ChangeStreamRecoveryTest.java`
  - needs: `CdcAdapterTest` boots a context holding the reader, the recovery and the catalogue
  - survives: an invalidated slot is rebuilt and streaming resumes at the current end of the log · a really
    invalidated slot against the real containerized Postgres
  - survives: no slot at all is rebuilt with no abandoned position · the real containerized Postgres
  - survives: a slot only behind rather than lost is refused and the engine keeps streaming · a real engine
    holding a real slot
  - survives: changes made while the slot was invalidated are never offered once streaming resumes · a really
    invalidated slot and a real engine
  - measures: application components this class starts and never touches 5 -> 0

- [x] R03 · tests · the reader test moves onto the slice
  - test-files:
    - `ledger-service/src/test/java/bot/finance/adapter/cdc/ChangeStreamReaderTest.java`
    - `ledger-service/src/test/java/bot/finance/common/boot/CdcCaptureTest.java`
  - needs: the slice's Redis template can be pointed at Toxiproxy rather than at the container directly
  - survives: a fresh slot offers only the change made after it was taken · the real engine, the real Postgres
    and the real Redis stream
  - survives: a stored position resumes streaming and every change since is offered in order · the same
  - survives: a stopped reader's position is resumed by a fresh reader · the same
  - survives: a rename reaches the stream as a category event carrying the tree's own change · the same
  - survives: an expense written after a grouping rename carries the new grouping name · the same
  - survives: a lone proposal delete shares its transaction with no expense insert · the same
  - survives: writes to uncaptured tables are never offered · the same
  - survives: only uncaptured tables written past the heartbeat interval advance the slot and offer nothing ·
    the same
  - survives: a change is held back and the position is not committed while Redis refuses · the real engine and
    a real Redis cut off through Toxiproxy
  - survives: a non-logical wal_level reports down and stops retrying · its own private Postgres container
  - survives: an absent publication reports down without taking the slot · its own private Postgres container
  - measures: application components this class starts and never touches 5 -> 0
  - measures: the three classes' own run, wall-clock 85.1 s -> 72.3 s

## Open Questions

- **Q1:** `CdcCaptureTest` stays for the four system tests, which do need the whole application. After this
  rework its javadoc describes a narrower audience than it serves today. Rewrite it as the system tests'
  annotation, or leave the wording and let the two annotations sit side by side?
  - A: Rewrite its javadoc as the annotation for a test that genuinely needs the whole application.
