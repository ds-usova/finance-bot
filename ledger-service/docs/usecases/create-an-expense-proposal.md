# Create an expense proposal

- **In:** the identity of the user it is recorded against · a category, by its stored id · a description ·
  a merchant (optional) · a money amount
- **Out:** the stored expense proposal
- **Why:** spending that has been assembled but not yet accepted is kept apart from the user's own ledger
  ([ADR 0006](../adr/0006-an-expense-proposal-is-a-table-and-an-entity-of-its-own.md))

*Implemented by `CreateExpenseProposalUseCase`.*

Nothing calls this use case yet.

## Collaborators

| Direction | Collaborator | Through                                                                              | For                                                             |
|-----------|--------------|--------------------------------------------------------------------------------------|-----------------------------------------------------------------|
| out       | Database     | [Users, categories, expenses and expense proposals](../contracts/out/database.md)     | resolving the user's identity, and storing the proposal          |

## Rules

- The identity is opaque text, whatever the caller identifies a person by, present and not blank.
- A description is present, and it is not blank.
- A category is named by its stored id, never by name, and the id is positive.
- A merchant is present as an optional value, never absent — but a present, blank merchant is normalized to
  absent rather than rejected.
- A money amount is present.
- Nothing is stored, and no proposal is built, when the identity names no user.
- Both timestamps are stamped at creation, equal to each other.
- How long its text may be is checked where it is stored
  ([ADR 0004](../adr/0004-column-widths-are-checked-in-the-persistence-adapter.md)).
- A stored proposal is not an expense, and nothing further happens to it yet.

## Outcomes

| Outcome           | When                                                                                                     | Result                                                                              |
|-------------------|----------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------|
| Proposal created  | the identity names a stored user, and every field is valid                                               | the proposal is stored, stamped with the current instant, and the creation is logged |
| Request rejected  | the command is absent, or a field violates [expense proposal](../domain/expense-proposal.md)'s invariants | invalid expense proposal — nothing is looked up or written                           |
| Identity unknown  | nothing is stored under the identity                                                                     | the request is rejected and nothing is written                                      |
| Category unknown  | the category id names no stored category                                                                 | the request is rejected and nothing is written                                      |
| Storage failed    | the store cannot be reached, or a value is too long for its column                                       | the failure reaches the caller                                                      |

## Components

```plantuml
@startuml C3-Component-CreateExpenseProposal
!include <C4/C4_Component>

AddElementTag("dbExternal", $bgColor="#d68910", $fontColor="#ffffff", $borderColor="#8f5c0a")
AddElementTag("portIn", $bgColor="#16a085", $fontColor="#ffffff", $borderColor="#0e6655", $legendText="inbound port (interface)")
AddElementTag("portOut", $bgColor="#7f8c8d", $fontColor="#ffffff", $borderColor="#566573", $legendText="outbound port (interface)")
AddElementTag("core", $bgColor="#2c3e50", $fontColor="#ffffff", $borderColor="#1b2631", $legendText="application core")
AddRelTag("implements", $lineStyle="dashed")

ContainerDb(db, "Database", "PostgreSQL", "Stores users, categories, expenses and expense proposals", $tags="dbExternal")

Container_Boundary(ledger, "Ledger Service (Java, Spring Boot)") {
  Component(createProposalPort, "Create Expense Proposal Port", "Interface", "Inbound port", $tags="portIn")
  Component(createProposalService, "Create an Expense Proposal Use Case", "Plain Java", "Resolves the user and stores the proposal", $tags="core")
  Component(userRepositoryPort, "User Repository Port", "Interface", "Outbound port", $tags="portOut")
  Component(proposalRepositoryPort, "Expense Proposal Repository Port", "Interface", "Outbound port", $tags="portOut")
  Component(proposalRepositoryAdapter, "Expense Proposal Repository Adapter", "Spring Data Relational", "Persists expense proposals", $tags="dbExternal")
}

Rel_L(createProposalService, createProposalPort, "Implements", $tags="implements")
Rel_D(createProposalService, userRepositoryPort, "Resolves the identity through")
Rel_D(createProposalService, proposalRepositoryPort, "Stores through")
Rel_L(proposalRepositoryAdapter, proposalRepositoryPort, "Implements", $tags="implements")
Rel_R(proposalRepositoryAdapter, db, "SQL", "JDBC")

SHOW_LEGEND()
@enduml
```

## Flow

```plantuml
@startuml CreateExpenseProposal-Sequence
participant "Caller" as Caller
participant "Ledger Service" as LS
database "Database" as DB

Caller -> LS : a new expense proposal

alt command is invalid
  LS --> Caller : invalid expense proposal
else identity is unknown
  LS -> DB : look the identity up
  DB --> LS : nothing
  LS --> Caller : identity unknown
else the category id names no category
  LS -> DB : look the identity up
  DB --> LS : the user
  LS -> DB : store the proposal
  DB --> LS : category unknown
  LS --> Caller : category unknown
else the store fails
  LS -> DB : look the identity up
  DB --> LS : the user
  LS -> DB : store the proposal
  DB --> LS : the write fails
  LS --> Caller : storage failed
else the identity is known
  LS -> DB : look the identity up
  DB --> LS : the user
  LS -> LS : stamp both timestamps
  LS -> DB : store the proposal
  DB --> LS : the stored proposal
  LS -> LS : log the creation
  LS --> Caller : the stored proposal
end
@enduml
```
