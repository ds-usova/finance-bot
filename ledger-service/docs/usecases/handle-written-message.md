# Act on a text filed from the web

- **In**
  - the oldest [text](../domain/written-message.md) waiting to be read, whoever filed it
- **Out**
  - whatever spending the text names, pending under the text's own [message id](../domain/incoming-message-id.md)
  - the text, settled `DONE` with what its reading came to
  - whether a text was claimed
- **Why:** a text filed from the expenses page becomes pending spending, and the page learns what it came to

*Implemented by `HandleWrittenMessageUseCase`.*

## Prerequisites

- It runs on the reader's own thread.
- Each look reads one text after another until none waits.
- The person is the one stored on the text.

## Collaborators

| Direction | Collaborator                                                                                                 | Through                                                                        | For                                                                                 |
|-----------|--------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------|-------------------------------------------------------------------------------------|
| in        | [File a text for reading](file-written-message.md)                                                           | [File a text for reading](file-written-message.md)                             | waking the reader the moment a text is filed                                        |
| in        | [The reader's schedule](../configuration.md)                                                                 | [Configuration](../configuration.md)                                           | looking for a waiting text every poll interval                                      |
| out       | [Database](../contracts/out/database.md)                                                                     | [Users, categories and expenses](../contracts/out/database.md)                 | claiming and settling the text, reading the person, reading back what the turn left |
| out       | [Record the spending a user's message names](../../../ai-connector-service/docs/usecases/extract-intents.md) | [AI Connector Service — intent extraction](../contracts/out/ai-connector.md) | acting on the text, as that person                                                  |
| out       | [Summarize spending over a period](summarize-spending.md)                                                    | [Users, categories and expenses](../contracts/out/database.md)                 | picking up the periods the text asked about, so they can be discarded               |

## Outcomes

| Outcome                | When                                                                             | Result                                                                              |
|------------------------|----------------------------------------------------------------------------------|-------------------------------------------------------------------------------------|
| Nothing waiting        | no text is `WAITING`                                                             | nothing claimed, and nothing else is read                                           |
| Spending recorded      | the connector finished, and an entry is under the text                           | settled `RECORDED`                                                                  |
| Period answered        | the connector finished, no entry is under the text, and a period was asked about | settled `ANSWERED`                                                                  |
| Nothing identified     | the connector finished, and nothing is under the text                            | settled `NOTHING_IDENTIFIED`                                                        |
| Person unprepared      | the person's catalogue cannot be read, or lacks the designated catch-all         | settled `FAILED`, the connector never called, and a warning logged                  |
| Currency unread        | the person's assumed currency cannot be read                                     | the turn runs, assuming no currency, and the failure is logged                      |
| Swept before hand-over | the text stopped being `READING` before the connector was called                 | the connector is never called, and the text is left as it was settled               |
| Hand-over unstamped    | recording the hand-over fails                                                    | settled `FAILED`, and the connector never called                                    |
| Connector failed       | the connector refuses the turn, or cannot be reached                             | settled `PARTIAL` if an entry is under the text, else `FAILED`, and an error logged |
| Periods kept           | the periods asked about cannot be discarded                                      | logged, and the text is still settled `ANSWERED`                                    |
| Swept during the turn  | the text stopped being `READING` before it could be settled                      | the outcome already stored stands                                                   |
| Storage failed         | the claim, the read-back or the settle fails                                     | the failure reaches the reader, and a claimed text stays `READING` for the sweep    |
| Look failed            | any failure reaches the reader                                                   | logged at error, and the next look runs as usual                                    |

- An entry counts whether it is still pending or already accepted.
- An info line names the text's message id and the person when a text is claimed, and again when it is settled.

## Components

The stores the turn reaches:

```plantuml
@startuml C3-Component-HandleWrittenMessage-Stores
!include <C4/C4_Component>

Container_Boundary(async, "adapter — background work") {
  Component(reader, "Written message reader", "single scheduled thread")
}
Container_Boundary(application, "application — usecases and ports") {
  Component(useCase, "Act on a text filed from the web", "use case")
  Component(writtenPort, "Written message repository", "outbound port")
  Component(groupingPort, "Grouping repository", "outbound port")
  Component(preferencePort, "User preference repository", "outbound port")
  Component(expensePort, "Expense repository", "outbound port")
  Component(queryPort, "Spending query repository", "outbound port")
}
Container_Boundary(outboundDb, "adapter (outbound) — Postgres") {
  Component(writtenAdapter, "Written message repository adapter", "Spring Data JDBC")
  Component(groupingAdapter, "Grouping repository adapter", "Spring Data JDBC")
  Component(preferenceAdapter, "User preference repository adapter", "Spring Data JDBC")
  Component(expenseAdapter, "Expense repository adapter", "Spring Data JDBC")
  Component(queryAdapter, "Spending query repository adapter", "Spring Data JDBC")
}
ContainerDb(db, "Database", "PostgreSQL")

Rel_D(reader, useCase, "Reads the next text through")
Rel_D(useCase, writtenPort, "Claims, stamps, settles")
Rel_D(useCase, groupingPort, "Reads the catalogue")
Rel_D(useCase, preferencePort, "Reads the currency")
Rel_D(useCase, expensePort, "Reads the days")
Rel_D(useCase, queryPort, "Reads, discards periods")
Rel_U(writtenAdapter, writtenPort, "Implements")
Rel_U(groupingAdapter, groupingPort, "Implements")
Rel_U(preferenceAdapter, preferencePort, "Implements")
Rel_U(expenseAdapter, expensePort, "Implements")
Rel_U(queryAdapter, queryPort, "Implements")
Rel_D(writtenAdapter, db, "SQL")
Rel_D(groupingAdapter, db, "SQL")
Rel_D(preferenceAdapter, db, "SQL")
Rel_D(expenseAdapter, db, "SQL")
Rel_D(queryAdapter, db, "SQL")

Lay_D(useCase, writtenPort)
Lay_D(writtenPort, writtenAdapter)
@enduml
```

The connector the turn hands the text to:

```plantuml
@startuml C3-Component-HandleWrittenMessage-Connector
!include <C4/C4_Component>

Container_Boundary(application, "application — usecases and ports") {
  Component(useCase, "Act on a text filed from the web", "use case")
  Component(extractionPort, "Intent extraction", "outbound port")
}
Container_Boundary(outboundAi, "adapter (outbound) — AI Connector") {
  Component(extractionAdapter, "Intent extraction adapter", "gRPC client")
  Component(minter, "Access token minter", "Nimbus JOSE", "Names the person and web:<id>")
}
Container(connector, "AI Connector Service", "Java, Spring Boot")

Rel_R(useCase, extractionPort, "Hands the text over")
Rel_U(extractionAdapter, extractionPort, "Implements")
Rel_R(extractionAdapter, minter, "Mints with")
Rel_D(extractionAdapter, connector, "Text, catalogue and credential", "gRPC")

Lay_D(extractionPort, extractionAdapter)
@enduml
```

## Flow

```plantuml
@startuml HandleWrittenMessage-Sequence
participant "The reader's schedule" as Reader
participant "Act on a text filed from the web" as UC
database "Database" as DB
participant "AI Connector Service" as AI
participant "Summarize spending over a period" as SS

Reader -> UC : read the next text
UC -> DB : claim the oldest WAITING text
alt nothing waits
  DB --> UC : nothing
  UC --> Reader : nothing claimed
else one claimed
  DB --> UC : the text, now READING
  UC -> DB : the person's catalogue and currency
  alt unreadable, or no catch-all
    UC -> DB : settle FAILED
  else prepared
    UC -> DB : stamp the hand-over, if still READING
    alt already swept
      DB --> UC : not stamped
    else the stamp fails
      UC -> DB : settle FAILED
    else stamped
      UC -> AI : the text and the catalogue, as the person, under web:<id>
      AI -> SS : any period asked about
      AI --> UC : handled, refused, or unreachable
      UC -> DB : the days and periods under web:<id>
      UC -> UC : choose the outcome
      opt periods were asked about
        UC -> DB : discard them
      end
      UC -> DB : settle DONE, if still READING
    end
  end
  UC --> Reader : a text was claimed
end
@enduml
```

## References

- [ADR 0015: A turn is named by the message that started it](../adr/0015-a-turn-is-named-by-the-message-that-started-it-not-by-a-value-minted-beside-it.md) —
  why everything the turn stores carries `web:<id>`
- [ADR 0007: An MCP caller is identified by a signed token, not a tool argument](../adr/0007-an-mcp-caller-is-identified-by-a-signed-token-not-a-tool-argument.md) —
  how the connector acts as the person for the length of the turn
- [Settle the texts left reading](sweep-written-messages.md) — what settles a text this leaves `READING`
- [Read what became of a filed text](read-written-message.md) — how the page learns the outcome
