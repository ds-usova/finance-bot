# Steps — `ai-connector-service`

**Affected Module:** `ai-connector-service`
**Rework:** [Meters proven at the scrape](../rework.md)

## Steps

- [x] R05 · tests · add the `MetricsAdapterTest` composed annotation and its context proof
  - test-files:
    - `ai-connector-service/src/test/java/bot/finance/ai/common/boot/MetricsAdapterTest.java`
    - `ai-connector-service/src/test/java/bot/finance/ai/common/boot/MetricsAdapterContextTest.java`
  - survives: every existing scenario · the slices it does not touch
  - docs: `ai-connector-service/docs/conventions/testing.md`

  The annotation boots an explicit bean list — `MicrometerRecallMeters`, `MicrometerChangeStreamMeters` — with
  autoconfiguration on over a random port, `memory.enabled=true`, the prometheus actuator endpoint exposed with
  a `PrometheusMeterRegistry`, the datasource, Redis and the provider excluded, and a Mockito mock
  `PendingEntryCountPort` bean for the gauge to sample. The context proof autowires one bean and asserts
  nothing. Both join the conventions' package tree and the Test Layers entry gains the slice.

- [x] R06 · tests · write the two scrape tests over the rendered Prometheus text
  - test-files:
    - `ai-connector-service/src/test/java/bot/finance/ai/adapter/metrics/MicrometerRecallMetersTest.java`
    - `ai-connector-service/src/test/java/bot/finance/ai/adapter/metrics/MicrometerChangeStreamMetersTest.java`
  - survives: every existing scenario · the slices this step does not touch
  - needs: R05
  - docs: `ai-connector-service/docs/contracts/in/operations.md`

  Each test drives the port and asserts the scrape over RestAssured: `recordExamples`/`recordBestSimilarity`
  render `ai_recall_examples_*` and `ai_recall_best_similarity_*` count/sum/max; `countDropped` renders
  `ai_cdc_deliveries_dropped_total`; with the mocked `PendingEntryCountPort` answering a value,
  `ai_cdc_entries_pending` renders it, and answering `NaN`, the gauge renders a `NaN` sample — never a numeric
  value a panel could mistake for a drained group. The step's `docs:` page carries one sentence this settles:
  "absent from the scrape altogether" becomes "answers `NaN`, which no panel plots as a value".

## Open Questions

- **Q2:** R06's `NaN` scenario does not match what the registry does: with the mocked port answering `NaN`,
  the scrape renders the line `ai_cdc_entries_pending NaN` rather than omitting it (observed on a real scrape
  under `@MetricsAdapterTest`; the other four scenarios pass). Should the scenario assert the rendered `NaN`
  sample instead of the line's absence, or is the absence itself the requirement — in which case the claim
  needs a different mechanism than this registry provides?
  - A: Assert the rendered `NaN` sample — the design's intent (F26: never a value read as a drained group;
    staleness owned by health) holds with a `NaN` line, and absence would need production code this rework may
    not write. R06's text and its `docs:` correction to
    `ai-connector-service/docs/contracts/in/operations.md` carry it (2026-08-21).
