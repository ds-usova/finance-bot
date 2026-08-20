# Recall the person's own worked examples

- **In:**
  - the person and the message the turn's token names
  - the text of that message
- **Out:**
  - either nothing was looked for, or the [message examples](../domain/message-example.md) the bounds admit
- **Why:** the model files a person's spending the way that person has already agreed to file it

*Implemented by `RecallExamplesUseCase`.*

## Prerequisites

- `MEMORY_ENABLED` is on.
- The turn's message has been registered under the person and the message the token names.

## Collaborators

| Direction | Collaborator                                                     | Through                                                  | For                                                             |
|-----------|------------------------------------------------------------------|----------------------------------------------------------|-----------------------------------------------------------------|
| in        | [Record the spending a user's message names](extract-intents.md) | Recall Examples Port                                     | putting the person's own filing habits in front of the model    |
| out       | [Message store](../contracts/out/database.md)                    | [Database](../contracts/out/database.md)                 | the registered row, its vector, and the neighbours it admits    |
| out       | [AI Provider](../contracts/out/ai-provider.md)                   | [Embeddings](../contracts/out/ai-provider.md#operations) | turning the message into the vector the neighbours are found by |

## Outcomes

| Outcome              | When                                                                    | Result                                                                                 |
|----------------------|-------------------------------------------------------------------------|----------------------------------------------------------------------------------------|
| Examples found       | earlier messages clear every bound                                      | those messages and their decided expenses                                              |
| Nothing close enough | no earlier message clears `MEMORY_MIN_SIMILARITY` inside the bounds     | a retrieval that found nothing                                                         |
| Message not known    | the store holds no row under that person and message                    | nothing was looked for                                                                 |
| Vector reused        | the registered row already holds a vector                               | no embedding call; that vector is the query                                            |
| Message embedded     | the registered row holds no vector                                      | it is embedded once, the vector is kept on the row, and it is the query                |
| Vector already there | a backfill or an earlier turn wrote one while this turn was embedding   | the row keeps the one it holds; the turn queries with the one it computed              |
| Embedding refused    | the provider refuses, or answers slower than `MEMORY_EMBEDDING_TIMEOUT` | nothing was looked for; one failed attempt is counted on the row; one `WARN`           |
| Message given up on  | that attempt is the `MEMORY_EMBEDDING_ATTEMPTS`th                       | one `ERROR` naming the row and carrying no text; it is never a neighbour               |
| Store refused        | the store cannot be reached, or refuses any read or the vector write    | nothing was looked for; one `WARN` naming the person and the message, carrying no text |

## Components

```plantuml
@startuml C3-Component-RecallExamples
!include <C4/C4_Component>

AddElementTag("aiExternal", $bgColor="#8e44ad", $fontColor="#ffffff", $borderColor="#5f2f74")
AddElementTag("storeExternal", $bgColor="#b9770e", $fontColor="#ffffff", $borderColor="#7d5109")
AddElementTag("portIn", $bgColor="#16a085", $fontColor="#ffffff", $borderColor="#0e6655", $legendText="inbound port (interface)")
AddElementTag("portOut", $bgColor="#7f8c8d", $fontColor="#ffffff", $borderColor="#566573", $legendText="outbound port (interface)")
AddElementTag("core", $bgColor="#2c3e50", $fontColor="#ffffff", $borderColor="#1b2631", $legendText="application core")

AddRelTag("implements", $lineStyle="dashed")

System_Ext(aiProvider, "AI Provider", "OpenAI-compatible embeddings API", $tags="aiExternal")
ContainerDb(database, "Message Store", "PostgreSQL", "The messages this service was handed, and their vectors", $tags="storeExternal")

Container_Boundary(aiConnector, "AI Connector Service (Java, Spring Boot)") {
  Component(turn, "Extract Intents Use Case", "Plain Java", "Asks for the examples once the turn's message is registered", $tags="core")
  Component(recallPort, "Recall Examples Port", "Interface", "Inbound port", $tags="portIn")
  Component(useCase, "Recall Examples Use Case", "Plain Java", "Reads the registered row and answers the neighbours the bounds admit", $tags="core")
  Component(embedder, "Message Embedder", "Plain Java", "Embeds a message once, keeps the vector, counts a failed attempt", $tags="core")

  Component(memoryPort, "Message Memory Port", "Interface", "Outbound port", $tags="portOut")
  Component(memoryAdapter, "Message Memory Adapter", "Spring Data JDBC", "Reads the row, writes the vector, finds the neighbours", $tags="storeExternal")
  Component(embeddingPort, "Message Embedding Port", "Interface", "Outbound port", $tags="portOut")
  Component(embeddingAdapter, "Message Embedding Adapter", "Spring AI EmbeddingModel", "Embeds a text, bounded by its own timeout", $tags="aiExternal")
}

Rel_D(turn, recallPort, "Invokes")
Rel_L(useCase, recallPort, "Implements", $tags="implements")
Rel_R(useCase, embedder, "Embeds a message through")
Rel_D(useCase, memoryPort, "Reads through")
Rel_D(embedder, embeddingPort, "Embeds through")
Rel_L(embedder, memoryPort, "Keeps the vector and counts an attempt through")
Rel_U(memoryAdapter, memoryPort, "Implements", $tags="implements")
Rel_U(embeddingAdapter, embeddingPort, "Implements", $tags="implements")
Rel_D(memoryAdapter, database, "The row, its vector, its neighbours", "SQL")
Rel_D(embeddingAdapter, aiProvider, "The message's text", "HTTPS")

Lay_D(recallPort, useCase)
Lay_D(memoryPort, memoryAdapter)
Lay_D(embeddingPort, embeddingAdapter)
Lay_R(memoryPort, embeddingPort)

SHOW_LEGEND()
@enduml
```

## Flow

### Getting to a query

```plantuml
@startuml RecallExamples-Sequence
participant "The turn" as Turn
participant "Recall" as Recall
database "Message Store" as Store
participant "AI Provider" as Provider

Turn -> Recall : the person, the message, its text
Recall -> Store : the registered row

alt no row is kept under that person and message
    Store --> Recall : nothing
    Recall --> Turn : nothing was looked for
else the row holds a vector
    Store --> Recall : the row and its vector
else the row holds no vector
    Store --> Recall : the row
    Recall -> Provider : embed this text

    alt refused, or no answer inside MEMORY_EMBEDDING_TIMEOUT
        Provider --> Recall : failure
        Recall -> Store : count one failed attempt on the row
        Recall --> Turn : nothing was looked for
    else embedded
        Provider --> Recall : the vector
        Recall -> Store : keep the vector, unless the row holds one already
    end
end

alt a vector is held
    Recall -> Store : the neighbours this vector admits
    Store --> Recall : the closest messages and their decided expenses
    Recall --> Turn : those examples, possibly none
end

alt the store refuses any read or write above
    Store --> Recall : failure
    Recall --> Turn : nothing was looked for
end
@enduml
```

### Choosing the examples

```plantuml
@startuml RecallExamples-Activity
start
:candidates — this person's own messages,
never this one, holding a vector,
received inside MEMORY_MAX_AGE,
with a decided expense;
fork
  :the closest MEMORY_EXAMPLES,
each at or above MEMORY_MIN_SIMILARITY;
fork again
  :the single closest received
inside MEMORY_RECENT_WINDOW,
on the same similarity bar;
end fork
if (is the recent one already among the closest?) then (yes)
  :the closest ones, closest first;
else (no)
  :the closest ones, then the recent one;
endif
:each example keeps its first
MEMORY_EXAMPLE_LINES decided expenses,
in the order they were learned;
stop
@enduml
```

## References

- [ADR 0017: The connector verifies its caller token and keeps the message it names](../../../docs/adr/0017-the-connector-verifies-its-caller-token-and-keeps-the-message-it-names.md) —
  why this service keeps a person's messages at all
- [Embedding](../domain/embedding.md) — what a message is compared by
- [Learn what the ledger did with a message](learn-message-outcome.md) — what decides an expense, and so what
  makes a message eligible as an example
- [Embed the messages nothing has embedded yet](backfill-embeddings.md) — what gives a vector to a message no
  turn embedded
- [Delete the messages kept past their age](purge-messages.md) — what takes a message out of the memory again
