# ADR 0009: The connector does not authenticate its caller

- **Status:** Superseded by [ADR 0017](0017-the-connector-verifies-its-caller-token-and-keeps-the-message-it-names.md)
- **Date:** 2026-08-02
- **Source:** [The Ledger's Tools on the Chat Client](../implemented/10-ledger-tools-on-the-chat-client/plan.md)

## Context

Two services call each other and neither runs an identity provider. The caller's token is minted and validated
by the Ledger Service for its own tool endpoint ([ADR 0007](../../ledger-service/docs/adr/0007-an-mcp-caller-is-identified-by-a-signed-token-not-a-tool-argument.md)),
which is what keeps one user's spending out of another's ledger. Nothing mints a token for a *service*, so the
connector has no way to establish who is calling it, only who the call claims to act for.

## Decision

The connector requires a token on every extraction call and forwards it unread. It verifies neither the
signature nor the claims: a call it cannot authenticate still reaches the model, and fails at the ledger when
the turn tries to record something.

Service-to-service authentication is deferred, not rejected. The intended shape is an identity provider —
Keycloak or equivalent — issuing tokens for both the caller and the user, with each service validating against
that provider's key set instead of the ledger's own.

## Consequences

- Whoever reaches the connector's gRPC port can spend its model budget. Keep the port off public networks; it is
  published to the host in `infrastructure/docker-compose.yaml` for local work only.
- The failure is state-dependent: with the tool list already cached, an unauthenticated turn whose model answers
  without calling a tool succeeds and bills a provider call.
- User data is unaffected. Identity is still enforced where it is spent, so no forged caller records an expense.
- Taking this up means a new token issuer for both services, and the connector gaining a validator — its first
  reason to read the token it currently only carries.
- **2026-08-12:** "User data is unaffected" no longer holds. `ledger-service` now republishes descriptions,
  merchants and amounts outside the database onto a Redis stream, keyed by internal `user_id` with no Telegram
  identity beside them. Reaching that stream — not the connector's gRPC port — is what now exposes that data,
  pseudonymously.
