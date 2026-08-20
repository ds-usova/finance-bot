# Embed the messages nothing has embedded yet

- **In:** nothing
- **Out:** nothing
- **Why:** a message the provider could not embed when it arrived still becomes an example later

*Implemented by `BackfillEmbeddingsUseCase`.*

## Prerequisites

- `MEMORY_ENABLED` is on.
- The purge has already run on this tick.

## Collaborators

| Direction | Collaborator                                     | Through                                                   | For                                                              |
|-----------|--------------------------------------------------|-----------------------------------------------------------|------------------------------------------------------------------|
| in        | [The service's own timer](../configuration.md)   | [Configuration](../configuration.md)                      | asking for a backfill every `MEMORY_PURGE_INTERVAL`              |
| out       | [Message store](../contracts/out/database.md)    | [Database](../contracts/out/database.md)                  | claiming messages with no vector, and keeping the vectors        |
| out       | [AI Provider](../contracts/out/ai-provider.md)   | [Embeddings](../contracts/out/ai-provider.md#operations)  | embedding a whole batch in one call                              |

## Outcomes

| Outcome              | When                                                                                              | Result                                                                                                 |
|----------------------|---------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------|
| Batch embedded       | a claim answered rows and the provider answered a vector for each                                 | each row holds its vector and no claim; what was already learned about it is untouched                 |
| Backlog bounded      | every claim comes back full                                                                       | at most `MEMORY_BACKFILL_BATCHES` claims of `MEMORY_BACKFILL_BATCH` rows; the rest waits for the next tick |
| Tick finished        | a claim answers fewer rows than `MEMORY_BACKFILL_BATCH`, or none at all                           | the tick ends; nothing more is claimed                                                                 |
| Provider refused     | the provider refuses, answers slower than `MEMORY_BACKFILL_TIMEOUT`, or answers fewer vectors than the batch held | one failed attempt on every claimed row, each claim released; one `WARN`; the tick ends |
| Message given up on  | a row's attempt is the `MEMORY_EMBEDDING_ATTEMPTS`th                                              | one `ERROR` naming that row and carrying no text; it is never claimed again, and the rows behind it move on |
| Store refused        | the claim or a vector write fails                                                                 | one `WARN`; the tick ends where it stood; the next tick starts again                                   |
| Attempt not counted  | the store fails while counting a failed attempt                                                   | the tick ends; the timer logs it at `ERROR`; the next tick starts again                                |
| Vector already there | a turn embedded a claimed row while its batch was out with the provider                           | the row keeps the vector the turn wrote; the batch's write leaves it alone                             |
| Claim retaken        | a claim is older than `MEMORY_PURGE_INTERVAL`                                                     | it is stale and may be claimed again                                                                   |
| Nothing embedded twice | two instances tick together                                                                     | a claim takes only rows no other run holds                                                             |

## Components

```plantuml
@startuml C3-Component-BackfillEmbeddings
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
  Component(scheduler, "Memory Purge Timer", "Scheduled task", "Asks for a backfill every MEMORY_PURGE_INTERVAL", $tags="core")
  Component(backfillPort, "Backfill Embeddings Port", "Interface", "Inbound port", $tags="portIn")
  Component(useCase, "Backfill Embeddings Use Case", "Plain Java", "Claims a batch at a time, up to the tick's bound", $tags="core")
  Component(embedder, "Message Embedder", "Plain Java", "Embeds a batch, keeps the vectors, counts a failed attempt per row", $tags="core")

  Component(memoryPort, "Message Memory Port", "Interface", "Outbound port", $tags="portOut")
  Component(memoryAdapter, "Message Memory Adapter", "Spring Data JDBC", "Claims rows with no vector, writes the vectors back", $tags="storeExternal")
  Component(embeddingPort, "Message Embedding Port", "Interface", "Outbound port", $tags="portOut")
  Component(embeddingAdapter, "Message Embedding Adapter", "Spring AI EmbeddingModel", "Embeds a batch in one call, bounded by its own timeout", $tags="aiExternal")
}

Rel_D(scheduler, backfillPort, "Invokes after the purge")
Rel_L(useCase, backfillPort, "Implements", $tags="implements")
Rel_R(useCase, embedder, "Embeds a batch through")
Rel_D(useCase, memoryPort, "Claims through")
Rel_D(embedder, embeddingPort, "Embeds through")
Rel_L(embedder, memoryPort, "Keeps the vectors and counts attempts through")
Rel_U(memoryAdapter, memoryPort, "Implements", $tags="implements")
Rel_U(embeddingAdapter, embeddingPort, "Implements", $tags="implements")
Rel_D(memoryAdapter, database, "The claim, then the vectors", "SQL")
Rel_D(embeddingAdapter, aiProvider, "One batch of texts", "HTTPS")

Lay_D(backfillPort, useCase)
Lay_D(memoryPort, memoryAdapter)
Lay_D(embeddingPort, embeddingAdapter)
Lay_R(memoryPort, embeddingPort)

SHOW_LEGEND()
@enduml
```

## Flow

```plantuml
@startuml BackfillEmbeddings-Sequence
participant "The timer" as Timer
participant "Backfill" as Backfill
database "Message Store" as Store
participant "AI Provider" as Provider

Timer -> Backfill : the purge has run

loop at most MEMORY_BACKFILL_BATCHES times, while a claim comes back full
    Backfill -> Store : claim MEMORY_BACKFILL_BATCH unembedded messages, oldest first

    alt the store refuses
        Store --> Backfill : failure
        Backfill --> Timer : the tick ends
    else nothing is claimable
        Store --> Backfill : no rows
        Backfill --> Timer : the tick ends
    else rows are claimed
        Store --> Backfill : the rows and their texts
        Backfill -> Provider : embed these texts, in one call

        alt refused, short, or no answer inside MEMORY_BACKFILL_TIMEOUT
            Provider --> Backfill : failure
            Backfill -> Store : count one failed attempt on every claimed row
            Backfill --> Timer : the tick ends
        else embedded
            Provider --> Backfill : one vector per text, in order
            Backfill -> Store : keep each vector, unless the row holds one already

            alt the store refuses a write
                Store --> Backfill : failure
                Backfill --> Timer : the tick ends
            end
        end
    end
end
@enduml
```

## References

- [Delete the messages kept past their age](purge-messages.md) — what runs first on the same timer
- [Recall the person's own worked examples](recall-examples.md) — what a vector is for, and what embeds a
  message inside a turn
- [Learn what the ledger did with a message](learn-message-outcome.md) — what a message keeps collecting while
  it waits for a vector
