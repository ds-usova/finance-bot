# Database — users, categories, expenses and expense proposals (SQL)

Everything the service remembers. A user is stored under the identity the delivering platform knows them by; the
categories they file spending under, the expenses they record, the expense proposals assembled against them, and
the periods they have asked about, all hang off that user.

- **Counterpart:** the service's own PostgreSQL database — its address is [configuration](../../configuration.md)
- **Transport:** SQL over JDBC
- **Schema:** below. No file holds the current state — it is spread across every migration ever applied, so this
  diagram is the one place it is written down. Read from `src/main/resources/db/migration/`.

This page carries the schema, not the statements run against it. What each use case reads and writes is on its
own page, which lists this contract as a collaborator.

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
  message_reference : UUID
  * created_at : TIMESTAMPTZ
  * updated_at : TIMESTAMPTZ
}

entity "expense_proposal" as expense_proposal {
  * id : BIGSERIAL <<PK>>
  --
  * user_id : BIGINT <<FK app_user.id>>
  * category_id : BIGINT <<FK category.id>>
  * description : VARCHAR(500)
  merchant : VARCHAR(255)
  * amount_minor_units : BIGINT <<check >= 0>>
  * currency_code : VARCHAR(3)
  * message_reference : UUID
  * created_at : TIMESTAMPTZ
  * updated_at : TIMESTAMPTZ
}

entity "spending_query" as spending_query {
  * id : BIGSERIAL <<PK>>
  --
  * user_id : BIGINT <<FK app_user.id>>
  * message_reference : UUID
  * period_start : DATE
  * period_end : DATE <<check >= period_start>>
  * created_at : TIMESTAMPTZ
}

app_user ||--o{ category
category ||--o{ category
app_user ||--o{ expense
category ||--o{ expense
app_user ||--o{ expense_proposal
category ||--o{ expense_proposal
app_user ||--o{ spending_query
@enduml
```

Indexes beyond the constraints above:

- `uq_category_user_parent_name` on `(user_id, parent_id, name)`, **`NULLS NOT DISTINCT`**.
- `idx_expense_user_created_at` on `(user_id, created_at DESC)`.
- `idx_expense_message_reference` on `(user_id, message_reference)`.
- `idx_expense_proposal_user_created_at` on `(user_id, created_at DESC)`.
- `idx_expense_proposal_message_reference` on `(user_id, message_reference)`.
- `idx_spending_query_message_reference` on `(user_id, message_reference)`.

## What a Column Means

- **A `category` row with no parent is a grouping.** That is the only thing telling a grouping from a category
  carrying the same name.
- `incoming_message_id` is a [message](../../domain/incoming-message-id.md), in all three tables that carry one.
- **A reference is in `expense_proposal` or in `expense`, never both.** No constraint enforces it
  ([ADR 0012](../../adr/0012-a-set-of-rows-moves-between-tables-in-one-statement.md)).
- `user_id` cascades on delete everywhere. `category_id` does not: a category cannot be removed while an expense
  or a proposal references it.

## Compatibility

Migrations are append-only. An applied migration is never edited — a change is a new one, so every database
reaches the same state by the same path.

Widening a column or adding a nullable one costs callers nothing. Narrowing one, or adding a constraint the
stored rows already violate, breaks the migration itself rather than the caller.
