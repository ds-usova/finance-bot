# Browse a person's categories

- **In**
  - the identity of the authenticated caller
  - a grouping, by id (optional)
- **Out**
  - their categories, whole and unpaged, ordered by grouping name then by their own
  - each carrying its own id and name, and its grouping's
- **Why:** it is what lets a browser put a name to the category an expense is filed under, and offer the
  categories a filter may choose between

*Implemented by `BrowseCategoriesUseCase`.*

## Prerequisites

- The caller is authenticated. The request never names whose categories they are.
- A user is stored under that identity.

## Collaborators

| Direction | Collaborator                                                                           | Through                                                                           | For                                                                       |
|-----------|----------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------|---------------------------------------------------------------------------|
| in        | [Browse recorded expenses](../../../web-app/docs/usecases/browse-recorded-expenses.md) | [Browsing the ledger from a browser](../contracts/in/web-browse-api.md)           | naming the category on each listed expense, and filling the filter        |
| out       | [Database](../contracts/out/database.md)                                               | [Users, categories, expenses and expense proposals](../contracts/out/database.md) | resolving the identity, and reading their categories with their groupings |

A [grouping](../domain/grouping.md) is named here by its stored id. [The MCP listing](list-categories.md) takes a
name instead, and the two are not interchangeable. A grouping is never answered as a category of itself.

## Outcomes

| Outcome           | When                                                                 | Result                                        |
|-------------------|----------------------------------------------------------------------|-----------------------------------------------|
| Categories listed | the identity names a stored user                                     | every category matching the grouping filter   |
| Empty list        | the grouping names none of theirs, or they have no categories at all | no rows, and no failure                       |
| Request rejected  | the request names no identity                                        | the request is refused — nothing is looked up |
| Identity unknown  | nothing is stored under the identity                                 | the request is rejected and nothing is listed |
| Storage failed    | the store cannot be reached                                          | the failure reaches the caller                |

## Components

```plantuml
@startuml C3-Component-BrowseCategories
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
  Component(endpoint, "Categories Endpoint", "Spring MVC", "Reads the grouping filter off the query and the caller off the session", $tags="webExternal")
  Component(browsePort, "Browse Categories Port", "Interface", "Inbound port", $tags="portIn")
  Component(browseService, "Browse a Person's Categories Use Case", "Plain Java", "Resolves the user, and reads their categories", $tags="core")
  Component(userRepositoryPort, "User Repository Port", "Interface", "Outbound port", $tags="portOut")
  Component(categoryRepositoryPort, "Category Repository Port", "Interface", "Outbound port", $tags="portOut")
  Component(userRepositoryAdapter, "User Repository Adapter", "Spring Data Relational", "Checks if user exists", $tags="dbExternal")
  Component(categoryRepositoryAdapter, "Category Repository Adapter", "Spring Data Relational", "Reads a user's categories, each with its grouping", $tags="dbExternal")
}

Rel(browser, accessControl, "Asks for the categories", "HTTP, session cookie")
Rel_D(accessControl, endpoint, "Admits the call, with the caller's identity")
Rel_D(endpoint, browsePort, "Invokes")
Rel_L(browseService, browsePort, "Implements", $tags="implements")
Rel_R(browseService, userRepositoryPort, "Resolves the identity through")
Rel_R(browseService, categoryRepositoryPort, "Reads the categories through")
Rel_L(userRepositoryAdapter, userRepositoryPort, "Implements", $tags="implements")
Rel_L(categoryRepositoryAdapter, categoryRepositoryPort, "Implements", $tags="implements")
Rel_R(userRepositoryAdapter, db, "SQL", "JDBC")
Rel_R(categoryRepositoryAdapter, db, "SQL", "JDBC")

Lay_D(userRepositoryPort, categoryRepositoryPort)
Lay_D(userRepositoryAdapter, categoryRepositoryAdapter)

SHOW_LEGEND()
@enduml
```

## Flow

```plantuml
@startuml BrowseCategories-Activity
start
:a signed-in person asks for their categories, with or without a grouping;
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
:read their categories from the database, each with its grouping;
if (the read fails?) then (yes)
  :storage failed;
  stop
endif
if (any category matched?) then (no)
  :answer an empty list;
  stop
endif
:answer every category, ordered by grouping and then by name;
stop
@enduml
```
