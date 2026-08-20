# An operator running the service — health and meters (HTTP)

The boundary a deployment is watched through: what an operator needs to see how the service is doing.

It is served on its own port, `MANAGEMENT_PORT`, which is never published alongside the service's own. Nothing
the product is used with is reachable here, and nothing here is reachable there.

- **Counterpart:** an operator, or the monitoring that stands in for one
- **Transport:** HTTP on the management port
- **Schema:** none held in a file

## Operations

| Operation         | Address                    | Who may call             | Purpose                                                                             |
|-------------------|----------------------------|--------------------------|-------------------------------------------------------------------------------------|
| [Health](#health) | `GET /actuator/health`     | anyone reaching the port | whether the service and each of its parts is up, for a liveness and readiness probe |
| [Meters](#meters) | `GET /actuator/prometheus` | anyone reaching the port | every meter in the Prometheus text format, for the metrics collector                |

## Health

With the memory on, the database answers as `db` and the change stream's store as `redis`, and either being
unreachable takes the aggregate down. With it off both components are absent, and the aggregate health is
unaffected by them. What the memory switch turns off is [configuration](../../configuration.md).

## Meters

Names are as they appear in the scrape. The JVM, the HTTP server and the connection pool are metered by the
framework alongside these.

The framework's, from the calls it makes on the service's behalf:

| Meter                               | Kind    | Says                                                                                                       |
|-------------------------------------|---------|------------------------------------------------------------------------------------------------------------|
| `grpc_server_seconds_*`             | timer   | how long an extraction call took, tagged by `rpc_method` and `grpc_status_code`                            |
| `gen_ai_client_operation_seconds_*` | timer   | how long a provider call took, tagged by `gen_ai_operation_name` — `chat` or `embedding` — and `error` |
| `gen_ai_client_token_usage_total`   | counter | tokens a chat answer reported, tagged by `gen_ai_token_type` — `input`, `output` and `total`             |
| `spring_ai_tool_seconds_*`          | timer   | how long a tool call the model made took, tagged by `spring_ai_tool_definition_name` and `error`           |

- A chat sample and its token counts move once per model round-trip, so one turn produces several.
- `gen_ai_token_type="total"` already carries input plus output. Summing across the tag counts them twice.
- `spring_ai_tool_definition_name` carries the tool's prefixed name, ending in the bare name
  [the ledger's own counter](../../../../ledger-service/docs/contracts/in/operations.md#meters) uses.
- An embedding call a turn stopped waiting for still records the provider's eventual outcome. What the turn did
  instead is [the recall's](../../usecases/recall-examples.md#outcomes).

The service's own:

| Meter                             | Kind                 | Says                                                                   |
|-----------------------------------|----------------------|------------------------------------------------------------------------|
| `ai_recall_examples_*`            | distribution summary | how many worked examples a recall that searched came back with         |
| `ai_recall_best_similarity_*`     | distribution summary | how close the closest of them was, on a recall that came back with any |
| `ai_cdc_entries_pending`          | gauge                | [the group's pending summary](../out/change-stream.md#operations)      |
| `ai_cdc_deliveries_dropped_total` | counter              | deliveries given up on                                                 |

- The pending gauge is what the group holds undone, not a backlog: entries nobody has read yet, and a consumer
  that is down, both read zero. Every instance publishes the same number, so a panel takes the maximum, never
  the sum.
- Before the first successful pending read the gauge is absent from the scrape altogether. Whether the value is
  stale is what [the health endpoint](#health) says.
- What a drop costs is [the use case's](../../usecases/learn-message-outcome.md#outcomes).
- With the memory switched off every `ai_` meter is absent — [configuration](../../configuration.md).

## Compatibility

A meter renamed or a tag added is a change to whatever alerts on it, including the provisioned dashboard,
[`infrastructure/grafana/dashboards/ai-connector-service.json`](../../../../infrastructure/grafana/dashboards/ai-connector-service.json).

Moving the management port changes where a probe and a collector point, and nothing else.
