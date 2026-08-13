# Configuration

Every value below is read from the environment at startup. The defaults suit a developer's machine; a
deployment supplies its own.

| Variable                              | Sets                                                                                | Default                                                   | Required                 | Secret |
|---------------------------------------|-------------------------------------------------------------------------------------|-----------------------------------------------------------|--------------------------|--------|
| `DB_JDBC_URL`                         | the database the service reads and writes                                           | `jdbc:postgresql://localhost:5432/ledger-db`               | yes                      | no     |
| `DB_USER`                             | the database account                                                                | `ledger-user`                                              | yes                      | no     |
| `DB_PASSWORD`                         | the password for that account                                                       | *(a local placeholder)*                                    | yes                      | yes    |
| `TELEGRAM_BOT_TOKEN`                  | which bot the service collects messages for and acts as                             | *(none)*                                                   | when polling is on       | yes    |
| `TELEGRAM_POLLING_ENABLED`            | whether the service collects Telegram messages at all                               | `true`                                                     | no                       | no     |
| `TELEGRAM_API_URL`                    | where Telegram's Bot API is reached                                                 | Telegram's own address                                     | no                       | no     |
| `TELEGRAM_LOGIN_MAX_AGE`              | how old a Telegram sign-in may be and still be accepted                             | `1d`                                                       | no                       | no     |
| `AI_CONNECTOR_GRPC_TARGET`            | where the AI Connector's gRPC server is reached                                     | `static://localhost:1001`                                  | no                       | no     |
| `MCP_ENABLED`                         | whether the MCP server endpoint is served at all                                    | `true`                                                     | no                       | no     |
| `TOKEN_SIGNING_KEYSTORE`              | where the keystore signing every token the service issues is read from              | the committed development keystore                         | yes                      | no     |
| `TOKEN_SIGNING_KEYSTORE_PASSWORD`     | the password for that keystore                                                      | *(a local placeholder)*                                    | yes                      | yes    |
| `TOKEN_SIGNING_KEY_ALIAS`             | which key pair in the keystore signs and verifies those tokens                      | `mcp-signing`                                              | with a supplied keystore | no     |
| `MCP_JWT_TTL`                         | how long a minted MCP token is valid                                                | `2m`                                                       | no                       | no     |
| `SESSION_JWT_TTL`                     | how long a browser session token is valid                                           | `7d`                                                       | no                       | no     |
| `WEB_SESSION_COOKIE_NAME`             | which cookie carries the browser session                                            | `fb_session`                                               | no                       | no     |
| `WEB_SESSION_COOKIE_SECURE`           | whether that cookie is sent over HTTPS only                                         | `false`                                                    | wherever HTTPS is served | no     |
| `WEB_SESSION_COOKIE_SAME_SITE`        | how that cookie behaves on a cross-site request                                     | `Lax`                                                      | no                       | no     |
| `REPORT_CLEARING_POOL_CORE_SIZE`      | the clearing pool's core thread count                                               | `1`                                                        | no                       | no     |
| `REPORT_CLEARING_POOL_MAX_SIZE`       | the clearing pool's maximum thread count                                            | `2`                                                        | no                       | no     |
| `REPORT_CLEARING_POOL_QUEUE_CAPACITY` | how many clearing tasks wait; the rest are dropped, never run on the request thread | `100`                                                      | no                       | no     |
| `CDC_ENABLED`                         | whether the change-capture engine runs at all                                       | `true`                                                     | no                       | no     |
| `REDIS_URL`                           | where Redis is reached                                                              | `redis://localhost:6379`                                   | no                       | no     |
| `REDIS_COMMAND_TIMEOUT`               | how long a Redis command may take before it is treated as a failure                 | `2s`                                                       | no                       | no     |
| `CDC_SLOT_NAME`                       | the replication slot the engine holds                                               | `finance_ledger_cdc`                                       | no                       | no     |
| `CDC_STREAM_KEY`                      | the stream every change is written to                                               | `ledger.cdc`                                               | no                       | no     |
| `CDC_STREAM_MAX_LENGTH`               | roughly how many entries the stream keeps                                           | `100000`                                                   | no                       | no     |
| `CDC_SNAPSHOT_MODE`                   | whether existing rows are published on first start                                  | `no_data`                                                   | no                       | no     |
| `CDC_HEARTBEAT_INTERVAL`              | how often the slot is moved on with no captured change                              | `30s`                                                      | no                       | no     |
| `CDC_SLOT_MONITOR_INTERVAL`           | how often the slot's retained size is read for the meters                           | `30s`                                                      | no                       | no     |
| `CDC_CATEGORY_CACHE_SIZE`             | how many category entries the resolver holds                                        | `50000`                                                    | no                       | no     |
| `CDC_RECOVERY_SECRET`                 | the header value the recovery operation demands                                     | *(none)*                                                  | yes                      | yes    |
| `MANAGEMENT_PORT`                     | where health, metrics and the recovery operation are served                         | `1010`                                                     | no                       | no     |

A secret belongs in the deployment's secret store, never in a committed file or a log line.

## Notes

- Polling on with no bot token stops startup. Set `TELEGRAM_POLLING_ENABLED=false` to boot without a bot, with
  no way to receive a message.
- The bot token appears in the address every Bot API call is made to, so it reaches anywhere a request URL does.
- `TELEGRAM_API_URL` exists so the service can be pointed at a stand-in for Telegram. A deployment leaves it
  alone ([ADR 0001](adr/0001-telegram-updates-arrive-by-long-polling.md)).
- The database defaults match the local Postgres in
  [`infrastructure/docker-compose.yaml`](../../infrastructure/docker-compose.yaml), which also supplies all
  three database values to the service when it runs under compose.
- `AI_CONNECTOR_GRPC_TARGET` defaults to the port that same file publishes for the AI Connector; under compose
  the service is given the connector's container address instead.
- How a target pointing nowhere shows is on [AI Connector Service](contracts/out/ai-connector.md).
- The keystore `TOKEN_SIGNING_KEYSTORE` defaults to is committed to the repository, and its password with it. A
  deployment supplies its own keystore and password.
- The keystore is read once at startup. A keystore that cannot be opened, or that holds no key under the alias,
  stops startup.
- `TOKEN_SIGNING_KEY_ALIAS` only needs setting when the supplied keystore names its key something other than the
  default.
- **One key pair signs both kinds of token the service issues** — the short-lived one an MCP caller carries and
  the long-lived one a browser session holds. What tells them apart is their audience, and the way each is
  carried. Rotating the key rotates both at once.
- `MCP_JWT_TTL` is both how long a minted MCP token lives and the longest lifetime an arriving one may claim; a
  token claiming more is refused. `SESSION_JWT_TTL` does the same for a browser session. See
  [Agent acting for a user](contracts/in/mcp.md) and
  [A person signing in from a browser](contracts/in/web-session-api.md).
- `WEB_SESSION_COOKIE_SECURE` defaults to off so the service works over plain HTTP when run directly on a
  developer's machine. **Compose turns it on**, because a Telegram sign-in needs an HTTPS tunnel in front of the
  web app and the browser would otherwise discard the cookie. Any deployment served over HTTPS sets it on too.
- `WEB_SESSION_COOKIE_SAME_SITE` can stay at `Lax` because the browser reaches the API on the same origin as the
  page — see [ADR 0014](../../docs/adr/0014-the-web-app-and-the-ledger-are-served-from-one-origin.md).
- `TELEGRAM_LOGIN_MAX_AGE` bounds how long a captured Login Widget payload stays replayable.
- `MCP_ENABLED=false` leaves the service running with the MCP endpoint gone. The session API is unaffected.
- The three `REPORT_CLEARING_POOL_*` values bound the one piece of work this service does off the request thread:
  taking the buttons off a report a web acceptance emptied
  ([Clear the emptied reports](usecases/clear-emptied-reports.md)).
- Raise `REPORT_CLEARING_POOL_QUEUE_CAPACITY` where a deployment serves more than one person's chats.
- `CDC_ENABLED=false` lets the service run against a Postgres that is not at `wal_level=logical` — a developer's
  own, or a managed instance where that setting needs a restart. Switching capture off does not release the
  replication slot: it keeps retaining the log with nothing reading it, until it is dropped by hand with
  `pg_drop_replication_slot('finance_ledger_cdc')` or the bound below invalidates it.
- `max_slot_wal_keep_size` is the database's own setting, applied to Postgres directly
  (`infrastructure/docker-compose.yaml` passes it as `postgres -c max_slot_wal_keep_size=1GB`), not a variable
  this service reads. Once the log the replication slot retains passes it, Postgres invalidates the slot rather
  than keeping the segments. That bounds the database's disk at the cost of every change made during the outage
  that reached the bound, unrecoverably. The service reads the effect through `ledger_cdc_slot_retained_bytes`
  and cannot set the bound itself.
- **A value passed as a `postgres -c` flag cannot be widened without restarting the container.** A command-line
  setting outranks `ALTER SYSTEM` plus `pg_reload_conf()`, so raising `max_slot_wal_keep_size` this way needs a
  restart even though the setting is itself reloadable. `wal_level` cannot be changed without one at all.
- The `finance_ledger_cdc` publication is checked before the engine starts. A database without it — a migration
  that did not run, a restore, a rename — leaves capture down and takes no replication slot, rather than
  streaming from nothing while the health reads up.
- `DB_USER` needs the `REPLICATION` attribute to open a logical replication slot. It works untouched everywhere
  this repository runs, because every Postgres instance here makes `DB_USER` its bootstrap superuser; a managed
  database does not grant that by default.
- The module pins Kafka Connect to the version Debezium is built against, in `gradle.properties`
  (`kafkaConnectVersion`, alongside `debeziumVersion`). Spring Boot's own dependency management otherwise selects
  a newer `kafka-clients`/`connect-api` that dropped the single-argument `SourceTask.commitRecord` the embedded
  engine calls; raising `debeziumVersion` without checking `kafkaConnectVersion` against it risks that mismatch
  again.
- `CDC_RECOVERY_SECRET` reaches the recovery operation only as a header, never a path segment, and is never
  logged — the same treatment `TELEGRAM_BOT_TOKEN` gets everywhere but the outbound call it authenticates.
- A blank or unset `CDC_RECOVERY_SECRET` stops startup, the way a missing bot token and an unopenable keystore
  do. A deploy supplies it whether or not it expects to use the operation, and whether or not capture is on: the
  replication slot outlives `CDC_ENABLED=false`, and rebuilding it is exactly what that state needs. See
  [Rebuilding the slot](contracts/in/operations.md#rebuilding-the-slot).
- `CDC_SNAPSHOT_MODE` at its default publishes no snapshot, so rows that already existed when the engine first
  started never reach [the change stream](contracts/out/change-stream.md) — only changes made from that point on.
