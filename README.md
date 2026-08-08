# Finance Bot

A Telegram bot that lets users record their expenses by simply speaking. A user sends a
voice message describing a purchase (e.g. *"Spent 15 euros on lunch"*); the system
transcribes it, extracts the expense details, and stores them per user.

## MVP Flow

1. A user sends a voice message to the Telegram bot.
2. The **Ledger Service** receives the update from Telegram and downloads the audio file.
3. The audio is sent to the **Transcription Service**, which returns a text transcript.
4. The transcript is sent to the **AI Connector Service**, which uses an AI provider to
   extract structured expense data: category, amount, and (optionally) currency.
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
  Container(webApp, "Web App", "TypeScript, React, nginx", "Signs a user in with Telegram and lists the expenses and proposals the ledger holds for them")
  Container(ledger, "Ledger Service", "Java, Spring Boot", "Orchestrates expense capture: coordinates transcription and AI extraction, then persists and confirms the result")
  Container(transcriber, "Transcription Service", "Python, FasterWhisper", "Converts voice message audio into a text transcript")
  Container(aiConnector, "AI Connector Service", "Java, Spring Boot, Spring AI", "Extracts structured expense data (category, amount, currency) from a transcript using an AI provider")
  ContainerDb(db, "Database", "PostgreSQL", "Stores users and their recorded expenses")
}

Rel(user, telegram, "Sends voice message", "Telegram app")
Rel_L(telegram, user, "Delivers confirmation reply", "Telegram app")

Rel_D(webUser, webApp, "Signs in and browses", "HTTPS")
Rel(webApp, telegram, "Embeds the sign-in widget", "HTTPS")
Rel_D(webApp, ledger, "Holds a browser session, and reads the expenses and categories behind it", "REST/HTTPS, same origin")

Rel_L(ledger, telegram, "Polls updates, downloads audio", "Telegram Bot API")
Rel_R(ledger, telegram, "Sends confirmation reply", "Telegram Bot API")

Rel_D(ledger, transcriber, "Sends audio", "REST/HTTPS")
Rel_L(transcriber, ledger, "Returns transcript", "REST/HTTPS")
Rel_R(ledger, aiConnector, "Sends text, the user's category groupings and today's date, with a token", "gRPC")
Rel_L(aiConnector, ledger, "Records expense proposals and asks for spending summaries, as the token's subject", "MCP over HTTP")
Rel_R(aiConnector, aiProvider, "Requests structured extraction", "HTTPS")
Rel_D(ledger, db, "Reads/writes users and expenses", "JDBC")

SHOW_LEGEND()
@enduml
```

## Services

| Container             | Stack                        | Responsibility                                   | Docs                                     | Ports |
|-----------------------|------------------------------|--------------------------------------------------|------------------------------------------|-------|
| Web App               | TypeScript, React, nginx     | Telegram sign-in and browsing the ledger         | [README](web-app/README.md)              | 1003  |
| Ledger Service        | Java, Spring Boot            | Orchestration, persistence, Telegram integration | [README](ledger-service/README.md)       | 1000  |
| Transcription Service | Python, FasterWhisper        | Speech-to-text                                   | -                                        | -     |
| AI Connector Service  | Java, Spring Boot, Spring AI | Structured expense extraction from text          | [README](ai-connector-service/README.md) | 1001  |
| Database              | PostgreSQL                   | Stores users and expenses                        | -                                        | 5432  |

Container definitions and port mappings live in
[`infrastructure/docker-compose.yaml`](infrastructure/docker-compose.yaml).

## Data Model (MVP)

Each recorded expense is linked to a Telegram user and includes:

- **category** — e.g. food, transport, entertainment
- **amount** — the numeric value spent
- **currency** *(optional)* — inferred from the message when possible
