# Learn what the ledger did with a message

- **In:**
  - one change the ledger made to a proposal, an expense or a category
  - the delivery that carried it
- **Out:** one of three answers — the change was learned, it should be offered again, or it was dropped
- **Why:** the service can say, later, which expenses came out of a message and where each ended up filed

*Implemented by `LearnMessageOutcomeUseCase`.*

## Prerequisites

- `MEMORY_ENABLED` is on.
- The ledger publishes its changes.
- The message the change names is one this service kept.

## Collaborators

| Direction | Collaborator                                                                                        | Through                                                | For                                                            |
|-----------|-----------------------------------------------------------------------------------------------------|--------------------------------------------------------|----------------------------------------------------------------|
| in        | [Ledger Service](../../../ledger-service/docs/contracts/out/change-stream.md)                       | [The ledger's changes](../contracts/out/change-stream.md) | hearing what the ledger did, without asking it                 |
| out       | [Recorded expense store](../contracts/out/database.md)                                              | [Database](../contracts/out/database.md)                | keeping what became of each proposal and expense               |
| out       | [Change attempt store](../contracts/out/database.md)                                                | [Database](../contracts/out/database.md)                | counting how often one delivery has been refused               |

## Outcomes

| Outcome                     | When                                                                                                     | Result                                                                          |
|-----------------------------|------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------|
| Proposal learned            | the ledger proposed an expense from the message, or refiled a pending one                                    | one pending row holds the amount, the currency, the category and both names        |
| Discard learned             | a proposal left the ledger alone, unaccompanied by an expense of the same ledger transaction                 | that row is discarded, remembering the transaction it left in                      |
| Acceptance learned          | a proposal left and an expense arrived in one ledger transaction, in either order, with the same description, merchant, amount, currency and category | one accepted row, naming both the proposal and the expense |
| Expense learned on its own   | an expense arrived and no discarded row of that transaction has the same description, merchant, amount, currency and category | a new accepted row for the expense, remembering the transaction |
| Expense refiled             | the ledger refiled a recorded expense                                                                        | its row takes the new category and both names                                      |
| Expense removed             | the ledger removed a recorded expense                                                                        | its row is gone; the message stays                                                 |
| Category renamed            | a category sitting under a grouping was renamed                                                              | every row filed under that category takes the new name                             |
| Grouping renamed            | a grouping was renamed                                                                                       | every row of that person under the old grouping name takes the new one             |
| Nothing to learn            | the change names no message, or names one this service does not keep                                         | nothing is written; the change is finished with                                    |
| Nothing to learn            | a category was created, deleted, or moved under another grouping without being renamed                       | nothing is written; the change is finished with                                    |
| Change held                 | the store cannot be reached                                                                                  | nothing is counted against the change; it is offered again                         |
| Change held                 | the store refused the change, fewer than `MEMORY_ENTRY_ATTEMPTS` times                                       | the refusal is counted; the change is offered again                                |
| Change dropped              | the store refused the change `MEMORY_ENTRY_ATTEMPTS` times                                                   | logged at `ERROR` naming the delivery, the kind of row, what happened to it and its id; the count is cleared |
| Acceptance abandoned        | the dropped change was an expense arriving                                                                   | every discarded row of that message and that transaction becomes unknown           |
| Acceptance left as it stood | that abandonment is itself refused                                                                           | logged at `WARN`; the change is still dropped                                      |

## Components

```plantuml
@startuml C3-Component-LearnMessageOutcome
!include <C4/C4_Component>

AddElementTag("streamExternal", $bgColor="#c0392b", $fontColor="#ffffff", $borderColor="#7b241c")
AddElementTag("storeExternal", $bgColor="#b9770e", $fontColor="#ffffff", $borderColor="#7d5109")
AddElementTag("portIn", $bgColor="#16a085", $fontColor="#ffffff", $borderColor="#0e6655", $legendText="inbound port (interface)")
AddElementTag("portOut", $bgColor="#7f8c8d", $fontColor="#ffffff", $borderColor="#566573", $legendText="outbound port (interface)")
AddElementTag("core", $bgColor="#2c3e50", $fontColor="#ffffff", $borderColor="#1b2631", $legendText="application core")

AddRelTag("implements", $lineStyle="dashed")

ContainerQueue(stream, "Ledger Change Stream", "Redis", "Every row change the ledger made", $tags="streamExternal")
ContainerDb(database, "Message Store", "PostgreSQL", "The messages this service was handed, and what became of each", $tags="storeExternal")

Container_Boundary(aiConnector, "AI Connector Service (Java, Spring Boot)") {
  Component(consumer, "Change Stream Consumer", "Stream reader", "Reads the group's entries and acknowledges what is settled", $tags="streamExternal")
  Component(entryReader, "Change Entry Reader", "Entry mapper", "Turns one entry into a change, or into nothing to learn", $tags="streamExternal")
  Component(learnPort, "Learn Message Outcome Port", "Interface", "Inbound port", $tags="portIn")
  Component(useCase, "Learn Message Outcome Use Case", "Plain Java", "Applies one change and bounds how often it is retried", $tags="core")
  Component(change, "Recorded Change", "Domain value object", "A spending row or a category row, before and after", $tags="core")

  Component(recordedPort, "Recorded Expense Store Port", "Interface", "Outbound port", $tags="portOut")
  Component(recordedAdapter, "Recorded Expense Store Adapter", "Spring Data JDBC", "Settles the change under the message's row lock", $tags="storeExternal")
  Component(attemptPort, "Change Attempt Store Port", "Interface", "Outbound port", $tags="portOut")
  Component(attemptAdapter, "Change Attempt Store Adapter", "Spring Data JDBC", "Counts and clears the refusals one delivery has drawn", $tags="storeExternal")
}

Rel_R(stream, consumer, "Entries of group ai-connector", "RESP")
Rel_R(consumer, entryReader, "Reads each entry through")
Rel_D(entryReader, change, "Produces")
Rel_D(consumer, learnPort, "Invokes")
Rel_R(useCase, learnPort, "Implements", $tags="implements")
Rel_D(useCase, recordedPort, "Applies the change through")
Rel_D(useCase, attemptPort, "Counts refusals through")
Rel_U(recordedAdapter, recordedPort, "Implements", $tags="implements")
Rel_U(attemptAdapter, attemptPort, "Implements", $tags="implements")
Rel_R(recordedAdapter, database, "What became of each proposal and expense", "SQL")
Rel_R(attemptAdapter, database, "One row per refused delivery", "SQL")

Lay_D(learnPort, recordedAdapter)
Lay_D(change, attemptPort)

SHOW_LEGEND()
@enduml
```

## Flow

```plantuml
@startuml LearnMessageOutcome-Activity
start
:one change arrives from the ledger's change stream;

if (is there anything to learn from it?) then (no)
  :the change attempt store forgets the delivery;
  stop
endif

:the recorded expense store applies the change;

if (the store applied it?) then (yes)
  :the change attempt store forgets the delivery;
  :the change is learned;
  stop
elseif (the store is unreachable?) then (yes)
  :nothing is counted;
  :the change is offered again;
  stop
else (the store refused it)
  :the change attempt store counts the refusal;
endif

if (has it now been refused MEMORY_ENTRY_ATTEMPTS times?) then (no)
  :the change is offered again;
  stop
endif

:the drop is logged;
if (was it an expense arriving?) then (yes)
  :the recorded expense store marks that
transaction's provisional discards unknown;
endif
:the change attempt store forgets the delivery;
:the change is dropped;
stop
@enduml
```

## References

- [Record the spending a user's message names](extract-intents.md) — what puts the message these changes hang off
  into the store
- [Delete the messages kept past their age](purge-messages.md) — what removes a message, and everything learned
  about it
