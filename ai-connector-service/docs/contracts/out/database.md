# Database — what this service remembers (SQL)

One row per message this service was asked to act on, filed under the person and the message the caller's token
named, and a row per expense and per refused delivery beside it.

- **Counterpart:** the service's own PostgreSQL database — its address is
  [configuration](../../configuration.md)
- **Transport:** SQL over JDBC
- **Schema:** below, and in `src/main/resources/db/migration/`.

## Schema

```plantuml
@startuml finance-ai-schema
hide circle
skinparam linetype ortho

entity "incoming_message" as incoming_message {
  * id : BIGSERIAL <<PK>>
  --
  * user_id : BIGINT <<unique with incoming_message_id>>
  * incoming_message_id : TEXT <<unique with user_id>>
  * text : TEXT
  * received_at : TIMESTAMPTZ
  --
  embedding : vector(1536)
  * embedding_attempts : INT
  backfill_claimed_at : TIMESTAMPTZ
}

entity "recorded_expense" as recorded_expense {
  * id : BIGSERIAL <<PK>>
  --
  * message_id : BIGINT <<FK incoming_message.id, ON DELETE CASCADE>>
  * user_id : BIGINT
  * expense_id : BIGINT <<unique>>
  --
  * description : TEXT
  merchant : TEXT
  * amount : TEXT
  * currency_code : VARCHAR(3)
  * category_id : BIGINT
  * category_name : TEXT
  grouping_id : BIGINT
  grouping_name : TEXT
  --
  * status : TEXT <<check PROPOSED | ACCEPTED | DISCARDED>>
  * applied_ms : BIGINT
  * applied_seq : BIGINT
  * updated_at : TIMESTAMPTZ
}

entity "stream_entry_failure" as stream_entry_failure {
  * entry_id : TEXT <<PK>>
  --
  * attempts : INT
  * first_failed_at : TIMESTAMPTZ
  * last_error : TEXT
}

incoming_message ||--o{ recorded_expense
@enduml
```

Indexes beyond the constraints above:

- `idx_incoming_message_user_received` on `(user_id, received_at DESC)`.
- `idx_incoming_message_received` on `(received_at)`.
- `idx_recorded_expense_message` on `(message_id)`.

## What a Column Holds

### `incoming_message`

| Column                | Holds                                                                              |
|-----------------------|------------------------------------------------------------------------------------|
| `user_id`             | the person, as the [message identity](../../domain/message-identity.md) names them |
| `incoming_message_id` | the message they sent, as that identity names it                                   |
| `text`                | the text of the request, character for character — never trimmed, cut or rewritten |
| `received_at`         | when the row was written, from the database's own clock                            |
| `embedding`           | the vector the text was embedded as; absent until it has been                      |
| `embedding_attempts`  | how often embedding this text has been tried and failed                            |
| `backfill_claimed_at` | when a backfill run last claimed the row; absent while nothing holds it            |

### `recorded_expense`

One row per expense the ledger made of a message, as a
[spending row](../../domain/spending-row.md) reaches this service.

| Column                        | Holds                                                                                        |
|-------------------------------|-----------------------------------------------------------------------------------------------|
| `message_id`                  | the message the expense came out of                                                           |
| `user_id`                     | the person, copied from the message                                                           |
| `expense_id`                  | the ledger's one id for the entry, for its whole life; the row's key                          |
| `description`                 | what the expense was for, as the ledger holds it                                              |
| `merchant`                    | who it was paid to; absent where the ledger holds none                                        |
| `amount`                      | the amount as the [spending row](../../domain/spending-row.md) carried it                     |
| `currency_code`               | the currency, as a [currency code](../../domain/currency-code.md)                             |
| `category_id`, `category_name`| the [category](../../domain/category-ref.md) it is filed under, as of the fact last applied   |
| `grouping_id`, `grouping_name`| the grouping that category sits in; both absent for a category with no parent                 |
| `status`                      | its [recorded status](../../domain/recorded-status.md)                                        |
| `applied_ms`, `applied_seq`   | the [stream position](../../domain/stream-position.md) the row was last written from          |
| `updated_at`                  | when this service last wrote the row, from its own clock                                      |

- The row's own `id` is its arrival order.

### `stream_entry_failure`

One row per delivery the store has refused.

| Column            | Holds                                                     |
|-------------------|-------------------------------------------------------------|
| `entry_id`        | the delivery, as the change stream named it                 |
| `attempts`        | how often the store has refused it                          |
| `first_failed_at` | when it was first refused                                   |
| `last_error`      | what the store said the last time                           |

## Failures

| Condition                                                      | Signal                                                 |
|----------------------------------------------------------------|--------------------------------------------------------|
| The database cannot be reached, or refuses a write or a delete | the write or the batch does not happen; the port raises |
| A migration cannot be applied                                  | the service does not start                             |

## Compatibility

Migrations are append-only. An applied migration is never edited; a change is a new one.

Widening a column or adding a nullable one costs nothing. Narrowing one, or adding a constraint the stored rows
already violate, breaks the migration itself rather than a reader.

The database is this service's alone, so a change here reaches no other module.
