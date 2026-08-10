# Resolve a reported proposal

- **In:** who tapped · the conversation the report sits in · which message carries the report · which tap it is ·
  the [message](../domain/incoming-message-id.md) the buttons name · which of the two buttons was tapped
- **Out:** an answer to the tap, and a report whose buttons are gone
- **Why:** the spending a report lists stops being pending — it becomes the person's ledger, or it is thrown away

*Implemented by `ResolveProposalsUseCase`.*

## Collaborators

| Direction | Collaborator                                          | Through                                                                           | For                                                                             |
|-----------|-------------------------------------------------------|-----------------------------------------------------------------------------------|---------------------------------------------------------------------------------|
| in        | [Telegram](../contracts/in/telegram-updates.md)       | [Incoming messages](../contracts/in/telegram-updates.md)                          | delivering the tap on a report's button                                         |
| in        | [Act on a user's message](handle-incoming-message.md) | [Outgoing replies](../contracts/out/telegram-replies.md)                          | putting the buttons on the report, and storing the proposals a tap resolves     |
| out       | [Database](../contracts/out/database.md)              | [Users, categories, expenses and expense proposals](../contracts/out/database.md) | resolving who tapped, moving or removing what the message proposed, counting it |
| out       | [Telegram](../contracts/out/telegram-replies.md)      | [Outgoing replies](../contracts/out/telegram-replies.md)                          | answering the tap, and taking the buttons off the report                        |

## Rules

- The person is whoever sent the tap, never a value the button carries.
- Every lookup is scoped to that person's own rows, so knowing an incoming message id grants nothing: another
  member's tap on the same report resolves none of the owner's spending.
- A tap names its sender, its conversation, the report message and itself, none of them blank; the message it
  names is present, and the button is one of the two.
- Confirm turns every proposal stored under that message into an expense; Delete removes them.
- Either way it is one statement, so two taps at once resolve the report once
  ([ADR 0012](../adr/0012-a-set-of-rows-moves-between-tables-in-one-statement.md)).
- A confirmed expense keeps the [message](../domain/incoming-message-id.md) that produced it.
- A confirmed expense keeps the day its proposal was assembled on. Only its last-updated instant is the moment
  of confirmation.
- A discarded proposal is gone, and nothing records that it existed.
- A confirmed expense is never undone: Delete on an already confirmed report removes nothing.
- A tap whose sender is stored under no user resolves nothing, and creates no user.
- When nothing was resolved, the expenses already stored under that message are counted — a count above zero
  means an earlier tap succeeded and only its answer was lost.
- Discard leaves no such trace, so a second tap on a discarded report cannot be told from a message the service
  never reported on.
- The count answered is what actually moved or was removed, which can exceed what the report listed.
- The tap is answered first, then the buttons come off.
- The buttons come off for every outcome, and even when the tap could not be answered.
- A tap that cannot be answered is the failure reported, whether or not the buttons came off.
- The report's own text is never rewritten, so it keeps reading as pending however it was resolved. What happened
  is carried by the answer to the tap alone, which is not part of the conversation.
- Every tap that reaches the use case is logged with the message, the button, the outcome and the count.

## Outcomes

| Outcome            | When                                                                                 | Result                                                                                      |
|--------------------|--------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| Confirmed          | the person's proposals under that message are still stored, and Confirm was tapped   | they become expenses carrying that message; the tap says how many, and the buttons come off |
| Discarded          | the person's proposals under that message are still stored, and Delete was tapped    | they are removed; the tap says how many, and the buttons come off                           |
| Already confirmed  | nothing was left to resolve, and expenses are stored under that message              | the tap says how many are already recorded, and the buttons come off                        |
| Nothing to resolve | nothing of that person's is stored under the message, or under their identity        | the tap is told there is nothing left, and the buttons come off                             |
| Tap skipped        | the tap names no sender or no conversation, or carries a payload this bot never sent | nothing happens and the tap is left unanswered                                              |
| Tap rejected       | the tap is absent                                                                    | rejected as invalid; nothing is looked up                                                   |
| Storage failed     | the move, the removal or the count fails                                             | nothing is moved or removed, the buttons stay, and the failure reaches the caller           |
| Answer failed      | the tap cannot be answered, or the buttons cannot be taken off                       | the resolution stands, and the failure reaches the caller                                   |

A failure of any kind is logged where the tap was delivered, and its batch is acknowledged with the rest.

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
  Component(proposalRepositoryPort, "Expense Proposal Repository Port", "Interface", "Outbound port", $tags="portOut")
  Component(expenseRepositoryPort, "Expense Repository Port", "Interface", "Outbound port", $tags="portOut")
  Component(messageDeliveryPort, "Message Delivery Port", "Interface", "Outbound port", $tags="portOut")
  Component(userRepositoryAdapter, "User Repository Adapter", "Spring Data Relational", "Reads the person stored under an identity", $tags="dbExternal")
  Component(proposalRepositoryAdapter, "Expense Proposal Repository Adapter", "Spring Data Relational", "Moves a message's proposals into expenses, or removes them", $tags="dbExternal")
  Component(expenseRepositoryAdapter, "Expense Repository Adapter", "Spring Data Relational", "Counts the expenses stored under a message", $tags="dbExternal")
  Component(deliveryAdapter, "Telegram Message Delivery Adapter", "Spring Component", "Answers the tap and clears the buttons", $tags="telegramExternal")
  Component(answerRenderer, "Resolution Answer Renderer", "Plain Java", "Words what happened", $tags="telegramExternal")
}

ContainerDb(db, "Database", "PostgreSQL", "Stores users, their expenses and their expense proposals", $tags="dbExternal")

Rel_R(telegram, telegramListener, "Update (button tap)", "Telegram Bot API, long polling")
Rel_D(telegramListener, buttonPayload, "Reads the tap with")
Rel_D(telegramListener, resolvePort, "Invokes")
Rel_L(resolveService, resolvePort, "Implements", $tags="implements")
Rel_R(resolveService, userRepositoryPort, "Uses")
Rel_R(resolveService, proposalRepositoryPort, "Uses")
Rel_R(resolveService, expenseRepositoryPort, "Uses")
Rel_L(resolveService, messageDeliveryPort, "Uses")
Rel_L(userRepositoryAdapter, userRepositoryPort, "Implements", $tags="implements")
Rel_L(proposalRepositoryAdapter, proposalRepositoryPort, "Implements", $tags="implements")
Rel_L(expenseRepositoryAdapter, expenseRepositoryPort, "Implements", $tags="implements")
Rel_R(deliveryAdapter, messageDeliveryPort, "Implements", $tags="implements")
Rel_D(deliveryAdapter, answerRenderer, "Words the answer with")
Rel_R(userRepositoryAdapter, db, "SQL", "JDBC")
Rel_R(proposalRepositoryAdapter, db, "SQL", "JDBC")
Rel_R(expenseRepositoryAdapter, db, "SQL", "JDBC")
Rel_U(deliveryAdapter, telegram, "The answer, then the cleared buttons", "Telegram Bot API")

Lay_D(resolvePort, userRepositoryPort)
Lay_D(userRepositoryPort, proposalRepositoryPort)
Lay_D(proposalRepositoryPort, expenseRepositoryPort)

SHOW_LEGEND()
@enduml
```

## Flow

```plantuml
@startuml ResolveAReportedProposal-Sequence
participant "Telegram" as TG
participant "Resolve a reported proposal" as UC
database "Database" as DB

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
      UC -> DB : move their proposals under the message into their expenses
    else Delete was tapped
      UC -> DB : remove their proposals under the message
    end
    alt the store fails
      DB --> UC : storage failed, nothing moved or removed
    else the store answers
      DB --> UC : how many moved or were removed
      alt nothing moved or was removed
        UC -> DB : count their expenses under the message
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
