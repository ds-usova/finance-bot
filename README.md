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
System(financeBot, "Finance Bot", "Transcribes voice messages and extracts structured expense data")
System_Ext(telegram, "Telegram", "Messaging platform; hosts the bot and audio files")
System_Ext(aiProvider, "AI Provider", "LLM used to extract structured expense data from a transcript")

Rel_R(user, telegram, "Sends voice message", "Telegram app")
Rel_L(telegram, user, "Delivers confirmation reply", "Telegram app")

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
System_Ext(telegram, "Telegram", "Messaging platform; hosts the bot and audio files")
System_Ext(aiProvider, "AI Provider", "LLM used for expense extraction")

System_Boundary(financeBot, "Finance Bot") {
  Container(ledger, "Ledger Service", "Java, Spring Boot", "Orchestrates expense capture: coordinates transcription and AI extraction, then persists and confirms the result")
  Container(transcriber, "Transcription Service", "Python, FasterWhisper", "Converts voice message audio into a text transcript")
  Container(aiConnector, "AI Connector Service", "REST API", "Extracts structured expense data (category, amount, currency) from a transcript using an AI provider")
  ContainerDb(db, "Database", "PostgreSQL", "Stores users and their recorded expenses")
}

Rel_R(user, telegram, "Sends voice message", "Telegram app")
Rel_L(telegram, user, "Delivers confirmation reply", "Telegram app")

Rel_L(ledger, telegram, "Polls updates, downloads audio", "Telegram Bot API")
Rel_R(ledger, telegram, "Sends confirmation reply", "Telegram Bot API")

Rel_D(ledger, transcriber, "Sends audio", "REST/HTTPS")
Rel_L(transcriber, ledger, "Returns transcript", "REST/HTTPS")
Rel_R(ledger, aiConnector, "Sends transcript", "REST/HTTPS")
Rel_L(aiConnector, ledger, "Returns structured expense data", "REST/HTTPS")
Rel_R(aiConnector, aiProvider, "Requests structured extraction", "HTTPS")
Rel_D(ledger, db, "Reads/writes users and expenses", "JDBC")

SHOW_LEGEND()
@enduml
```

### C3 — Component (Ledger Service)

Every interaction the Ledger Service has with something outside its own boundary
(Telegram, the Transcription Service, the AI Connector Service, the database) is
mediated by an **abstract port (interface)** — never a direct call from the
application core. Inbound ports are driven by the outside world; outbound ports are
driven by the core and implemented by adapters. Adapters are color-coded by the
external dependency they front, so it's clear at a glance which piece talks to what.

```plantuml
@startuml C3-Component-LedgerService
!include <C4/C4_Component>

AddElementTag("telegramExternal", $bgColor="#1c94e0", $fontColor="#ffffff", $borderColor="#125d8c")
AddElementTag("transcriberExternal", $bgColor="#2e8b57", $fontColor="#ffffff", $borderColor="#1c5c3a")
AddElementTag("aiConnectorExternal", $bgColor="#8e44ad", $fontColor="#ffffff", $borderColor="#5f2f74")
AddElementTag("dbExternal", $bgColor="#d68910", $fontColor="#ffffff", $borderColor="#8f5c0a")
AddElementTag("portIn", $bgColor="#16a085", $fontColor="#ffffff", $borderColor="#0e6655", $legendText="inbound port (interface)")
AddElementTag("portOut", $bgColor="#7f8c8d", $fontColor="#ffffff", $borderColor="#566573", $legendText="outbound port (interface)")
AddElementTag("core", $bgColor="#2c3e50", $fontColor="#ffffff", $borderColor="#1b2631", $legendText="application core")
AddRelTag("implements", $lineStyle="dashed")

System_Ext(telegram, "Telegram", "Messaging platform; hosts the bot and audio files", $tags="telegramExternal")
Container(transcriber, "Transcription Service", "Python, FasterWhisper", "Converts audio into a text transcript", $tags="transcriberExternal")
Container(aiConnector, "AI Connector Service", "REST API", "Extracts structured expense data from a transcript", $tags="aiConnectorExternal")
ContainerDb(db, "Database", "PostgreSQL", "Stores users and expenses", $tags="dbExternal")

Container_Boundary(ledger, "Ledger Service (Java, Spring Boot)") {
  Component(telegramListener, "Telegram Update Listener", "Spring Component", "Primary adapter: receives incoming Telegram updates", $tags="telegramExternal")
  Component(recordExpensePort, "Record Expense Port", "Interface", "Inbound port: use-case boundary for recording an expense from a voice message", $tags="portIn")
  Component(expenseService, "Expense Recording Service", "Spring Service", "Application core: orchestrates the pipeline and applies business logic", $tags="core")

  Component(audioFetchPort, "Audio Fetch Port", "Interface", "Outbound port: retrieves voice message audio", $tags="portOut")
  Component(transcriptionPort, "Transcription Port", "Interface", "Outbound port: converts audio into text", $tags="portOut")
  Component(extractionPort, "Expense Extraction Port", "Interface", "Outbound port: extracts structured expense data from text", $tags="portOut")
  Component(repositoryPort, "Expense Repository Port", "Interface", "Outbound port: persists users and expenses", $tags="portOut")
  Component(notificationPort, "Notification Port", "Interface", "Outbound port: notifies the user of the recorded expense", $tags="portOut")

  Component(telegramFileAdapter, "Telegram File Adapter", "Spring Component", "Implements the Audio Fetch Port via the Telegram Bot API", $tags="telegramExternal")
  Component(transcriptionAdapter, "Transcription Adapter", "Spring REST Client", "Implements the Transcription Port via REST", $tags="transcriberExternal")
  Component(aiConnectorAdapter, "AI Connector Adapter", "Spring REST Client", "Implements the Expense Extraction Port via REST", $tags="aiConnectorExternal")
  Component(repositoryAdapter, "Expense Repository Adapter", "Spring Data Relational", "Implements the Expense Repository Port", $tags="dbExternal")
  Component(telegramNotifierAdapter, "Telegram Notifier Adapter", "Spring Component", "Implements the Notification Port via the Telegram Bot API", $tags="telegramExternal")
}

Rel(telegram, telegramListener, "Update (voice message)", "Telegram Bot API")
Rel_R(telegramListener, recordExpensePort, "Invokes")
Rel_L(expenseService, recordExpensePort, "Implements", $tags="implements")

Rel_U(expenseService, audioFetchPort, "Uses")
Rel_R(telegramFileAdapter, audioFetchPort, "Implements", $tags="implements")
Rel_U(telegramFileAdapter, telegram, "Downloads audio file", "Telegram Bot API")

Rel_R(expenseService, transcriptionPort, "Uses")
Rel_L(transcriptionAdapter, transcriptionPort, "Implements", $tags="implements")
Rel_R(transcriptionAdapter, transcriber, "Audio bytes", "REST/HTTPS")

Lay_D(transcriptionPort, extractionPort)
Rel_R(expenseService, extractionPort, "Uses")
Rel_L(aiConnectorAdapter, extractionPort, "Implements", $tags="implements")
Rel_R(aiConnectorAdapter, aiConnector, "Transcript text", "REST/HTTPS")

Lay_D(repositoryPort, transcriptionPort)
Rel_R(expenseService, repositoryPort, "Uses")
Rel_L(repositoryAdapter, repositoryPort, "Implements", $tags="implements")
Rel_R(repositoryAdapter, db, "SQL", "JDBC")

Rel_D(expenseService, notificationPort, "Uses")
Rel_R(telegramNotifierAdapter, notificationPort, "Implements", $tags="implements")
Rel_R(telegramNotifierAdapter, telegram, "Sends message", "Telegram Bot API")

SHOW_LEGEND()
@enduml
```

## Tech Stack

| Container             | Stack                 | Responsibility                                   |
|-----------------------|-----------------------|--------------------------------------------------|
| Ledger Service        | Java, Spring Boot     | Orchestration, persistence, Telegram integration |
| Transcription Service | Python, FasterWhisper | Speech-to-text                                   |
| AI Connector Service  | Java, Spring AI       | Structured expense extraction from text          |
| Database              | PostgreSQL            | Stores users and expenses                        |

## Data Model (MVP)

Each recorded expense is linked to a Telegram user and includes:

- **category** — e.g. food, transport, entertainment
- **amount** — the numeric value spent
- **currency** *(optional)* — inferred from the message when possible
