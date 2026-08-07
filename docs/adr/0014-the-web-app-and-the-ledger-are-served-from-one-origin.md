# ADR 0014: The web app and the ledger are served from one origin

- **Status:** Accepted
- **Date:** 2026-08-06
- **Source:** [Frontend module with Telegram sign-in](../17-frontend-telegram-sign-in/plan.md)

## Context

The browser session is a cookie
([ADR 0013](../../ledger-service/docs/adr/0013-a-browser-session-is-a-cookie-borne-token-with-its-own-audience.md)),
and the web app and the ledger are separate containers on separate ports. A cookie sent from a page on one origin
to an API on another is a cross-site cookie: it needs `SameSite=None`, which browsers only honour together with
`Secure`, which plain HTTP cannot provide. That makes local development impossible without TLS, and adds a
cross-origin allow-list to configure and keep correct.

## Decision

The browser knows one origin. The web app's nginx proxies `/api` to the ledger, and the Vite dev server proxies
it the same way, so every API call leaves the page as a same-origin request.

No CORS configuration exists anywhere in the repository.

## Consequences

- The session cookie is first-party, so `SameSite=Lax` is sufficient and works over plain HTTP locally.
- There are no preflight requests, and no allow-list of origins that can drift out of date.
- The ledger's published port stays a development convenience. The browser never uses it.
- Adding a browser client on another origin is a deliberate change — a CORS configuration and a cookie policy
  that does not exist yet — rather than a config edit.
- The proxy is on the path of every API call. Its failure is indistinguishable, from the browser, from the
  ledger's own.
- nginx resolves the ledger's address when it starts, so the web app container depends on the ledger being
  there.
- Must stay true: the web app is never served from a different host than the API it calls, in any environment.
