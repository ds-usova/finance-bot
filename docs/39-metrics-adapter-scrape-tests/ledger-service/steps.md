# Steps — `ledger-service`

**Affected Module:** `ledger-service`
**Rework:** [Meters proven at the scrape](../rework.md)

## Steps

- [x] R01 · tests · add the `MetricsAdapterTest` composed annotation and its context proof
  - test-files:
    - `ledger-service/src/test/java/bot/finance/common/boot/MetricsAdapterTest.java`
    - `ledger-service/src/test/java/bot/finance/common/boot/MetricsAdapterContextTest.java`
  - survives: every existing scenario · the slices it does not touch
  - docs: `ledger-service/docs/conventions/testing.md`

  The annotation boots, in the shape `McpAdapterTest` and `CdcRecoveryEndpointTest` set: an explicit bean list —
  `MicrometerToolCallMeters`, `MicrometerTurnMeters`, `MicrometerOutboxMeters` — with autoconfiguration on over
  a random port, the prometheus actuator endpoint exposed with a `PrometheusMeterRegistry`, and the datasource
  and Redis excluded. The context proof autowires one bean and asserts nothing, per the testing conventions'
  runtime-proof rule. Both join the conventions' package tree and the Test Layers entry gains the slice.

- [x] R02 · tests · write the three scrape tests over the rendered Prometheus text
  - test-files:
    - `ledger-service/src/test/java/bot/finance/adapter/metrics/MicrometerToolCallMetersTest.java`
    - `ledger-service/src/test/java/bot/finance/adapter/metrics/MicrometerTurnMetersTest.java`
    - `ledger-service/src/test/java/bot/finance/adapter/metrics/MicrometerOutboxMetersTest.java`
  - survives: every existing scenario · the slices this step does not touch
  - needs: R01

  Each test drives the port and asserts the scrape over RestAssured: `countOk`/`countRejected` render
  `ledger_mcp_tool_calls_total` with `tool`, `outcome` and `reason` (`none`, the exception's simple name,
  `unexpected`); `countTurn`/`countUnreported` render `ledger_turns_total` under the outcome names and
  `UNREPORTED`; `countResolved(ACCEPT, 2)` renders `ledger_proposals_resolved_total{resolution="accepted"}`
  grown by 2 and `DISCARD` maps to `discarded`; `countFactsDropped("ProposalAccepted", 2)` renders
  `ledger_cdc_facts_dropped_total` with its `type` tag.

- [ ] R03 · tests · swap the real `MicrometerOutboxMeters` for a mocked `OutboxMeters` in the five contexts
  - test-files:
    - `ledger-service/src/test/java/bot/finance/common/boot/CaptureAdapterConfiguration.java`
    - `ledger-service/src/test/java/bot/finance/adapter/persistence/LedgerEventOutboxTest.java`
    - `ledger-service/src/test/java/bot/finance/adapter/persistence/ExpenseRepositoryAdapterTest.java`
    - `ledger-service/src/test/java/bot/finance/adapter/persistence/ExpenseRepositoryAdapterEventsTest.java`
    - `ledger-service/src/test/java/bot/finance/adapter/persistence/ExpenseRepositoryAdapterConcurrencyTest.java`
  - survives: a refused write counts both rows under their type · the real registry -> the mocked port, the
    rendered name and tags now proven by `MicrometerOutboxMetersTest` (Q1)
  - survives: a recorded write counts nothing as dropped · the mocked port, as today's spy already verifies
  - survives: every cdc scenario · the unchanged capture beans and their own registry
  - needs: R02

  `CaptureAdapterConfiguration` drops the `MicrometerOutboxMeters` import and declares a Mockito mock
  `OutboxMeters` bean; the four test classes drop it from their `@Import` lists, `LedgerEventOutboxTest`'s
  `@MockitoSpyBean` becomes `@MockitoBean`, its registry assertion becomes
  `verify(outboxMeters).countFactsDropped("ProposalAccepted", 2L)`, and its `MeterRegistry` field goes.

- [ ] R04 · tests · drop the now-unused `InMemoryMeters`
  - test-files:
    - `ledger-service/src/test/java/bot/finance/common/boot/InMemoryMeters.java`
    - `ledger-service/src/test/java/bot/finance/common/boot/PersistenceAdapterTest.java`
  - survives: every persistence scenario · the slice without a registry it no longer reads
  - measures: test files referencing `InMemoryMeters` 2 -> 0
  - needs: R03
  - docs: `ledger-service/docs/conventions/testing.md`

  Remove the `@Import(InMemoryMeters.class)` from `PersistenceAdapterTest`, delete the class with `git rm`, and
  drop its line from the conventions' package tree.
