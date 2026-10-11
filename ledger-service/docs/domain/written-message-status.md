# Written message status

How far the reading of a text filed from the web has got.

## Invariants

- `WAITING | READING | DONE`
- `WAITING -> READING -> DONE` only

## Made of / held by

- [Written message](written-message.md) — carries one, and draws the moves between them.
- [File a text for reading](../usecases/file-written-message.md) — stores every new text `WAITING`.
- [Act on a text filed from the web](../usecases/handle-written-message.md) — claims a text `READING`, then
  settles it `DONE`.
- [Settle the texts left reading](../usecases/sweep-written-messages.md) — settles a stale `READING` text `DONE`.
- [Read what became of a filed text](../usecases/read-written-message.md) — reads its days back only once `DONE`.
- [List a person's texts being read](../usecases/browse-written-messages.md) — lists those `WAITING` or `READING`.
- [Database](../contracts/out/database.md) — where it is kept.
