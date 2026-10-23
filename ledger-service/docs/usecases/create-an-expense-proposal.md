# Create an expense proposal

- **In**
  - the identity of the authenticated caller
  - the reference of the message being handled
  - a category, by name
  - the grouping that category is filed under
  - a description
  - a merchant (optional)
  - a money amount
- **Out**
  - the stored entry, pending
- **Why:** assembled spending stays out of the user's own totals until they accept it

*Implemented by `CreateExpenseProposalUseCase`.*

## Collaborators

| Direction | Collaborator                                                                                                 | Through                                                                            | For                                                                          |
|-----------|--------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------|------------------------------------------------------------------------------|
| in        | [Record the spending a user's message names](../../../ai-connector-service/docs/usecases/extract-intents.md) | [MCP — the create expense proposal tool](../contracts/in/mcp.md)                 | recording spending assembled from a conversation                             |
| in        | [Record the spending a user's message names](../../../ai-connector-service/docs/usecases/extract-intents.md) | [Turn operations — file an expense proposal](../contracts/in/turn-operations.md) | filing an expense a turn read from a message                                 |
| out       | [Database](../contracts/out/database.md)                                                                     | [Users, categories and expenses](../contracts/out/database.md)                     | resolving the identity, the grouping and its category, and storing the entry |
| out       | [A consumer of the ledger's changes](../contracts/out/change-stream.md)                                      | [The change stream](../contracts/out/change-stream.md)                             | publishing the fact this write produced                                      |

## Outcomes

| Outcome          | When                                                                                    | Result                                                                                  |
|------------------|-----------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------|
| Proposal created | the identity, the grouping and a category of that name under it all resolve             | a pending entry is stored, stamped with the current instant, and the creation is logged |
| Request rejected | the command is absent, or a field violates [expense](../domain/expense.md)'s invariants | invalid expense — nothing is looked up or written                                     |
| Identity unknown | nothing is stored under the identity                                                    | the request is rejected and nothing is written                                          |
| Grouping unknown | no grouping of theirs carries the grouping name                                         | the request is rejected, repeating that name, and nothing is written                    |
| Category unknown | that grouping holds no category of the category name                                    | the request is rejected, naming both, and nothing is written                            |
| Storage failed   | the store cannot be reached, or a value is too long for its column                      | the failure reaches the caller                                                          |

## Components

```plantuml
@startuml C3-Component-CreateExpenseProposal
!include <C4/C4_Component>

AddElementTag("dbExternal", $bgColor="#d68910", $fontColor="#ffffff", $borderColor="#8f5c0a")
AddElementTag("mcpExternal", $bgColor="#c0392b", $fontColor="#ffffff", $borderColor="#7b241c")
AddElementTag("aiExternal", $bgColor="#8e44ad", $fontColor="#ffffff", $borderColor="#5b2c6f")
AddElementTag("portIn", $bgColor="#16a085", $fontColor="#ffffff", $borderColor="#0e6655", $legendText="inbound port (interface)")
AddElementTag("portOut", $bgColor="#7f8c8d", $fontColor="#ffffff", $borderColor="#566573", $legendText="outbound port (interface)")
AddElementTag("core", $bgColor="#2c3e50", $fontColor="#ffffff", $borderColor="#1b2631", $legendText="application core")
AddRelTag("implements", $lineStyle="dashed")

System_Ext(agent, "Agent acting for a user", "An MCP client", $tags="mcpExternal")
Container(connector, "AI Connector Service", "Java, Spring Boot", "Files a turn's results", $tags="aiExternal")
ContainerDb(db, "Database", "PostgreSQL", "Stores users, categories and expenses", $tags="dbExternal")

Container_Boundary(ledger, "Ledger Service (Java, Spring Boot)") {
  Component(accessControl, "Access Control", "Spring Security", "Admits only calls carrying a valid token", $tags="mcpExternal")
  Component(mcpTool, "Create Expense Proposal Tool", "Spring AI MCP Server", "Takes the tool's arguments and the caller's identity", $tags="mcpExternal")
  Component(turnCheck, "Turn Credential Check", "gRPC", "Admits only the turn's token, naming a message", $tags="aiExternal")
  Component(turnOperations, "Turn Operations", "gRPC server", "Takes the operation's fields and the caller's identity", $tags="aiExternal")
  Component(createProposalPort, "Create Expense Proposal Port", "Interface", "Inbound port", $tags="portIn")
  Component(createProposalService, "Create an Expense Proposal Use Case", "Plain Java", "Resolves the user, the grouping and the category, and stores the proposal", $tags="core")
  Component(userRepositoryPort, "User Repository Port", "Interface", "Outbound port", $tags="portOut")
  Component(groupingRepositoryPort, "Grouping Repository Port", "Interface", "Outbound port", $tags="portOut")
  Component(categoryRepositoryPort, "Category Repository Port", "Interface", "Outbound port", $tags="portOut")
  Component(expenseRepositoryPort, "Expense Repository Port", "Interface", "Outbound port", $tags="portOut")
  Component(userRepositoryAdapter, "User Repository Adapter", "Spring Data Relational", "Checks if user exists", $tags="dbExternal")
  Component(groupingRepositoryAdapter, "Grouping Repository Adapter", "Spring Data Relational", "Reads a grouping by name", $tags="dbExternal")
  Component(categoryRepositoryAdapter, "Category Repository Adapter", "Spring Data Relational", "Reads a category by name under a grouping", $tags="dbExternal")
  Component(expenseRepositoryAdapter, "Expense Repository Adapter", "Spring Data Relational", "Persists the entry as pending", $tags="dbExternal")
}

Rel(agent, accessControl, "Tool call", "MCP over HTTP")
Rel_D(accessControl, mcpTool, "Admits the call, with the caller's identity")
Rel_D(mcpTool, createProposalPort, "Invokes")
Rel(connector, turnCheck, "FileExpenseProposal", "gRPC")
Rel_D(turnCheck, turnOperations, "Admits the call, with the caller's identity")
Rel_D(turnOperations, createProposalPort, "Invokes")
Rel_L(createProposalService, createProposalPort, "Implements", $tags="implements")
Rel_R(createProposalService, userRepositoryPort, "Resolves the identity through")
Rel_R(createProposalService, groupingRepositoryPort, "Resolves the grouping through")
Rel_R(createProposalService, categoryRepositoryPort, "Resolves the category under it through")
Rel_R(createProposalService, expenseRepositoryPort, "Stores through")
Rel_L(userRepositoryAdapter, userRepositoryPort, "Implements", $tags="implements")
Rel_L(groupingRepositoryAdapter, groupingRepositoryPort, "Implements", $tags="implements")
Rel_L(categoryRepositoryAdapter, categoryRepositoryPort, "Implements", $tags="implements")
Rel_L(expenseRepositoryAdapter, expenseRepositoryPort, "Implements", $tags="implements")
Rel_R(userRepositoryAdapter, db, "SQL", "JDBC")
Rel_R(groupingRepositoryAdapter, db, "SQL", "JDBC")
Rel_R(categoryRepositoryAdapter, db, "SQL", "JDBC")
Rel_R(expenseRepositoryAdapter, db, "SQL", "JDBC")

Lay_D(userRepositoryPort, groupingRepositoryPort)
Lay_D(groupingRepositoryPort, categoryRepositoryPort)
Lay_D(categoryRepositoryPort, expenseRepositoryPort)

SHOW_LEGEND()
@enduml
```

## Flow

```plantuml
@startuml CreateExpenseProposal-Activity
start
:the AI connector sends a new expense proposal, as a tool call or a turn operation;
if (the command is absent, or a field is invalid?) then (yes)
  :invalid expense — nothing is looked up or written;
  stop
endif
:look the identity up in the database;
if (the identity names a stored user?) then (no)
  :identity unknown, nothing is written;
  stop
endif
:read their grouping carrying the grouping name from the database;
if (the read fails?) then (yes)
  :storage failed;
  stop
endif
if (a grouping carries the name?) then (no)
  :grouping unknown, repeating the name;
  stop
endif
:read the category of that name under that grouping from the database;
if (the read fails?) then (yes)
  :storage failed;
  stop
endif
if (the grouping holds a category of that name?) then (no)
  :category unknown, naming the category and the grouping;
  stop
endif
:stamp both timestamps;
:store the entry as pending, under that category and that message, its fact appended in the same transaction;
if (the write fails?) then (yes)
  :storage failed;
  stop
endif
:log the creation;
:answer the stored entry;
stop
@enduml
```

## References

- [ADR 0018: A proposal is a status on the expense table](../adr/0018-a-proposal-is-a-status-on-the-expense-table.md) —
  why the id this answers with survives the person's acceptance
- [ADR 0007: An MCP caller is identified by a signed token, not a tool argument](../adr/0007-an-mcp-caller-is-identified-by-a-signed-token-not-a-tool-argument.md) —
  why the request names no person
- [ADR 0020: The ledger serves a turn's writes as gRPC operations beside its MCP tools](../../../docs/adr/0020-the-ledger-serves-a-turns-writes-as-grpc-operations-on-a-port-of-their-own-beside-its-mcp-tools-behind-the-tools-own-credential.md) —
  why a proposal arrives two ways
- [ADR 0015: A turn is named by the message that started it](../adr/0015-a-turn-is-named-by-the-message-that-started-it-not-by-a-value-minted-beside-it.md) —
  why the request names no message either
- [ADR 0003: A category is unique per user and parent, not per user](../adr/0003-a-category-is-unique-per-user-and-parent-not-per-user.md) —
  why a grouping is resolved before the category under it
- [ADR 0011: The amount is scaled to minor units in the domain](../adr/0011-the-amount-is-scaled-to-minor-units-in-the-domain.md) —
  what happens to the amount as the message wrote it
- [ADR 0004: Column widths are checked in the persistence adapter](../adr/0004-column-widths-are-checked-in-the-persistence-adapter.md) —
  where a description too long is refused
