# Create an expense

- **In**
  - the identity of the user it is recorded against
  - a category, by its stored id
  - a description
  - a merchant (optional)
  - a money amount
- **Out**
  - the stored expense
- **Why:** a person's spending is kept against their own ledger, filed under a category, from the moment it
  happens

*Implemented by `CreateExpenseUseCase`.*

## Collaborators

| Direction | Collaborator                                                            | Through                                                        | For                                                    |
|-----------|-------------------------------------------------------------------------|----------------------------------------------------------------|--------------------------------------------------------|
| out       | [Database](../contracts/out/database.md)                                | [Users, categories and expenses](../contracts/out/database.md) | resolving the user's identity, and storing the expense |
| out       | [A consumer of the ledger's changes](../contracts/out/change-stream.md) | [The change stream](../contracts/out/change-stream.md)         | publishing the fact this write produced                |

## Outcomes

| Outcome          | When                                                                                       | Result                                                                              |
|------------------|--------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------|
| Expense created  | the identity names a stored user, and every field is valid                                 | the expense is stored recorded and naming no message, stamped with the current instant, and the creation is logged |
| Request rejected | the command is absent, or a field violates [expense](../domain/expense.md)'s invariants    | invalid expense — nothing is looked up or written                                   |
| Identity unknown | nothing is stored under the identity                                                       | the request is rejected and nothing is written                                      |
| Category unknown | the category id names no stored category                                                   | the request is rejected and nothing is written                                      |
| Storage failed   | the store cannot be reached, or a value is too long for its column                         | the failure reaches the caller                                                      |

## Components

```plantuml
@startuml C3-Component-CreateExpense
!include <C4/C4_Component>

AddElementTag("dbExternal", $bgColor="#d68910", $fontColor="#ffffff", $borderColor="#8f5c0a")
AddElementTag("portIn", $bgColor="#16a085", $fontColor="#ffffff", $borderColor="#0e6655", $legendText="inbound port (interface)")
AddElementTag("portOut", $bgColor="#7f8c8d", $fontColor="#ffffff", $borderColor="#566573", $legendText="outbound port (interface)")
AddElementTag("core", $bgColor="#2c3e50", $fontColor="#ffffff", $borderColor="#1b2631", $legendText="application core")
AddRelTag("implements", $lineStyle="dashed")

ContainerDb(db, "Database", "PostgreSQL", "Stores users, categories and expenses", $tags="dbExternal")

Container_Boundary(ledger, "Ledger Service (Java, Spring Boot)") {
  Component(createExpensePort, "Create Expense Port", "Interface", "Inbound port", $tags="portIn")
  Component(createExpenseService, "Create an Expense Use Case", "Plain Java", "Resolves the user and stores the expense", $tags="core")
  Component(userRepositoryPort, "User Repository Port", "Interface", "Outbound port", $tags="portOut")
  Component(expenseRepositoryPort, "Expense Repository Port", "Interface", "Outbound port", $tags="portOut")
  Component(expenseRepositoryAdapter, "Expense Repository Adapter", "Spring Data Relational", "Persists expenses", $tags="dbExternal")
}

Rel_L(createExpenseService, createExpensePort, "Implements", $tags="implements")
Rel_D(createExpenseService, userRepositoryPort, "Resolves the identity through")
Rel_D(createExpenseService, expenseRepositoryPort, "Stores through")
Rel_L(expenseRepositoryAdapter, expenseRepositoryPort, "Implements", $tags="implements")
Rel_R(expenseRepositoryAdapter, db, "SQL", "JDBC")

SHOW_LEGEND()
@enduml
```

## Flow

```plantuml
@startuml CreateExpense-Sequence
participant "Caller" as Caller
participant "Ledger Service" as LS
database "Database" as DB
queue "The change stream" as Stream

Caller -> LS : a new expense

alt command is invalid
  LS --> Caller : invalid expense
else identity is unknown
  LS -> DB : look the identity up
  DB --> LS : nothing
  LS --> Caller : identity unknown
else the category id names no category
  LS -> DB : look the identity up
  DB --> LS : the user
  LS -> DB : store the expense
  DB --> LS : category unknown
  LS --> Caller : category unknown
else the store fails
  LS -> DB : look the identity up
  DB --> LS : the user
  LS -> DB : store the expense
  DB --> LS : the write fails
  LS --> Caller : storage failed
else the identity is known
  LS -> DB : look the identity up
  DB --> LS : the user
  LS -> LS : stamp both timestamps
  LS -> DB : store the expense
  DB --> LS : the stored expense
  DB -> Stream : the fact, once committed
  LS -> LS : log the creation
  LS --> Caller : the stored expense
end
@enduml
```

## References

- [ADR 0004: Column widths are checked in the persistence adapter](../adr/0004-column-widths-are-checked-in-the-persistence-adapter.md) —
  where a description or a merchant too long is refused
- [ADR 0003: A category is unique per user and parent, not per user](../adr/0003-a-category-is-unique-per-user-and-parent-not-per-user.md) —
  why the caller sends a stored id rather than a category's name
