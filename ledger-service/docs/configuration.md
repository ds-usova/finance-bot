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

## Notes

- Polling on with no bot token stops startup. Set `TELEGRAM_POLLING_ENABLED=false` to boot without a bot — the
  service then runs with no way to receive a message, which is what local database work wants.
- The bot token is a secret and it appears in the address every Bot API call is made to. It belongs in the
  deployment's secret store, never in a committed file or a log line.
- `TELEGRAM_API_URL` exists so the service can be pointed at a stand-in for Telegram. A deployment leaves it
  alone; see [ADR 0001](../../docs/adr/0001-telegram-updates-arrive-by-long-polling.md) for why every Telegram
  interaction is an outbound call to this address.
- The database defaults match the local Postgres in
  [`infrastructure/docker-compose.yaml`](../../infrastructure/docker-compose.yaml), which also supplies all
  three database values to the service when it runs under compose.
