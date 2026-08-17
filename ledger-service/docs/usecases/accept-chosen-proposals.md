# Accept the proposals a person chose

- **In**
  - the identity of the authenticated caller
  - the [ids of the pending entries](../domain/proposal-ids.md) to accept
- **Out**
  - how many pending entries became recorded
  - how many of the ids named nothing
- **Why:** a person turns spending into their ledger from the page they read it on, one entry or a day at a time

*Implemented by `AcceptExpensesUseCase`.*

## Prerequisites

- The caller is authenticated. The request never names whose ledger it is.
- A user is stored under that identity.

What bounds the list, what an id is taken as, and how the two counts relate are all fixed by
[the specification](../../../openapi/ledger-api.yaml).

## Collaborators

| Direction | Collaborator                                                                         | Through                                                                           | For                                                                                 |
|-----------|--------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------|-------------------------------------------------------------------------------------|
| in        | [Accept pending expenses](../../../web-app/docs/usecases/accept-pending-expenses.md) | [Browsing the ledger from a browser](../contracts/in/web-browse-api.md)           | turning the entries a person ticked into their ledger                               |
| out       | [Database](../contracts/out/database.md)                                             | [Users, categories and expenses](../contracts/out/database.md)                    | resolving the identity, and recording the pending entries the ids name              |
| out       | [Clear the emptied reports](clear-emptied-reports.md)                                | [Clear the emptied reports](clear-emptied-reports.md)                             | handing over the messages this acceptance may have emptied                          |

## Outcomes

| Outcome           | When                                                               | Result                                                                                       |
|-------------------|--------------------------------------------------------------------|----------------------------------------------------------------------------------------------|
| Spending accepted | the ids name pending entries of the caller's                       | those become recorded, both counts are answered, and the emptied messages go to the clearing |
| Nothing matched   | no id names a pending entry of theirs                              | both counts are answered, and no clearing is handed over                                     |
| List rejected     | the list breaks a bound [proposal ids](../domain/proposal-ids.md) sets | invalid expense acceptance, naming the bound — nothing is recorded                       |
| Body rejected     | the body is not JSON                                               | the request is refused, naming the field — nothing is recorded                             |
| Request rejected  | the request names no ids                                           | the request is refused — nothing is looked up                                              |
| Identity unknown  | nothing is stored under the caller's identity                      | the request is rejected, and nothing is recorded or handed over                              |
| Storage failed    | the write fails                                                    | the failure reaches the caller, and nothing is recorded or handed over                       |

## Components

```plantuml
@startuml C3-Component-AcceptChosenProposals
!include <C4/C4_Component>

AddElementTag("dbExternal", $bgColor="#d68910", $fontColor="#ffffff", $borderColor="#8f5c0a")
AddElementTag("webExternal", $bgColor="#2874a6", $fontColor="#ffffff", $borderColor="#1b4f72")
AddElementTag("portIn", $bgColor="#16a085", $fontColor="#ffffff", $borderColor="#0e6655", $legendText="inbound port (interface)")
AddElementTag("portOut", $bgColor="#7f8c8d", $fontColor="#ffffff", $borderColor="#566573", $legendText="outbound port (interface)")
AddElementTag("core", $bgColor="#2c3e50", $fontColor="#ffffff", $borderColor="#1b2631", $legendText="application core")
AddRelTag("implements", $lineStyle="dashed")

System_Ext(browser, "A signed-in person's browser", "The web app's page", $tags="webExternal")
ContainerDb(db, "Database", "PostgreSQL", "Stores users and their expenses, pending and recorded", $tags="dbExternal")

Container_Boundary(ledger, "Ledger Service (Java, Spring Boot)") {
  Component(accessControl, "Access Control", "Spring Security", "Admits only a call carrying a valid session cookie and a matching token", $tags="webExternal")
  Component(endpoint, "Expenses Endpoint", "Spring MVC", "Reads the ids and the caller, and renders the two counts", $tags="webExternal")
  Component(acceptPort, "Accept Expenses Port", "Interface", "Inbound port", $tags="portIn")
  Component(acceptService, "Accept the Chosen Proposals Use Case", "Plain Java", "Resolves the person, records the chosen entries, then hands the emptied messages on", $tags="core")
  Component(userRepositoryPort, "User Repository Port", "Interface", "Outbound port", $tags="portOut")
  Component(expenseRepositoryPort, "Expense Repository Port", "Interface", "Outbound port", $tags="portOut")
  Component(dispatchPort, "Report Clearing Dispatch Port", "Interface", "Outbound port", $tags="portOut")
  Component(userRepositoryAdapter, "User Repository Adapter", "Spring Data Relational", "Reads the person stored under an identity", $tags="dbExternal")
  Component(expenseRepositoryAdapter, "Expense Repository Adapter", "Spring Data Relational", "Records the chosen pending entries, and says which messages they were reported on", $tags="dbExternal")
  Component(dispatcher, "Report Clearing Dispatcher", "Bounded thread pool", "Takes the clearing off the request thread", $tags="core")
}

Rel(browser, accessControl, "Posts the ids it ticked", "HTTP, session cookie, CSRF token")
Rel_D(accessControl, endpoint, "Admits the call, with the caller's identity")
Rel_D(endpoint, acceptPort, "Invokes")
Rel_L(acceptService, acceptPort, "Implements", $tags="implements")
Rel_R(acceptService, userRepositoryPort, "Resolves the identity through")
Rel_R(acceptService, expenseRepositoryPort, "Records the entries through")
Rel_R(acceptService, dispatchPort, "Hands the emptied messages to")
Rel_L(userRepositoryAdapter, userRepositoryPort, "Implements", $tags="implements")
Rel_L(expenseRepositoryAdapter, expenseRepositoryPort, "Implements", $tags="implements")
Rel_L(dispatcher, dispatchPort, "Implements", $tags="implements")
Rel_R(userRepositoryAdapter, db, "SQL", "JDBC")
Rel_R(expenseRepositoryAdapter, db, "SQL", "JDBC")

Lay_D(userRepositoryPort, expenseRepositoryPort)
Lay_D(expenseRepositoryPort, dispatchPort)
Lay_D(userRepositoryAdapter, expenseRepositoryAdapter)
Lay_D(expenseRepositoryAdapter, dispatcher)

SHOW_LEGEND()
@enduml
```

## Flow

```plantuml
@startuml AcceptChosenProposals-Sequence
participant "A signed-in person's browser" as Web
participant "Accept the proposals a person chose" as UC
database "Database" as DB
participant "Clear the emptied reports" as Clearing

Web -> UC : the ids that were ticked, and the caller's identity

alt the list breaks a bound
  UC --> Web : invalid expense acceptance, naming the bound
else the list is usable
  UC -> DB : find the person behind the identity
  alt nothing is stored under that identity
    DB --> UC : nothing
    UC --> Web : the caller is unknown
  else the person is known
    DB --> UC : the person
    UC -> DB : record their pending entries carrying those ids
    alt the write fails
      DB --> UC : storage failed, nothing recorded
      UC --> Web : the failure
    else the write ran
      DB --> UC : the message each recorded entry was reported on
      opt anything was recorded
        UC -> Clearing : those messages, off this thread
      end
      UC --> Web : how many were recorded, and how many named nothing
    end
  end
end
@enduml
```

## References

- [ADR 0018: A proposal is a status on the expense table](../adr/0018-a-proposal-is-a-status-on-the-expense-table.md) —
  why an acceptance and a Telegram tap over the same entries record them once, and why an id survives
