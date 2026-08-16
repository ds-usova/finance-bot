# ADR 0017: The connector verifies its caller token and keeps the message it names

- **Status:** Accepted
- **Date:** 2026-08-15
- **Source:** [The Connector Registers the Message](../implemented/31-the-connector-registers-the-message/plan.md)
- **Supersedes:** [0009](0009-the-connector-does-not-authenticate-its-caller.md)

## Context

Putting a person's earlier messages in front of the model needs the connector to keep what it is handed, and so
to know whose message it is. Both keys ride the token the ledger mints — `sub` the internal user id, `imi` the
message that started the turn ([ADR 0015](../../ledger-service/docs/adr/0015-a-turn-is-named-by-the-message-that-started-it-not-by-a-value-minted-beside-it.md))
— but reading them off an unverified token would let whoever reaches the gRPC port file text under any person's
id. The ledger publishes the signing key, so verifying needs no new issuer and no new secret.

## Decision

The connector verifies every caller token against the ledger's published key set — signature, expiry, issuer,
audience — and reads `sub` and `imi` from it. A token that does not verify is refused as unauthenticated before
the model is reached; a key set unfetchable inside its timeout answers unavailable. The connector keeps a store
of its own, a database on the shared Postgres instance, and registers each message under those two keys before
the model is called. That store never fails a turn: a refused write is logged and the turn runs. Service-to-service
authentication stays deferred: what is established is who the call acts for, not who calls.

## Consequences

- A forged or expired token no longer reaches the model or spends its budget.
- The connector depends on the ledger for its key set as well as its tools. The set is cached once fetched, so a
  ledger outage refuses only a connector yet to fetch it. Rotating the ledger's signing key needs no change here.
- Connector writes land on the ledger's Postgres cluster and count against the log its replication slot retains.
- What a person wrote outlives the turn; a retention bound on a timer is the only thing that removes it.
- `MEMORY_ENABLED=false` switches all of it off together, and the connector runs as it did before this decision.
