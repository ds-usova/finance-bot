# Act on a user's message

- **In:** the conversation a message belongs to · its text
- **Out:** nothing — the turn either completes or fails
- **Why:** it is the point at which a user's words become spending someone can review

*Implemented by `HandleIncomingMessageUseCase`.*

## Collaborators

| Direction | Collaborator                                                                                                       | Through                                                                                                                   | For                                                                        |
|-----------|--------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------|
| in        | [Telegram](../contracts/in/telegram-updates.md)                                                                    | [Incoming messages](../contracts/in/telegram-updates.md)                                                                  | delivering what a user typed to the bot                                    |
| out       | [Initialize a new user](initialize-a-new-user.md)                                                                  | [Initialize a new user](initialize-a-new-user.md)                                                                         | resolving the person behind the conversation, creating them on first sight |
| out       | [Database](../contracts/out/database.md)                                                                           | [Users, categories, expenses and expense proposals](../contracts/out/database.md)                                         | reading the categories that person may file spending under                 |
| out       | [Extract the intents in a user's message](../../../ai-connector-service/docs/usecases/extract-intents.md)           | [AI Connector Service — intent extraction](../contracts/out/ai-connector.md)                                              | acting on whatever the message asks for, as that person                    |

## Rules

- A conversation is named, and the name is not blank; text is present, and it is not blank.
- The conversation's name is opaque text — whatever the delivering platform calls a conversation; here it is
  Telegram that names it.
- That same name is the identity the person is stored under, so a first message creates them and their
  categories, and every later one finds them.
- What travels is every category the person may file spending under — each with the grouping it sits in. A
  grouping itself never travels.
- A person with no such category has nothing to send, and the turn is refused before the connector is reached.
- No currency is assumed: an amount stated without one is not acted on.
- The connector acts as that person for the length of the turn, on a credential minted per call
  ([ADR 0007](../adr/0007-an-mcp-caller-is-identified-by-a-signed-token-not-a-tool-argument.md)).
- The user's own words are never written to this service's log.
- One message is one turn: nothing is retried, and a failed turn is not replayed.
- The user gets no reply, and nothing is stored on this side of the turn.

## Outcomes

| Outcome           | When                                                        | Result                                                                            |
|-------------------|-------------------------------------------------------------|-------------------------------------------------------------------------------------|
| Message handled   | the person resolves and the connector completes the turn    | an info line names the conversation, and whatever it asked for has been acted on  |
| Message discarded | the message carries no text                                 | nothing happens and the message is not seen again                                 |
| Message rejected  | the message is absent                                       | invalid incoming message — nothing is looked up                                   |
| Request refused   | the person has no category spending can be filed under      | invalid extraction request — the connector is never reached                       |
| Storage failed    | the person cannot be resolved, or their categories not read | the failure reaches the caller                                                    |
| Turn failed       | the connector refuses the turn or cannot be reached         | the failure reaches the caller, naming what came back                             |

A failure of any kind is logged where the message was delivered, and its batch is acknowledged with the rest. A
turn reported as failed does not mean nothing was recorded: the connector may have acted on part of the message
already.

## Components

```plantuml
@startuml C3-Component-ActOnUserMessage
!include <C4/C4_Component>

AddElementTag("telegramExternal", $bgColor="#1c94e0", $fontColor="#ffffff", $borderColor="#125d8c")
AddElementTag("dbExternal", $bgColor="#d68910", $fontColor="#ffffff", $borderColor="#8f5c0a")
AddElementTag("aiExternal", $bgColor="#8e44ad", $fontColor="#ffffff", $borderColor="#5b2c6f")
AddElementTag("portIn", $bgColor="#16a085", $fontColor="#ffffff", $borderColor="#0e6655", $legendText="inbound port (interface)")
AddElementTag("portOut", $bgColor="#7f8c8d", $fontColor="#ffffff", $borderColor="#566573", $legendText="outbound port (interface)")
AddElementTag("core", $bgColor="#2c3e50", $fontColor="#ffffff", $borderColor="#1b2631", $legendText="application core")
AddRelTag("implements", $lineStyle="dashed")

System_Ext(telegram, "Telegram", "Messaging platform; hosts the bot", $tags="telegramExternal")

Container_Boundary(ledger, "Ledger Service (Java, Spring Boot)") {
  Component(telegramListener, "Telegram Update Listener", "Spring Component", "Long-polls the Bot API", $tags="telegramExternal")
  Component(handleMessagePort, "Handle Incoming Message Port", "Interface", "Inbound port", $tags="portIn")
  Component(handleMessageService, "Handle Incoming Message Use Case", "Plain Java", "Resolves the person, then hands the turn over", $tags="core")
  Component(initializeUserPort, "Initialize User Port", "Interface", "Inbound port", $tags="portIn")
  Component(initializeUserService, "Initialize a New User Use Case", "Plain Java", "Finds or creates the person", $tags="core")
  Component(userRepositoryPort, "User Repository Port", "Interface", "Outbound port", $tags="portOut")
  Component(categoryRepositoryPort, "Category Repository Port", "Interface", "Outbound port", $tags="portOut")
  Component(intentExtractionPort, "Intent Extraction Port", "Interface", "Outbound port", $tags="portOut")
  Component(userRepositoryAdapter, "User Repository Adapter", "Spring Data Relational", "Persists users and their categories", $tags="dbExternal")
  Component(categoryRepositoryAdapter, "Category Repository Adapter", "Spring Data Relational", "Reads a user's categories", $tags="dbExternal")
  Component(intentExtractionAdapter, "Intent Extraction Adapter", "gRPC client", "Mints a credential and calls the connector", $tags="aiExternal")
  Component(tokenMinter, "Access Token Minter", "Nimbus JOSE", "Signs a credential naming the person", $tags="aiExternal")
}

ContainerDb(db, "Database", "PostgreSQL", "Stores users and their categories", $tags="dbExternal")
Container(connector, "AI Connector Service", "Java, Spring Boot", "Reads the actions out of a message", $tags="aiExternal")

Rel_R(telegram, telegramListener, "Update (message)", "Telegram Bot API, long polling")
Rel_R(telegramListener, handleMessagePort, "Invokes")
Rel_L(handleMessageService, handleMessagePort, "Implements", $tags="implements")
Rel_R(handleMessageService, initializeUserPort, "Uses")
Rel_L(initializeUserService, initializeUserPort, "Implements", $tags="implements")
Rel_R(initializeUserService, userRepositoryPort, "Uses")
Rel_R(handleMessageService, categoryRepositoryPort, "Uses")
Rel_R(handleMessageService, intentExtractionPort, "Uses")
Rel_L(userRepositoryAdapter, userRepositoryPort, "Implements", $tags="implements")
Rel_L(categoryRepositoryAdapter, categoryRepositoryPort, "Implements", $tags="implements")
Rel_L(intentExtractionAdapter, intentExtractionPort, "Implements", $tags="implements")
Rel_D(intentExtractionAdapter, tokenMinter, "Mints with")
Rel_R(userRepositoryAdapter, db, "SQL", "JDBC")
Rel_R(categoryRepositoryAdapter, db, "SQL", "JDBC")
Rel_R(intentExtractionAdapter, connector, "Text, categories and a credential", "gRPC")

Lay_D(handleMessagePort, initializeUserPort)
Lay_D(initializeUserPort, userRepositoryPort)
Lay_D(userRepositoryPort, categoryRepositoryPort)
Lay_D(categoryRepositoryPort, intentExtractionPort)

SHOW_LEGEND()
@enduml
```

## Flow

```plantuml
@startuml ActOnUserMessage-Sequence
participant "Telegram" as TG
participant "Act on a user's message" as UC
participant "Initialize a new user" as IU
database "Database" as DB
participant "AI Connector Service" as AI

TG -> UC : batch of waiting messages

loop each message in the batch
  alt message carries no text
    UC -> UC : discard the message
  else message carries text
    UC -> IU : the conversation's name as an identity
    IU -> DB : find or create the person
    DB --> IU : the person, with their categories on a first message
    IU --> UC : the person
    UC -> DB : read the categories they may file spending under
    alt the store fails
      DB --> UC : storage failed
    else the categories are read
      UC -> AI : the text, the categories with their groupings, a credential naming the person
      alt the turn does not complete
        AI --> UC : refused, or unreachable
      else the turn completes
        AI --> UC : handled
        UC -> UC : log that the conversation's message was handled
      end
    end
    alt the turn failed
      UC -> UC : log the failure
    end
  end
end

UC --> TG : acknowledge the whole batch
@enduml
```
