# Receive a user's message

- **In:** the conversation a message belongs to · its text
- **Out:** a log entry
- **Why:** it is the point at which a user's words enter the service; today the log is where they stop

*Implemented by `HandleIncomingMessageUseCase`.*

## Collaborators

| Direction | Collaborator                                    | Through                                                  | For                                     |
|-----------|-------------------------------------------------|----------------------------------------------------------|-----------------------------------------|
| in        | [Telegram](../contracts/in/telegram-updates.md) | [Incoming messages](../contracts/in/telegram-updates.md) | delivering what a user typed to the bot |

Nothing is called outward.

## Rules

- A message is accepted only when it names a conversation and carries text.
- The conversation is whatever Telegram identifies it by, kept as opaque text.
- The log entry is all that happens: nothing is stored, nothing is extracted, and the user gets no reply.
- A batch is acknowledged whole, so Telegram moves on to the messages behind it.

## Outcomes

| Outcome           | When                                  | Result                                                                  |
|-------------------|---------------------------------------|-------------------------------------------------------------------------|
| Message logged    | the message carries text              | the conversation and the text are written to the service's log          |
| Message discarded | the message carries no text           | nothing is logged and the message is not seen again                     |
| Handling failed   | handling the message raises a failure | the failure is logged in its place and the batch is acknowledged anyway |

## Components

```plantuml
@startuml C3-Component-ReceiveUserMessage
!include <C4/C4_Component>

AddElementTag("telegramExternal", $bgColor="#1c94e0", $fontColor="#ffffff", $borderColor="#125d8c")
AddElementTag("portIn", $bgColor="#16a085", $fontColor="#ffffff", $borderColor="#0e6655", $legendText="inbound port (interface)")
AddElementTag("core", $bgColor="#2c3e50", $fontColor="#ffffff", $borderColor="#1b2631", $legendText="application core")
AddRelTag("implements", $lineStyle="dashed")

System_Ext(telegram, "Telegram", "Messaging platform; hosts the bot", $tags="telegramExternal")

Container_Boundary(ledger, "Ledger Service (Java, Spring Boot)") {
  Component(telegramListener, "Telegram Update Listener", "Spring Component", "Long-polls the Bot API", $tags="telegramExternal")
  Component(handleMessagePort, "Handle Incoming Message Port", "Interface", "Inbound port", $tags="portIn")
  Component(handleMessageService, "Handle Incoming Message Use Case", "Plain Java", "Logs the conversation and the text", $tags="core")
}

Rel(telegram, telegramListener, "Update (message)", "Telegram Bot API, long polling")
Rel_R(telegramListener, handleMessagePort, "Invokes")
Rel_L(handleMessageService, handleMessagePort, "Implements", $tags="implements")

SHOW_LEGEND()
@enduml
```

## Flow

```plantuml
@startuml ReceiveUserMessage-Sequence
participant "Telegram" as TG
participant "Ledger Service" as LS

TG -> LS : batch of waiting messages

loop each message in the batch
  alt message carries text
    LS -> LS : log the conversation and the text
  else message carries no text
    LS -> LS : discard the message
  else handling fails
    LS -> LS : log the failure
  end
end

LS --> TG : acknowledge the whole batch
@enduml
```
