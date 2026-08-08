# Browse a person's groupings

- **In**
  - the identity of the authenticated caller
- **Out**
  - their groupings, whole and unpaged, ordered by name
  - each by id and name
- **Why:** it is the top of the tree a browser offers as a filter, and the source of the id
  [the category listing](browse-categories.md) narrows by

*Implemented by `BrowseGroupingsUseCase`.*

## Prerequisites

- The caller is authenticated. The request never names whose groupings they are.
- A user is stored under that identity.

## Collaborators

| Direction | Collaborator                                                                           | Through                                                                           | For                                                          |
|-----------|----------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------|--------------------------------------------------------------|
| in        | [Browse recorded expenses](../../../web-app/docs/usecases/browse-recorded-expenses.md) | [Browsing the ledger from a browser](../contracts/in/web-browse-api.md)           | offering the person's groupings as a way to narrow the tree  |
| out       | [Database](../contracts/out/database.md)                                               | [Users, categories, expenses and expense proposals](../contracts/out/database.md) | resolving the identity, and reading their groupings          |

A [grouping](../domain/grouping.md) holding no categories is answered too.
[The turn answering a message](handle-incoming-message.md) hides one; this listing does not.

## Outcomes

| Outcome          | When                                 | Result                                        |
|------------------|--------------------------------------|-----------------------------------------------|
| Groupings listed | the identity names a stored user     | every grouping of theirs, ordered by name     |
| Empty list       | they have no groupings at all        | no rows, and no failure                       |
| Request rejected | the request names no identity        | the request is refused — nothing is looked up |
| Identity unknown | nothing is stored under the identity | the request is rejected and nothing is listed |
| Storage failed   | the store cannot be reached          | the failure reaches the caller                |

## Components

```plantuml
@startuml C3-Component-BrowseGroupings
!include <C4/C4_Component>

AddElementTag("dbExternal", $bgColor="#d68910", $fontColor="#ffffff", $borderColor="#8f5c0a")
AddElementTag("webExternal", $bgColor="#2874a6", $fontColor="#ffffff", $borderColor="#1b4f72")
AddElementTag("portIn", $bgColor="#16a085", $fontColor="#ffffff", $borderColor="#0e6655", $legendText="inbound port (interface)")
AddElementTag("portOut", $bgColor="#7f8c8d", $fontColor="#ffffff", $borderColor="#566573", $legendText="outbound port (interface)")
AddElementTag("core", $bgColor="#2c3e50", $fontColor="#ffffff", $borderColor="#1b2631", $legendText="application core")
AddRelTag("implements", $lineStyle="dashed")

System_Ext(browser, "A signed-in person's browser", "The web app's page", $tags="webExternal")
ContainerDb(db, "Database", "PostgreSQL", "Stores users, categories, expenses and expense proposals", $tags="dbExternal")

Container_Boundary(ledger, "Ledger Service (Java, Spring Boot)") {
  Component(accessControl, "Access Control", "Spring Security", "Admits only calls carrying a valid session cookie", $tags="webExternal")
  Component(endpoint, "Groupings Endpoint", "Spring MVC", "Reads the caller off the session, and takes nothing else", $tags="webExternal")
  Component(browsePort, "Browse Groupings Port", "Interface", "Inbound port", $tags="portIn")
  Component(browseService, "Browse a Person's Groupings Use Case", "Plain Java", "Resolves the user, and reads their groupings", $tags="core")
  Component(userRepositoryPort, "User Repository Port", "Interface", "Outbound port", $tags="portOut")
  Component(groupingRepositoryPort, "Grouping Repository Port", "Interface", "Outbound port", $tags="portOut")
  Component(userRepositoryAdapter, "User Repository Adapter", "Spring Data Relational", "Checks if user exists", $tags="dbExternal")
  Component(groupingRepositoryAdapter, "Grouping Repository Adapter", "Spring Data Relational", "Reads a user's groupings, empty ones included", $tags="dbExternal")
}

Rel(browser, accessControl, "Asks for the groupings", "HTTP, session cookie")
Rel_D(accessControl, endpoint, "Admits the call, with the caller's identity")
Rel_D(endpoint, browsePort, "Invokes")
Rel_L(browseService, browsePort, "Implements", $tags="implements")
Rel_R(browseService, userRepositoryPort, "Resolves the identity through")
Rel_R(browseService, groupingRepositoryPort, "Reads the groupings through")
Rel_L(userRepositoryAdapter, userRepositoryPort, "Implements", $tags="implements")
Rel_L(groupingRepositoryAdapter, groupingRepositoryPort, "Implements", $tags="implements")
Rel_R(userRepositoryAdapter, db, "SQL", "JDBC")
Rel_R(groupingRepositoryAdapter, db, "SQL", "JDBC")

Lay_D(userRepositoryPort, groupingRepositoryPort)
Lay_D(userRepositoryAdapter, groupingRepositoryAdapter)

SHOW_LEGEND()
@enduml
```

## Flow

```plantuml
@startuml BrowseGroupings-Activity
start
:a signed-in person asks for their groupings;
if (the request names no identity?) then (yes)
  :the request is refused — nothing is looked up;
  stop
endif
:look the identity up in the database;
if (the read fails?) then (yes)
  :storage failed;
  stop
endif
if (the identity names a stored user?) then (no)
  :identity unknown;
  stop
endif
:read their groupings from the database, ordered by name;
if (the read fails?) then (yes)
  :storage failed;
  stop
endif
:answer every grouping, empty ones included;
stop
@enduml
```
