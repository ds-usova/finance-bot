# Configuration

Every value below is read from the environment at startup: the AI provider the service reads a message with, the
ledger it records the result in, and the database it keeps each message in. The defaults suit a developer's
machine.

| Variable                | Sets                                                       | Default                                       | Required          | Secret |
|-------------------------|------------------------------------------------------------|-----------------------------------------------|-------------------|--------|
| `OPENAI_API_KEY`        | the credential the AI provider is called with              | *(none)*                                      | yes               | yes    |
| `OPENAI_BASE_URL`       | where that provider is reached                             | `https://api.openai.com/v1`                   | no                | no     |
| `OPENAI_MODEL`          | which model reads the message                              | `gpt-4o-mini`                                 | no                | no     |
| `LEDGER_MCP_URL`        | where the ledger's tools are reached                       | `http://localhost:1000`                       | yes               | no     |
| `MEMORY_ENABLED`        | whether the service keeps the messages it is handed at all | `true`                                        | no                | no     |
| `DB_JDBC_URL`           | the database those messages are kept in                    | `jdbc:postgresql://localhost:5432/finance_ai` | when memory is on | no     |
| `DB_USER`               | the database account                                       | *(none)*                                      | when memory is on | no     |
| `DB_PASSWORD`           | the password for that account                              | *(none)*                                      | when memory is on | yes    |
| `LEDGER_TOKEN_ISSUER`   | the issuer a caller token must name                        | `ledger-service`                              | no                | no     |
| `LEDGER_TOKEN_AUDIENCE` | the audience a caller token must name                      | `mcp-adapter`                                 | no                | no     |
| `LEDGER_JWKS_TIMEOUT`   | how long a fetch of the ledger's key set may take          | `3s`                                          | no                | no     |
| `MEMORY_MAX_AGE`        | how long a message is kept                                 | `365d`                                        | no                | no     |
| `MEMORY_PURGE_INTERVAL` | how often messages past `MEMORY_MAX_AGE` are deleted       | `1d`                                          | no                | no     |
| `MEMORY_PURGE_BATCH`    | how many messages one purge transaction deletes            | `1000`                                        | no                | no     |

A secret belongs in the deployment's secret store, never in a committed file or a log line.

## Notes

- The address is the base the chat-completions path hangs off, version segment included. One without it makes
  the provider answer not-found on every call, which reaches the caller as the same unavailable result.
- `OPENAI_BASE_URL` also exists so the service can be pointed at a stand-in for the provider. A deployment
  leaves it alone.
- The model must be able to call tools. One that cannot answers with text instead, so the turn succeeds having
  recorded nothing — a wrong model here looks like a service that understands nothing.
- `LEDGER_MCP_URL` is the ledger's base address; the tool path is fixed. Its default reaches a ledger on the
  same machine and nothing else, so a deployment supplies it. A wrong one no longer fails the turn per expense:
  the tool list itself cannot be read, which fails the whole turn as
  [unavailable](contracts/out/ledger-mcp.md#failures) before any expense is attempted.
- The ledger can switch its tool endpoint off, which has the same effect as a wrong address here.
- `MEMORY_ENABLED=false` runs the service with no database at all: no message is stored, no purge runs, and the
  caller's token reaches the ledger unread. None of the `when memory is on` variables is read, so it is what a
  developer without a database runs against.
- The ledger's key set is read at `LEDGER_MCP_URL/.well-known/jwks.json`, so pointing the tools somewhere points
  the key set with them.
- `LEDGER_TOKEN_ISSUER` and `LEDGER_TOKEN_AUDIENCE` must name what the ledger mints its caller tokens with.
  Either one wrong refuses every call as unauthenticated, before the provider is reached.
- A `MEMORY_MAX_AGE` or `MEMORY_PURGE_BATCH` that is not positive stops startup. A batch of zero deletes nothing,
  which would leave retention silently never running.
- [`infrastructure/docker-compose.yaml`](../../infrastructure/docker-compose.yaml) supplies the key, the
  ledger's container address and the database on the shared Postgres, and takes the other defaults; it also
  holds the ports the service is reached on, which are not configurable from the environment.
