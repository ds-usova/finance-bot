# Delete the messages kept past their age

- **In:** nothing
- **Out:** nothing
- **Why:** what a person wrote does not stay on this service longer than the retention it was promised

*Implemented by `PurgeMessagesUseCase`.*

## Prerequisites

- `MEMORY_ENABLED` is on.

## Collaborators

| Direction | Collaborator                                                   | Through                                | For                                            |
|-----------|-----------------------------------------------------------------|----------------------------------------|------------------------------------------------|
| in        | [The service's own timer](../configuration.md)                 | [Configuration](../configuration.md)   | asking for a purge every `MEMORY_PURGE_INTERVAL` |
| out       | [Message store](../contracts/out/database.md)                  | [Database](../contracts/out/database.md) | deleting the messages received before the cut  |

## Outcomes

| Outcome                | When                                                          | Result                                                                  |
|------------------------|----------------------------------------------------------------|-------------------------------------------------------------------------|
| Messages removed       | a message was received before now minus `MEMORY_MAX_AGE`      | it is deleted with everything learned about it, `MEMORY_PURGE_BATCH` at a time, until a batch removes none |
| Nothing to remove      | every kept message is younger than `MEMORY_MAX_AGE`           | the run ends after one look                                              |
| Purge deferred         | the store refuses a batch                                     | the run ends where it stood; the next tick starts again                  |

## Components

```plantuml
@startuml C3-Component-PurgeMessages
!include <C4/C4_Component>

AddElementTag("storeExternal", $bgColor="#b9770e", $fontColor="#ffffff", $borderColor="#7d5109")
AddElementTag("portIn", $bgColor="#16a085", $fontColor="#ffffff", $borderColor="#0e6655", $legendText="inbound port (interface)")
AddElementTag("portOut", $bgColor="#7f8c8d", $fontColor="#ffffff", $borderColor="#566573", $legendText="outbound port (interface)")
AddElementTag("core", $bgColor="#2c3e50", $fontColor="#ffffff", $borderColor="#1b2631", $legendText="application core")

AddRelTag("implements", $lineStyle="dashed")

ContainerDb(database, "Message Store", "PostgreSQL", "The messages this service was handed", $tags="storeExternal")

Container_Boundary(aiConnector, "AI Connector Service (Java, Spring Boot)") {
  Component(scheduler, "Memory Purge Timer", "Scheduled task", "Asks for a purge every MEMORY_PURGE_INTERVAL", $tags="core")
  Component(purgePort, "Purge Messages Port", "Interface", "Inbound port", $tags="portIn")
  Component(useCase, "Purge Messages Use Case", "Plain Java", "Deletes batch by batch until none is left", $tags="core")
  Component(storePort, "Message Store Port", "Interface", "Outbound port", $tags="portOut")
  Component(storeAdapter, "Message Store Adapter", "Spring Data JDBC", "Deletes one batch, claiming only rows no other run holds", $tags="storeExternal")
}

Rel_D(scheduler, purgePort, "Invokes")
Rel_L(useCase, purgePort, "Implements", $tags="implements")
Rel_D(useCase, storePort, "Deletes through")
Rel_U(storeAdapter, storePort, "Implements", $tags="implements")
Rel_R(storeAdapter, database, "One batch received before the cut", "SQL")

Lay_D(scheduler, purgePort)
Lay_D(storePort, storeAdapter)

SHOW_LEGEND()
@enduml
```

## Flow

```plantuml
@startuml PurgeMessages-Activity
start
:the timer fires;
:take the cut — now minus MEMORY_MAX_AGE;
repeat
  :ask the message store for one batch\nof messages received before the cut;
  if (the store refused) then (yes)
    :leave the rest to the next tick;
    stop
  else (no)
  endif
repeat while (the batch removed anything) is (yes)
->no;
stop
@enduml
```

## References

- [ADR 0017: The connector verifies its caller token and keeps the message it names](../../../docs/adr/0017-the-connector-verifies-its-caller-token-and-keeps-the-message-it-names.md) —
  why this service keeps a message at all, and why it is bounded
- [Record the spending a user's message names](extract-intents.md) — what puts a message in the store
- [Learn what the ledger did with a message](learn-message-outcome.md) — what else hangs off a message, and goes
  with it
