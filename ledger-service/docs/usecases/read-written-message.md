# Read what became of a filed text

- **In**
  - the identity of the authenticated caller
  - the id filing the text answered
- **Out**
  - the [text](../domain/written-message.md) as it now stands
  - once its reading is done, the UTC days the entries it produced were stored on, pending or since accepted
- **Why:** the page learns when a filed text is read, what it came to, and which days to open

*Implemented by `ReadWrittenMessageUseCase`.*

## Prerequisites

- The caller is authenticated. The request never names whose text it is.
- A user is stored under that identity.

## Collaborators

| Direction | Collaborator                                                                     | Through                                                                 | For                                                                    |
|-----------|----------------------------------------------------------------------------------|-------------------------------------------------------------------------|------------------------------------------------------------------------|
| in        | [File expenses as text](../../../web-app/docs/usecases/file-expenses-as-text.md) | [Browsing the ledger from a browser](../contracts/in/web-browse-api.md) | following a filed text until its reading ends                          |
| out       | [Database](../contracts/out/database.md)                                         | [Users, categories and expenses](../contracts/out/database.md)          | resolving the identity, reading the text, and the days of its entries  |

## Outcomes

| Outcome          | When                                                       | Result                                                     |
|------------------|------------------------------------------------------------|------------------------------------------------------------|
| Still reading    | the caller's text is `WAITING` or `READING`                | the text, with no days, and no entry is read               |
| Reading done     | the caller's text is `DONE`                                | the text, what it came to, and its days, oldest first      |
| Nothing stored   | the text is `DONE` and no entry carries its id             | the text, what it came to, and no days                     |
| Text not found   | the id names another person's text, or nothing at all      | the request is rejected the same way for both              |
| Id unusable      | the id is below `1`                                        | the request is rejected naming `id`, and nothing is read   |
| Identity unknown | nothing is stored under the identity                       | the request is rejected, and no text is looked for         |
| Request rejected | the request carries no identity                            | no browser session is open, and nothing is looked up       |
| Storage failed   | the store cannot be reached                                | the failure reaches the caller                             |

## Components

```plantuml
@startuml C3-Component-ReadWrittenMessage
!include <C4/C4_Component>

Person(browser, "Signed-in person", "The expenses page")

Container_Boundary(inboundWeb, "adapter (inbound) — Browser") {
  Component(endpoint, "Messages endpoint", "Spring MVC", "Reads the caller off the session")
  Component(mapper, "Written message mapper", "Plain Java", "Reads the request, writes the answer")
}
Container_Boundary(application, "application — usecases and ports") {
  Component(useCase, "Read what became of a filed text", "use case")
  Component(userPort, "User repository", "outbound port")
  Component(writtenPort, "Written message repository", "outbound port")
  Component(expensePort, "Expense repository", "outbound port")
}
Container_Boundary(outboundDb, "adapter (outbound) — Postgres") {
  Component(userAdapter, "User repository adapter", "Spring Data JDBC")
  Component(writtenAdapter, "Written message repository adapter", "Spring Data JDBC")
  Component(expenseAdapter, "Expense repository adapter", "Spring Data JDBC")
}
ContainerDb(db, "Database", "PostgreSQL")

Rel_D(browser, endpoint, "Asks for a text by id", "HTTP, session cookie")
Rel_R(endpoint, mapper, "Maps with")
Rel_D(endpoint, useCase, "Reads through")
Rel_D(useCase, userPort, "Resolves the caller")
Rel_D(useCase, writtenPort, "Finds their text")
Rel_D(useCase, expensePort, "Reads the days")
Rel_U(userAdapter, userPort, "Implements")
Rel_U(writtenAdapter, writtenPort, "Implements")
Rel_U(expenseAdapter, expensePort, "Implements")
Rel_D(userAdapter, db, "SQL")
Rel_D(writtenAdapter, db, "SQL")
Rel_D(expenseAdapter, db, "SQL")

Lay_D(writtenPort, writtenAdapter)
@enduml
```

## Flow

```plantuml
@startuml ReadWrittenMessage-Sequence
participant "Browser" as Browser
participant "Ledger Service" as LS
database "Database" as DB

Browser -> LS : read a text by id
alt no identity, or the id is unusable
  LS --> Browser : rejected
else the request is usable
  LS -> DB : read the person under that identity
  alt nothing is stored
    DB --> LS : no such person
    LS --> Browser : the caller is unknown
  else a person is stored
    LS -> DB : read their text under that id
    alt none of theirs
      DB --> LS : nothing
      LS --> Browser : not found
    else not yet DONE
      DB --> LS : the text
      LS --> Browser : the text, no days
    else DONE
      DB --> LS : the text and its outcome
      LS -> DB : the UTC days of the entries under web:<id>
      DB --> LS : the days, oldest first
      LS --> Browser : the text, its outcome and its days
    end
  end
end
@enduml
```

## References

- [ADR 0015: A turn is named by the message that started it](../adr/0015-a-turn-is-named-by-the-message-that-started-it-not-by-a-value-minted-beside-it.md) —
  why the entries are found by the text's own id
- [Act on a text filed from the web](handle-written-message.md) — what each outcome means
- [File a text for reading](file-written-message.md) — where the id comes from
