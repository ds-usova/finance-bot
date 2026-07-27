# Receive a user's message

Takes a message a user sent to the bot — the conversation it belongs to and its text — and writes it to the
service's log. It is the point at which a user's words enter the service; today the log entry is where they stop.

*Implemented by `HandleIncomingMessageUseCase`.*

## Collaborators

| Direction | Collaborator                                    | Through                                                  | For                                     |
|-----------|-------------------------------------------------|----------------------------------------------------------|-----------------------------------------|
| in        | [Telegram](../contracts/in/telegram-updates.md) | [Incoming messages](../contracts/in/telegram-updates.md) | delivering what a user typed to the bot |

Nothing is called outward: no other system takes part.

## Flow

1. Telegram delivers a batch of messages waiting for the bot.
2. Each message carrying text is taken as one message from one conversation; every other message is discarded.
3. The conversation and the text are written to the log.
4. The whole batch is acknowledged, so Telegram moves on to the messages behind it.

## Rules

- A message is accepted only when it names a conversation and carries text. A message missing either is not a
  message this service can receive.
- The conversation is whatever Telegram identifies it by, kept as opaque text.
- The log entry is all that happens: nothing is stored, nothing is extracted from the text, and the user gets no
  reply.

## Outcomes

| Outcome           | When                                  | Result                                                                  |
|-------------------|---------------------------------------|-------------------------------------------------------------------------|
| Message logged    | the message carries text              | the conversation and the text are written to the service's log          |
| Message discarded | the message carries no text           | nothing is logged and the message is not seen again                     |
| Handling failed   | handling the message raises a failure | the failure is logged in its place and the batch is acknowledged anyway |

## Sequence

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
