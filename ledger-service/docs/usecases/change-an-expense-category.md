# Change an entry's category

- **In**
  - the identity of the authenticated caller
  - the entry's id
  - the category to file it under, by its stored id
- **Out**
  - the entry as it now stands
- **Why:** correcting where one piece of spending was filed, without waiting for a decision on it first

*Implemented by `ChangeExpenseCategoryUseCase`.*

## Prerequisites

- The caller is authenticated. The request never names whose ledger it is.
- A user is stored under that identity.

The path, the document and each refusal are fixed by [the specification](../../../openapi/ledger-api.yaml).

## Collaborators

| Direction | Collaborator                                                            | Through                                                                 | For                                                                    |
|-----------|-------------------------------------------------------------------------|-------------------------------------------------------------------------|------------------------------------------------------------------------|
| in        | [A signed-in person's browser](../contracts/in/web-browse-api.md)       | [Browsing the ledger from a browser](../contracts/in/web-browse-api.md) | refiling one entry under another category                              |
| out       | [Database](../contracts/out/database.md)                                | [Users, categories and expenses](../contracts/out/database.md)          | resolving the identity, admitting the category, and refiling the entry |
| out       | [A consumer of the ledger's changes](../contracts/out/change-stream.md) | [The change stream](../contracts/out/change-stream.md)                  | publishing the fact this refile produces                               |

## Outcomes

| Outcome           | When                                                      | Result                                                                    |
|-------------------|-------------------------------------------------------------|------------------------------------------------------------------------------|
| Entry refiled     | the id names an entry of theirs, and the category is theirs | its category changes, and the change is logged                           |
| Same category     | the category named is the one the entry already carries     | answered the same way; only the last-updated instant moves               |
| Entry not found   | no entry of theirs carries that id                           | the request is refused, naming the entry — nothing is refiled            |
| Category refused  | the category id names no category of theirs                 | the request is refused, naming the category id — the entry is never read |
| Request rejected  | the request is absent                                        | the request is refused — nothing is looked up                            |
| Identity unknown  | nothing is stored under the caller's identity                | the request is rejected and nothing is refiled                           |
| Storage failed    | admitting the category, or the refile itself, fails          | the failure reaches the caller, and nothing is refiled                   |

## Components

```plantuml
@startuml C3-Component-ChangeExpenseCategory
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
  Component(accessControl, "Access Control", "Spring Security", "Admits only a call carrying a valid session cookie and a matching token", $tags="webExternal")
  Component(endpoint, "Expenses Endpoint", "Spring MVC", "Reads the id and the document, and renders the entry", $tags="webExternal")
  Component(changePort, "Change Expense Category Port", "Interface", "Inbound port", $tags="portIn")
  Component(changeService, "Change an Entry's Category Use Case", "Plain Java", "Resolves the person, admits the category, then refiles the entry", $tags="core")
  Component(userRepositoryPort, "User Repository Port", "Interface", "Outbound port", $tags="portOut")
  Component(categoryRepositoryPort, "Category Repository Port", "Interface", "Outbound port", $tags="portOut")
  Component(expenseRepositoryPort, "Expense Repository Port", "Interface", "Outbound port", $tags="portOut")
  Component(userRepositoryAdapter, "User Repository Adapter", "Spring Data Relational", "Reads the person stored under an identity", $tags="dbExternal")
  Component(categoryRepositoryAdapter, "Category Repository Adapter", "Spring Data Relational", "Says whether a category is the caller's own", $tags="dbExternal")
  Component(expenseRepositoryAdapter, "Expense Repository Adapter", "Spring Data Relational", "Refiles the caller's entry", $tags="dbExternal")
}

Rel(browser, accessControl, "Patches one entry's category", "HTTP, session cookie, CSRF token")
Rel_D(accessControl, endpoint, "Admits the call, with the caller's identity")
Rel_D(endpoint, changePort, "Invokes")
Rel_L(changeService, changePort, "Implements", $tags="implements")
Rel_R(changeService, userRepositoryPort, "Resolves the identity through")
Rel_R(changeService, categoryRepositoryPort, "Admits the category through")
Rel_R(changeService, expenseRepositoryPort, "Refiles through")
Rel_L(userRepositoryAdapter, userRepositoryPort, "Implements", $tags="implements")
Rel_L(categoryRepositoryAdapter, categoryRepositoryPort, "Implements", $tags="implements")
Rel_L(expenseRepositoryAdapter, expenseRepositoryPort, "Implements", $tags="implements")
Rel_R(userRepositoryAdapter, db, "SQL", "JDBC")
Rel_R(categoryRepositoryAdapter, db, "SQL", "JDBC")
Rel_R(expenseRepositoryAdapter, db, "SQL", "JDBC")

Lay_D(userRepositoryPort, categoryRepositoryPort)
Lay_D(categoryRepositoryPort, expenseRepositoryPort)
Lay_D(userRepositoryAdapter, categoryRepositoryAdapter)
Lay_D(categoryRepositoryAdapter, expenseRepositoryAdapter)

SHOW_LEGEND()
@enduml
```

## Flow

```plantuml
@startuml ChangeAnExpenseCategory-Sequence
participant "A signed-in person's browser" as Web
participant "Change an entry's category" as UC
database "Database" as DB
queue "The change stream" as Stream

Web -> UC : the entry's id, the category, and the caller's identity

alt the request is absent or a value is out of bounds
  UC --> Web : the request is refused, naming what was refused
else the request is usable
  UC -> DB : find the person behind the identity
  alt nothing is stored under that identity
    DB --> UC : nothing
    UC --> Web : the caller is unknown
  else the person is known
    DB --> UC : the person
    UC -> DB : is that category one of theirs?
    alt it is not
      DB --> UC : no
      UC --> Web : the category id is refused
    else it is
      DB --> UC : yes
      UC -> DB : refile their entry carrying that id
      alt no such entry
        DB --> UC : nothing
        UC --> Web : no entry of theirs carries that id
      else the entry was refiled
        DB --> UC : the entry as it now stands
        DB -> Stream : the fact, once committed
        UC -> UC : log the person, the entry, its status and its new category
        UC --> Web : the entry
      end
    end
  end
end
@enduml
```

## References

- [Browse a person's expenses](browse-expenses.md) — where the id this takes is answered
- [Browse a person's categories](browse-categories.md) — where the category id this takes is answered
- [ADR 0003: A category is unique per user and parent, not per user](../adr/0003-a-category-is-unique-per-user-and-parent-not-per-user.md) —
  why the caller sends a stored id rather than a category's name
- [ADR 0018: A proposal is a status on the expense table](../adr/0018-a-proposal-is-a-status-on-the-expense-table.md) —
  why one id is enough to name the entry, whichever status it stands under
