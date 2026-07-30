# Configuration

Every value below is read from the environment at startup. The defaults suit a developer's machine; a
deployment supplies its own.

| Variable                   | Sets                                                        | Default                                       | Required           |
|----------------------------|-------------------------------------------------------------|-----------------------------------------------|--------------------|
| `DB_JDBC_URL`              | the database the service reads and writes                   | `jdbc:postgresql://localhost:5432/ledger-db`  | yes                |
| `DB_USER`                  | the database account                                        | `ledger-user`                                 | yes                |
| `DB_PASSWORD`              | the password for that account                               | *(a local placeholder)*                       | yes                |
| `TELEGRAM_BOT_TOKEN`       | which bot the service collects messages for and acts as     | *(none)*                                      | when polling is on |
| `TELEGRAM_POLLING_ENABLED` | whether the service collects Telegram messages at all       | `true`                                        | no                 |
| `TELEGRAM_API_URL`         | where Telegram's Bot API is reached                         | Telegram's own address                        | no                 |
| `AI_CONNECTOR_GRPC_TARGET` | where the AI Connector's gRPC server is reached             | `static://localhost:1001`                     | no                 |
| `MCP_ENABLED`               | whether the MCP server endpoint is served at all             | `true`                                        | no                 |
| `MCP_JWT_KEYSTORE`          | where the MCP token signing keystore is read from            | the committed development keystore            | yes                |
| `MCP_JWT_KEYSTORE_PASSWORD` | the password for that keystore                               | *(a local placeholder)*                       | yes                |
| `MCP_JWT_KEY_ALIAS`         | which key pair in the keystore signs and verifies MCP tokens | `mcp-signing`                                 | with a supplied keystore |
| `MCP_JWT_TTL`               | how long a minted MCP token is valid                         | `2m`                                          | no                 |

## Notes

- Polling on with no bot token stops startup. Set `TELEGRAM_POLLING_ENABLED=false` to boot without a bot — the
  service then runs with no way to receive a message, which is what local database work wants.
- The bot token is a secret and it appears in the address every Bot API call is made to. It belongs in the
  deployment's secret store, never in a committed file or a log line.
- `TELEGRAM_API_URL` exists so the service can be pointed at a stand-in for Telegram. A deployment leaves it
  alone; see [ADR 0001](adr/0001-telegram-updates-arrive-by-long-polling.md) for why every Telegram
  interaction is an outbound call to this address.
- The database defaults match the local Postgres in
  [`infrastructure/docker-compose.yaml`](../../infrastructure/docker-compose.yaml), which also supplies all
  three database values to the service when it runs under compose.
- `AI_CONNECTOR_GRPC_TARGET` defaults to the port that same file publishes for the AI Connector; under compose
  the service is given the connector's container address instead. A target pointing nowhere shows in
  `/actuator/health` — see [AI Connector Service](contracts/out/ai-connector.md).
- The keystore `MCP_JWT_KEYSTORE` defaults to is committed to the repository, and its password with it. It is a
  development convenience and nothing more: a deployment supplies its own keystore, and its password, from its
  secret store.
- The keystore is read once at startup. A keystore that cannot be opened, or that holds no key under the alias,
  stops startup.
- `MCP_JWT_KEY_ALIAS` only needs setting when the supplied keystore names its key something other than the
  default.
- `MCP_JWT_TTL` is both how long a minted token lives and the longest lifetime an arriving token may claim; a
  token claiming more is refused. See
  [Agent acting for a user — the expense proposal tool](contracts/in/mcp.md).
- `MCP_ENABLED=false` leaves the service running with the MCP endpoint gone.
