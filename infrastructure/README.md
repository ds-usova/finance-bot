# Infrastructure

The local runtime: [`docker-compose.yaml`](docker-compose.yaml) runs every service in the
[root README](../README.md#services) and what they depend on. Values come from `.env`, whose shape is
[`.env.example`](.env.example).

## Postgres

One `pgvector/pgvector:pg18` instance holds a database per service. Each service reaches its own database under a
role that owns that database and nothing else; the image's `POSTGRES_USER` and `POSTGRES_DB` are the bootstrap
superuser and its database, used by nothing else.

[`postgres/init/create-databases.sh`](postgres/init/create-databases.sh) creates the roles and databases:

| Service              | Variables                                                                               | The role gets                          |
|----------------------|-----------------------------------------------------------------------------------------|----------------------------------------|
| Ledger Service       | `FINANCE_BOT_LEDGER_DB`, `FINANCE_BOT_LEDGER_DB_USER`, `FINANCE_BOT_LEDGER_DB_PASSWORD` | `REPLICATION`, for its change capture  |
| AI Connector Service | `FINANCE_BOT_AI_DB`, `FINANCE_BOT_AI_DB_USER`, `FINANCE_BOT_AI_DB_PASSWORD`             | the `vector` extension in its database |

**The image runs that script on an empty volume only.** A `finance-bot-postgres` volume initialized before this
arrangement gets no such databases and no roles. Wiping the volume is what puts the stack on it, and it discards
the data already there.

The instance is started with `wal_level=logical` and `max_slot_wal_keep_size`, which the ledger's change capture
needs; what each costs is [the ledger's configuration](../ledger-service/docs/configuration.md).
