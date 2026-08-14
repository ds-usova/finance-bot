# Rework: a category event refreshes the cached row it names

**Affected Modules:** `ledger-service`
**Source:** `docs/implemented/24-evict-one-category-name-at-a-time/review/findings.md`, candidates R1 and R3
**Baseline:** `d44d792`, measured VERIFIED by `review/evidence.md` in that same rework

## What the code does now

| What                                                                | Where                                        | What is wrong with it                                                                              |
|---------------------------------------------------------------------|----------------------------------------------|------------------------------------------------------------------------------------------------------|
| A `category` event calls `evict(id)` and the next resolve re-reads it | `adapter/cdc/ChangeEventPublisher.java:47`   | The event already carries the committed row. The read that follows fetches what the payload just delivered. |
| `CategoryNameReader` answers a row, not a name                      | `adapter/persistence/CategoryNameReader.java` | It answers one `category` row for a grouping and a category alike, so its name states a role it stopped having. |

The publisher already reads `after` and `before` off the payload to find the id. `REPLICA IDENTITY FULL` puts the
whole row on both sides, so the name and the parent id are there beside it.

## What must stay true

- A cached row is never older than one a read would answer. Events reach the publisher in commit order on one
  thread, so a refresh from the payload cannot move the cache backwards. Broken, a consumer sees a name that was
  already superseded, and nothing corrects it until the next event for that row.
- A deleted category leaves nothing cached under its id. Broken, a category created later under a reused id
  answers the deleted one's name.
- An id no row carries is still cached as absent, which `CategoryRowCacheTest` asserts. Broken, a run of change
  events for a deleted category reads the database once per event.
- The cache never holds more than `cdc.category-cache-size` entries. Broken, it shows as heap growth under a
  large category tree.

## Steps

- [x] R01 · stabilize · `CategoryNameReader` becomes `CategoryRowReader`
  - files:
    - `ledger-service/src/main/java/bot/finance/adapter/persistence/CategoryNameReader.java`
    - `ledger-service/src/main/java/bot/finance/adapter/persistence/CategoryRowReader.java`
    - `ledger-service/src/main/java/bot/finance/adapter/cdc/CategoryNameResolver.java`
  - test-files:
    - `ledger-service/src/test/java/bot/finance/adapter/persistence/CategoryNameReaderTest.java`
    - `ledger-service/src/test/java/bot/finance/adapter/persistence/CategoryRowReaderTest.java`
    - `ledger-service/src/test/java/bot/finance/adapter/cdc/CategoryNameResolverTest.java`
  - docs: none — the class is internal to the module and no page names it

- [x] R02 · behaviour · a category insert or update writes the row from the payload, and only a delete drops it
  - files:
    - `ledger-service/src/main/java/bot/finance/adapter/cdc/ChangeEventPublisher.java`
    - `ledger-service/src/main/java/bot/finance/adapter/cdc/CategoryNameResolver.java`
  - test-files:
    - `ledger-service/src/test/java/bot/finance/adapter/cdc/ChangeEventPublisherTest.java`
    - `ledger-service/src/test/java/bot/finance/adapter/cdc/CategoryNameResolverTest.java`
  - runs: `bot.finance.adapter.cdc.ChangeEventPublisherTest`,
    `bot.finance.adapter.cdc.CategoryNameResolverTest`
  - now: every `category` event drops the row under its id, and the next resolve of it reads the database
  - then: an event with an `after` side writes that row into the cache under its id; an event without one drops
    it, and no read follows either
  - needs: the cache is keyed by the row a change event names, which rework 24 made true
  - docs: none — the staleness `contracts/out/change-stream.md` bounds only narrows

- [x] R03 · pin · a capture test follows a rename to the name a consumer reads off the stream
  - test-files:
    - `ledger-service/src/test/java/bot/finance/adapter/cdc/ChangeStreamReaderTest.java`
  - needs: a category event writes the payload's row, which R02 makes true — before it the same scenario passes
    through the re-read instead, and pins nothing about the refresh
  - proves: `refresh` inverted to keep the entry it already holds rather than replace it, and the test read the
    pre-rename grouping name off the stream — `expected: "Household 2358c39e" but was: "Groceries 165f93ff"`

## Open Questions

- **Q1:** Does a refresh write a row the cache does not already hold, or only replace one it does? Writing
  unconditionally warms the cache from the stream, so a category nobody enriches occupies an entry and evicts one
  that is being read. Replacing only what is held keeps the bound meaning what it means today.
  - A: Only replace a row the cache already holds. An id nothing has resolved stays out of it, so the bound and
    the LRU order keep being driven by reads.
- **Q2:** Candidate R3 in rework 24 was that nothing follows a rename through capture to the enrichment a
  consumer reads. `ChangeStreamReaderTest` boots the application with capture on and already drives a rename.
  Close that gap here, or leave it open?
  - A: Close it here, as R03. It is the only test that catches a refresh writing the wrong row.
