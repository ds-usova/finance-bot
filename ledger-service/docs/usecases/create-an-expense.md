# Create an expense

- **In:** the identity of the user it is recorded against · a category, by its stored id · a description ·
  a merchant (optional) · a money amount
- **Out:** the stored expense
- **Why:** a person's spending is kept against their own ledger, filed under a category, from the moment it
  happens

*Implemented by `CreateExpenseUseCase`.*

Nothing calls this use case yet.

## Collaborators

| Direction | Collaborator | Through | For |
|-----------|--------------|---------|-----|
| out | Database | [Users, categories and expenses](../contracts/out/database.md) | resolving the user's identity, and storing the expense |

## Rules

- The identity is opaque text, whatever the caller identifies a person by.
- Nothing is stored, and no expense is built, when the identity names no user
  ([new expense](../domain/new-expense.md) invariants; [expense](../domain/expense.md) invariants).
- A category is named by its stored id, never by name — resolving a name to an id is the caller's own concern
  ([ADR 0007](../adr/0007-a-category-is-named-by-its-stored-id.md)).
- Both timestamps are stamped at creation, equal to each other.
- How long its text may be is checked where it is stored
  ([ADR 0004](../adr/0004-column-widths-are-checked-in-the-persistence-adapter.md)).

## Outcomes

| Outcome | When | Result |
|---------|------|--------|
| Expense created | the identity names a stored user, and every field is valid | the expense is stored, stamped with the current instant, and the creation is logged |
| Request rejected | the command is absent, or a field violates [new expense](../domain/new-expense.md)'s invariants | invalid expense — nothing is looked up or written |
| Identity unknown | nothing is stored under the identity | the request is rejected and nothing is written |
| Storage failed | the store cannot be reached, refuses the write, or a value is too long for its column | the failure reaches the caller |

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

Caller -> LS : a new expense

alt command is invalid
  LS --> Caller : invalid expense
else identity is unknown
  LS -> DB : look the identity up
  DB --> LS : nothing
  LS --> Caller : identity unknown
else the store refuses the write
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
  LS -> LS : log the creation
  LS --> Caller : the stored expense
end
@enduml
```
