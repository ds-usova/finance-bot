# ADR 0016: An embedded engine holds the log position until Redis acknowledges, bounded by the database

- **Status:** Accepted
- **Date:** 2026-08-11
- **Source:** [Broadcast Ledger Changes to Redis](../23-broadcast-ledger-changes-to-redis/plan.md)

## Context

Republishing `ledger-service`'s own row changes needs something that reads Postgres's write-ahead log and must
not lose a change once accepted, the same guarantee the ledger itself carries everywhere else. A sidecar
container — Debezium Server, or Kafka Connect — was the tempting alternative: it is the more common way to run
Debezium, but it sits outside the module's Testcontainers-based integration-test culture, and holding a log
position open indefinitely against a failing downstream is not a pattern anything in this module's background
work does today — the one precedent, clearing report buttons, drops work it is safe to drop.

## Decision

The Debezium embedded engine runs inside the `ledger-service` JVM, on a thread of its own, rather than a sidecar.
It commits its read position back to Postgres only after Redis acknowledges an event, retrying indefinitely
rather than skipping one. Postgres bounds how much log a replication slot may retain
(`max_slot_wal_keep_size`); past that bound the database invalidates the slot rather than keeping the segments.

## Consequences

- A Redis outage grows the database's retained log, not the service's memory — but only up to the bound.
- An outage that outlasts the bound loses every ledger change made during it, permanently; the recovery
  operation only replaces the invalidated slot, it never replays the gap.
- The trade is disk safety for stream completeness: nothing reads the stream yet, so today the cost is a gap in
  an unread pipeline, not a broken guarantee to a consumer.
