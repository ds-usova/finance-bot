# Initialize a new user

- **In:** the external identity a person is known by
- **Out:** the user stored under that identity
- **Why:** a person can record spending from their first message, against a ready set of categories

*Implemented by `InitializeUserUseCase`.*

## The catalogue

A new user is given 20 [groupings](../domain/grouping.md) holding 77 [categories](../domain/category.md).

| Grouping          | Categories                                                                  |
|-------------------|-----------------------------------------------------------------------------|
| Housing           | Rent, Mortgage, HOA, Property Tax, Home Insurance, Repairs, Furniture       |
| Groceries         | Supermarkets, Markets, Household Supplies                                   |
| Dining            | Restaurants, Cafés, Fast Food, Delivery                                     |
| Transportation    | Fuel, Public Transport, Parking, Taxis/Uber, Car Maintenance, Car Insurance |
| Utilities         | Electricity, Gas, Water, Internet, Mobile Phone                             |
| Healthcare        | Doctors, Pharmacy, Dental, Vision, Health Insurance                         |
| Education         | Tuition, Books, Courses, Certifications                                     |
| Shopping          | Clothing, Electronics, Home Goods, Gifts                                    |
| Entertainment     | Movies, Games, Hobbies                                                      |
| Travel            | Hotels, Flights, Vacation, Attractions                                      |
| Pets              | Food, Vet, Grooming                                                         |
| Family & Children | Childcare, School Supplies, Toys                                            |
| Financial         | Taxes, Bank Fees, Loan Payments, Interest                                   |
| Investments       | Brokerage, Retirement, Crypto, Savings Transfers                            |
| Gifts & Donations | Charity, Birthday Gifts, Holidays                                           |
| Work              | Office Supplies, Business Expenses                                          |
| Insurance         | Life, Home, Vehicle, Travel                                                 |
| Personal Care     | Haircuts, Cosmetics, Gym, Spa                                               |
| Subscriptions     | Streaming, Music, Cloud Storage, Apps & Software                            |
| Miscellaneous     | Uncategorized Expenses                                                      |

## Collaborators

| Direction | Collaborator                                          | Through                                                                           | For                                                       |
|-----------|-------------------------------------------------------|-----------------------------------------------------------------------------------|-----------------------------------------------------------|
| in        | [Act on a user's message](handle-incoming-message.md) | [Act on a user's message](handle-incoming-message.md)                             | resolving the person who sent a message, on every message |
| in        | [Web App](../../../web-app/README.md)                 | [The session API](../contracts/in/web-session-api.md)                             | resolving the person signing in, on every sign-in         |
| out       | [Database](../contracts/out/database.md)              | [Users, categories, expenses and expense proposals](../contracts/out/database.md) | storing the user and their catalogue                      |

## Rules

- The external identity is opaque text, whatever the caller identifies a person by, present and not blank.
- What a [user](../domain/user.md) is, and what a [grouping](../domain/grouping.md) holds, are their own rules.
- A category name is unique under one grouping, which is why Travel is both a grouping and a category under
  Insurance ([ADR 0003](../adr/0003-a-category-is-unique-per-user-and-parent-not-per-user.md)).
- The catalogue is fixed at release. Each user gets a copy of it at creation, and it is never re-applied: a
  user who edits their categories diverges from it, and a changed catalogue reaches only users created
  afterwards.
- The catalogue names no brands, so it stays legible when a service is renamed or replaced.
- How long an identity may be is checked where it is stored
  ([ADR 0004](../adr/0004-column-widths-are-checked-in-the-persistence-adapter.md)).
- Two callers initializing the same identity at once both get the same user. One of them creates it; the other
  is given what the first created, and no second set of categories is written.

## Outcomes

| Outcome                | When                                             | Result                                                                     |
|------------------------|--------------------------------------------------|----------------------------------------------------------------------------|
| User created           | nothing is stored under the identity             | the user and the catalogue are stored together, and the creation is logged |
| Existing user returned | a user is already stored under the identity      | that user is returned and nothing is written                               |
| Request rejected       | the request is absent or carries no identity     | invalid user — nothing is looked up                                        |
| Identity too long      | the identity is over 255 characters              | invalid user — nothing is stored                                           |
| Storage failed         | the store cannot be reached or refuses the write | the failure reaches the caller                                             |

## Components

```plantuml
@startuml C3-Component-InitializeUser
!include <C4/C4_Component>

AddElementTag("dbExternal", $bgColor="#d68910", $fontColor="#ffffff", $borderColor="#8f5c0a")
AddElementTag("portIn", $bgColor="#16a085", $fontColor="#ffffff", $borderColor="#0e6655", $legendText="inbound port (interface)")
AddElementTag("portOut", $bgColor="#7f8c8d", $fontColor="#ffffff", $borderColor="#566573", $legendText="outbound port (interface)")
AddElementTag("core", $bgColor="#2c3e50", $fontColor="#ffffff", $borderColor="#1b2631", $legendText="application core")
AddRelTag("implements", $lineStyle="dashed")

ContainerDb(db, "Database", "PostgreSQL", "Stores users and their categories", $tags="dbExternal")

Container_Boundary(ledger, "Ledger Service (Java, Spring Boot)") {
  Component(initializeUserPort, "Initialize User Port", "Interface", "Inbound port", $tags="portIn")
  Component(initializeUserService, "Initialize a New User Use Case", "Plain Java", "Creates a user and their categories", $tags="core")
  Component(userRepositoryPort, "User Repository Port", "Interface", "Outbound port", $tags="portOut")
  Component(userRepositoryAdapter, "User Repository Adapter", "Spring Data Relational", "Persists users and their categories", $tags="dbExternal")
}

Rel_L(initializeUserService, initializeUserPort, "Implements", $tags="implements")
Rel_D(initializeUserService, userRepositoryPort, "Uses")
Rel_L(userRepositoryAdapter, userRepositoryPort, "Implements", $tags="implements")
Rel_R(userRepositoryAdapter, db, "SQL", "JDBC")

SHOW_LEGEND()
@enduml
```

## Flow

```plantuml
@startuml InitializeUser-Sequence
participant "Act on a user's message" as Caller
participant "Ledger Service" as LS
database "Database" as DB

Caller -> LS : an external identity

alt identity is absent
  LS --> Caller : invalid user
else identity is given
  LS -> DB : look the identity up
  alt a user is stored
    DB --> LS : the user
    LS --> Caller : the stored user
  else nothing is stored
    alt identity is too long
      LS --> Caller : invalid user
    else identity fits
      LS -> DB : claim the identity
      alt claimed
        LS -> DB : store the categories
        LS -> LS : log the creation
        LS --> Caller : the created user
      else another caller claimed it first
        DB --> LS : the user they created
        LS --> Caller : that user
      end
    end
  end
end
@enduml
```
