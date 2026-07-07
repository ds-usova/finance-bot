# Ledger Service

The Ledger Service is the orchestration core of the [Finance Bot](../README.md)
system. It receives Telegram updates, coordinates transcription and AI-driven
expense extraction, persists the result, and confirms it back to the user.

Diagrams follow the [C4 model](https://c4model.com) and are written in
[PlantUML](https://plantuml.com/). 

For C1 (System Context) and C2 (Container),
see the [root README](../README.md#architecture). C3 below zooms into this
service specifically.

## Package Structure

Code is organized by Clean Architecture layer, under `bot.finance`:

```
bot.finance
├── domain          # enterprise business rules
│   ├── model       # entities with identity, e.g. Expense, User
│   ├── value       # value objects
│   └── exception
├── application     # application business rules
│   ├── usecase
│   ├── port        # inbound/outbound port interfaces
│   └── dto
└── adapter         # interface adapters
    ├── web
    └── persistence
```

### C3 — Component

Every interaction the Ledger Service has with something outside its own boundary
(Telegram, the Transcription Service, the AI Connector Service, the database) is
mediated by an **abstract port (interface)** — never a direct call from the
application core. 

Inbound ports are driven by the outside world; outbound ports are
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
