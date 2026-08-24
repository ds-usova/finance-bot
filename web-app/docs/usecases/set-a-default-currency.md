# Set a default currency

- **At:** `/settings`, behind the route guard
- **In:** a signed-in person
- **In:** the currency they pick from the offered list
- **Out:** that currency, stored as the one their amounts are assumed to be in
- **Why:** an amount the bot is sent with no currency is acted on rather than ignored

*Implemented by the settings page, the currency picker and the preferences client.*

## Collaborators

| Direction | Collaborator                                                                                | Through                                                 | For                                     |
|-----------|---------------------------------------------------------------------------------------------|---------------------------------------------------------|-----------------------------------------|
| in        | [A signed-in person's browser](sign-in-with-telegram.md)                                    | the settings page                                       | choosing the currency their bot assumes |
| out       | [Read the preferences](../../../ledger-service/docs/usecases/read-the-preferences.md)       | [the browse API](../contracts/out/ledger-browse-api.md) | the currency stored for them today      |
| out       | [Replace the preferences](../../../ledger-service/docs/usecases/replace-the-preferences.md) | [the browse API](../contracts/out/ledger-browse-api.md) | storing the currency they picked        |
| out       | [Sign in with Telegram](sign-in-with-telegram.md)                                           | [the session state it owns](sign-in-with-telegram.md)   | dropping to anonymous on a refused call |

## Prerequisites

- A session is open.

## Outcomes

| Outcome                    | When                                                    | Result                                                                     |
|----------------------------|---------------------------------------------------------|----------------------------------------------------------------------------|
| The stored choice is shown | the read answers a currency                             | the picker stands on it, and nothing is offered to save                    |
| Nothing chosen yet         | the read answers no currency                            | the picker stands unset, and nothing is offered to save                    |
| Ready to save              | the picked currency differs from the stored one         | the save is offered                                                        |
| Saved                      | the replacement is answered                             | the page reports it was saved, and the picker stands on the saved currency |
| Nothing is sent            | the save is used again while one is out                 | nothing, and one call stands                                               |
| The report clears          | another currency is picked                              | the save is offered again for the new choice                               |
| Save refused               | the replacement is refused for any reason but a session | the wording is shown beside the save, and the picked currency stands       |
| Nothing to configure       | the read fails for any reason but a session             | the failure is shown in place of the picker                                |
| Sent to sign in            | either call is refused for want of a session            | the sign-in page is shown, and no failure is left behind                   |

## Flow

```plantuml
@startuml SetADefaultCurrency-Sequence
actor "Person" as User
participant "Settings page" as Page
participant "Currency picker" as Picker
participant "Session state" as Auth
participant "Ledger" as Ledger

User -> Page : opens /settings
Page -> Ledger : read the preferences

alt the read is answered
  Ledger --> Page : the stored code, or none
  Page -> Picker : stand on what is stored
  Picker --> User : the currencies the catalogue names
  User -> Picker : picks a currency
  Picker -> Page : the picked code
  User -> Page : saves
  Page -> Ledger : replace the preferences with the picked code

  alt the replacement is answered
    Ledger --> Page : the preferences as they now stand
    Page --> User : that it was saved
  else refused for want of a session
    Ledger --> Page : no valid session
    Page -> Auth : the session has expired
    Auth --> User : the sign-in page
  else refused otherwise
    Ledger --> Page : the failure
    Page --> User : what went wrong, beside the save
  end
else refused for want of a session
  Ledger --> Page : no valid session
  Page -> Auth : the session has expired
  Auth --> User : the sign-in page
else the read fails otherwise
  Ledger --> Page : the failure
  Page --> User : what went wrong, and no picker
end
@enduml
```

## Components

```plantuml
@startuml C3-SetADefaultCurrency-Components
!include <C4/C4_Component>

AddElementTag("page", $bgColor="#A85C74", $fontColor="#FFFFFF", $borderColor="#7E4457")

Person(user, "Person", "Signed in, choosing the currency their amounts are assumed to be in")
System_Ext(ledger, "Ledger Service", "Holds the preference and sends it with every turn")

Container_Boundary(webApp, "Web App") {
  Component(appShell, "App shell", "React", "Carries the way to the page and the way back", $tags="page")
  Component(routeGuard, "Route guard", "React", "Keeps the page behind an open session")
  Component(settingsPage, "Settings page", "React", "Reads the preferences and saves a picked currency", $tags="page")
  Component(currencyPicker, "Currency picker", "React", "Offers the currencies, searchable by name or code")
  Component(catalogue, "English catalogue", "TypeScript", "Names every offered currency")
  Component(errorBanner, "Error banner", "React", "States a failed read at the top of the page")
  Component(authContext, "Session state", "React context", "Holds who is signed in")
  Component(preferencesClient, "Preferences client", "TypeScript", "Calls the read and the replacement")
}

Rel_R(user, appShell, "Opens")
Rel_R(appShell, routeGuard, "Renders the matched route through")
Rel_R(routeGuard, settingsPage, "Admits")
Rel_D(routeGuard, authContext, "Asks whether a session is open")
Rel_D(settingsPage, currencyPicker, "Renders")
Rel_R(settingsPage, errorBanner, "Renders on a failed read")
Rel_D(settingsPage, authContext, "Reports an expired session to")
Rel_R(currencyPicker, catalogue, "Offers the codes it names")
Rel_D(settingsPage, preferencesClient, "Reads and replaces through")
Rel_R(preferencesClient, ledger, "Preference requests", "HTTPS, same origin")
@enduml
```

## References

- [ADR 0014: The web app and the ledger are served from one origin](../../../docs/adr/0014-the-web-app-and-the-ledger-are-served-from-one-origin.md) —
  why the read and the write carry the session cookie with no CORS step
- [Browse recorded expenses](browse-recorded-expenses.md) — where a person lands after signing in again
