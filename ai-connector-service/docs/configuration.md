# Configuration

Every value below is read from the environment at startup, and the AI provider is all the service is
configured against. The defaults suit a developer's machine.

| Variable          | Sets                                                  | Default                     | Required |
|-------------------|--------------------------------------------------------|-----------------------------|----------|
| `OPENAI_API_KEY`  | the credential the AI provider is called with          | *(empty)*                   | yes      |
| `OPENAI_BASE_URL` | where that provider is reached                         | `https://api.openai.com/v1` | no       |
| `OPENAI_MODEL`    | which model extracts the intents                       | `gpt-4o-mini`               | no       |

## Notes

- The key's default is empty so the service can boot without one; every extraction then fails at the provider
  and the caller is told the service is
  [unavailable](contracts/in/intent-extraction.md#failures). The key is a secret and belongs in the
  deployment's secret store, never in a committed file or a log line.
- The address is the base the chat-completions path hangs off, version segment included. One without it makes
  the provider answer not-found on every call, which reaches the caller as the same unavailable result.
- `OPENAI_BASE_URL` also exists so the service can be pointed at a stand-in for the provider. A deployment
  leaves it alone.
- The model must be able to follow a supplied answer shape. One that cannot turns every message into unknown
  entries rather than into a failure, so a wrong model here looks like a service that understands nothing.
- [`infrastructure/docker-compose.yaml`](../../infrastructure/docker-compose.yaml) supplies only the key and
  takes the other two defaults; it also holds the ports the service is reached on, which are not configurable
  from the environment.
