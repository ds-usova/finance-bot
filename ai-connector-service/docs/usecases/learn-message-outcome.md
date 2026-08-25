# Learn what the ledger did with a message

- **In:**
  - one fact the ledger published about a proposal or an expense
  - the position that fact stands at on the stream
  - the delivery that carried it
- **Out:** one of three answers — the fact was applied, it should be offered again, or it was dropped
- **Why:** the service can say, later, which expenses came out of a message and where each ended up filed

*Implemented by `LearnMessageOutcomeUseCase`.*

## Prerequisites

- `MEMORY_ENABLED` is on.
- The ledger publishes its facts.

## Collaborators

| Direction | Collaborator                                                                  | Through                                                 | For                                              |
|-----------|-------------------------------------------------------------------------------|---------------------------------------------------------|--------------------------------------------------|
| in        | [Ledger Service](../../../ledger-service/docs/contracts/out/change-stream.md) | [The ledger's facts](../contracts/out/change-stream.md) | hearing what the ledger did                      |
| out       | [Recorded expense store](../contracts/out/database.md)                        | [Database](../contracts/out/database.md)                | keeping what became of each proposal and expense |
| out       | [Delivery attempt store](../contracts/out/database.md)                        | [Database](../contracts/out/database.md)                | counting how often one delivery has been refused |

## Outcomes

One row per fact of
[the ledger's catalogue](../../../ledger-service/docs/contracts/out/change-stream.md#the-catalogue).

| Outcome            | When                                                                 | Result                                                                                                                                                                                                                    |
|--------------------|----------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Proposal learned   | `ProposalCreated`                                                    | one row holds the whole entry, pending                                                                                                                                                                                    |
| Proposal refiled   | `ProposalRefiled`                                                    | that row takes the new category and grouping, and stays pending                                                                                                                                                           |
| Proposal accepted  | `ProposalAccepted`                                                   | that row is accepted                                                                                                                                                                                                      |
| Proposal discarded | `ProposalDiscarded`                                                  | that row is discarded                                                                                                                                                                                                     |
| Expense learned    | `ExpenseRecorded`, naming a message kept here                        | one accepted row holds the whole entry                                                                                                                                                                                    |
| Expense refiled    | `ExpenseRefiled`                                                     | one accepted row holds the new category and grouping                                                                                                                                                                      |
| Older fact ignored | the fact stands at a position the row was already written past       | nothing is written; the row keeps the newer fact                                                                                                                                                                          |
| Nothing to learn   | the fact names no message, or names one this service does not keep   | nothing is written; the delivery is finished with                                                                                                                                                                         |
| Delivery held      | the store cannot be reached                                          | nothing is counted against the delivery; it is offered again                                                                                                                                                              |
| Delivery held      | the store refused the fact, fewer than `MEMORY_ENTRY_ATTEMPTS` times | the refusal is counted; the delivery is offered again                                                                                                                                                                     |
| Delivery dropped   | the store refused the fact `MEMORY_ENTRY_ATTEMPTS` times             | counted as a [dropped delivery](../contracts/in/operations.md#meters); the fact the delivery carried is never learned; logged at `ERROR` naming the delivery, the status it carried and the expense; the count is cleared |

A fact for an expense no row holds yet is inserted whole, whatever it says happened. A fact's status replaces
the row's, whatever the row held before.

## Components

```plantuml
@startuml C3-Component-LearnMessageOutcome
!include <C4/C4_Component>

AddElementTag("callerExternal", $bgColor="#1c94e0", $fontColor="#ffffff", $borderColor="#125d8c")
AddElementTag("storeExternal", $bgColor="#b9770e", $fontColor="#ffffff", $borderColor="#7d5109")
AddElementTag("portIn", $bgColor="#16a085", $fontColor="#ffffff", $borderColor="#0e6655", $legendText="inbound port (interface)")
AddElementTag("portOut", $bgColor="#7f8c8d", $fontColor="#ffffff", $borderColor="#566573", $legendText="outbound port (interface)")
AddElementTag("core", $bgColor="#2c3e50", $fontColor="#ffffff", $borderColor="#1b2631", $legendText="application core")

AddRelTag("implements", $lineStyle="dashed")

Container(ledger, "Ledger Service", "Java, Spring Boot", "Publishes every fact about a piece of spending", $tags="callerExternal")
ContainerQueue(changeStream, "Change stream", "Redis", "The facts the ledger publishes, read as a consumer group", $tags="callerExternal")
ContainerDb(database, "Message Store", "PostgreSQL", "The messages this service was handed, and what became of each", $tags="storeExternal")

Container_Boundary(aiConnector, "AI Connector Service (Java, Spring Boot)") {
  Component(consumer, "Change Stream Consumer", "Redis consumer group", "Claims stalled entries, reads its own and then new ones, acknowledges what is finished with", $tags="callerExternal")
  Component(handler, "Change Stream Entry Handler", "Plain Java", "Reads one entry, offers it, and acknowledges what is finished with", $tags="callerExternal")
  Component(entryReader, "Change Stream Entry Reader", "Plain Java", "Reads an entry's body as one spending fact and its position", $tags="callerExternal")
  Component(learnPort, "Learn Message Outcome Port", "Interface", "Inbound port", $tags="portIn")
  Component(useCase, "Learn Message Outcome Use Case", "Plain Java", "Applies one fact, and bounds a delivery the store keeps refusing", $tags="core")

  Component(recordedStorePort, "Recorded Expense Store Port", "Interface", "Outbound port", $tags="portOut")
  Component(recordedStoreAdapter, "Recorded Expense Store Adapter", "Spring Data JDBC", "Applies one fact to the expense's row, never over a newer one", $tags="storeExternal")
  Component(attemptsPort, "Change Attempt Store Port", "Interface", "Outbound port", $tags="portOut")
  Component(attemptsAdapter, "Change Attempt Store Adapter", "Spring Data JDBC", "Counts a delivery's refusals, and forgets it once it is finished with", $tags="storeExternal")
}

Rel_D(ledger, changeStream, "Publishes each fact", "RESP")
Rel_D(changeStream, consumer, "Reads, claims and acknowledges", "RESP")
Rel_R(consumer, handler, "Hands each entry to")
Rel_R(handler, entryReader, "Reads an entry through")
Rel_D(handler, learnPort, "Invokes")
Rel_L(useCase, learnPort, "Implements", $tags="implements")
Rel_D(useCase, recordedStorePort, "Applies the fact through")
Rel_R(useCase, attemptsPort, "Counts and clears a delivery through")
Rel_U(recordedStoreAdapter, recordedStorePort, "Implements", $tags="implements")
Rel_U(attemptsAdapter, attemptsPort, "Implements", $tags="implements")
Rel_D(recordedStoreAdapter, database, "The expense's row", "SQL")
Rel_D(attemptsAdapter, database, "The delivery's refusals", "SQL")

Lay_D(handler, learnPort)
Lay_D(learnPort, useCase)
Lay_D(recordedStorePort, recordedStoreAdapter)
Lay_D(attemptsPort, attemptsAdapter)

SHOW_LEGEND()
@enduml
```

## Flow

```plantuml
@startuml LearnMessageOutcome-Activity
start
:one fact arrives from the ledger's change stream;

if (does it name a message?) then (no)
  :the delivery attempt store forgets the delivery;
  stop
endif

:the recorded expense store applies the fact,
where the row is not already past its position;

if (the store applied it?) then (yes)
  :the delivery attempt store forgets the delivery;
  :the fact is learned;
  stop
elseif (the store is unreachable?) then (yes)
  :nothing is counted;
  :the delivery is offered again;
  stop
else (the store refused it)
  :the delivery attempt store counts the refusal;
endif

if (has it now been refused MEMORY_ENTRY_ATTEMPTS times?) then (no)
  :the delivery is offered again;
  stop
endif

:the drop is counted;
:the drop is logged;
:the delivery attempt store forgets the delivery;
:the delivery is dropped;
stop
@enduml
```

## References

- [Record the spending a user's message names](extract-intents.md) — what puts the message these facts hang off
  into the store
- [Recall the person's own worked examples](recall-examples.md) — what a decided expense becomes on a later turn
- [Embed the messages nothing has embedded yet](backfill-embeddings.md) — what a message still needs before it
  can be recalled
- [Delete the messages kept past their age](purge-messages.md) — what removes a message, and everything learned
  about it
