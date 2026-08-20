# Resolve a reported proposal

- **In**
  - who tapped
  - the conversation the report sits in
  - which message carries the report
  - which tap it is
  - the [message](../domain/incoming-message-id.md) the buttons name
  - which of the two buttons was tapped
- **Out**
  - an answer to the tap
  - a report whose buttons are gone
- **Why:** the spending a report lists stops being pending. It enters the person's ledger or is thrown away

*Implemented by `ResolveProposalsUseCase`.*

## Collaborators

| Direction | Collaborator                                                            | Through                                                        | For                                                                                |
|-----------|-------------------------------------------------------------------------|----------------------------------------------------------------|------------------------------------------------------------------------------------|
| in        | [Telegram](../contracts/in/telegram-updates.md)                         | [Incoming messages](../contracts/in/telegram-updates.md)       | delivering the tap on a report's button                                            |
| in        | [Act on a user's message](handle-incoming-message.md)                   | [Outgoing replies](../contracts/out/telegram-replies.md)       | putting the buttons on the report, and storing the proposals a tap resolves        |
| out       | [Database](../contracts/out/database.md)                                | [Users, categories and expenses](../contracts/out/database.md) | resolving who tapped, recording or removing what the message proposed, counting it |
| out       | [Telegram](../contracts/out/telegram-replies.md)                        | [Outgoing replies](../contracts/out/telegram-replies.md)       | answering the tap, and taking the buttons off the report                           |
| out       | [A consumer of the ledger's changes](../contracts/out/change-stream.md) | [The change stream](../contracts/out/change-stream.md)         | publishing the fact each resolution produces                                       |

## Outcomes

| Outcome            | When                                                                                 | Result                                                                                      |
|--------------------|--------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| Confirmed          | the person still has pending entries under that message, and Confirm was tapped      | they become recorded; the tap says how many, and the buttons come off                       |
| Discarded          | the person still has pending entries under that message, and Delete was tapped       | they are removed; the tap says how many, and the buttons come off                           |
| Already confirmed  | nothing was left to resolve, and recorded entries stand under that message           | the tap says how many are already recorded, and the buttons come off                        |
| Nothing to resolve | nothing of that person's is stored under the message, or under their identity        | the tap is told there is nothing left, and the buttons come off                             |
| Tap skipped        | the tap names no sender or no conversation, or carries a payload this bot never sent | nothing happens and the tap is left unanswered                                              |
| Tap rejected       | the tap is absent                                                                    | rejected as invalid; nothing is looked up                                                   |
| Storage failed     | the recording, the removal or the count fails                                        | nothing is recorded or removed, the buttons stay, and the failure reaches the caller        |
| Answer failed      | the tap cannot be answered, or the buttons cannot be taken off                       | the resolution stands, and the failure reaches the caller                                   |

A failure of any kind is [logged, and its batch acknowledged with the rest](../contracts/in/telegram-updates.md).

## Components

```plantuml
@startuml C3-Component-ResolveAReportedProposal
!include <C4/C4_Component>

AddElementTag("telegramExternal", $bgColor="#1c94e0", $fontColor="#ffffff", $borderColor="#125d8c")
AddElementTag("dbExternal", $bgColor="#d68910", $fontColor="#ffffff", $borderColor="#8f5c0a")
AddElementTag("portIn", $bgColor="#16a085", $fontColor="#ffffff", $borderColor="#0e6655", $legendText="inbound port (interface)")
AddElementTag("portOut", $bgColor="#7f8c8d", $fontColor="#ffffff", $borderColor="#566573", $legendText="outbound port (interface)")
AddElementTag("core", $bgColor="#2c3e50", $fontColor="#ffffff", $borderColor="#1b2631", $legendText="application core")
AddRelTag("implements", $lineStyle="dashed")

System_Ext(telegram, "Telegram", "Messaging platform; hosts the bot", $tags="telegramExternal")

Container_Boundary(ledger, "Ledger Service (Java, Spring Boot)") {
  Component(telegramListener, "Telegram Update Listener", "Spring Component", "Long-polls the Bot API", $tags="telegramExternal")
  Component(buttonPayload, "Proposal Button Payload", "Plain Java", "Reads the button and the message out of a tap", $tags="telegramExternal")
  Component(resolvePort, "Resolve Proposals Port", "Interface", "Inbound port", $tags="portIn")
  Component(resolveService, "Resolve a Reported Proposal Use Case", "Plain Java", "Resolves the person, moves or removes what the message proposed, then answers", $tags="core")
  Component(userRepositoryPort, "User Repository Port", "Interface", "Outbound port", $tags="portOut")
  Component(expenseRepositoryPort, "Expense Repository Port", "Interface", "Outbound port", $tags="portOut")
  Component(messageDeliveryPort, "Message Delivery Port", "Interface", "Outbound port", $tags="portOut")
  Component(userRepositoryAdapter, "User Repository Adapter", "Spring Data Relational", "Reads the person stored under an identity", $tags="dbExternal")
  Component(expenseRepositoryAdapter, "Expense Repository Adapter", "Spring Data Relational", "Records or removes a message's pending entries, and counts its recorded ones", $tags="dbExternal")
  Component(deliveryAdapter, "Telegram Message Delivery Adapter", "Spring Component", "Answers the tap and clears the buttons", $tags="telegramExternal")
  Component(answerRenderer, "Resolution Answer Renderer", "Plain Java", "Words what happened", $tags="telegramExternal")
}

ContainerDb(db, "Database", "PostgreSQL", "Stores users and their expenses, pending and recorded", $tags="dbExternal")

Rel_R(telegram, telegramListener, "Update (button tap)", "Telegram Bot API, long polling")
Rel_D(telegramListener, buttonPayload, "Reads the tap with")
Rel_D(telegramListener, resolvePort, "Invokes")
Rel_L(resolveService, resolvePort, "Implements", $tags="implements")
Rel_R(resolveService, userRepositoryPort, "Uses")
Rel_R(resolveService, expenseRepositoryPort, "Uses")
Rel_L(resolveService, messageDeliveryPort, "Uses")
Rel_L(userRepositoryAdapter, userRepositoryPort, "Implements", $tags="implements")
Rel_L(expenseRepositoryAdapter, expenseRepositoryPort, "Implements", $tags="implements")
Rel_R(deliveryAdapter, messageDeliveryPort, "Implements", $tags="implements")
Rel_D(deliveryAdapter, answerRenderer, "Words the answer with")
Rel_R(userRepositoryAdapter, db, "SQL", "JDBC")
Rel_R(expenseRepositoryAdapter, db, "SQL", "JDBC")
Rel_U(deliveryAdapter, telegram, "The answer, then the cleared buttons", "Telegram Bot API")

Lay_D(resolvePort, userRepositoryPort)
Lay_D(userRepositoryPort, expenseRepositoryPort)

SHOW_LEGEND()
@enduml
```

## Flow

```plantuml
@startuml ResolveAReportedProposal-Sequence
participant "Telegram" as TG
participant "Resolve a reported proposal" as UC
database "Database" as DB
queue "The change stream" as Stream

TG -> UC : a tap on Confirm or Delete

alt the tap names no sender or conversation, or carries an unknown payload
  UC -> UC : skip the tap
else the tap is complete
  UC -> DB : find the person who tapped
  alt nothing is stored under that identity
    DB --> UC : nothing
  else the person is known
    DB --> UC : the person
    alt Confirm was tapped
      UC -> DB : record their pending entries under the message
    else Delete was tapped
      UC -> DB : remove their pending entries under the message
    end
    alt the store fails
      DB --> UC : storage failed, nothing recorded or removed
    else the store answers
      DB --> UC : how many were recorded or removed
      opt anything matched
        DB -> Stream : the facts, once committed
      end
      alt nothing matched
        UC -> DB : count their recorded entries under the message
        DB --> UC : how many are already recorded
      end
    end
  end
  UC -> UC : log the message, the button, the outcome and the count
  UC -> TG : answer the tap with what happened
  UC -> TG : take the buttons off the report
  alt either call fails
    TG --> UC : delivery failed
  end
end
@enduml
```

## References

- [ADR 0018: A proposal is a status on the expense table](../adr/0018-a-proposal-is-a-status-on-the-expense-table.md) —
  why two taps at once resolve the report once, and why an entry keeps its id through the confirmation
