# List a grouping's categories

- **In:** the identity of the authenticated caller · a grouping, by name
- **Out:** the names of the categories filed under that grouping, ordered by name
- **Why:** spending is filed under a category, and this is how a caller holding only a grouping finds the
  categories it may choose between

*Implemented by `ListCategoriesUseCase`.*

## Collaborators

| Direction | Collaborator                                                                                                 | Through                                                                           | For                                                                                             |
|-----------|--------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------|
| in        | [Record the spending a user's message names](../../../ai-connector-service/docs/usecases/extract-intents.md) | [MCP — the list categories tool](../contracts/in/mcp.md)                          | narrowing a grouping to the categories an expense may be filed under                            |
| out       | [Database](../contracts/out/database.md)                                                                     | [Users, categories, expenses and expense proposals](../contracts/out/database.md) | resolving the identity, resolving the grouping, reading its categories, telling a category from a grouping |

## Rules

- The identity is the one the service has already authenticated
  ([authenticated user id](../domain/authenticated-user-id.md)); the request never names whose categories it is
  ([ADR 0007](../adr/0007-an-mcp-caller-is-identified-by-a-signed-token-not-a-tool-argument.md)).
- Only the caller's own groupings and categories are reachable; no argument widens that.
- The [incoming message id](../domain/incoming-message-id.md) is not read, so a credential carrying none still lists.
- A [grouping](../domain/grouping.md) is named, never identified by a stored id.
- The name is present, and it is not blank.
- The name is resolved against the caller's groupings, and a grouping carrying it holds it alone.
- A name no grouping of theirs carries is rejected, and the message repeats the name.
- A name carried by a category of theirs rather than a grouping is rejected as a category, not a grouping.
- A name that is both a grouping and a category resolves to the grouping.
- Whether a category carries the name is asked only once no grouping does.
- A grouping holding no categories answers an empty list, which is not a failure.
- Nothing is stored, created or changed.

## Outcomes

| Outcome            | When                                                                     | Result                                                                 |
|--------------------|--------------------------------------------------------------------------|------------------------------------------------------------------------|
| Categories listed  | the identity names a stored user and the name resolves to their grouping | that grouping's categories, ordered by name                            |
| Request rejected   | the request is absent, or the name is missing or blank                   | invalid grouping — nothing is looked up                                |
| Identity unknown   | nothing is stored under the identity                                     | the request is rejected and nothing is listed                          |
| Grouping unknown   | neither a grouping nor a category of theirs carries that name            | the request is rejected, repeating the name that was asked for         |
| Name is a category | no grouping of theirs carries the name and a category of theirs does     | the request is rejected, saying the name is a category, not a grouping |
| Storage failed     | the store cannot be reached                                              | the failure reaches the caller                                         |

## Components

```plantuml
@startuml C3-Component-ListCategories
!include <C4/C4_Component>

AddElementTag("dbExternal", $bgColor="#d68910", $fontColor="#ffffff", $borderColor="#8f5c0a")
AddElementTag("mcpExternal", $bgColor="#c0392b", $fontColor="#ffffff", $borderColor="#7b241c")
AddElementTag("portIn", $bgColor="#16a085", $fontColor="#ffffff", $borderColor="#0e6655", $legendText="inbound port (interface)")
AddElementTag("portOut", $bgColor="#7f8c8d", $fontColor="#ffffff", $borderColor="#566573", $legendText="outbound port (interface)")
AddElementTag("core", $bgColor="#2c3e50", $fontColor="#ffffff", $borderColor="#1b2631", $legendText="application core")
AddRelTag("implements", $lineStyle="dashed")

System_Ext(agent, "Agent acting for a user", "An MCP client", $tags="mcpExternal")
ContainerDb(db, "Database", "PostgreSQL", "Stores users, categories, expenses and expense proposals", $tags="dbExternal")

Container_Boundary(ledger, "Ledger Service (Java, Spring Boot)") {
  Component(accessControl, "Access Control", "Spring Security", "Admits only calls carrying a valid token", $tags="mcpExternal")
  Component(mcpTool, "List Categories Tool", "Spring AI MCP Server", "Takes the grouping's name and the caller's identity", $tags="mcpExternal")
  Component(listCategoriesPort, "List Categories Port", "Interface", "Inbound port", $tags="portIn")
  Component(listCategoriesService, "List a Grouping's Categories Use Case", "Plain Java", "Resolves the user and the grouping, and reads its categories", $tags="core")
  Component(userRepositoryPort, "User Repository Port", "Interface", "Outbound port", $tags="portOut")
  Component(groupingRepositoryPort, "Grouping Repository Port", "Interface", "Outbound port", $tags="portOut")
  Component(categoryRepositoryPort, "Category Repository Port", "Interface", "Outbound port", $tags="portOut")
  Component(userRepositoryAdapter, "User Repository Adapter", "Spring Data Relational", "Checks if user exists", $tags="dbExternal")
  Component(groupingRepositoryAdapter, "Grouping Repository Adapter", "Spring Data Relational", "Reads a grouping by name and the categories it holds", $tags="dbExternal")
  Component(categoryRepositoryAdapter, "Category Repository Adapter", "Spring Data Relational", "Tells whether a category carries a name", $tags="dbExternal")
}

Rel(agent, accessControl, "Tool call", "MCP over HTTP")
Rel_D(accessControl, mcpTool, "Admits the call, with the caller's identity")
Rel_D(mcpTool, listCategoriesPort, "Invokes")
Rel_L(listCategoriesService, listCategoriesPort, "Implements", $tags="implements")
Rel_R(listCategoriesService, userRepositoryPort, "Resolves the identity through")
Rel_R(listCategoriesService, groupingRepositoryPort, "Resolves the grouping and reads its categories through")
Rel_R(listCategoriesService, categoryRepositoryPort, "Tells a category from a grouping through")
Rel_L(userRepositoryAdapter, userRepositoryPort, "Implements", $tags="implements")
Rel_L(groupingRepositoryAdapter, groupingRepositoryPort, "Implements", $tags="implements")
Rel_L(categoryRepositoryAdapter, categoryRepositoryPort, "Implements", $tags="implements")
Rel_R(userRepositoryAdapter, db, "SQL", "JDBC")
Rel_R(groupingRepositoryAdapter, db, "SQL", "JDBC")
Rel_R(categoryRepositoryAdapter, db, "SQL", "JDBC")

Lay_D(userRepositoryPort, groupingRepositoryPort)
Lay_D(groupingRepositoryPort, categoryRepositoryPort)
Lay_D(groupingRepositoryAdapter, categoryRepositoryAdapter)

SHOW_LEGEND()
@enduml
```

## Flow

```plantuml
@startuml ListCategories-Activity
start
:an agent acting for a user asks for a grouping's categories;
if (the request is absent, or the name is missing or blank?) then (yes)
  :invalid grouping — nothing is looked up;
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
:read their grouping carrying that name from the database;
if (the read fails?) then (yes)
  :storage failed;
  stop
endif
if (a grouping carries the name?) then (no)
  :ask whether a category of theirs carries it;
  if (the read fails?) then (yes)
    :storage failed;
    stop
  endif
  if (a category carries it?) then (yes)
    :the name is a category, not a grouping;
    stop
  else (no)
    :grouping unknown, repeating the name;
    stop
  endif
endif
:read that grouping's categories from the database, ordered by name;
if (the read fails?) then (yes)
  :storage failed;
  stop
endif
:answer the categories;
stop
@enduml
```
