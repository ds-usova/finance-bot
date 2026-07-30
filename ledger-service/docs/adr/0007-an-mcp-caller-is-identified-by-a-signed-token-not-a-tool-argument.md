# ADR 0007: An MCP caller is identified by a signed token, not a tool argument

- **Status:** Accepted
- **Date:** 2026-07-31
- **Source:** [MCP Adapter with a Create Expense Proposal Tool](../../../docs/implemented/8-plan-mcp-adapter-create-expense-proposal.md)

## Context

An MCP tool's arguments are filled in by a model, from content the model has read. A prompt injection in that
content can dictate any of them. The obvious shape for `create_expense_proposal` — a `userExternalId` argument
beside the category and the amount — therefore lets whoever controls the text the agent is reading choose whose
ledger the spending lands in.

The service also had no `SecurityFilterChain` at all: `spring-boot-starter-security` and the resource-server
starter were on the classpath, and every endpoint sat behind Boot's default. Something had to say who may call
`/mcp` before a tool could be exposed on it.

## Decision

The caller's identity travels as a short-lived RS256 JWT on the HTTP transport and is read server-side from the
validated token's `sub` claim. No tool's input schema carries an identity argument.

The Ledger Service mints and validates that token itself, with a key pair loaded at startup from a PKCS#12
keystore and published as a JWK Set at `/.well-known/jwks.json`.

## Consequences

- A prompt injection can misstate a category or an amount — both visible to a human before a proposal becomes an
  expense — but cannot address another user.
- A tool can only act as the token's subject, so the rule holds for every tool added later, not just this one.
- An unauthenticated, expired or wrongly-addressed call is a transport-level 401 with no body, so nothing about
  why validation failed reaches a model's context.
- Rotation is a new key in the keystore plus a restart: clients re-read the JWK Set and need no redeploy.
- Replicas share one keystore rather than generating their own keys, so a token minted by one validates on
  another and a restart does not invalidate live tokens.
- The service is now both issuer and audience of its own tokens. Moving to a real identity provider later means
  replacing the minter and pointing the decoder at someone else's JWK Set — the tools and the use cases behind
  them are unaffected.
