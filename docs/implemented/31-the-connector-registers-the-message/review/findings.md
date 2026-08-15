# Review: The Connector Registers the Message

**One manual test open; nothing else.**

## Manual test

**[ ] `infrastructure` — the ledger's change stream still runs under its own non-superuser role (design F18, plan Q4)**

- **Given** the `finance-bot-postgres` volume wiped, `infrastructure/.env` carrying the six new database
  variables, and `docker compose up` brought up from `infrastructure/`
- **When** both services report healthy and an expense is recorded through the bot
- **Then** `ledger-service`'s `/actuator/health` shows the change-stream component `UP` and the change reaches the
  Redis stream
