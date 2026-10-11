# Observability

## Page Map

1. [Measurements](#measurements) — what each case records.
2. [Per-Case Collection Flow](#per-case-collection-flow) — when each measurement is collected.
3. [Required Changes](#required-changes) — what must be added to each system.
4. [Trace Contract](#trace-contract) — the data produced by instrumentation.
5. [Implementation Decisions](#implementation-decisions) — choices that remain open.
6. [Existing Signals and Collection Rules](#existing-signals-and-collection-rules) — reference material.

## Measurements

| Measurement | How | Current support | Change location | Required work |
|-------------|-----|-----------------|-----------------|---------------|
| wall-clock turn duration | monotonic time from accepted `POST /api/v1/messages` to the first terminal `GET /api/v1/messages/{id}` response | REST endpoints exist | evaluation runner | add runner timing |
| terminal message outcome | poll `GET /api/v1/messages/{id}` until `state: DONE`; record `outcome` | REST endpoint exists | evaluation runner | add runner polling |
| resulting proposals | require `PENDING.total = 0` before filing; after terminal outcome read `GET /api/v1/expenses?status=PENDING&limit=100`; resolve category and grouping through `GET /api/v1/categories` | expense and category REST APIs exist | evaluation runner | add the precondition check, result read, and category lookup |
| provider chat calls and duration | count and time every provider `chat` call for the case repetition | `gen_ai_client_operation_seconds_*` provides aggregate count and duration | <ul><li>`ai-connector-service`</li><li>trace collector</li><li>evaluation runner</li></ul> | add per-call tracing and case correlation |
| input and output tokens | record provider response `usage` for every chat call | `gen_ai_client_token_usage_total` provides aggregate counts | <ul><li>`ai-connector-service`</li><li>trace collector</li><li>evaluation runner</li></ul> | add per-call response capture |
| MCP calls | record tool name, order, arguments, result, status, and duration | `spring_ai_tool_seconds_*` and `ledger_mcp_tool_calls_total` provide aggregate count and duration | <ul><li>`ai-connector-service`</li><li>`ledger-service`</li><li>trace collector</li><li>evaluation runner</li></ul> | add per-call tracing and payload capture |
| recalled examples | record the exact examples passed to the model, in order, with their decisions and similarity | `ai_recall_examples_*` and `ai_recall_best_similarity_*` provide only count and best similarity | <ul><li>`ai-connector-service`</li><li>trace collector</li><li>evaluation runner</li></ul> | emit the recall span and events defined below |
| learning availability | time from accepted decision until the AI Connector database satisfies the [readiness condition](memory-and-retrieval.md#learned-decision-availability) | required database state exists | evaluation runner | add a read-only SQL poll and runner timing |
| system call trace | record calls across REST, worker, gRPC, provider, MCP, change stream, and SQL boundaries | no distributed tracing backend | <ul><li>`ledger-service`</li><li>`ai-connector-service`</li><li>observability infrastructure</li><li>evaluation runner</li></ul> | add trace production, correlation, collection, and storage |
| request and response payloads | attach sanitized parameters and bodies to the corresponding call | no common payload capture | <ul><li>`ledger-service`</li><li>`ai-connector-service`</li><li>trace collector</li></ul> | capture sanitized request and response payloads at REST, provider, MCP, and gRPC boundaries and attach them to the case trace |
| error | record stage, component, exception class or protocol status, and message | protocol statuses and service logs exist | <ul><li>`ledger-service`</li><li>`ai-connector-service`</li><li>trace collector</li><li>evaluation runner</li></ul> | add case correlation and structured capture |

## Per-Case Collection Flow

| Step | Runner action | Evidence produced |
|------|---------------|-------------------|
| 1 | snapshot both `/actuator/prometheus` endpoints | starting metric values |
| 2 | submit the message and start the monotonic timer | `run_id`, `case_id`, `repetition`, `incoming_message_id` |
| 3 | let the services process the message | correlated spans and events |
| 4 | poll the message until its terminal state | outcome and wall-clock duration |
| 5 | read pending expenses and resolve category ids | application-visible proposals |
| 6 | for a learning step, poll the readiness condition | learning-availability duration or timeout |
| 7 | snapshot metrics again and calculate deltas | per-case aggregate metrics |
| 8 | collect the case trace and write raw artifacts | case result and `traces.jsonl` records |

Case cleanup is defined in [Case Steps](case-steps.md); artifact files are defined in [Artifacts and Baselines](artifacts-and-baselines.md).

## Required Changes

| System | Add |
|--------|-----|
| evaluation runner | <ul><li>REST precondition checks, message polling, and proposal reads</li><li>monotonic wall-clock timing</li><li>before-and-after metric snapshots</li><li>read-only memory-readiness polling</li><li>trace retrieval and artifact writing</li></ul> |
| `ledger-service` | <ul><li>propagate case and trace context through REST, worker, gRPC, MCP, and change-stream operations</li><li>attach sanitized boundary payloads</li><li>emit correlated protocol and processing errors</li></ul> |
| `ai-connector-service` | <ul><li>propagate case and trace context through provider, MCP, memory, and change-stream operations</li><li>capture each provider call, token usage, and each MCP call</li><li>emit the recall span and events defined below</li><li>attach sanitized boundary payloads</li></ul> |
| recall persistence | <ul><li>`JdbcMessageMemoryAdapter.neighbourIds`: preserve id, similarity, rank, and selection reason</li><li>recent-example query: return similarity with the id</li><li>example materialization: expose the incoming-message id and decided expense lines to instrumentation</li></ul> |
| trace collector | <ul><li>receive spans and events from both services</li><li>retain payloads after redaction</li><li>return or export all records for one case repetition</li></ul> |

## Trace Contract

### Correlation Fields

| Field | Rule |
|-------|------|
| `run_id` | shared by every case in one evaluation run |
| `case_id` | copied from the case YAML |
| `repetition` | identifies one execution of the case |
| `incoming_message_id` | links the REST message, worker turn, proposals, memory rows, and traces |
| `trace_id` | shared across synchronous calls and linked across asynchronous boundaries |
| `span_id` / `parent_span_id` | preserve call order and parent-child relationships |

### Call Records

| Boundary | Capture |
|----------|---------|
| evaluator → Ledger REST | method, route, sanitized request and response, status, duration |
| Ledger worker → AI Connector gRPC | method, sanitized protobuf request and response, status, duration |
| AI Connector → AI provider | model, parameters, sanitized request and response, token usage, status, duration |
| AI Connector → Ledger MCP | tool, sanitized arguments and result, accepted or rejected status, duration |
| Ledger → change stream → AI Connector | event type, stream position, sanitized payload, processing status, duration |
| services → PostgreSQL | database, operation, sanitized statement, status, duration |

### Recall Records

| Record | Capture |
|--------|---------|
| `ai.recall` span | target `incoming_message_id`, duration, `examples`, `min_similarity`, `recent_window`, `max_age`, `example_lines`, and result |
| `ai.recall.example` event | source `incoming_message_id`, returned rank, similarity, and selection reason: `closest`, `recent`, or `both` |
| `ai.recall.expense` event | example rank, expense-line rank, `ACCEPTED` or `DISCARDED`, category, and grouping |

| Recall result | Meaning |
|---------------|---------|
| `message_not_registered` | no retrieval was attempted |
| `embedding_failed` | the target message could not be embedded |
| `store_failed` | the memory store refused the operation |
| `no_examples` | retrieval completed and returned no example |
| `examples_found` | the listed examples were passed to the model |

Do not record the embedding vector. Do not duplicate example text outside the sanitized provider request.

## Implementation Decisions

Each row is one unresolved implementation choice; selecting an option does not change the measurement contract above.

| Decision | Options | Selected | Why |
|----------|---------|----------|-----|
| evaluation runner | <ul><li>Gradle evaluation test suite: write the runner as JUnit tests and execute it only through a dedicated Gradle task</li><li>standalone Java program: write a separate `main` program that owns case execution and reporting; Gradle only starts it</li></ul> | standalone Java program started by Gradle | the cross-service, long-running workflow needs its own lifecycle, repetitions, baseline handling, and artifact generation |
| trace production | <ul><li>Spring-native: `spring-boot-micrometer-tracing-opentelemetry`, Micrometer `Observation` and `Tracer`, OTLP exporter</li><li>OpenTelemetry Java agent plus API or instrumentation annotations</li><li>application-written structured JSON events</li></ul> | Spring-native Micrometer Tracing with an OpenTelemetry exporter and explicit application spans and events | extends the existing Spring and Micrometer stack, keeps a standard trace format, and supports the required domain-specific spans and events |
| payload capture | <ul><li>application-level capture at boundary adapters</li><li>OpenTelemetry Java agent extension</li><li>traffic proxy</li></ul> | application-level capture at boundary adapters | provides domain-aware field selection, redaction before export, and precise correlation with the owning span |
| trace storage | <ul><li>OpenTelemetry Collector and Grafana Tempo: queryable traces; export selected spans to `traces.jsonl`</li><li>OpenTelemetry Collector with OTLP JSONL file export: direct artifact; file format is not stable</li></ul> | OpenTelemetry Collector and Grafana Tempo; export run traces to normalized `traces.jsonl` | provides trace search and visualization through the existing Grafana stack while keeping a durable, backend-independent run artifact |
| memory-readiness probe | <ul><li>runner-owned PostgreSQL JDBC probe with read-only credentials</li><li>evaluation-only application endpoint in the ephemeral environment</li></ul> | runner-owned PostgreSQL JDBC probe with read-only credentials | verifies the actual persisted state without adding evaluation-only behaviour to the application API; schema coupling remains isolated in one runner component |

## Existing Signals and Collection Rules

### Existing Metrics

| Area | Meters |
|------|--------|
| provider | `gen_ai_client_operation_seconds_*`, `gen_ai_client_token_usage_total` |
| MCP | `spring_ai_tool_seconds_*`, `ledger_mcp_tool_calls_total` |
| recall | `ai_recall_examples_*`, `ai_recall_best_similarity_*` |
| outcomes | `ledger_turns_total`, `ledger_proposals_resolved_total` |

Read each service's `/actuator/prometheus` endpoint immediately before and after a case repetition and use the delta. Cases run sequentially without unrelated traffic. Prometheus retains run-level series, but its 15-second scrape interval is not used for per-case values.

### Collection and Reporting Rules

| Rule | Requirement |
|------|-------------|
| granularity | retain raw measurements and spans for every case repetition |
| attribution | include only traffic and background work caused by the case |
| sanitization | follow the [artifact redaction rules](artifacts-and-baselines.md#reproducibility-metadata) |
| derived values | calculate averages, percentiles, totals, and deltas from raw artifacts |
| durations | report provider and end-to-end duration separately |
| MCP | group duration by tool name; include accepted and rejected calls |
| MCP recovery | report observed recovery; do not force invalid calls as a deterministic precondition |
| reproducibility | every reported value must be reproducible from raw artifacts |
