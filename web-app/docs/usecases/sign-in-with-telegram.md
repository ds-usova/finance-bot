# Sign in with Telegram

- **At:** `/login`, and the session state every other route reads
- **In:** a person, and the Telegram account they use for the bot
- **Out:** a browser session held for that person
- **Why:** the ledger's data belongs to a Telegram user, so a browser has to prove it is one

*Implemented by the sign-in page, the login button and the session state.*

## Collaborators

| Direction | Collaborator                                             | Through                                                   | For                                            |
|-----------|----------------------------------------------------------|-----------------------------------------------------------|--------------------------------------------------|
| in        | A person's browser                                       | the sign-in page                                          | starting a session                             |
| in        | The app shell, on every route                            | the session state                                         | showing sign-out while signed in, and ending it |
| in        | Every page behind the route guard                        | the session state                                         | dropping an expired session                    |
| out       | [Telegram](https://core.telegram.org/widgets/login)      | the Login Widget script the page embeds                   | proving which Telegram account is signing in   |
| out       | [Ledger Service](../contracts/out/ledger-session-api.md) | [The session API](../contracts/out/ledger-session-api.md) | opening, reading and ending the session        |

## Outcomes

| Outcome           | When                                       | Result                                                          |
|-------------------|--------------------------------------------|-----------------------------------------------------------------|
| Already signed in | the session read on load succeeds          | the expenses page is shown, with no sign-in step                |
| Signed in         | the widget's payload is accepted           | the expenses page is shown, and the session is held for the tab |
| Sign-in refused   | the payload is rejected or the call fails  | the sign-in page says so and offers the widget again            |
| Not signed in     | the session read is refused                | the sign-in page is shown                                       |
| Signed out        | the sign-out control in the header is used | the sign-in page is shown, and the ledger clears the cookie     |

## Flow

```plantuml
@startuml SignInWithTelegram-Sequence
actor "Person" as User
participant "Sign-in page" as Page
participant "App shell" as Shell
participant "Session state" as Auth
participant "Telegram" as Telegram
participant "Ledger" as Ledger

User -> Page : opens the app
Page -> Auth : what is the session?
Auth -> Ledger : read the session

alt a session is already open
  Ledger --> Auth : the external id
  Auth --> Page : signed in
  Page --> User : the expenses page
else nobody is signed in
  Ledger --> Auth : refused
  Auth --> Page : anonymous
  Page --> User : the Telegram widget

  User -> Telegram : approves the sign-in
  Telegram --> Page : the signed payload
  Page -> Auth : sign in with this payload
  Auth -> Ledger : open a session

  alt accepted
    Ledger --> Auth : the external id + the session cookie
    Auth --> Page : signed in
    Page --> User : the expenses page
  else refused
    Ledger --> Auth : refused
    Auth --> Page : the sign-in failed
    Page --> User : try again
  end
end

group ending the session
  User -> Shell : signs out
  Shell -> Auth : end the session
  Auth -> Ledger : end the session
  Auth --> User : the sign-in page
end
@enduml
```

## References

- [ADR 0013: A browser session is a cookie-borne token with its own audience](../../../ledger-service/docs/adr/0013-a-browser-session-is-a-cookie-borne-token-with-its-own-audience.md) —
  why the page never reads the session itself
- [ADR 0014: The web app and the ledger are served from one origin](../../../docs/adr/0014-the-web-app-and-the-ledger-are-served-from-one-origin.md) —
  why the cookie reaches the API with no CORS step
- [Browse recorded expenses](browse-recorded-expenses.md) — where a signed-in person lands
