# ADR 0001: Telegram updates arrive by long polling

- **Status:** Accepted
- **Date:** 2026-07-25
- **Source:** [Telegram Update Listener](../implemented/1-plan-telegram-update-listener.md)

## Context

The Bot API delivers updates two ways: a webhook Telegram calls, or a call the bot makes and Telegram holds
open until an update arrives. A webhook needs a publicly reachable HTTPS endpoint with a valid certificate in
every environment the bot runs in, a developer's laptop included, and it inverts the direction of every other
Telegram interaction the service will have — downloading audio and sending the confirmation reply are both
outbound calls.

## Decision

The service polls Telegram for updates and asks for message updates only. The base URL it polls is
configuration, so the whole loop can be pointed at a stub server. Polling is a startup switch; with polling on
and no bot token configured, the service refuses to start rather than polling anonymously.

See [the Telegram contract](../../ledger-service/docs/contracts/in/telegram-updates.md) for what crosses that
boundary and [the use case](../../ledger-service/docs/usecases/handle-incoming-message.md) for what happens to
a message once it arrives.

## Consequences

Deployment needs no public ingress, no TLS termination and no DNS entry for the bot, and a developer runs the
real bot against real Telegram from a laptop. Because delivery is an outbound call to a configurable address,
tests drive the real client end to end against an in-process stub server: update delivery is exercised rather
than simulated.

One instance holds the poll loop. Two instances sharing a bot token compete for the same updates, so scaling
out horizontally is not available under this decision — it would need webhook delivery or an external
dispatcher.

What must stay true: the poll address stays configurable, and a failed poll is retried rather than allowed to
end the loop.
