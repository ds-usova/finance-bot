# Configuration

| Variable                     | Sets                                             | Default  | Required | Secret |
|------------------------------|--------------------------------------------------|----------|----------|--------|
| `VITE_TELEGRAM_BOT_USERNAME` | which bot the Login Widget signs a person in for | *(none)* | yes      | no     |

## Notes

- The value is the bot's username without a leading `@`. It must be the same bot whose token the ledger holds:
  the widget signs with that bot's key, and the ledger checks the signature with it.
- **It is read at build time, not at runtime.** Vite bakes it into the bundle, so the image is specific to one
  bot. Under compose it comes from `FINANCE_BOT_TELEGRAM_BOT_USERNAME` as a build argument.
- **Under `npm run dev` it comes from `.env.local`**, copied from `.env.example` and never committed. Without
  it the widget renders naming no bot and signs nothing, with no error.
- **There is no API address to configure.** The page reaches the ledger at `/api` on its own origin, which nginx
  and the dev server both proxy — see
  [ADR 0014](../../docs/adr/0014-the-web-app-and-the-ledger-are-served-from-one-origin.md).
- The bot the widget names is public. The bot *token* never reaches the browser.
