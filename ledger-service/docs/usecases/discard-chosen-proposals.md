# Discard the proposals a person chose

- **In**
  - the identity of the authenticated caller
  - the [ids of the pending entries](../domain/proposal-ids.md) to discard
- **Out**
  - how many pending entries were thrown away
  - how many of the ids named nothing
- **Why:** a person throws away chosen pending spending by id

*Implemented by `DiscardExpensesUseCase`.*

## Prerequisites

- The caller is authenticated.
- A user is stored under that identity.

## Collaborators

| Direction | Collaborator                                                            | Through                                                                 | For                                                                   |
|-----------|-------------------------------------------------------------------------|-------------------------------------------------------------------------|-----------------------------------------------------------------------|
| in        | [A signed-in caller](../contracts/in/web-browse-api.md)                 | [Browsing the ledger from a browser](../contracts/in/web-browse-api.md) | throwing away the pending entries it names                            |
| out       | [Database](../contracts/out/database.md)                                | [Users, categories and expenses](../contracts/out/database.md)          | resolving the identity, and removing the pending entries the ids name |
| out       | [Clear the emptied reports](clear-emptied-reports.md)                   | [Clear the emptied reports](clear-emptied-reports.md)                   | handing over the messages this discard may have emptied               |
| out       | [A consumer of the ledger's changes](../contracts/out/change-stream.md) | [The change stream](../contracts/out/change-stream.md)                  | publishing the fact each discard produces                             |

## Outcomes

| Outcome            | When                                                                   | Result                                                                                     |
|--------------------|------------------------------------------------------------------------|--------------------------------------------------------------------------------------------|
| Spending discarded | the ids name pending entries of the caller's                           | those are removed, both counts are answered, and their messages go to the clearing         |
| Nothing matched    | no id names a pending entry of theirs                                  | both counts are answered, and no clearing is handed over                                   |
| List rejected      | the list breaks a bound [proposal ids](../domain/proposal-ids.md) sets | the request is refused, naming the bound — nothing is discarded                            |
| Body rejected      | the body is not JSON                                                   | the request is refused — nothing is discarded                                              |
| Request rejected   | the request is absent                                                  | rejected as invalid — nothing is looked up                                                 |
| Identity unknown   | nothing is stored under the caller's identity                          | the request is rejected, and nothing is removed or handed over                             |
| Storage failed     | the write fails                                                        | the failure reaches the caller, and nothing is removed or handed over                      |

## Components

```plantuml
@startuml C3-Component-DiscardChosenProposals
!include <C4/C4_Component>

AddElementTag("dbExternal", $bgColor="#d68910", $fontColor="#ffffff", $borderColor="#8f5c0a")
AddElementTag("webExternal", $bgColor="#2874a6", $fontColor="#ffffff", $borderColor="#1b4f72")
AddElementTag("portIn", $bgColor="#16a085", $fontColor="#ffffff", $borderColor="#0e6655", $legendText="inbound port (interface)")
AddElementTag("portOut", $bgColor="#7f8c8d", $fontColor="#ffffff", $borderColor="#566573", $legendText="outbound port (interface)")
AddElementTag("core", $bgColor="#2c3e50", $fontColor="#ffffff", $borderColor="#1b2631", $legendText="application core")
AddRelTag("implements", $lineStyle="dashed")

System_Ext(caller, "A signed-in caller", "Holds a browser session", $tags="webExternal")
ContainerDb(db, "Database", "PostgreSQL", "Stores users and their expenses, pending and recorded", $tags="dbExternal")

Container_Boundary(ledger, "Ledger Service (Java, Spring Boot)") {
  Component(accessControl, "Access Control", "Spring Security", "Admits only a call carrying a valid session cookie and a matching token", $tags="webExternal")
  Component(endpoint, "Expenses Endpoint", "Spring MVC", "Reads the ids and the caller, and renders the two counts", $tags="webExternal")
  Component(discardPort, "Discard Expenses Port", "Interface", "Inbound port", $tags="portIn")
  Component(discardService, "Discard the Chosen Proposals Use Case", "Plain Java", "Resolves the person, removes the chosen entries, then hands their messages on", $tags="core")
  Component(userRepositoryPort, "User Repository Port", "Interface", "Outbound port", $tags="portOut")
  Component(expenseRepositoryPort, "Expense Repository Port", "Interface", "Outbound port", $tags="portOut")
  Component(dispatchPort, "Report Clearing Dispatch Port", "Interface", "Outbound port", $tags="portOut")
  Component(userRepositoryAdapter, "User Repository Adapter", "Spring Data Relational", "Reads the person stored under an identity", $tags="dbExternal")
  Component(expenseRepositoryAdapter, "Expense Repository Adapter", "Spring Data Relational", "Removes the chosen pending entries, and says which messages they were reported on", $tags="dbExternal")
  Component(dispatcher, "Report Clearing Dispatcher", "Bounded thread pool", "Takes the clearing off the request thread", $tags="core")
}

Rel(caller, accessControl, "Posts the ids to discard", "HTTP, session cookie, CSRF token")
Rel_D(accessControl, endpoint, "Admits the call, with the caller's identity")
Rel_D(endpoint, discardPort, "Invokes")
Rel_L(discardService, discardPort, "Implements", $tags="implements")
Rel_R(discardService, userRepositoryPort, "Resolves the identity through")
Rel_R(discardService, expenseRepositoryPort, "Removes the entries through")
Rel_R(discardService, dispatchPort, "Hands the touched messages to")
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
@startuml DiscardChosenProposals-Sequence
participant "A signed-in caller" as Caller
participant "Discard the proposals a person chose" as UC
database "Database" as DB
queue "The change stream" as Stream
participant "Clear the emptied reports" as Clearing

Caller -> UC : the ids to discard, and the caller's identity

alt the list breaks a bound
  UC --> Caller : the request is refused, naming the bound
else the list is usable
  UC -> DB : find the person behind the identity
  alt nothing is stored under that identity
    DB --> UC : nothing
    UC --> Caller : the caller is unknown
  else the person is known
    DB --> UC : the person
    UC -> DB : remove their pending entries carrying those ids
    alt the write fails
      DB --> UC : storage failed, nothing removed
      UC --> Caller : the failure
    else the write ran
      DB --> UC : the message each removed entry was reported on
      opt anything was removed
        DB -> Stream : the facts, once committed
        UC -> Clearing : those messages once each, off this thread
      end
      UC --> Caller : how many were removed, and how many named nothing
    end
  end
end
@enduml
```

## References

- [Resolve a reported proposal](resolve-a-reported-proposal.md) — what a report's buttons answer once its
  entries are gone
