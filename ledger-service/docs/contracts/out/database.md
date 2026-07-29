# Database — users and their categories (SQL)

Everything the service remembers. A user is stored under the identity the delivering platform knows them by, and
the categories they file spending under hang off that user.

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

app_user ||--o{ category
category ||--o{ category
@enduml
```

Indexes beyond the constraints above:

- `uq_category_user_parent_name` on `(user_id, parent_id, name)`, **`NULLS NOT DISTINCT`**.

## Operations

| Operation               | Purpose                                          | Used by                                                          |
|-------------------------|--------------------------------------------------|------------------------------------------------------------------|
| Find a user by identity | reads the user stored under an external identity | [Initialize a new user](../../usecases/initialize-a-new-user.md) |
| Create a user           | stores a user and their categories together      | [Initialize a new user](../../usecases/initialize-a-new-user.md) |

## Compatibility

Migrations are append-only. An applied migration is never edited — a change is a new one, so every database
reaches the same state by the same path.

Widening a column or adding a nullable one costs callers nothing. Narrowing one, or adding a constraint the
stored rows already violate, breaks the migration itself rather than the caller.
