# Create an expense proposal

- **In:** the identity of the authenticated caller · the reference of the message being handled · a category, by
  name · the grouping that category is filed under · a description · a merchant (optional) · a money amount
- **Out:** the stored expense proposal
- **Why:** spending that has been assembled but not yet accepted is kept apart from the user's own ledger
  ([ADR 0006](../adr/0006-an-expense-proposal-is-a-table-and-an-entity-of-its-own.md))

*Implemented by `CreateExpenseProposalUseCase`.*

## Collaborators

| Direction | Collaborator                                                                                                 | Through                                                                           | For                                                                                        |
|-----------|--------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------|
| in        | [Record the spending a user's message names](../../../ai-connector-service/docs/usecases/extract-intents.md) | [MCP — the create expense proposal tool](../contracts/in/mcp.md)                  | recording spending it has assembled from a conversation                                    |
| out       | [Database](../contracts/out/database.md)                                                                     | [Users, categories, expenses and expense proposals](../contracts/out/database.md) | resolving the identity, resolving the grouping and the category under it, storing the proposal |

## Rules

- The identity is the one the service has already authenticated
  ([authenticated user id](../domain/authenticated-user-id.md)); the request never names whose proposal it is
  ([ADR 0007](../adr/0007-an-mcp-caller-is-identified-by-a-signed-token-not-a-tool-argument.md)).
- The [incoming message id](../domain/incoming-message-id.md) is read from the same credential as the identity, and
  the request never names it either
  ([ADR 0015](../adr/0015-a-turn-is-named-by-the-message-that-started-it-not-by-a-value-minted-beside-it.md)).
- A stored proposal records which message produced it, so the report answering that message can name it.
- Spending is filed under a [category](../domain/category.md), never under the
  [grouping](../domain/grouping.md) holding it.
- Both names are resolved among the caller's own rows only, and neither is ever a stored id.
- The grouping is required, and it is not blank.
- The grouping is resolved first: a name no grouping of theirs carries is rejected, and the message repeats it.
- The category is resolved under that grouping: a name it holds no category of is rejected, and the message
  names both.
- A grouping's own name is not one of its categories, so sending it as the category is rejected the same way.
- A grouping holds at most one category of a given name
  ([ADR 0003](../adr/0003-a-category-is-unique-per-user-and-parent-not-per-user.md)).
- A description is present, and it is not blank.
- A merchant is present as an optional value, never absent — but a present, blank merchant is normalized to
  absent rather than rejected.
- A money amount is present.
- Nothing is stored, and no proposal is built, when the identity names no user.
- Both timestamps are stamped at creation, equal to each other.
- How long its text may be is checked where it is stored
  ([ADR 0004](../adr/0004-column-widths-are-checked-in-the-persistence-adapter.md)).
- A stored proposal is not an expense, and nothing further happens to it yet.
- Nothing makes a repeat of the same request the same proposal: it stores a second one.

## Outcomes

| Outcome          | When                                                                                                      | Result                                                                               |
|------------------|-----------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------|
| Proposal created | the identity names a stored user, the grouping resolves, and it holds a category of the name given        | the proposal is stored, stamped with the current instant, and the creation is logged |
| Request rejected | the command is absent, or a field violates [expense proposal](../domain/expense-proposal.md)'s invariants | invalid expense proposal — nothing is looked up or written                           |
| Identity unknown | nothing is stored under the identity                                                                      | the request is rejected and nothing is written                                       |
| Grouping unknown | no grouping of theirs carries the grouping name                                                           | the request is rejected, repeating that name, and nothing is written                 |
| Category unknown | that grouping holds no category of the category name                                                      | the request is rejected, naming both, and nothing is written                         |
| Storage failed   | the store cannot be reached, or a value is too long for its column                                        | the failure reaches the caller                                                       |

## Components

```plantuml
@startuml C3-Component-CreateExpenseProposal
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
  Component(mcpTool, "Create Expense Proposal Tool", "Spring AI MCP Server", "Takes the tool's arguments and the caller's identity", $tags="mcpExternal")
  Component(createProposalPort, "Create Expense Proposal Port", "Interface", "Inbound port", $tags="portIn")
  Component(createProposalService, "Create an Expense Proposal Use Case", "Plain Java", "Resolves the user, the grouping and the category, and stores the proposal", $tags="core")
  Component(userRepositoryPort, "User Repository Port", "Interface", "Outbound port", $tags="portOut")
  Component(groupingRepositoryPort, "Grouping Repository Port", "Interface", "Outbound port", $tags="portOut")
  Component(categoryRepositoryPort, "Category Repository Port", "Interface", "Outbound port", $tags="portOut")
  Component(proposalRepositoryPort, "Expense Proposal Repository Port", "Interface", "Outbound port", $tags="portOut")
  Component(userRepositoryAdapter, "User Repository Adapter", "Spring Data Relational", "Checks if user exists", $tags="dbExternal")
  Component(groupingRepositoryAdapter, "Grouping Repository Adapter", "Spring Data Relational", "Reads a grouping by name", $tags="dbExternal")
  Component(categoryRepositoryAdapter, "Category Repository Adapter", "Spring Data Relational", "Reads a category by name under a grouping", $tags="dbExternal")
  Component(proposalRepositoryAdapter, "Expense Proposal Repository Adapter", "Spring Data Relational", "Persists expense proposals", $tags="dbExternal")
}

Rel(agent, accessControl, "Tool call", "MCP over HTTP")
Rel_D(accessControl, mcpTool, "Admits the call, with the caller's identity")
Rel_D(mcpTool, createProposalPort, "Invokes")
Rel_L(createProposalService, createProposalPort, "Implements", $tags="implements")
Rel_R(createProposalService, userRepositoryPort, "Resolves the identity through")
Rel_R(createProposalService, groupingRepositoryPort, "Resolves the grouping through")
Rel_R(createProposalService, categoryRepositoryPort, "Resolves the category under it through")
Rel_R(createProposalService, proposalRepositoryPort, "Stores through")
Rel_L(userRepositoryAdapter, userRepositoryPort, "Implements", $tags="implements")
Rel_L(groupingRepositoryAdapter, groupingRepositoryPort, "Implements", $tags="implements")
Rel_L(categoryRepositoryAdapter, categoryRepositoryPort, "Implements", $tags="implements")
Rel_L(proposalRepositoryAdapter, proposalRepositoryPort, "Implements", $tags="implements")
Rel_R(userRepositoryAdapter, db, "SQL", "JDBC")
Rel_R(groupingRepositoryAdapter, db, "SQL", "JDBC")
Rel_R(categoryRepositoryAdapter, db, "SQL", "JDBC")
Rel_R(proposalRepositoryAdapter, db, "SQL", "JDBC")

Lay_D(userRepositoryPort, groupingRepositoryPort)
Lay_D(groupingRepositoryPort, categoryRepositoryPort)
Lay_D(categoryRepositoryPort, proposalRepositoryPort)

SHOW_LEGEND()
@enduml
```

## Flow

```plantuml
@startuml CreateExpenseProposal-Activity
start
:an agent acting for a user sends a new expense proposal;
if (the command is absent, or a field is invalid?) then (yes)
  :invalid expense proposal — nothing is looked up or written;
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
:store the proposal in the database under that category;
if (the write fails?) then (yes)
  :storage failed;
  stop
endif
:log the creation;
:answer the stored proposal;
stop
@enduml
```
