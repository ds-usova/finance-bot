# ADR 0017: The connector verifies its caller token and keeps the message it names

- **Status:** Accepted
- **Date:** 2026-08-15
- **Source:** [The Connector Registers the Message](../../../docs/implemented/31-the-connector-registers-the-message/plan.md)
- **Supersedes:** [0009](../../../docs/adr/0009-the-connector-does-not-authenticate-its-caller.md)

## Context

The connector forwarded its caller token unread and held nothing between calls
([ADR 0009](../../../docs/adr/0009-the-connector-does-not-authenticate-its-caller.md)). Reading a message well
enough to be useful needs the earlier messages of the same person in front of the model, which needs the
connector to keep what it is handed, which needs it to know whose message it is.

Both keys are already on the token the ledger mints: `sub` is the internal user id, `imi` names the message that
started the turn
([ADR 0015](../../../ledger-service/docs/adr/0015-a-turn-is-named-by-the-message-that-started-it-not-by-a-value-minted-beside-it.md)).
Reading them off an unverified token would let whoever reaches the gRPC port file text under any person's id.
The ledger publishes the key that signs those tokens, so verifying them needs no new issuer and no new secret.

## Decision

The connector verifies every caller token against the ledger's published key set — signature, expiry, issuer and
audience, what the ledger's own decoder checks — and reads `sub` and `imi` from it. A token that does not verify
is refused as unauthenticated before the model is reached. A key set that cannot be fetched inside its timeout
answers unavailable, since the ledger the turn needs for its tools is unreachable.

The connector keeps a store of its own: a database on the shared Postgres instance, one row per message,
holding the text under the person and the message the token names. The message is registered before the model is
called, and a redelivered message registers once.

That store never fails a turn. A refused write is logged and swallowed, the turn runs as it did, and that one
message goes unremembered.

Service-to-service authentication is still deferred. Nothing mints a token for a *service*, so what the
connector establishes is who the call acts for, not who is calling.

## Consequences

- A forged or expired token no longer reaches the model, so the model budget is no longer spendable by whoever
  reaches the gRPC port.
- The connector is coupled to the ledger for a second reason: it now reads the ledger's key set as well as its
  tools. Once fetched, the key set is served from cache, so a ledger that goes down refuses only the turns of a
  connector that has not fetched it yet.
- Rotating the ledger's signing key rotates what the connector trusts, with no change here.
- The connector's writes land on the ledger's Postgres cluster, so they count against the log its replication
  slot retains.
- What a person wrote now outlives the turn. A retention bound deletes each message on a timer, and that bound
  is the only thing that removes one.
- `MEMORY_ENABLED` switches all of it off together, and the connector then runs the way it ran before this
  decision: the token forwarded unread, nothing kept. That is what a developer without the database runs.
