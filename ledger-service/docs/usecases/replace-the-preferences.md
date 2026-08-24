# Replace a person's preferences

- **In**
  - the identity of the authenticated caller
  - the currency their amounts are to be assumed in
- **Out**
  - their preferences as they now stand
- **Why:** the bot stops ignoring an amount a person states with no currency

*Implemented by `ReplacePreferencesUseCase`.*

## Prerequisites

- The caller is authenticated. The request never names whose preferences they are.
- A user is stored under that identity.

## Collaborators

| Direction | Collaborator                                                                       | Through                                                                 | For                                              |
|-----------|------------------------------------------------------------------------------------|-------------------------------------------------------------------------|--------------------------------------------------|
| in        | [Set a default currency](../../../web-app/docs/usecases/set-a-default-currency.md) | [Browsing the ledger from a browser](../contracts/in/web-browse-api.md) | saving the currency a person picked              |
| out       | [Database](../contracts/out/database.md)                                           | [Users, categories and expenses](../contracts/out/database.md)          | resolving the identity, and storing their choice |

## Outcomes

| Outcome              | When                                                                                          | Result                                                      |
|----------------------|-----------------------------------------------------------------------------------------------|-------------------------------------------------------------|
| Preferences replaced | the identity names a stored person                                                            | that currency is the only one stored for them, and answered |
| Currency refused     | the code names no currency, or one [no amount can be recorded in](../domain/currency-code.md) | the request is refused and nothing is stored                |
| Identity unknown     | nothing is stored under the identity                                                          | the request is rejected and nothing is stored               |
| Request rejected     | the request carries no identity                                                               | no browser session is open — nothing is stored            |
| Storage failed       | the store cannot be reached                                                                   | the failure reaches the caller                              |

Two replacements arriving together land one after the other; neither is refused.

## Components

```plantuml
@startuml C3-Component-ReplacePreferences
!include <C4/C4_Component>

AddElementTag("dbExternal", $bgColor="#d68910", $fontColor="#ffffff", $borderColor="#8f5c0a")
AddElementTag("webExternal", $bgColor="#2874a6", $fontColor="#ffffff", $borderColor="#1b4f72")
AddElementTag("portIn", $bgColor="#16a085", $fontColor="#ffffff", $borderColor="#0e6655", $legendText="inbound port (interface)")
AddElementTag("portOut", $bgColor="#7f8c8d", $fontColor="#ffffff", $borderColor="#566573", $legendText="outbound port (interface)")
AddElementTag("core", $bgColor="#2c3e50", $fontColor="#ffffff", $borderColor="#1b2631", $legendText="application core")
AddRelTag("implements", $lineStyle="dashed")

System_Ext(browser, "A signed-in person's browser", "The configuration page", $tags="webExternal")
ContainerDb(db, "Database", "PostgreSQL", "Stores users and their chosen currency", $tags="dbExternal")

Container_Boundary(ledger, "Ledger Service (Java, Spring Boot)") {
  Component(accessControl, "Access Control", "Spring Security", "Admits only a call carrying a valid session cookie and a matching CSRF token", $tags="webExternal")
  Component(endpoint, "Preferences Endpoint", "Spring MVC", "Reads the caller off the session", $tags="webExternal")
  Component(mapper, "Preferences Web Mapper", "Plain Java", "Turns the request into a command, refusing a code naming no currency or one no amount can be recorded in", $tags="webExternal")
  Component(replacePort, "Replace Preferences Port", "Interface", "Inbound port", $tags="portIn")
  Component(replaceService, "Replace a Person's Preferences Use Case", "Plain Java", "Resolves the person, and stores their choice", $tags="core")
  Component(userRepositoryPort, "User Repository Port", "Interface", "Outbound port", $tags="portOut")
  Component(preferenceRepositoryPort, "User Preference Repository Port", "Interface", "Outbound port", $tags="portOut")
  Component(userRepositoryAdapter, "User Repository Adapter", "Spring Data Relational", "Reads a person by their stored id", $tags="dbExternal")
  Component(preferenceRepositoryAdapter, "User Preference Repository Adapter", "Spring Data Relational", "Writes a person's chosen currency, replacing any it holds", $tags="dbExternal")
}

Rel(browser, accessControl, "Sends the currency to assume", "HTTP, session cookie")
Rel_D(accessControl, endpoint, "Admits the call, with the caller's identity")
Rel_R(endpoint, mapper, "Maps the request with")
Rel_D(endpoint, replacePort, "Invokes")
Rel_L(replaceService, replacePort, "Implements", $tags="implements")
Rel_R(replaceService, userRepositoryPort, "Resolves the identity through")
Rel_R(replaceService, preferenceRepositoryPort, "Stores the choice through")
Rel_L(userRepositoryAdapter, userRepositoryPort, "Implements", $tags="implements")
Rel_L(preferenceRepositoryAdapter, preferenceRepositoryPort, "Implements", $tags="implements")
Rel_R(userRepositoryAdapter, db, "SQL", "JDBC")
Rel_R(preferenceRepositoryAdapter, db, "SQL", "JDBC")

Lay_D(userRepositoryPort, preferenceRepositoryPort)
Lay_D(userRepositoryAdapter, preferenceRepositoryAdapter)

SHOW_LEGEND()
@enduml
```

## Flow

```plantuml
@startuml ReplacePreferences-Sequence
participant "A signed-in person's browser" as Browser
participant "Ledger Service" as LS
database "Database" as DB

Browser -> LS : replace the preferences with a currency

alt the request carries no identity
  LS --> Browser : no browser session is open
else the code names no currency, or one no amount can be recorded in
  LS --> Browser : the code is refused, by name
else the code is accepted
  LS -> DB : read the person under that identity
  alt nothing is stored
    DB --> LS : no such person
    LS --> Browser : the caller is unknown
  else a person is stored
    LS -> DB : store that currency as their only choice
    alt the store cannot be reached
      DB --> LS : the write failed
      LS --> Browser : storage failed
    else the write lands
      DB --> LS : stored
      LS --> Browser : the preferences as they now stand
    end
  end
end
@enduml
```

## References

- [ADR 0013: A browser session is a cookie-borne token with its own audience](../adr/0013-a-browser-session-is-a-cookie-borne-token-with-its-own-audience.md) —
  what the caller is holding when it reaches here
- [Read a person's preferences](read-the-preferences.md) — how the stored choice is read back
- [Act on a user's message](handle-incoming-message.md) — what the stored choice is used for
