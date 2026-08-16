# Finance Bot

A Telegram bot that records a user's expenses from a voice message describing a purchase (*"Spent 15 euros on
lunch"*). The system transcribes it, extracts the expense details, and stores them per user.

## MVP Flow

1. A user sends a voice message to the Telegram bot.
2. The **Ledger Service** takes the update from Telegram and downloads the audio file.
3. The **Transcription Service** returns a text transcript of that audio.
4. The **AI Connector Service** extracts the expense details from the transcript, using an AI provider.
5. The Ledger Service saves the extracted expense against the user in the **database**.
6. The Ledger Service replies on Telegram confirming what was recorded.

## Architecture

Diagrams follow the [C4 model](https://c4model.com) and are written in [PlantUML](https://plantuml.com/).

### C1 — System Context

```plantuml
@startuml C1-System-Context
!include <C4/C4_Context>

Person(user, "Telegram User", "Sends voice messages describing expenses")
Person(webUser, "Web User", "Signs in and views their ledger in a browser")
System(financeBot, "Finance Bot", "Transcribes voice messages and extracts structured expense data")
System_Ext(telegram, "Telegram", "Messaging platform; hosts the bot, its audio files and the sign-in widget")
System_Ext(aiProvider, "AI Provider", "LLM used to extract structured expense data from a transcript")

Rel_R(user, telegram, "Sends voice message", "Telegram app")
Rel_L(telegram, user, "Delivers confirmation reply", "Telegram app")

Rel_D(webUser, financeBot, "Signs in and browses", "HTTPS")
Rel_U(webUser, telegram, "Approves the sign-in", "Login Widget")

Rel_L(financeBot, telegram, "Receives updates, downloads audio", "Telegram Bot API")
Rel_L(financeBot, telegram, "Sends confirmation reply", "Telegram Bot API")

Rel_R(financeBot, aiProvider, "Requests expense extraction from transcript", "HTTPS/REST")

SHOW_LEGEND()
@enduml
```

### C2 — Container

```plantuml
@startuml C2-Container
!include <C4/C4_Container>

Person(user, "Telegram User", "Sends voice messages describing expenses")
Person(webUser, "Web User", "Signs in and views their ledger in a browser")
System_Ext(telegram, "Telegram", "Messaging platform; hosts the bot, its audio files and the sign-in widget")
System_Ext(aiProvider, "AI Provider", "LLM used for expense extraction")

System_Boundary(financeBot, "Finance Bot") {
  Container(webApp, "Web App", "TypeScript, React, nginx", "Telegram sign-in; browses the ledger")
  Container(ledger, "Ledger Service", "Java, Spring Boot", "Orchestrates, persists, confirms")
  Container(transcriber, "Transcription Service", "Python, FasterWhisper", "Speech to text")
  Container(aiConnector, "AI Connector Service", "Java, Spring Boot, Spring AI", "Reads a message with the model")
  ContainerDb(ledgerDb, "Ledger Database", "PostgreSQL, wal_level=logical", "Users, expenses; a replication slot")
  ContainerDb(connectorDb, "Connector Database", "PostgreSQL, pgvector", "The messages received, what became of them")
  ContainerQueue(changeStream, "Change Stream", "Redis", "Ledger row changes")
}

Rel(user, telegram, "Voice message", "Telegram app")
Rel_L(telegram, user, "Confirmation", "Telegram app")

Rel_D(webUser, webApp, "Signs in, browses", "HTTPS")
Rel(webApp, telegram, "Sign-in widget", "HTTPS")
Rel_D(webApp, ledger, "Session, expenses, categories", "REST, same origin")

Rel_L(ledger, telegram, "Updates, audio", "Bot API")
Rel_R(ledger, telegram, "Confirmation", "Bot API")

Rel_D(ledger, transcriber, "Audio", "REST")
Rel_L(transcriber, ledger, "Transcript", "REST")
Rel_R(ledger, aiConnector, "Message + token", "gRPC")
Rel_L(aiConnector, ledger, "Tool calls, key set", "MCP, HTTP")
Rel_R(aiConnector, aiProvider, "Extraction", "HTTPS")
Rel_D(aiConnector, connectorDb, "Messages", "JDBC")
Rel_D(ledger, ledgerDb, "Users, expenses", "JDBC")
Rel_U(ledgerDb, ledger, "Row changes", "logical replication")
Rel_D(ledger, changeStream, "Row changes", "RESP")
Rel_R(changeStream, aiConnector, "Row changes", "RESP")

SHOW_LEGEND()
@enduml
```

## Services

| Container             | Stack                           | Responsibility                                   | Docs                                                            | Ports |
|-----------------------|---------------------------------|--------------------------------------------------|-----------------------------------------------------------------|-------|
| Web App               | TypeScript, React, nginx        | Telegram sign-in and browsing the ledger         | [README](web-app/README.md)                                     | 1003  |
| Ledger Service        | Java, Spring Boot               | Orchestration, persistence, Telegram integration | [README](ledger-service/README.md)                              | 1000  |
| Transcription Service | Python, FasterWhisper           | Speech-to-text                                   | -                                                               | -     |
| AI Connector Service  | Java, Spring Boot, Spring AI    | Structured expense extraction from text          | [README](ai-connector-service/README.md)                        | 1001  |
| Ledger Database       | PostgreSQL, `wal_level=logical` | Users and expenses                               | [contract](ledger-service/docs/contracts/out/database.md)       | 5432  |
| Connector Database    | PostgreSQL, pgvector            | The messages received, and what became of them   | [contract](ai-connector-service/docs/contracts/out/database.md) | 5432  |

Container definitions live in [`infrastructure/docker-compose.yaml`](infrastructure/docker-compose.yaml); how the
stack is set up is [`infrastructure/README.md`](infrastructure/README.md).

## Data Model (MVP)

Each recorded expense is linked to a Telegram user and filed under a category, with the amount spent and an
optional currency, inferred from the message when possible. The full shape is
[Expense](ledger-service/docs/domain/expense.md).
