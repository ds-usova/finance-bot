# Rework: the name cache holds one category row per id

**Affected Modules:** `ledger-service`
**Source:** `docs/implemented/23-broadcast-ledger-changes-to-redis/review/findings.md`, refactoring candidate R2
**Baseline:** `ce931f8`

## What the code does now

| What                                                                    | Where                                                 | What is wrong with it                                                                                       |
|-------------------------------------------------------------------------|-------------------------------------------------------|---------------------------------------------------------------------------------------------------------------|
| `evict(long categoryId)` calls `cache.clear()` and ignores its argument | `adapter/cdc/CategoryNameResolver.java:55`            | Empties up to `cdc.category-cache-size` entries. Every event after it re-reads the database.                 |
| A cached entry is the pair of names, keyed by the category's id         | `adapter/cdc/CategoryNameResolver.java:21`            | A grouping's name is copied into every entry under it. No entry is addressable by the row a rename changed.  |
| `findCategoryNames` joins a category to its parent                      | `adapter/persistence/CategoryEntityRepository.java:54` | The join is what fuses two rows into one cache entry.                                                       |

The `category` table holds groupings (`parent_id IS NULL`) and categories alike, so one CDC event on that table
is either.

## What must stay true

- A resolve after an evict of the same id reads the database again. Broken, a renamed category keeps its old
  name on every later change entry, which a consumer of the change stream reads directly.
- A rename of a grouping reaches the categories under it. Broken, a consumer sees the old grouping name on
  entries for categories nothing touched.
- An id no row carries is still cached as absent. Broken, a run of change events for a deleted category reads
  the database once per event.
- The cache never holds more than `cdc.category-cache-size` entries, which `CategoryNameResolverTest` asserts.
  Broken, it shows as heap growth under a large category tree.
- `resolve` and `evict` stay `synchronized`. One thread reaches them: Debezium's task pool, sized by
  `tasks.max`, which the Postgres connector leaves at one. The lock is a memory barrier rather than mutual
  exclusion, since a restarted engine hands the surviving cache to a different thread. Broken, a category
  renamed before a restart still answers its old name after it.

## Steps

**R01 shows no red run.** The reader's answer changes shape and meaning together, so a test for what it answers
afterwards cannot compile against the signature it has now. Its guardrail is the reader's rewritten scenarios
and the module's whole suite. `CategoryNames`, `ChangeEventPublisher` and the capture tests are untouched, which
is the behaviour-preserved claim.

- [x] R01 · behaviour · the cache holds one category row per id, and a category's two names come from two lookups
  - files:
    - `ledger-service/src/main/java/bot/finance/adapter/persistence/CategoryEntityRepository.java`
    - `ledger-service/src/main/java/bot/finance/adapter/persistence/CategoryNamesProjection.java`
    - `ledger-service/src/main/java/bot/finance/adapter/persistence/CategoryRowProjection.java`
    - `ledger-service/src/main/java/bot/finance/adapter/persistence/CategoryNameReader.java`
    - `ledger-service/src/main/java/bot/finance/adapter/cdc/CategoryRow.java`
    - `ledger-service/src/main/java/bot/finance/adapter/cdc/CategoryNameResolver.java`
  - test-files:
    - `ledger-service/src/test/java/bot/finance/adapter/persistence/CategoryNameReaderTest.java`
    - `ledger-service/src/test/java/bot/finance/adapter/cdc/CategoryNameResolverTest.java`
  - runs: `bot.finance.adapter.persistence.CategoryNameReaderTest`,
    `bot.finance.adapter.cdc.CategoryNameResolverTest`
  - now: the reader joins a category to its parent and answers both names, and nothing for a grouping's own id;
    the resolver caches that pair under the category's id
  - then: the reader answers any category row by its id — its name and its parent's id, absent on a grouping;
    the resolver caches one such row per id and reads the parent's row for the grouping name, so
    `cdc.category-cache-size` now bounds rows including groupings
  - docs: none — `configuration.md` calls `CDC_CATEGORY_CACHE_SIZE` the entries the resolver holds, which a
    grouping's row is

- [x] R02 · behaviour · `evict(id)` drops the row stored under that id and leaves every other entry
  - files:
    - `ledger-service/src/main/java/bot/finance/adapter/cdc/CategoryNameResolver.java`
  - test-files:
    - `ledger-service/src/test/java/bot/finance/adapter/cdc/CategoryNameResolverTest.java`
  - runs: `bot.finance.adapter.cdc.CategoryNameResolverTest`
  - now: every cached entry is dropped, whichever id the event carried
  - then: the entry under that id is dropped and every other entry stays cached, a renamed grouping included
  - needs: the cache is keyed by the row a change event names, which R01 makes true
  - docs: none — this narrows the staleness `contracts/out/change-stream.md` bounds, without changing the bound

## Open Questions

- **Q1:** The finding prescribes widening `CategoryNames` with the parent id and matching cached entries against
  it on eviction. Is that the fix, or the redesign the verdict line asked for?
  - A: Neither. Cache rows rather than pairs — `id -> name` plus the parent id, resolved with two `O(1)`
    lookups. Eviction becomes a removal by key.
- **Q2:** How does `evict` find the entries filed under a grouping — a scan, or a maintained index?
  - A: Neither is needed. Under Q1's answer a grouping has an entry of its own, so `cache.remove(id)` reaches
    it.
