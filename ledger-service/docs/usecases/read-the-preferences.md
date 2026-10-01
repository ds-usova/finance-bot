# Read a person's preferences

- **In**
  - the identity of the authenticated caller
- **Out**
  - the currency their amounts are assumed to be in, or nothing where they have chosen none
- **Why:** a configuration page can show what the bot is currently assuming, before anyone changes it

*Implemented by `ReadPreferencesUseCase`.*

## Prerequisites

- The caller is authenticated. The request never names whose preferences they are.
- A user is stored under that identity.

## Collaborators

| Direction | Collaborator                                                                      | Through                                                                 | For                                          |
|-----------|-----------------------------------------------------------------------------------|-------------------------------------------------------------------------|----------------------------------------------|
| in        | [Set a default currency](../../../web-app/docs/usecases/set-a-default-currency.md) | [Browsing the ledger from a browser](../contracts/in/web-browse-api.md) | standing the picker on the stored choice     |
| out       | [Database](../contracts/out/database.md)                                           | [Users, categories and expenses](../contracts/out/database.md)          | resolving the identity, and reading their stored choice |

## Outcomes

| Outcome              | When                                                                    | Result                                            |
|----------------------|-------------------------------------------------------------------------|---------------------------------------------------|
| Preferences answered | the identity names a stored person                                      | the currency they chose                           |
| Nothing chosen       | that person has never chosen one, or their stored choice cannot be read | no currency, and no failure                       |
| Identity unknown     | nothing is stored under the identity                                    | the request is rejected and nothing is read       |
| Request rejected     | the request carries no identity                                         | no browser session is open — nothing is looked up |
| Storage failed       | the store cannot be reached                                             | the failure reaches the caller                    |

## Components

```plantuml
@startuml C3-Component-ReadPreferences
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
  Component(accessControl, "Access Control", "Spring Security", "Admits only calls carrying a valid session cookie", $tags="webExternal")
  Component(endpoint, "Preferences Endpoint", "Spring MVC", "Reads the caller off the session", $tags="webExternal")
  Component(readPort, "Read Preferences Port", "Interface", "Inbound port", $tags="portIn")
  Component(readService, "Read a Person's Preferences Use Case", "Plain Java", "Resolves the person, and reads their choice", $tags="core")
  Component(userRepositoryPort, "User Repository Port", "Interface", "Outbound port", $tags="portOut")
  Component(preferenceRepositoryPort, "User Preference Repository Port", "Interface", "Outbound port", $tags="portOut")
  Component(userRepositoryAdapter, "User Repository Adapter", "Spring Data Relational", "Reads a person by their stored id", $tags="dbExternal")
  Component(preferenceRepositoryAdapter, "User Preference Repository Adapter", "Spring Data Relational", "Reads a person's chosen currency", $tags="dbExternal")
}

Rel(browser, accessControl, "Asks for the preferences", "HTTP, session cookie")
Rel_D(accessControl, endpoint, "Admits the call, with the caller's identity")
Rel_D(endpoint, readPort, "Invokes")
Rel_L(readService, readPort, "Implements", $tags="implements")
Rel_R(readService, userRepositoryPort, "Resolves the identity through")
Rel_R(readService, preferenceRepositoryPort, "Reads the chosen currency through")
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
@startuml ReadPreferences-Sequence
participant "A signed-in person's browser" as Browser
participant "Ledger Service" as LS
database "Database" as DB

Browser -> LS : read the preferences

alt the request carries no identity
  LS --> Browser : no browser session is open
else an identity is carried
  LS -> DB : read the person under that identity
  alt nothing is stored
    DB --> LS : no such person
    LS --> Browser : the caller is unknown
  else a person is stored
    LS -> DB : read their chosen currency
    alt the store cannot be reached
      DB --> LS : the read failed
      LS --> Browser : storage failed
    else the read answers
      DB --> LS : the chosen currency, or nothing
      LS --> Browser : the currency they chose, or nothing chosen
    end
  end
end
@enduml
```

## References

- [ADR 0013: A browser session is a cookie-borne token with its own audience](../adr/0013-a-browser-session-is-a-cookie-borne-token-with-its-own-audience.md) —
  what the caller is holding when it reaches here
- [Replace a person's preferences](replace-the-preferences.md) — how the choice this answers is made
- [Act on a user's message](handle-incoming-message.md) — what the stored choice is used for
