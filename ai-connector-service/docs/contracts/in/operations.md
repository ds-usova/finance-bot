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

With the memory on, the database and the change stream's store each answer as a component, and either being
unreachable takes the aggregate down. With it off both components are absent, and the aggregate health is
unaffected by them. What the memory switch turns off is [configuration](../../configuration.md).

## Meters

Every meter is the framework's — the JVM, the HTTP server, the connection pool. The service declares none of
its own.

## Compatibility

A meter renamed or a tag added is a change to whatever alerts on it, including the provisioned dashboard,
[`infrastructure/grafana/dashboards/ai-connector-service.json`](../../../../infrastructure/grafana/dashboards/ai-connector-service.json).

Moving the management port changes where a probe and a collector point, and nothing else.
