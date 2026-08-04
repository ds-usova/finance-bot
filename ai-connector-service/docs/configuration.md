# Configuration

Every value below is read from the environment at startup: the AI provider the service reads a message with, and
the ledger it records the result in. The defaults suit a developer's machine.

| Variable          | Sets                                          | Default                     | Required | Secret |
|-------------------|-----------------------------------------------|-----------------------------|----------|--------|
| `OPENAI_API_KEY`  | the credential the AI provider is called with | *(none)*                    | yes      | yes    |
| `OPENAI_BASE_URL` | where that provider is reached                | `https://api.openai.com/v1` | no       | no     |
| `OPENAI_MODEL`    | which model reads the message                 | `gpt-4o-mini`               | no       | no     |
| `LEDGER_MCP_URL`  | where the ledger's tools are reached          | `http://localhost:1000`     | yes      | no     |

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
- [`infrastructure/docker-compose.yaml`](../../infrastructure/docker-compose.yaml) supplies the key and the
  ledger's container address, and takes the other two defaults; it also holds the ports the service is reached
  on, which are not configurable from the environment.
