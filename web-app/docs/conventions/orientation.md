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

## Documentation

- [Use cases](../usecases/) — what the module does, one page each.
- [Configuration](../configuration.md) — every setting, its default and whether it is required.
- [Contracts](../contracts/) — what it sends to other systems.
- [README](../../README.md) — what the module is, with its component diagram.
