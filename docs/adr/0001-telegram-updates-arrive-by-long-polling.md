# ADR 0001: Telegram updates arrive by long polling

- **Status:** Accepted
- **Date:** 2026-07-25
- **Source:** [Telegram Update Listener](../implemented/1-plan-telegram-update-listener.md)

## Context

The Bot API delivers updates either by webhook or by a call the bot makes and Telegram holds open. A webhook
needs public HTTPS with a valid certificate in every environment, a developer's laptop included, and it inverts
the direction of every other Telegram interaction the service has — fetching audio and replying are both
outbound.

## Decision

The service polls Telegram for message updates. The address it polls is configuration, so the loop can be
pointed at a stub server. With polling enabled and no bot token, the service refuses to start rather than
polling anonymously.

See [the Telegram contract](../../ledger-service/docs/contracts/in/telegram-updates.md) for what crosses that
boundary.

## Consequences

- No public ingress, TLS termination or DNS entry; a developer runs the real bot from a laptop.
- Tests drive the real client against an in-process stub, so delivery is exercised rather than simulated.
- One instance owns the loop. Two sharing a token compete for the same updates, so scaling out needs webhooks
  or an external dispatcher.
- Must stay true: the poll address stays configurable, and a failed poll is retried rather than ending the loop.
