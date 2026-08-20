# Rework: Meters proven at the scrape, slices freed of the metrics adapter

**Affected Modules:** `ledger-service`, `ai-connector-service`
**Source:** the user's request in conversation, 2026-08-21 — after task 38's meter ports landed
**Baseline:** 0f18f34

## The fix

Give each service's `adapter/metrics` classes their own integration slice — an actuator-scrape boot in the
shape of `CdcRecoveryEndpointTest` — and mock the meter ports everywhere else, so a CDC or persistence slice
boots only its own adapter. The rendered Prometheus text (normalised names, tags, the `ACCEPT`→`accepted`
mapping, a `NaN` gauge rendering no plottable value) gains a direct test for the first time below system level; the slices that
today carry `MicrometerOutboxMeters` for it verify the port instead.

## What changes

**A metrics scrape slice per service**

| #   | Kind  | What changes                                                             | Touches                          |
|-----|-------|---------------------------------------------------------------------------|-----------------------------------|
| R01 | tests | add the ledger's `MetricsAdapterTest` boot and its context proof         | `bot.finance.common.boot`         |
| R02 | tests | add scrape tests for the ledger's three Micrometer classes               | `bot.finance.adapter.metrics`     |
| R05 | tests | add the connector's `MetricsAdapterTest` boot and its context proof      | `bot.finance.ai.common.boot`      |
| R06 | tests | add scrape tests for the connector's two Micrometer classes              | `bot.finance.ai.adapter.metrics`  |

**The other slices mock the port**

| #   | Kind  | What changes                                                             | Touches                          |
|-----|-------|-----------------------------------------------------------------------------|-----------------------------------|
| R03 | tests | swap `MicrometerOutboxMeters` for a mocked `OutboxMeters` in five contexts | `bot.finance.adapter.persistence` |
| R04 | tests | drop the now-unused `InMemoryMeters`                                      | `bot.finance.common.boot`         |

**Not changed:** production code, every meter name and tag, the system tests (`PipelineMetersSystemTest`,
`MetersSystemTest`, `MetersWithMemoryOffSystemTest`), the cdc-owned `ChangeStreamMeters` and its tests, and
`CaptureAdapterConfiguration`'s own `SimpleMeterRegistry`, which the cdc meters still need.

## What the code does now

| What                                                        | Where                                                                 | What is wrong with it                                                      |
|-------------------------------------------------------------|------------------------------------------------------------------------|-----------------------------------------------------------------------------|
| Five test contexts boot the real `MicrometerOutboxMeters`  | `CaptureAdapterConfiguration`, `LedgerEventOutboxTest`, three `ExpenseRepositoryAdapter*` slices | a persistence/CDC slice carries a second adapter it is not testing |
| Meter rendering is proven only at system level              | `PipelineMetersSystemTest`, `MetersSystemTest`                        | names, tags and the `accepted`/`discarded` mapping have no cheap direct test |
| `InMemoryMeters` exists for the persistence slices          | `bot.finance.common.boot`                                             | orphaned once the port is mocked there                                     |

## What must stay true

- Every scraped meter name and tag stays byte-identical — the scrape tests and the untouched system tests are
  what would catch a drift.
- The cdc slices keep asserting `ledger_cdc_*` through their real registry — their meters live in
  `adapter/cdc` (design 38 D6) and are not this rework's.
- The memory-off absence of every `ai_*` meter — `MetersWithMemoryOffSystemTest`, untouched.

## Steps

In the module files: [`ledger-service/steps.md`](ledger-service/steps.md) — R01–R04,
[`ai-connector-service/steps.md`](ai-connector-service/steps.md) — R05–R06.

## Open Questions

- **Q1:** `LedgerEventOutboxTest`'s "a refused write counts both rows under their type" today asserts the real
  registry (`ledger_cdc_facts_dropped_total`, tag, value 2.0); R03 turns it into a verification on the mocked
  port, and the rendered name and tags move to `MicrometerOutboxMetersTest`. Swapping the real thing for a mock
  changes what that one test proves — approved?
  - A: Approved — the name/tag proof moves to `MicrometerOutboxMetersTest`; the outbox test keeps the call and
    the count (user, 2026-08-21).
