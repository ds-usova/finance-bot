# Database — users, categories and expenses (SQL)

Everything the service remembers.

- **Counterpart:** the service's own PostgreSQL database — its address is [configuration](../../configuration.md)
- **Transport:** SQL over JDBC
- **Schema:** below. No file holds the current state — it is spread across every migration ever applied, so this
  diagram is the one place it is written down. Read from `src/main/resources/db/migration/`.

## Schema

```plantuml
@startuml ledger-schema
hide circle
skinparam linetype ortho

entity "app_user" as app_user {
  * id : BIGSERIAL <<PK>>
  --
  * external_id : VARCHAR(255) <<unique>>
}

entity "category" as category {
  * id : BIGSERIAL <<PK>>
  --
  * user_id : BIGINT <<FK app_user.id>>
  parent_id : BIGINT <<FK category.id>>
  * name : VARCHAR(100)
}

entity "expense" as expense {
  * id : BIGSERIAL <<PK>>
  --
  * user_id : BIGINT <<FK app_user.id>>
  * category_id : BIGINT <<FK category.id>>
  * description : VARCHAR(500)
  merchant : VARCHAR(255)
  * amount_minor_units : BIGINT <<check >= 0>>
  * currency_code : VARCHAR(3)
  incoming_message_id : TEXT <<check required while PENDING>>
  * status : VARCHAR(10) <<check PENDING | RECORDED>>
  * created_at : TIMESTAMPTZ
  * updated_at : TIMESTAMPTZ
}

entity "spending_query" as spending_query {
  * id : BIGSERIAL <<PK>>
  --
  * user_id : BIGINT <<FK app_user.id>>
  * incoming_message_id : TEXT
  * period_start : DATE
  * period_end : DATE <<check >= period_start>>
  * created_at : TIMESTAMPTZ
}

entity "proposal_report" as proposal_report {
  * id : BIGSERIAL <<PK>>
  --
  * user_id : BIGINT <<FK app_user.id>>
  * incoming_message_id : TEXT
  * conversation_id : TEXT
  * sent_message_id : TEXT
  * created_at : TIMESTAMPTZ
  * updated_at : TIMESTAMPTZ
}

entity "cdc_heartbeat" as cdc_heartbeat {
  * id : BOOLEAN <<PK>> <<check id>>
  --
  * beat_at : TIMESTAMPTZ
}

app_user ||--o{ category
category ||--o{ category
app_user ||--o{ expense
category ||--o{ expense
app_user ||--o{ spending_query
app_user ||--o{ proposal_report
@enduml
```

The database also carries `debezium_offset_storage`, which no migration declares, and a replica identity on two
of the tables above. Both are [Change capture](change-capture.md)'s.

Indexes beyond the constraints above:

- `uq_category_user_parent_name` on `(user_id, parent_id, name)`, **`NULLS NOT DISTINCT`**.
- `idx_expense_user_created_at` on `(user_id, created_at DESC)`.
- `idx_expense_incoming_message` on `(user_id, incoming_message_id)`.
- `idx_spending_query_incoming_message` on `(user_id, incoming_message_id)`.
- `idx_proposal_report_incoming_message` on `(user_id, incoming_message_id)`.

## What a Table Holds

| Table              | Domain                                                | A row is                                                          |
|--------------------|-------------------------------------------------------|--------------------------------------------------------------------|
| `app_user`         | [User](../../domain/user.md)                          | a person, under the identity Telegram knows them by                |
| `category`         | [Grouping](../../domain/grouping.md), with no parent  | a heading spending is filed under, never spending itself           |
| `category`         | [Category](../../domain/category.md), with a parent   | what one expense is filed under, inside its grouping               |
| `expense`          | [Expense](../../domain/expense.md)                    | one piece of spending, pending or recorded                         |
| `spending_query`   | [Spending query](../../domain/spending-query.md)      | a period a message asked about, waiting to be totalled in a report |
| `proposal_report`  | [Proposal report](../../domain/proposal-report.md)    | the message the bot sent back, so its buttons can be reached again |
| `cdc_heartbeat`    | none                                                  | no use case writes it — see [Change capture](change-capture.md)    |

- A grouping and a category are the same table. The parent is what tells them apart.
- `incoming_message_id` is a [message a person sent](../../domain/incoming-message-id.md), in all three tables
  that carry one.
- `proposal_report` names two different messages: `incoming_message_id` is what the person sent,
  `sent_message_id` is what the bot sent back in `conversation_id`.
- `status` is the [expense status](../../domain/expense-status.md)
  ([ADR 0018](../../adr/0018-a-proposal-is-a-status-on-the-expense-table.md)).

## Compatibility

Migrations are append-only. An applied migration is never edited; a change is a new one.

Widening a column or adding a nullable one costs callers nothing. Narrowing one, or adding a constraint the
stored rows already violate, breaks the migration itself rather than the caller.
