# List a person's texts being read

- **In**
  - the identity of the authenticated caller
  - the state asked for, `READING`
- **Out**
  - the caller's [texts](../domain/written-message.md) still `WAITING` or `READING`
- **Why:** a page opened or reloaded mid-reading picks the text up again and goes on following it

*Implemented by `BrowseWrittenMessagesUseCase`.*

## Prerequisites

- The caller is authenticated. The request never names whose texts they are.
- A user is stored under that identity.

## Collaborators

| Direction | Collaborator                                                                     | Through                                                                 | For                                                          |
|-----------|----------------------------------------------------------------------------------|-------------------------------------------------------------------------|--------------------------------------------------------------|
| in        | [File expenses as text](../../../web-app/docs/usecases/file-expenses-as-text.md) | [Browsing the ledger from a browser](../contracts/in/web-browse-api.md) | finding a text being read on load, or after a refused filing |
| out       | [Database](../contracts/out/database.md)                                         | [Users, categories and expenses](../contracts/out/database.md)          | resolving the identity, and reading their texts              |

## Outcomes

| Outcome          | When                                              | Result                                               |
|------------------|---------------------------------------------------|------------------------------------------------------|
| Text listed      | the caller has a text `WAITING` or `READING`      | a list of that one text                              |
| Nothing reading  | every text of theirs is `DONE`, or they have none | an empty list                                        |
| State unusable   | the state is absent, or anything but `READING`    | the request is rejected naming `state`               |
| Identity unknown | nothing is stored under the identity              | the request is rejected, and no text is read         |
| Request rejected | the request carries no identity                   | no browser session is open, and nothing is looked up |
| Storage failed   | the store cannot be reached                       | the failure reaches the caller                       |

## Components

```plantuml
@startuml C3-Component-BrowseWrittenMessages
!include <C4/C4_Component>

Person(browser, "Signed-in person", "The expenses page")

Container_Boundary(inboundWeb, "adapter (inbound) — Browser") {
  Component(endpoint, "Messages endpoint", "Spring MVC", "Reads the caller off the session")
  Component(mapper, "Written message mapper", "Plain Java", "Checks the state, writes the answer")
}
Container_Boundary(application, "application — usecases and ports") {
  Component(useCase, "List a person's texts being read", "use case")
  Component(userPort, "User repository", "outbound port")
  Component(writtenPort, "Written message repository", "outbound port")
}
Container_Boundary(outboundDb, "adapter (outbound) — Postgres") {
  Component(userAdapter, "User repository adapter", "Spring Data JDBC")
  Component(writtenAdapter, "Written message repository adapter", "Spring Data JDBC")
}
ContainerDb(db, "Database", "PostgreSQL")

Rel_D(browser, endpoint, "Asks for texts being read", "HTTP, session cookie")
Rel_R(endpoint, mapper, "Maps with")
Rel_D(endpoint, useCase, "Lists through")
Rel_D(useCase, userPort, "Resolves the caller")
Rel_D(useCase, writtenPort, "Reads their unsettled texts")
Rel_U(userAdapter, userPort, "Implements")
Rel_U(writtenAdapter, writtenPort, "Implements")
Rel_D(userAdapter, db, "SQL")
Rel_D(writtenAdapter, db, "SQL")

Lay_D(writtenPort, writtenAdapter)
@enduml
```

## Flow

```plantuml
@startuml BrowseWrittenMessages-Sequence
participant "Browser" as Browser
participant "Ledger Service" as LS
database "Database" as DB

Browser -> LS : list texts being read
alt no identity, or the state is unusable
  LS --> Browser : rejected
else the request is usable
  LS -> DB : read the person under that identity
  alt nothing is stored
    DB --> LS : no such person
    LS --> Browser : the caller is unknown
  else a person is stored
    LS -> DB : their texts WAITING or READING
    DB --> LS : none, or one
    LS --> Browser : that list
  end
end
@enduml
```

## References

- [Read what became of a filed text](read-written-message.md) — how the page follows the text it finds
- [File a text for reading](file-written-message.md) — how a text comes to be read
