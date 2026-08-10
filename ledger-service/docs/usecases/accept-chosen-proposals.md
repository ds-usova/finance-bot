# Accept the proposals a person chose

- **In**
  - the identity of the authenticated caller
  - the [ids of the pending entries](../domain/proposal-ids.md) to accept
- **Out**
  - how many proposals became expenses
  - how many of the ids named nothing
- **Why:** it lets a person turn spending into their ledger from the page they are already reading it on, one
  entry or a whole day at a time

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
| out       | [Database](../contracts/out/database.md)                                             | [Users, categories, expenses and expense proposals](../contracts/out/database.md) | resolving the identity, and moving the chosen proposals into that person's expenses |
| out       | [Clear the emptied reports](clear-emptied-reports.md)                                | [Clear the emptied reports](clear-emptied-reports.md)                             | handing over the messages this acceptance may have emptied                          |

## Rules

- Only that person's pending proposals are reachable. An id is never widened to somebody else's row.
- The move is one statement, so an acceptance and a Telegram **Confirm** racing over the same rows move them
  once ([ADR 0012](../adr/0012-a-set-of-rows-moves-between-tables-in-one-statement.md)).
- An accepted proposal keeps the day it was proposed on — see [Expense](../domain/expense.md#lifecycle).
- The order the ids were given in is kept, and says nothing about what moves.
- The messages the moved rows were reported on are handed to the clearing, each one once.
- An acceptance that moved nothing hands nothing over.
- The clearing runs after the caller is answered, so nothing it does can change the answer.
- One info line per acceptance names the person, how many moved and how many named nothing.

## Outcomes

| Outcome           | When                                                               | Result                                                                                       |
|-------------------|--------------------------------------------------------------------|----------------------------------------------------------------------------------------------|
| Spending accepted | the ids name pending proposals of the caller's                     | those become expenses, both counts are answered, and the emptied messages go to the clearing |
| Nothing matched   | no id names a pending proposal of theirs                           | both counts are answered, and no clearing is handed over                                     |
| List rejected     | the list is empty, too long, repeats an id, or holds an id below 1 | invalid expense acceptance, naming the bound — nothing is moved                            |
| Body rejected     | the body is not JSON, or breaks a bound the specification declares | the request is refused, naming the field — nothing is moved                                |
| Request rejected  | the request names no ids                                           | the request is refused — nothing is looked up                                              |
| Identity unknown  | nothing is stored under the caller's identity                      | the request is rejected, and nothing is moved or handed over                                 |
| Storage failed    | the move fails                                                     | the failure reaches the caller, and nothing is moved or handed over                          |

What each outcome answers a browser with is [the browse API](../contracts/in/web-browse-api.md)'s, and the
[specification](../../../openapi/ledger-api.yaml) describes every one.

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
ContainerDb(db, "Database", "PostgreSQL", "Stores users, expenses and expense proposals", $tags="dbExternal")

Container_Boundary(ledger, "Ledger Service (Java, Spring Boot)") {
  Component(accessControl, "Access Control", "Spring Security", "Admits only a call carrying a valid session cookie and a matching token", $tags="webExternal")
  Component(endpoint, "Expenses Endpoint", "Spring MVC", "Reads the ids and the caller, and renders the two counts", $tags="webExternal")
  Component(acceptPort, "Accept Expenses Port", "Interface", "Inbound port", $tags="portIn")
  Component(acceptService, "Accept the Chosen Proposals Use Case", "Plain Java", "Resolves the person, moves the chosen proposals, then hands the emptied messages on", $tags="core")
  Component(userRepositoryPort, "User Repository Port", "Interface", "Outbound port", $tags="portOut")
  Component(proposalRepositoryPort, "Expense Proposal Repository Port", "Interface", "Outbound port", $tags="portOut")
  Component(dispatchPort, "Report Clearing Dispatch Port", "Interface", "Outbound port", $tags="portOut")
  Component(userRepositoryAdapter, "User Repository Adapter", "Spring Data Relational", "Reads the person stored under an identity", $tags="dbExternal")
  Component(proposalRepositoryAdapter, "Expense Proposal Repository Adapter", "Spring Data Relational", "Moves the chosen proposals into expenses, and says which messages they were reported on", $tags="dbExternal")
  Component(dispatcher, "Report Clearing Dispatcher", "Bounded thread pool", "Takes the clearing off the request thread", $tags="core")
}

Rel(browser, accessControl, "Posts the ids it ticked", "HTTP, session cookie, CSRF token")
Rel_D(accessControl, endpoint, "Admits the call, with the caller's identity")
Rel_D(endpoint, acceptPort, "Invokes")
Rel_L(acceptService, acceptPort, "Implements", $tags="implements")
Rel_R(acceptService, userRepositoryPort, "Resolves the identity through")
Rel_R(acceptService, proposalRepositoryPort, "Moves the proposals through")
Rel_R(acceptService, dispatchPort, "Hands the emptied messages to")
Rel_L(userRepositoryAdapter, userRepositoryPort, "Implements", $tags="implements")
Rel_L(proposalRepositoryAdapter, proposalRepositoryPort, "Implements", $tags="implements")
Rel_L(dispatcher, dispatchPort, "Implements", $tags="implements")
Rel_R(userRepositoryAdapter, db, "SQL", "JDBC")
Rel_R(proposalRepositoryAdapter, db, "SQL", "JDBC")

Lay_D(userRepositoryPort, proposalRepositoryPort)
Lay_D(proposalRepositoryPort, dispatchPort)
Lay_D(userRepositoryAdapter, proposalRepositoryAdapter)
Lay_D(proposalRepositoryAdapter, dispatcher)

SHOW_LEGEND()
@enduml
```

Where the clearing goes from there is [its own page](clear-emptied-reports.md).

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
    UC -> DB : move their pending proposals with those ids into their expenses
    alt the move fails
      DB --> UC : storage failed, nothing moved
      UC --> Web : the failure
    else the move ran
      DB --> UC : the message each moved proposal was reported on
      opt anything moved
        UC -> Clearing : those messages, off this thread
      end
      UC --> Web : how many moved, and how many named nothing
    end
  end
end
@enduml
```
