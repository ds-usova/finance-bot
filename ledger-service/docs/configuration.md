# Configuration

Every value below is read from the environment at startup. The defaults suit a developer's machine; a
deployment supplies its own.

| Variable                              | Sets                                                                                | Default                                      | Required                 | Secret |
|---------------------------------------|-------------------------------------------------------------------------------------|----------------------------------------------|--------------------------|--------|
| `DB_JDBC_URL`                         | the database the service reads and writes                                           | `jdbc:postgresql://localhost:5432/ledger-db` | yes                      | no     |
| `DB_USER`                             | the database account                                                                | `ledger-user`                                | yes                      | no     |
| `DB_PASSWORD`                         | the password for that account                                                       | *(a local placeholder)*                      | yes                      | yes    |
| `TELEGRAM_BOT_TOKEN`                  | which bot the service collects messages for and acts as                             | *(none)*                                     | when polling is on       | yes    |
| `TELEGRAM_POLLING_ENABLED`            | whether the service collects Telegram messages at all                               | `true`                                       | no                       | no     |
| `TELEGRAM_API_URL`                    | where Telegram's Bot API is reached                                                 | Telegram's own address                       | no                       | no     |
| `TELEGRAM_LOGIN_MAX_AGE`              | how old a Telegram sign-in may be and still be accepted                             | `1d`                                         | no                       | no     |
| `AI_CONNECTOR_GRPC_TARGET`            | where the AI Connector's gRPC server is reached                                     | `static://localhost:1001`                    | no                       | no     |
| `MCP_ENABLED`                         | whether the MCP server endpoint is served at all                                    | `true`                                       | no                       | no     |
| `TOKEN_SIGNING_KEYSTORE`              | where the keystore signing every token the service issues is read from              | the committed development keystore           | yes                      | no     |
| `TOKEN_SIGNING_KEYSTORE_PASSWORD`     | the password for that keystore                                                      | *(a local placeholder)*                      | yes                      | yes    |
| `TOKEN_SIGNING_KEY_ALIAS`             | which key pair in the keystore signs and verifies those tokens                      | `mcp-signing`                                | with a supplied keystore | no     |
| `MCP_JWT_TTL`                         | how long a minted MCP token is valid                                                | `2m`                                         | no                       | no     |
| `SESSION_JWT_TTL`                     | how long a browser session token is valid                                           | `7d`                                         | no                       | no     |
| `WEB_SESSION_COOKIE_NAME`             | which cookie carries the browser session                                            | `fb_session`                                 | no                       | no     |
| `WEB_SESSION_COOKIE_SECURE`           | whether that cookie is sent over HTTPS only                                         | `false`                                      | wherever HTTPS is served | no     |
| `WEB_SESSION_COOKIE_SAME_SITE`        | how that cookie behaves on a cross-site request                                     | `Lax`                                        | no                       | no     |
| `REPORT_CLEARING_POOL_CORE_SIZE`      | the clearing pool's core thread count                                               | `1`                                          | no                       | no     |
| `REPORT_CLEARING_POOL_MAX_SIZE`       | the clearing pool's maximum thread count                                            | `2`                                          | no                       | no     |
| `REPORT_CLEARING_POOL_QUEUE_CAPACITY` | how many clearing tasks wait; the rest are dropped, never run on the request thread | `100`                                        | no                       | no     |

A secret belongs in the deployment's secret store, never in a committed file or a log line.

## Notes

- Polling on with no bot token stops startup. Set `TELEGRAM_POLLING_ENABLED=false` to boot without a bot — the
  service then runs with no way to receive a message, which is what local database work wants.
- The bot token appears in the address every Bot API call is made to, so it reaches anywhere a request URL does —
  further than a credential carried in a header.
- `TELEGRAM_API_URL` exists so the service can be pointed at a stand-in for Telegram. A deployment leaves it
  alone; see [ADR 0001](adr/0001-telegram-updates-arrive-by-long-polling.md) for why every Telegram
  interaction is an outbound call to this address.
- The database defaults match the local Postgres in
  [`infrastructure/docker-compose.yaml`](../../infrastructure/docker-compose.yaml), which also supplies all
  three database values to the service when it runs under compose.
- `AI_CONNECTOR_GRPC_TARGET` defaults to the port that same file publishes for the AI Connector; under compose
  the service is given the connector's container address instead. A target pointing nowhere shows in
  `/actuator/health` — see [AI Connector Service](contracts/out/ai-connector.md).
- The keystore `TOKEN_SIGNING_KEYSTORE` defaults to is committed to the repository, and its password with it. It
  is a development convenience and nothing more: a deployment supplies its own keystore and password.
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
- The session token is verified in process against the public half of the signing key, so reading it makes no
  request to the published key set.
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
