# [Conventions](../conventions.md) > Orientation

`web-app` is the browser client. It signs a user in with the Telegram Login Widget and serves the authenticated
shell behind that sign-in.

## Tech Stack

| Concern       | Choice                                     |
|---------------|--------------------------------------------|
| Language      | TypeScript, `strict`                       |
| UI            | React 19, function components              |
| Routing       | React Router                               |
| Build & dev   | Vite                                       |
| Tests         | Vitest, Testing Library, jsdom             |
| Lint & format | ESLint, Prettier                           |
| Served by     | nginx, which also proxies the ledger's API |

State management, styling and data fetching each have a deliberate answer rather than a library: state is React
context, styling is one hand-written stylesheet, and fetching is the module's own client over `fetch`.

## Systems It Talks To

| System                | Direction | Through                                                                         |
|-----------------------|-----------|---------------------------------------------------------------------------------|
| Ledger Service        | out       | [`contracts/out/ledger-session-api.md`](../contracts/out/ledger-session-api.md) |
| Telegram Login Widget | in        | A script the sign-in page embeds, which calls back with a signed payload        |

## What Cannot Be Exercised Locally

**The Telegram Login Widget does not render on `localhost`.** BotFather's `/setdomain` refuses `localhost` and
bare IP addresses, and the widget checks the page's origin against the domain registered for the bot. A real
sign-in therefore cannot happen from a bare local run — it needs a tunnel, registered with BotFather, and the
steps are in the [README](../../README.md#signing-in-for-real).

Nothing else in the module needs one, and the tests do not: they build a payload and sign it with a test bot
token, the same way `ledger-service`'s fixtures do.

No sign-in bypass exists, and none is added: an authentication bypass that ships by accident costs more than the
inconvenience.

## Documentation

- [Use cases](../usecases/) — what the module does, one page each.
- [Configuration](../configuration.md) — every setting, its default and whether it is required.
- [Contracts](../contracts/) — what it sends to other systems.
- [README](../../README.md) — what the module is, with its component diagram.
