# Read the current session

- **In:** the identity of the authenticated caller
- **Out:** the stored person that identity names
- **Why:** a page that has just loaded can tell whether anyone is signed in, and who

*Implemented by `ReadSessionUseCase`.*

## Prerequisites

- The caller carries a browser session this service signed.
- A person is stored under the identity that session names.

## Collaborators

| Direction | Collaborator                             | Through                                                                           | For                                            |
|-----------|------------------------------------------|-----------------------------------------------------------------------------------|------------------------------------------------|
| in        | [Web App](../../../web-app/README.md)    | [The session API](../contracts/in/web-session-api.md)                             | deciding whether to show the shell or the sign-in |
| out       | [Database](../contracts/out/database.md) | [Users, categories and expenses](../contracts/out/database.md)                    | resolving the caller's own row                 |

## Outcomes

| Outcome          | When                                              | Result                                                     |
|------------------|---------------------------------------------------|------------------------------------------------------------|
| Session answered | the identity names a stored person                | that person, answered by the identity the platform knows them by |
| Caller unknown   | nothing is stored under the identity              | the request is rejected as an unknown caller               |
| Request rejected | the request carries no identity                   | no browser session is open — nothing is looked up          |
| Storage failed   | the store cannot be reached                       | the failure reaches the caller                             |

## Components

```plantuml
@startuml C3-Component-ReadSession
!include <C4/C4_Component>

AddElementTag("dbExternal", $bgColor="#d68910", $fontColor="#ffffff", $borderColor="#8f5c0a")
AddElementTag("webExternal", $bgColor="#2874a6", $fontColor="#ffffff", $borderColor="#1a5276")
AddElementTag("portIn", $bgColor="#16a085", $fontColor="#ffffff", $borderColor="#0e6655", $legendText="inbound port (interface)")
AddElementTag("portOut", $bgColor="#7f8c8d", $fontColor="#ffffff", $borderColor="#566573", $legendText="outbound port (interface)")
AddElementTag("core", $bgColor="#2c3e50", $fontColor="#ffffff", $borderColor="#1b2631", $legendText="application core")
AddRelTag("implements", $lineStyle="dashed")

System_Ext(browser, "A person's browser", "The web app", $tags="webExternal")
ContainerDb(db, "Database", "PostgreSQL", "Stores users", $tags="dbExternal")

Container_Boundary(ledger, "Ledger Service (Java, Spring Boot)") {
  Component(accessControl, "Access Control", "Spring Security", "Admits only a request carrying a session this service signed", $tags="webExternal")
  Component(sessionEndpoint, "Session Endpoint", "Spring MVC", "Reads the caller's identity off the session", $tags="webExternal")
  Component(readSessionPort, "Read Session Port", "Interface", "Inbound port", $tags="portIn")
  Component(readSessionService, "Read the Current Session Use Case", "Plain Java", "Resolves the caller's stored row", $tags="core")
  Component(userRepositoryPort, "User Repository Port", "Interface", "Outbound port", $tags="portOut")
  Component(userRepositoryAdapter, "User Repository Adapter", "Spring Data Relational", "Reads a person by their stored id", $tags="dbExternal")
}

Rel(browser, accessControl, "Read the session", "HTTPS")
Rel_D(accessControl, sessionEndpoint, "Admits the request, with the caller's identity")
Rel_D(sessionEndpoint, readSessionPort, "Invokes")
Rel_L(readSessionService, readSessionPort, "Implements", $tags="implements")
Rel_D(readSessionService, userRepositoryPort, "Resolves the identity through")
Rel_L(userRepositoryAdapter, userRepositoryPort, "Implements", $tags="implements")
Rel_R(userRepositoryAdapter, db, "SQL", "JDBC")

SHOW_LEGEND()
@enduml
```

## Flow

```plantuml
@startuml ReadSession-Sequence
participant "A person's browser" as Browser
participant "Ledger Service" as LS
database "Database" as DB

Browser -> LS : read the session

alt the request carries no identity
  LS --> Browser : no browser session is open
else an identity is carried
  LS -> DB : read the person under that identity
  alt a person is stored
    DB --> LS : the person
    LS --> Browser : the identity the platform knows them by
  else nothing is stored
    LS --> Browser : the caller is unknown
  end
end
@enduml
```

## References

- [ADR 0013: A browser session is a cookie-borne token with its own audience](../adr/0013-a-browser-session-is-a-cookie-borne-token-with-its-own-audience.md) —
  what the caller is holding when it reaches here
- [Initialize a new user](initialize-a-new-user.md) — where the row this reads is created
