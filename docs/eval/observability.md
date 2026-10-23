# Observability

## Page Map

1. [Measurements](#measurements) — what each case records.
2. [Per-Case Collection Flow](#per-case-collection-flow) — when each measurement is collected.
3. [Required Changes](#required-changes) — what must be added to each system.
4. [Trace Contract](#trace-contract) — the data produced by instrumentation.
5. [Implementation Decisions](#implementation-decisions) — selected tools and reasons.
6. [Existing Signals and Collection Rules](#existing-signals-and-collection-rules) — reference material.

## Measurements

Instrumentation belongs to the application and must support ordinary production traffic as well as evaluation.
Both use the same observability stack; evaluation adds run context and exports its own records, not a separate
collection or storage deployment. Benchmark execution remains local and uses isolated application data.

| Measurement                      | How                                                                                                                                                                                                                                  | Current support                                                                                   | Change location                                                                                                                  | Required work                                                                                                                 |
|----------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------|
| wall-clock turn duration         | monotonic time from accepted `POST /api/v1/messages` to the first terminal `GET /api/v1/messages/{id}` response                                                                                                                      | REST endpoints exist                                                                              | evaluation runner                                                                                                                | add runner timing                                                                                                             |
| terminal message outcome         | poll `GET /api/v1/messages/{id}` until `state: DONE`; record `outcome`                                                                                                                                                               | REST endpoint exists                                                                              | evaluation runner                                                                                                                | add runner polling                                                                                                            |
| resulting proposals              | follow the [proposal inspection contract](application-boundary.md#completion-and-result): verify the precondition, read all pending-expense pages after terminal outcome, exclude learning-proposal IDs, and resolve catalogue names | expense and category REST APIs exist                                                              | evaluation runner                                                                                                                | add the precondition check, paginated result read, ID-based attribution, and category lookup                                  |
| provider chat calls and duration | count and time every provider `chat` call for the case repetition                                                                                                                                                                    | `gen_ai_client_operation_seconds_*` provides aggregate count and duration                         | <ul><li>`ai-connector-service`</li><li>trace collector</li><li>evaluation runner</li></ul>                                       | add per-call tracing and case correlation                                                                                     |
| input and output tokens          | record provider response `usage` for every chat call                                                                                                                                                                                 | `gen_ai_client_token_usage_total` provides aggregate counts                                       | <ul><li>`ai-connector-service`</li><li>trace collector</li><li>evaluation runner</li></ul>                                       | add per-call response capture                                                                                                 |
| MCP calls                        | record tool name, order, arguments, result, status, and duration                                                                                                                                                                     | `spring_ai_tool_seconds_*` and `ledger_mcp_tool_calls_total` provide aggregate count and duration | <ul><li>`ai-connector-service`</li><li>`ledger-service`</li><li>trace collector</li><li>evaluation runner</li></ul>              | add per-call tracing and payload capture                                                                                      |
| recalled examples                | record the exact examples passed to the model, in order, with their decisions and similarity                                                                                                                                         | `ai_recall_examples_*` and `ai_recall_best_similarity_*` provide only count and best similarity   | <ul><li>`ai-connector-service`</li><li>trace collector</li><li>evaluation runner</li></ul>                                       | emit the recall span and events defined below                                                                                 |
| learning availability            | time from accepted decision until the AI Connector database satisfies the [readiness condition](memory-and-retrieval.md#learned-decision-availability)                                                                               | required database state exists                                                                    | evaluation runner                                                                                                                | add a read-only SQL poll and runner timing                                                                                    |
| system call trace                | record calls across REST, worker, gRPC, provider, MCP, change stream, and SQL boundaries                                                                                                                                             | no distributed tracing backend                                                                    | <ul><li>`ledger-service`</li><li>`ai-connector-service`</li><li>observability infrastructure</li><li>evaluation runner</li></ul> | add trace production, correlation, collection, and storage                                                                    |
| request and response payloads    | attach sanitized parameters and bodies to the corresponding call                                                                                                                                                                     | no common payload capture                                                                         | <ul><li>`ledger-service`</li><li>`ai-connector-service`</li><li>trace collector</li></ul>                                        | capture sanitized request and response payloads at REST, provider, MCP, and gRPC boundaries and attach them to the case trace |
| error                            | record stage, component, exception class or protocol status, and message                                                                                                                                                             | protocol statuses and service logs exist                                                          | <ul><li>`ledger-service`</li><li>`ai-connector-service`</li><li>trace collector</li><li>evaluation runner</li></ul>              | add case correlation and structured capture                                                                                   |

## Per-Case Collection Flow

Each repetition may contain several learning turns and one target turn. Record them separately; learning setup
must not inflate the target's latency, call counts, or token usage.

| Step | Runner action                                                                                         | Evidence produced                                                                            |
|------|-------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------|
| 1    | establish attempt context, verify the fixture, and snapshot both `/actuator/prometheus` endpoints     | attempt identity and starting metric values                                                  |
| 2    | for each declared learning entry, file its message, time/poll its turn, and inspect its proposals     | separate learning-message outcome, proposals, timings, calls, and usage                      |
| 3    | apply that entry's classification and approve/discard decision, then poll readiness before proceeding | decision trace and learning-availability duration or timeout, attributed to that learning ID |
| 4    | file the target message after all learning entries are ready; start its own turn timer                | target message ID and target-phase trace context                                             |
| 5    | poll the target until terminal outcome, then inspect proposals through the application boundary       | target outcome, wall-clock duration, and attributed proposals                                |
| 6    | evaluate declared MCP expectations and collect diagnostic recall evidence from target traces          | target-only MCP check results and retrieval records                                          |
| 7    | snapshot metrics again and calculate deltas                                                           | execution-interval diagnostic deltas, including learning and target work                     |
| 8    | collect phase-attributed measurements and preserve artifacts in `finally` before cleanup              | separate learning/target results, attempt errors, and `traces.jsonl` records                 |

Steps 2–3 are skipped for cases without learning entries. If learning setup fails, the target is not filed;
target measurements are unavailable, not zero. Evidence collection and teardown still follow the steps contract.

### Measurement Attribution

| Scope               | Included                                                                                    | Reporting rule                                                                                              |
|---------------------|---------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------|
| learning            | one learning message, its classification/decision, and resulting propagation/readiness work | separate record per `learningId`; turn duration and decision-to-readiness duration are distinct             |
| target              | target processing, its provider/MCP calls, and recall used by that turn                     | primary benchmark measurements; only these calls satisfy `expected.mcpCalls` or consume target call budgets |
| setup / teardown    | fixture checks and cleanup/restoration work                                                 | retain separately; exclude from target turn metrics                                                         |
| attempt diagnostics | before/after service-meter deltas for the execution interval                                | may include learning and delayed background work; never substitute for target-only trace measurements       |

Provider/MCP counts and token usage are derived from correlated call records, not by subtracting learning totals
from aggregate meters. Count one client-side dispatch once, even when both services emit spans; retries are separate
calls. Background events retain their originating message and phase even if processed during a later phase.

Case cleanup is defined in [Case Steps](case-steps.md); artifact files are defined
in [Artifacts and Baselines](artifacts-and-baselines.md).

## Required Changes

| System                 | Add                                                                                                                                                                                                                                                                                                                                |
|------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| evaluation runner      | <ul><li>REST precondition checks, message polling, and proposal reads</li><li>separate learning and target timing/measurement records</li><li>attempt and phase correlation</li><li>before-and-after diagnostic metric snapshots</li><li>read-only memory-readiness polling</li><li>trace retrieval and artifact writing</li></ul> |
| `ledger-service`       | <ul><li>propagate case and trace context through REST, worker, gRPC, MCP, and change-stream operations</li><li>attach sanitized boundary payloads</li><li>emit correlated protocol and processing errors</li></ul>                                                                                                                 |
| `ai-connector-service` | <ul><li>propagate case and trace context through provider, MCP, memory, and change-stream operations</li><li>capture each provider call, token usage, and each MCP call</li><li>emit the recall span and events defined below</li><li>attach sanitized boundary payloads</li></ul>                                                 |
| recall persistence     | <ul><li>`JdbcMessageMemoryAdapter.neighbourIds`: preserve id, similarity, rank, and selection reason</li><li>recent-example query: return similarity with the id</li><li>example materialization: expose the incoming-message id and decided expense lines to instrumentation</li></ul>                                            |
| trace collector        | <ul><li>receive spans and events from both services</li><li>retain payloads after redaction</li><li>return or export all records for one case repetition</li></ul>                                                                                                                                                                 |

## Trace Contract

### Correlation Fields

For ordinary application traffic, trace/message correlation remains available without evaluation metadata.
The evaluation-only fields below are required for benchmark work and absent for ordinary traffic. Attach an
environment identifier to telemetry so records remain distinguishable in shared storage. Evaluation identifiers
are trace attributes, not high-cardinality Prometheus metric labels.

| Field                        | Rule                                                                                                                                             |
|------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------|
| `run_id`                     | shared by every case in one evaluation run                                                                                                       |
| `case_id`                    | copied from the case YAML                                                                                                                        |
| `repetition`                 | identifies one execution of the case                                                                                                             |
| `attempt`                    | distinguishes retries of the same repetition after UTC rollover                                                                                  |
| `phase`                      | `setup`, `learning`, `target`, or `teardown`; asynchronous work retains the originating phase                                                    |
| `learning_id`                | case YAML learning-entry ID for learning work; null otherwise                                                                                    |
| `incoming_message_id`        | links the originating REST message, worker turn, proposals, memory rows, and traces; null before an ID is assigned or for work without a message |
| `trace_id`                   | shared across synchronous calls and linked across asynchronous boundaries                                                                        |
| `span_id` / `parent_span_id` | preserve call order and parent-child relationships                                                                                               |

Persist or propagate attribution across worker and change-stream boundaries. A shared case ID or a time window
alone cannot distinguish learning from target work. Trace snake_case fields map to the corresponding camelCase
artifact identity fields; measurement records retain `phase`, `learningId`, and `incomingMessageId`.

### Call Records

| Boundary                              | Capture                                                                          |
|---------------------------------------|----------------------------------------------------------------------------------|
| evaluator → Ledger REST               | method, route, sanitized request and response, status, duration                  |
| Ledger worker → AI Connector gRPC     | method, sanitized protobuf request and response, status, duration                |
| AI Connector → AI provider            | model, parameters, sanitized request and response, token usage, status, duration |
| AI Connector → Ledger MCP             | tool, sanitized arguments and result, accepted or rejected status, duration      |
| Ledger → change stream → AI Connector | event type, stream position, sanitized payload, processing status, duration      |
| services → PostgreSQL                 | database, operation, sanitized statement, status, duration                       |

### Recall Records

| Record                    | Capture                                                                                                                       |
|---------------------------|-------------------------------------------------------------------------------------------------------------------------------|
| `ai.recall` span          | target `incoming_message_id`, duration, `examples`, `min_similarity`, `recent_window`, `max_age`, `example_lines`, and result |
| `ai.recall.example` event | source `incoming_message_id`, returned rank, similarity, and selection reason: `closest`, `recent`, or `both`                 |
| `ai.recall.expense` event | example rank, expense-line rank, `ACCEPTED` or `DISCARDED`, category, and grouping                                            |

| Recall result            | Meaning                                      |
|--------------------------|----------------------------------------------|
| `message_not_registered` | no retrieval was attempted                   |
| `embedding_failed`       | the target message could not be embedded     |
| `store_failed`           | the memory store refused the operation       |
| `no_examples`            | retrieval completed and returned no example  |
| `examples_found`         | the listed examples were passed to the model |

Do not record the embedding vector. Do not duplicate example text outside the sanitized provider request.

## Implementation Decisions

Each row records an agreed implementation choice, the alternatives considered, and the reason for selection.

| Decision               | Options                                                                                                                                                                                                                                                            | Selected                                                                                                  | Why                                                                                                                                                           |
|------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| evaluation runner      | <ul><li>Gradle evaluation test suite: write the runner as JUnit tests and execute it only through a dedicated Gradle task</li><li>standalone Java program: write a separate `main` program that owns case execution and reporting; Gradle only starts it</li></ul> | standalone Java program started by Gradle                                                                 | the cross-service, long-running workflow needs its own lifecycle, repetitions, baseline handling, and artifact generation                                     |
| trace production       | <ul><li>Spring-native: `spring-boot-micrometer-tracing-opentelemetry`, Micrometer `Observation` and `Tracer`, OTLP exporter</li><li>OpenTelemetry Java agent plus API or instrumentation annotations</li><li>application-written structured JSON events</li></ul>  | Spring-native Micrometer Tracing with an OpenTelemetry exporter and explicit application spans and events | extends the existing Spring and Micrometer stack, keeps a standard trace format, and supports the required domain-specific spans and events                   |
| payload capture        | <ul><li>application-level capture at boundary adapters</li><li>OpenTelemetry Java agent extension</li><li>traffic proxy</li></ul>                                                                                                                                  | application-level capture at boundary adapters                                                            | provides domain-aware field selection, redaction before export, and precise correlation with the owning span                                                  |
| trace storage          | <ul><li>OpenTelemetry Collector and Grafana Tempo: queryable traces; export selected spans to `traces.jsonl`</li><li>OpenTelemetry Collector with OTLP JSONL file export: direct artifact; file format is not stable</li></ul>                                     | application's shared OpenTelemetry Collector and Grafana Tempo; export run traces to normalized `traces.jsonl` | supports the same measurements for production and evaluation through the existing Grafana stack; avoids an evaluation-only stack and retains backend-independent run artifacts |
| memory-readiness probe | <ul><li>runner-owned PostgreSQL JDBC probe with read-only credentials</li><li>evaluation-only application endpoint in the ephemeral environment</li></ul>                                                                                                          | runner-owned PostgreSQL JDBC probe with read-only credentials                                             | verifies the actual persisted state without adding evaluation-only behaviour to the application API; schema coupling remains isolated in one runner component |

## Existing Signals and Collection Rules

### Existing Metrics

| Area     | Meters                                                                 |
|----------|------------------------------------------------------------------------|
| provider | `gen_ai_client_operation_seconds_*`, `gen_ai_client_token_usage_total` |
| MCP      | `spring_ai_tool_seconds_*`, `ledger_mcp_tool_calls_total`              |
| recall   | `ai_recall_examples_*`, `ai_recall_best_similarity_*`                  |
| outcomes | `ledger_turns_total`, `ledger_proposals_resolved_total`                |

Read each service's `/actuator/prometheus` endpoint at the execution-interval boundaries shown above and retain
the delta as attempt-level diagnostic evidence. Cases run sequentially without unrelated traffic, but background
learning work may overlap the target turn. Use correlated traces for message/phase-specific values. Prometheus
retains run-level series; its 15-second scrape interval is not used for per-attempt deltas.

### Collection and Reporting Rules

| Rule            | Requirement                                                                                                       |
|-----------------|-------------------------------------------------------------------------------------------------------------------|
| granularity     | retain raw measurements and spans for every attempt, separated by message and phase                               |
| attribution     | include only case-owned traffic and background work; target performance summaries exclude learning/setup/teardown |
| sanitization    | follow the [artifact redaction rules](artifacts-and-baselines.md#reproducibility-metadata)                        |
| derived values  | calculate averages, percentiles, totals, and deltas from raw artifacts                                            |
| durations       | report provider and end-to-end duration separately                                                                |
| MCP             | group duration by tool name; include accepted and rejected calls                                                  |
| MCP recovery    | report observed recovery; do not force invalid calls as a deterministic precondition                              |
| reproducibility | every reported value must be reproducible from raw artifacts                                                      |
