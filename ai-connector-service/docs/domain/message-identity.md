# Message identity

The person a message was written by and the message itself — the pair a kept message is filed under.

## Invariants

- **The person:** `int64`, present, non-blank, parses as a whole number.
- **The message:** present, non-blank.
- **Both or neither.**

## Made of / held by

- **Made of:** the person's id in the ledger, and the
  [id of the message they sent](../../../ledger-service/docs/domain/incoming-message-id.md).
- **Read from:** the claims of the [caller's token](../contracts/in/intent-extraction.md#the-token), never from
  the request.
- **Held by:** every message [kept in the store](../contracts/out/database.md), as the key it is filed under.
- **Also read from:** a [spending row](spending-row.md) the ledger announced, where it names a message — the key
  the expense is hung off.
