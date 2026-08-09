# Web App

The browser client. A person signs in with the Telegram account they use for the bot, and lands on a list of
everything the ledger holds for them.

The identity is a Telegram user id. Someone who has only ever talked to the
bot and someone who has only ever used this page are the same user.

- [Conventions](docs/conventions.md) — how this module is structured, tested, styled and built.
- [Configuration](docs/configuration.md) — every setting and its default.
- [Sign in with Telegram](docs/usecases/sign-in-with-telegram.md) — what the sign-in does.
- [Browse recorded expenses](docs/usecases/browse-recorded-expenses.md) — what the list behind it does.
- [Ledger session API](docs/contracts/out/ledger-session-api.md) — what it sends the ledger to hold a session.
- [Ledger browse API](docs/contracts/out/ledger-browse-api.md) — what it sends the ledger to fill the list.

## Components

```plantuml
@startuml C3-WebApp-Components
!include <C4/C4_Component>

AddElementTag("page", $bgColor="#A85C74", $fontColor="#FFFFFF", $borderColor="#7E4457")

Person(user, "Person", "Signs in with their Telegram account and browses their ledger")
System_Ext(telegram, "Telegram", "Signs the payload identifying the user")
System_Ext(ledger, "Ledger Service", "Holds the browser session, and answers what the ledger contains")
ContainerDb_Ext(store, "Browser local storage", "Web Storage", "Keeps the light or dark choice")

Container_Boundary(webApp, "Web App") {
  Component(appShell, "App shell", "React", "The frame every route renders inside, carrying sign-out", $tags="page")
  Component(loginPage, "Sign-in page", "React", "Offers the Telegram widget and reports a refused sign-in", $tags="page")
  Component(loginButton, "Telegram login button", "React", "Embeds Telegram's widget and receives the signed payload")
  Component(authContext, "Session state", "React context", "Holds who is signed in and what is still unknown")
  Component(routeGuard, "Route guard", "React", "Keeps a page behind an open session")
  Component(expensesPage, "Expenses page", "React", "Composes the list and the filter", $tags="page")
  Component(expenseList, "Expense list", "React", "Cuts one page of entries into UTC days")
  Component(daySection, "Day section", "React", "Heads a day with its count and its spend, and lists it")
  Component(expenseFilters, "Filter controls", "React", "Offers the tree and the narrowing a listing accepts")
  Component(theme, "Theme", "TypeScript", "Resolves, applies and stores the light or dark choice")
  Component(catalogue, "Message catalogue", "i18next", "The English wording every surface reads")
  Component(apiClient, "Ledger client", "TypeScript", "Calls the ledger with cookies and the CSRF token")
}

Rel_D(user, appShell, "Opens")
Rel_D(appShell, loginPage, "Renders the matched route")
Rel_D(appShell, routeGuard, "Renders the guarded route")
Rel_D(appShell, authContext, "Reads the session, and ends it")
Rel_R(appShell, theme, "Flips the theme through")
Rel_R(theme, store, "Reads and writes the choice")
Rel_R(loginPage, loginButton, "Shows")
Rel_R(loginButton, telegram, "Embeds the widget", "HTTPS")
Rel_L(telegram, loginButton, "Returns the signed payload")
Rel(loginButton, authContext, "Hands the payload to the sign-in")
Rel_D(authContext, apiClient, "Opens, reads and ends the session")
Rel_R(apiClient, ledger, "Session and browse requests", "HTTPS, same origin")
Rel_D(routeGuard, authContext, "Asks whether a session is open")
Rel(routeGuard, expensesPage, "Admits")
Rel_D(expensesPage, expenseList, "Renders")
Rel_D(expensesPage, expenseFilters, "Renders")
Rel_D(expenseList, daySection, "Renders one per day")
Rel_R(expensesPage, apiClient, "Reads the expenses and the tree through")
Rel_R(expensesPage, authContext, "Drops to anonymous on a refused read")
Rel_R(appShell, catalogue, "Reads its wording from")
Rel_R(loginPage, catalogue, "Reads its wording from")
Rel_R(expenseFilters, catalogue, "Reads its wording from")
Rel_R(expenseList, catalogue, "Reads its wording from")
Rel_R(daySection, catalogue, "Reads its wording from")

Lay_D(theme, catalogue)

SHOW_LEGEND()
@enduml
```

## Running It

```
npm ci
npm run dev
```

The dev server proxies `/api` to a ledger on port 1000, so the page and the API share one origin.

## Signing In for Real

The Telegram widget will not render on `localhost` — Telegram refuses to register that domain for a bot. Signing
in therefore needs a public hostname in front of the dev server:

```
ngrok http 1004
```

Then, once per tunnel address:

| Step | What                                                                                |
|------|-------------------------------------------------------------------------------------|
| 1    | Give the tunnel's hostname to BotFather with `/setdomain`                           |
| 2    | Start the ledger with `WEB_SESSION_COOKIE_SECURE=true` — the browser now sees HTTPS |
| 3    | Open the tunnel's address, not `localhost`                                          |

`server.allowedHosts` in `vite.config.ts` already covers ngrok's own domains. A tunnel on a custom domain, or a
different provider, is a new entry in that list — Vite refuses a `Host` header it was not told about.

A free tunnel address changes on every restart, so steps 1 and 3 repeat each time. A reserved domain pins it.

Only the sign-in needs this. Everything else runs on `localhost`, and the tests never call Telegram at all —
they build a payload and sign it with a test bot token.
