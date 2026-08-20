# Browse a person's expenses

- **In**
  - the identity of the authenticated caller
  - an [expense filter](../domain/expense-filter.md)
- **Out**
  - one page of entries, newest first, with the page size and the offset applied
  - how many rows the filter matches
  - each UTC day's recorded spend, as figures ready to show
- **Why:** it is how a signed-in person sees everything the ledger holds for them in one list

*Implemented by `BrowseExpensesUseCase`.*

## Prerequisites

- The caller is authenticated. The request never names whose expenses it is.
- A user is stored under that identity.

## Collaborators

| Direction | Collaborator                                                                           | Through                                                                           | For                                                                            |
|-----------|----------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------|--------------------------------------------------------------------------------|
| in        | [Browse recorded expenses](../../../web-app/docs/usecases/browse-recorded-expenses.md) | [Browsing the ledger from a browser](../contracts/in/web-browse-api.md)           | showing a signed-in person their spending, newest first                        |
| out       | [Database](../contracts/out/database.md)                                               | [Users, categories and expenses](../contracts/out/database.md)                    | resolving the identity, reading the page, and counting what the filter matches |

## Outcomes

| Outcome          | When                                                           | Result                                                                                    |
|------------------|----------------------------------------------------------------|-------------------------------------------------------------------------------------------|
| Page answered    | the identity names a stored user                               | the matching entries and each day's figures, with the page size, the offset and the total |
| Empty page       | the filter matches nothing, or the offset is past the last row | no entries, no day figures, and the real total                                            |
| Request rejected | the request names no identity, or no filter                    | the request is refused — nothing is looked up                                             |
| Filter rejected  | the page size is out of bounds, or the offset is negative      | invalid expense filter, naming the bound — nothing is looked up                           |
| Identity unknown | nothing is stored under the identity                           | the request is rejected and nothing is listed                                             |
| Storage failed   | the store cannot be reached                                    | the failure reaches the caller                                                            |

An unfiltered listing answers both kinds. A category arrives as an id, named by
[the category listing](browse-categories.md).

| Kind     | Counts towards the day's figures |
|----------|----------------------------------|
| Recorded | yes                              |
| Pending  | no                               |

What the [specification](../../../openapi/ledger-api.yaml) does not fix about those figures:

- A day split by the page boundary is figured on each page, over its own part.
- A narrowed filter narrows the figures.

## Components

```plantuml
@startuml C3-Component-BrowseExpenses
!include <C4/C4_Component>

AddElementTag("dbExternal", $bgColor="#d68910", $fontColor="#ffffff", $borderColor="#8f5c0a")
AddElementTag("webExternal", $bgColor="#2874a6", $fontColor="#ffffff", $borderColor="#1b4f72")
AddElementTag("portIn", $bgColor="#16a085", $fontColor="#ffffff", $borderColor="#0e6655", $legendText="inbound port (interface)")
AddElementTag("portOut", $bgColor="#7f8c8d", $fontColor="#ffffff", $borderColor="#566573", $legendText="outbound port (interface)")
AddElementTag("core", $bgColor="#2c3e50", $fontColor="#ffffff", $borderColor="#1b2631", $legendText="application core")
AddRelTag("implements", $lineStyle="dashed")

System_Ext(browser, "A signed-in person's browser", "The web app's page", $tags="webExternal")
ContainerDb(db, "Database", "PostgreSQL", "Stores users, categories and expenses", $tags="dbExternal")

Container_Boundary(ledger, "Ledger Service (Java, Spring Boot)") {
  Component(accessControl, "Access Control", "Spring Security", "Admits only calls carrying a valid session cookie", $tags="webExternal")
  Component(endpoint, "Expenses Endpoint", "Spring MVC", "Reads the filter and the caller, and renders every figure it answers", $tags="webExternal")
  Component(browsePort, "Browse Expenses Port", "Interface", "Inbound port", $tags="portIn")
  Component(browseService, "Browse a Person's Expenses Use Case", "Plain Java", "Resolves the user, reads the page, and counts the matches", $tags="core")
  Component(userRepositoryPort, "User Repository Port", "Interface", "Outbound port", $tags="portOut")
  Component(expenseRepositoryPort, "Expense Repository Port", "Interface", "Outbound port", $tags="portOut")
  Component(userRepositoryAdapter, "User Repository Adapter", "Spring Data Relational", "Checks if user exists", $tags="dbExternal")
  Component(expenseRepositoryAdapter, "Expense Repository Adapter", "Spring Data Relational", "Reads one page across both statuses, and counts what the filter matches", $tags="dbExternal")
}

Rel(browser, accessControl, "Asks for a page of expenses", "HTTP, session cookie")
Rel_D(accessControl, endpoint, "Admits the call, with the caller's identity")
Rel_D(endpoint, browsePort, "Invokes")
Rel_L(browseService, browsePort, "Implements", $tags="implements")
Rel_R(browseService, userRepositoryPort, "Resolves the identity through")
Rel_R(browseService, expenseRepositoryPort, "Reads the page and the total through")
Rel_L(userRepositoryAdapter, userRepositoryPort, "Implements", $tags="implements")
Rel_L(expenseRepositoryAdapter, expenseRepositoryPort, "Implements", $tags="implements")
Rel_R(userRepositoryAdapter, db, "SQL", "JDBC")
Rel_R(expenseRepositoryAdapter, db, "SQL", "JDBC")

Lay_D(userRepositoryPort, expenseRepositoryPort)
Lay_D(userRepositoryAdapter, expenseRepositoryAdapter)

SHOW_LEGEND()
@enduml
```

## Flow

```plantuml
@startuml BrowseExpenses-Activity
start
:a signed-in person asks for a page of their expenses;
if (the request names no identity, or no filter?) then (yes)
  :the request is refused — nothing is looked up;
  stop
endif
if (the page size or the offset is out of bounds?) then (yes)
  :invalid expense filter, naming the bound;
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
:read one page of their entries from the database, under the status asked for or both, newest first;
if (the read fails?) then (yes)
  :storage failed;
  stop
endif
:count how many of their rows the filter matches;
if (the read fails?) then (yes)
  :storage failed;
  stop
endif
:total the page's recorded entries by UTC day and currency;
:answer the entries, the day figures, the page size, the offset and the total;
stop
@enduml
```
