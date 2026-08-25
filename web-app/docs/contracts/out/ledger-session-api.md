# Ledger Service — the session API

Where the web app gets its identity. It opens a session from a Telegram sign-in, asks who is signed in, and ends
the session.

- **Counterpart:** [the ledger's session API](../../../../ledger-service/docs/contracts/in/web-session-api.md),
  which owns what each request takes and answers with
- **Specification:** [`openapi/ledger-api.yaml`](../../../../openapi/ledger-api.yaml), from which this module
  generates the types it declares the calls against
- **Transport:** HTTP on `/api/v1/session`, on the same origin as the page

## What It Sends, and When

| Endpoint                 | Called by                                                        | When                         | It sends                        |
|--------------------------|------------------------------------------------------------------|------------------------------|---------------------------------|
| `GET /api/v1/session`    | [Sign in with Telegram](../../usecases/sign-in-with-telegram.md) | the page loads               | —                             |
| `POST /api/v1/session`   | [Sign in with Telegram](../../usecases/sign-in-with-telegram.md) | Telegram's widget calls back | the widget's payload, unchanged |
| `DELETE /api/v1/session` | [Sign in with Telegram](../../usecases/sign-in-with-telegram.md) | the sign-out control is used | —                             |

The Login Widget's payload is forwarded **field for field**, including any field this module does not
understand. Telegram signs all of them, so dropping or adding one makes the sign-in unverifiable.

Every request carries cookies. Every write carries the CSRF token, read back from the cookie the ledger set on
the page's first request.

## What It Does With the Answer

- **A session opened**: the external id is held in memory for as long as the tab lives, and
  [the expenses page](../../usecases/browse-recorded-expenses.md) is shown. The cookie itself is never read — it
  cannot be, and nothing here needs to.
- **A session read**: the same, without a sign-in.
- **A session read that is refused**: treated as nobody being signed in, not as an error. This is the ordinary
  answer on a first visit.
- **A sign-in that is refused**: the sign-in page says so and offers the widget again. The reason is not shown,
  because the ledger does not give one.

## When the Call Fails

| The failure                                  | What the person gets                                                      |
|-----------------------------------------------|------------------------------------------------------------------------------|
| A network failure, a proxy failure, or a 500 | reported as a refused sign-in — the three are indistinguishable from here |

Nothing is retried. A person who wants another attempt uses the widget again. Nothing is cached. A reload reads
the session again rather than trusting what the last one found.
