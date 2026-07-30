# Database — users, categories, expenses and expense proposals (SQL)

Everything the service remembers. A user is stored under the identity the delivering platform knows them by; the
categories they file spending under, the expenses they record, and the expense proposals assembled against them,
hang off that user.

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
  * created_at : TIMESTAMPTZ
  * updated_at : TIMESTAMPTZ
}

app_user ||--o{ category
category ||--o{ category
app_user ||--o{ expense
category ||--o{ expense
app_user ||--o{ expense_proposal
category ||--o{ expense_proposal
@enduml
```

Indexes beyond the constraints above:

- `uq_category_user_parent_name` on `(user_id, parent_id, name)`, **`NULLS NOT DISTINCT`**.
- `idx_expense_user_created_at` on `(user_id, created_at DESC)`.
- `idx_expense_proposal_user_created_at` on `(user_id, created_at DESC)`.

`expense.user_id` and `expense_proposal.user_id` cascade on delete: removing a user removes their expenses and
their proposals. `expense.category_id` and `expense_proposal.category_id` carry no `ON DELETE` clause: a category
cannot be removed while either references it.

## Operations

| Operation               | Purpose                                          | Used by                                                          |
|-------------------------|--------------------------------------------------|------------------------------------------------------------------|
| Find a user by identity | reads the user stored under an external identity | [Initialize a new user](../../usecases/initialize-a-new-user.md), [Create an expense](../../usecases/create-an-expense.md), [Create an expense proposal](../../usecases/create-an-expense-proposal.md) |
| Create a user           | stores a user and their categories together      | [Initialize a new user](../../usecases/initialize-a-new-user.md) |
| Create an expense       | stores an expense against a user and category    | [Create an expense](../../usecases/create-an-expense.md)         |
| Find a user's categories by name | reads every category of one user carrying a name, each with the name of the one it sits under | [Create an expense proposal](../../usecases/create-an-expense-proposal.md) |
| Find a category's children | reads the names of the categories filed under one category | [Create an expense proposal](../../usecases/create-an-expense-proposal.md) |
| Create an expense proposal | stores a proposal against a user and category | [Create an expense proposal](../../usecases/create-an-expense-proposal.md) |

## Compatibility

Migrations are append-only. An applied migration is never edited — a change is a new one, so every database
reaches the same state by the same path.

Widening a column or adding a nullable one costs callers nothing. Narrowing one, or adding a constraint the
stored rows already violate, breaks the migration itself rather than the caller.
