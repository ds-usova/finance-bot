# Incoming message

Something a person said, as it enters the service, with the conversation it belongs to.

## Invariants

- A conversation is named, and the name is not blank.
- Text is present, and it is not blank.
- The conversation name is opaque text — whatever the delivering platform calls a conversation, kept as it came.

## Made of / held by

A conversation name and the text itself.

- [Receive a user's message](../usecases/handle-incoming-message.md) — what it does with one.
- [Telegram — incoming messages](../contracts/in/telegram-updates.md) — where one comes from today.
