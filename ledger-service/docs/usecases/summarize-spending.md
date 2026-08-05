# Summarize spending over a period

- **In:** the identity of the authenticated caller · the reference of the message being handled · the first day
  of the period, as written · the last day, as written
- **Out:** the period that was accepted, and no amount
- **Why:** it is how a question about what someone spent becomes a period the turn answering their message can
  total

*Implemented by `SummarizeSpendingUseCase`.*

## Collaborators

| Direction | Collaborator                                                                                                 | Through                                                                           | For                                                                    |
|-----------|--------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------|--------------------------------------------------------------------------|
| in        | [Record the spending a user's message names](../../../ai-connector-service/docs/usecases/extract-intents.md) | [MCP — the summarize spending tool](../contracts/in/mcp.md)                       | asking about the period it read out of its caller's message            |
| out       | [Database](../contracts/out/database.md)                                                                     | [Users, categories, expenses and expense proposals](../contracts/out/database.md) | resolving the identity, and recording the period against the message   |

## Rules

- The identity is the one the service has already authenticated
  ([authenticated user id](../domain/authenticated-user-id.md)); the request never names whose spending it is
  ([ADR 0007](../adr/0007-an-mcp-caller-is-identified-by-a-signed-token-not-a-tool-argument.md)).
- The [message reference](../domain/message-reference.md) is read from the same credential as the identity, and
  the request never names it either
  ([ADR 0010](../adr/0010-a-message-reference-rides-the-caller-token-not-the-extraction-request.md)).
- The two written days are read into a [spending period](../domain/spending-period.md) before anything is looked
  up, so a period that does not hold together reaches no store.
- The identity is resolved only once a period holds, and the period is recorded only once the identity does.
- A recorded [spending query](../domain/spending-query.md) is tied to the message being handled, so the turn that
  minted the reference is the one that reports it.
- Nothing is read out of the ledger here, and no total is computed: the amounts are put in front of the user by
  [the turn](handle-incoming-message.md), and reach the caller nowhere.
- The answer is the period that was accepted, as two days.
- A repeat of the same request records a second query; the turn reports each distinct period once.

## Outcomes

| Outcome          | When                                                                             | Result                                                             |
|------------------|------------------------------------------------------------------------------------|----------------------------------------------------------------------|
| Period recorded  | the identity names a stored user and the two days make a period                  | the period is stored under the message reference, and answered back |
| Request rejected | the request is absent, or names no identity or no message                        | invalid spending query — nothing is looked up or written           |
| Period rejected  | a day is missing, blank or unreadable, or the last day is before the first       | invalid spending period — nothing is looked up or written          |
| Identity unknown | nothing is stored under the identity                                             | the request is rejected and nothing is written                     |
| Storage failed   | the store cannot be reached                                                      | the failure reaches the caller                                     |

## Components

```plantuml
@startuml C3-Component-SummarizeSpending
!include <C4/C4_Component>

AddElementTag("dbExternal", $bgColor="#d68910", $fontColor="#ffffff", $borderColor="#8f5c0a")
AddElementTag("mcpExternal", $bgColor="#c0392b", $fontColor="#ffffff", $borderColor="#7b241c")
AddElementTag("portIn", $bgColor="#16a085", $fontColor="#ffffff", $borderColor="#0e6655", $legendText="inbound port (interface)")
AddElementTag("portOut", $bgColor="#7f8c8d", $fontColor="#ffffff", $borderColor="#566573", $legendText="outbound port (interface)")
AddElementTag("core", $bgColor="#2c3e50", $fontColor="#ffffff", $borderColor="#1b2631", $legendText="application core")
AddRelTag("implements", $lineStyle="dashed")

System_Ext(agent, "Agent acting for a user", "An MCP client", $tags="mcpExternal")
ContainerDb(db, "Database", "PostgreSQL", "Stores users, categories, expenses and the periods asked about", $tags="dbExternal")

Container_Boundary(ledger, "Ledger Service (Java, Spring Boot)") {
  Component(accessControl, "Access Control", "Spring Security", "Admits only calls carrying a valid token", $tags="mcpExternal")
  Component(mcpTool, "Summarize Spending Tool", "Spring AI MCP Server", "Takes the two days, and the caller and the message off the token", $tags="mcpExternal")
  Component(summarizePort, "Summarize Spending Port", "Interface", "Inbound port", $tags="portIn")
  Component(summarizeService, "Summarize Spending Use Case", "Plain Java", "Reads the period, resolves the user, and records the question", $tags="core")
  Component(userRepositoryPort, "User Repository Port", "Interface", "Outbound port", $tags="portOut")
  Component(queryRepositoryPort, "Spending Query Repository Port", "Interface", "Outbound port", $tags="portOut")
  Component(userRepositoryAdapter, "User Repository Adapter", "Spring Data Relational", "Checks if user exists", $tags="dbExternal")
  Component(queryRepositoryAdapter, "Spending Query Repository Adapter", "Spring Data Relational", "Records a period asked about", $tags="dbExternal")
}

Rel(agent, accessControl, "Tool call", "MCP over HTTP")
Rel_D(accessControl, mcpTool, "Admits the call, with the caller's identity")
Rel_D(mcpTool, summarizePort, "Invokes")
Rel_L(summarizeService, summarizePort, "Implements", $tags="implements")
Rel_R(summarizeService, userRepositoryPort, "Resolves the identity through")
Rel_R(summarizeService, queryRepositoryPort, "Records the period through")
Rel_L(userRepositoryAdapter, userRepositoryPort, "Implements", $tags="implements")
Rel_L(queryRepositoryAdapter, queryRepositoryPort, "Implements", $tags="implements")
Rel_R(userRepositoryAdapter, db, "SQL", "JDBC")
Rel_R(queryRepositoryAdapter, db, "SQL", "JDBC")

Lay_D(userRepositoryPort, queryRepositoryPort)
Lay_D(userRepositoryAdapter, queryRepositoryAdapter)

SHOW_LEGEND()
@enduml
```

## Flow

```plantuml
@startuml SummarizeSpending-Activity
start
:an agent acting for a user asks about a period;
if (the request is absent, or names no identity or no message?) then (yes)
  :invalid spending query — nothing is looked up or written;
  stop
endif
:read the two written days into a period;
if (a day is missing, blank or unreadable?) then (yes)
  :invalid spending period, naming the day at fault;
  stop
endif
if (the last day is before the first?) then (yes)
  :invalid spending period — the period ends before it starts;
  stop
endif
:look the identity up in the database;
if (the read fails?) then (yes)
  :storage failed;
  stop
endif
if (the identity names a stored user?) then (no)
  :identity unknown, nothing is written;
  stop
endif
:record the period in the database, under the message reference;
if (the write fails?) then (yes)
  :storage failed;
  stop
endif
:answer the period accepted, and no amount;
stop
@enduml
```
