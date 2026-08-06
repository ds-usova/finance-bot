# ADR 0013: A browser session is a cookie-borne token with its own audience

- **Status:** Accepted
- **Date:** 2026-08-06
- **Source:** [Frontend module with Telegram sign-in](../../../docs/17-frontend-telegram-sign-in/plan.md)

## Context

The service already mints and validates RS256 tokens, for the MCP endpoint
([ADR 0007](0007-an-mcp-caller-is-identified-by-a-signed-token-not-a-tool-argument.md)). A browser client needs
an identity too, and the obvious move is to reuse that machinery.

It does not fit as it stands. The MCP token's audience is `mcp-adapter`, its lifetime is capped at two minutes,
and both are enforced by validators on the one filter chain the service has. A browser session that survives a
page reload cannot pass either check, and must not be able to: a token that opens a browser session must never
open a tool call, and the reverse.

A token a page script can read is a token any script on that page can steal.

## Decision

A browser session is a long-lived RS256 token with audience `web-app`, carried in an HttpOnly cookie.

- A second `SecurityFilterChain`, ordered first and matched on `/api/**`, resolves the token from that cookie
  alone and validates it with a decoder of its own — issuer, audience, expiry, and a maximum lifetime of its own.
  The MCP chain is unchanged behind it.
- Under `/api` the `Authorization` header is not read at all, so a token for another audience cannot be
  presented by hand.
- One key pair signs both kinds of token. The audience is the only thing that tells them apart.
- The session decoder verifies against the public key already in memory, rather than fetching the published key
  set over HTTP as the MCP decoder does.

## Consequences

- One keystore and one key id still serve everything, so the published key set is unchanged and a rotation
  rotates both token kinds at once.
- Neither token kind validates in the other's chain. A crossover is a 401, and the system test asserts it in
  both directions.
- The cookie makes the session API forgeable from another site unless it is CSRF-protected, so it is — including
  the sign-in itself. The MCP chain still has no CSRF protection and needs none: it carries no cookie.
- A session cannot be revoked before it expires. Signing out clears the browser's cookie; a copy taken earlier
  stays valid for the rest of its lifetime. Shortening the lifetime is the only lever.
- The keystore configuration is named for signing rather than for MCP, since it is no longer specific to one
  endpoint.
- Must stay true: a `BearerTokenResolver` is never registered as a bean. A single one in the context is picked
  up by every resource-server chain, which would leave the MCP endpoint reading a cookie instead of its header.
