# Plan: Meter the Extraction Pipeline — Infrastructure

**Affected Modules:** `infrastructure`
**Design:** [Meter the Extraction Pipeline](../design.md)

Per the design's D1, the infrastructure README and the existing dashboard JSONs are this plan's authority —
the module has no conventions file and no test types, so the plan is stabilization only. `prometheus.yml`
already scrapes both management ports and does not change.

## Components

No classes. The change edits two provisioned dashboard files under `infrastructure/grafana/dashboards/`,
adding panels over the meters the two service plans create. Every expression uses the scraped, underscored
meter names, and latency panels plot `rate(_sum)/rate(_count)` with `_max` beside it, as the existing HTTP
latency panel does.

## Step-by-Step Implementation Map (To-Do List)

### Stabilization

#### Interface-First / Build Stabilization

**Configuration**

- [x] ST01 · Extend `ledger-service.json` with panels, following the file's existing panel and layout style ·
  scenarios: A21
  - tool-call rate by `tool` and `outcome`, from `ledger_mcp_tool_calls_total`
  - rejection reasons, from the same counter filtered to `outcome="rejected"`, legend by `tool` and `reason`
  - turn outcomes, from `ledger_turns_total`, legend by `outcome`
  - proposals resolved, from `ledger_proposals_resolved_total`, legend by `resolution`
- [x] ST02 · Extend `ai-connector-service.json` with panels, following the file's existing panel and layout
  style. Every selector pins its `job` as the file's existing targets do · scenarios: A21
  - extraction latency avg and max, from `grpc_server_seconds_*`, pinned to `rpc_method="ExtractIntents"`
  - provider latency avg and max, from `gen_ai_client_operation_seconds_*`, aggregated
    `by (gen_ai_operation_name)` (values `chat`, `embedding`)
  - token usage, as `sum by (gen_ai_token_type) (rate(gen_ai_client_token_usage_total{...}[5m]))`, legend
    `{{gen_ai_token_type}}` — never aggregated across the label: the framework also emits a `total` series, and
    summing over it doubles the count
  - tool-call latency, from `spring_ai_tool_seconds_*` aggregated `by (spring_ai_tool_definition_name)`; tool
    errors as the same rate filtered `error!="none"`
  - tool-call rounds per turn (the ratio D2 fixes), fully aggregated on both sides so the disjoint label sets
    cannot break the vector match:
    `sum(rate(spring_ai_tool_seconds_count{job="ai-connector-service"}[5m])) / sum(rate(ledger_turns_total{job="ledger-service"}[5m]))`
  - recall contribution: one panel per meter (their units differ), each the avg-and-max pair the file's latency
    panels use — `rate(_sum)/rate(_count)` with `_max` beside — over `ai_recall_examples` and
    `ai_recall_best_similarity`
  - pending entries, as `max(ai_cdc_entries_pending{job="ai-connector-service"})`
  - dropped deliveries, mirroring the ledger dashboard's "Facts dropped (1h)" stat form —
    `sum(increase(ai_cdc_deliveries_dropped_total{job="ai-connector-service"}[1h]))`, instant, red above 0

Verification is A21 by hand: bring the compose stack up and confirm both dashboards render the new panels
against live meters.

## Open Questions / Blockers

None.

## Review Findings

- **F1:** The rounds-per-turn ratio was a naked division over vectors with disjoint labels, yielding an empty
  panel.
  - Resolution: mechanical
  - Action: applied — both sides fully aggregated with pinned jobs; the provisioned datasource scrapes both
    services, so the cross-service query works.

- **F2:** The token-usage panel would double-count: the framework emits `input`, `output` and `total` series.
  - Resolution: mechanical
  - Action: applied — `sum by (gen_ai_token_type)`, never aggregated across the label.

- **F3:** ST02 named no Prometheus label keys, unlike ST01.
  - Resolution: mechanical
  - Action: applied — `gen_ai_operation_name`, `gen_ai_token_type`, `spring_ai_tool_definition_name`,
    `rpc_method` and `error!="none"` named in the bullets.

- **F4:** Extraction latency selected `grpc_server_seconds_*` unfiltered, covering every RPC.
  - Resolution: mechanical
  - Action: applied — pinned to `rpc_method="ExtractIntents"`.

- **F5:** The dropped-deliveries bullet named the meter but no panel form.
  - Resolution: mechanical
  - Action: applied — mirrors the ledger's "Facts dropped (1h)" stat: `sum(increase(...[1h]))`, red above 0.

- **F6:** The recall-contribution bullets left the statistic and panel split unstated.
  - Resolution: decision
  - Action: resolved — D1 makes the existing JSONs the authority, and their precedent is the avg-and-max pair;
    one panel per meter since the units differ.
