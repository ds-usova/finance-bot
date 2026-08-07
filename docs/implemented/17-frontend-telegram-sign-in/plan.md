# Frontend module with Telegram sign-in

## Context

Finance Bot is reachable only through Telegram today. Every entry point is a chat message or a callback query,
and the only HTTP surfaces are `/mcp/**`, `/actuator/**` and `/.well-known/jwks.json`. There is no browser
client and no JavaScript anywhere in the repository.

This adds the first one: a standalone React module that signs a user in with the Telegram Login Widget and puts
them into an authenticated shell. The user already exists — `app_user.external_id` holds the stringified
Telegram user id, and `InitializeUserUseCase` already resolves it find-or-create. The web app reuses that
identity rather than introducing a second one.

Scope is deliberately narrow: sign in, know who you are, sign out. No expense list, no spending summary, no
write endpoints. What it delivers is a working browser identity that later features build on.

### Decisions taken

| Decision          | Chosen                                     | Why                                                       |
|-------------------|--------------------------------------------|-----------------------------------------------------------|
| Sign-in mechanism | Telegram Login Widget (standalone website) | Works in any browser, not only inside the Telegram client |
| Scope             | Sign in + empty authenticated shell        | Smallest slice that proves the identity end to end        |
| Module layout     | Top-level `web-app/` with its own build    | Mirrors the repository's one-module-one-build pattern     |
| Session           | A token in an HttpOnly cookie              | Not readable by page scripts                              |
| Origin            | nginx and Vite proxy `/api` to the ledger  | A first-party cookie needs no CORS and no `SameSite=None` |

---

## Backend — `ledger-service`

### Step 1 — Split the signing key out of the MCP token configuration

The MCP token and the session token share one key pair but nothing else. `AccessTokenProperties` owned both the
keystore and the MCP-specific issuer, audience and lifetime, so signing a session token with `MCP_JWT_KEYSTORE`
would have made the configuration page wrong.

**Create** in `adapter/security/`: `TokenSigningProperties`, `TokenSigningKeys`, `SessionTokenProperties`,
`SessionTokenMinter` (no `mrf` claim).

**Modify** `AccessTokenProperties` (keeps issuer, audience, lifetime), `AccessTokenMinter` (takes
`TokenSigningKeys`, keeps its signatures), `application.yaml`.

**Tests** `SessionTokenMinterTest`, `TokenSigningKeysTest`, and the construction change in
`AccessTokenMinterTest`.

### Step 2 — Verify the Telegram Login Widget payload

Lives in `adapter/telegram`: the algorithm is Telegram's, it reads the bot token, and it knows nothing about
tokens or cookies.

**Create** `TelegramLoginProperties`, `TelegramLoginVerifier`, `TelegramLoginRejectedException`, and the
`TelegramLoginPayloads` test fixture that signs a payload the way Telegram does.

The check string is every received field except `hash`, sorted by key, joined by newlines — an unknown field
Telegram adds later is signed by the widget and so must be included.

**Tests** `TelegramLoginVerifierTest`: a genuine payload passes; a tampered field, a foreign bot token, an added
field, a missing or non-hex hash, a stale or future `auth_date`, and a blank or absent id all fail.

### Step 3 — A second security filter chain for `/api/**`

The existing chain's audience is `mcp-adapter` and its lifetime validator caps a token at two minutes. A session
token cannot pass it, and must not.

**Modify** `SecurityConfiguration`: a chain ordered first and matched on `/api/**`, with CSRF on, stateless
sessions, and a decoder of its own; `@Order(2)` on the MCP chain, otherwise unchanged.

**Create** `SessionCookieBearerTokenResolver` and `WebSessionProperties`.

The resolver is **never a bean**: a lone `BearerTokenResolver` in the context is picked up by every
resource-server chain, which leaves `/mcp` reading a cookie instead of its header.

### Step 4 — `SessionController`

**Create** in `adapter/web/`: `SessionController` (`POST`, `GET`, `DELETE /api/session`), `SessionResponse`,
`WebExceptionHandler`.

No new use case, port or DTO. The whole flow is adapter work over the existing `InitializeUserPort`.

**Tests** `SessionControllerTest` — a `@WebMvcTest` slice asserting the cookie's attributes, that the verified
external id reaches the port, that a rejected payload stores nothing and sets no cookie, and that a write with
no CSRF token is refused.

### Step 5 — System test: the two token worlds do not mix

**Create** `WebSessionSystemTest`, with its own bot token. It proves the sign-in stores a user, that signing in
twice stores one, that a session cookie is refused at `/mcp`, that an MCP token is refused as a session cookie,
and that a valid MCP token still reaches `/mcp` — the regression guard for the chain ordering.

### Step 6 — Ledger documentation

`configuration.md` gains the new settings and loses the MCP-specific keystore names; `contracts/in/web-session-api.md`
is created as the owning page; `usecases/initialize-a-new-user.md` and `conventions/orientation.md` gain the new
inbound path.

---

## Frontend — `web-app/`

### Step 7 — Scaffold

Vite, React 19, TypeScript `strict`, React Router, Vitest with jsdom and Testing Library, ESLint and Prettier.
`verify` is the gate; coverage sits outside it, as the Gradle modules' coverage guardrail sits outside `check`.

### Step 8 — API client and auth state

`api/client.ts` is the only place `fetch` is called: cookies always, the CSRF header on every write, and a typed
`ApiError` carrying the status. `auth/AuthContext.tsx` reads the session on mount and exposes `signIn` and
`signOut`.

### Step 9 — Login button, routes, shell

`TelegramLoginButton` injects Telegram's widget script under a uniquely-named global callback and removes it on
unmount. `RequireAuth` renders neither branch while the session is still being read, so a reload never flashes
the sign-in page.

### Step 10 — Container, compose, ignores

An nginx image built from the repository root, proxying `/api` to the ledger, published on 1003.

### Step 11 — CI

A second job in `.github/workflows/build.yml`, since the JVM matrix cannot absorb a module with no `gradlew`.

---

## Documentation and repository wiring

### Step 12 — Conventions

`docs/conventions/node-build.md` at the repository tier, the six-file module tier under `web-app/docs/conventions/`,
the module's README, configuration, use case and outbound contract, and the root README's diagrams and services
table.

### Step 13 — ADRs

- [ADR 0013](../../ledger-service/docs/adr/0013-a-browser-session-is-a-cookie-borne-token-with-its-own-audience.md)
- [ADR 0014](../adr/0014-the-web-app-and-the-ledger-are-served-from-one-origin.md)

---

## Verification

| What                      | How                                                                 |
|---------------------------|---------------------------------------------------------------------|
| Ledger suite              | `tools/agent-test/agent-test.sh --module ledger-service --all`      |
| Ledger coverage guardrail | `tools/agent-test/agent-test.sh --module ledger-service --coverage` |
| Frontend                  | `npm run verify` and `npm run verify:coverage`, from `web-app/`     |
| Whole stack               | `docker compose -f infrastructure/docker-compose.yaml up --build`   |

End-to-end sign-in cannot be exercised on `localhost` — see
[Agent Configuration](../../web-app/docs/conventions/agent.md#what-cannot-be-exercised-locally).
