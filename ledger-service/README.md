# Ledger Service

The orchestration core of the [Finance Bot](../README.md) system. It receives
Telegram updates, coordinates transcription and AI-driven expense extraction,
persists the result, and confirms it back to the user.

For C1 (System Context) and C2 (Container) see the [root README](../README.md#architecture);
C3 is below. Package structure is in the
[Architecture & Layering conventions](docs/conventions/architecture.md#package-structure).

### Use Cases

- [Receive a user's message](docs/usecases/handle-incoming-message.md)
- [Initialize a new user](docs/usecases/initialize-a-new-user.md)

### Contracts

- [Telegram — incoming messages](docs/contracts/in/telegram-updates.md) (inbound)

### Running It

- [Configuration](docs/configuration.md) — the environment variables a deployment supplies.

### C3 — Component

The expense pipeline, the service's primary use case; each [use case](#use-cases)
page carries its own C3. Every interaction with something outside the service
boundary goes through a port, never a direct call from the core. Adapters are
colour-coded by the external dependency they front.

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
Container(aiConnector, "AI Connector Service", "gRPC API", "Extracts structured expense data from a transcript", $tags="aiConnectorExternal")
ContainerDb(db, "Database", "PostgreSQL", "Stores users and expenses", $tags="dbExternal")

Container_Boundary(ledger, "Ledger Service (Java, Spring Boot)") {
  Component(telegramListener, "Telegram Update Listener", "Spring Component", "Long-polls the Bot API", $tags="telegramExternal")
  Component(handleMessagePort, "Handle Incoming Message Port", "Interface", "Inbound port", $tags="portIn")
  Component(expenseService, "Expense Recording Use Case", "Plain Java", "Orchestrates the pipeline", $tags="core")

  Component(audioFetchPort, "Audio Fetch Port", "Interface", "Outbound port", $tags="portOut")
  Component(transcriptionPort, "Transcription Port", "Interface", "Outbound port", $tags="portOut")
  Component(extractionPort, "Intent Extraction Port", "Interface", "Outbound port", $tags="portOut")
  Component(repositoryPort, "Expense Repository Port", "Interface", "Outbound port", $tags="portOut")
  Component(notificationPort, "Notification Port", "Interface", "Outbound port", $tags="portOut")

  Component(telegramFileAdapter, "Telegram File Adapter", "Spring Component", "Downloads audio", $tags="telegramExternal")
  Component(transcriptionAdapter, "Transcription Adapter", "Spring REST Client", "Calls the transcriber", $tags="transcriberExternal")
  Component(aiConnectorAdapter, "AI Connector Intent Extraction Adapter", "gRPC Client", "Calls the AI connector", $tags="aiConnectorExternal")
  Component(repositoryAdapter, "Expense Repository Adapter", "Spring Data Relational", "Persists expenses", $tags="dbExternal")
  Component(telegramNotifierAdapter, "Telegram Notifier Adapter", "Spring Component", "Sends the confirmation", $tags="telegramExternal")
}

Rel(telegram, telegramListener, "Update (message)", "Telegram Bot API, long polling")
Rel_R(telegramListener, handleMessagePort, "Invokes")
Rel_L(expenseService, handleMessagePort, "Implements", $tags="implements")

Rel_U(expenseService, audioFetchPort, "Uses")
Rel_R(telegramFileAdapter, audioFetchPort, "Implements", $tags="implements")
Rel_U(telegramFileAdapter, telegram, "Downloads audio file", "Telegram Bot API")

Rel_R(expenseService, transcriptionPort, "Uses")
Rel_L(transcriptionAdapter, transcriptionPort, "Implements", $tags="implements")
Rel_R(transcriptionAdapter, transcriber, "Audio bytes", "REST/HTTPS")

Lay_D(transcriptionPort, extractionPort)
Rel_R(expenseService, extractionPort, "Uses")
Rel_L(aiConnectorAdapter, extractionPort, "Implements", $tags="implements")
Rel_R(aiConnectorAdapter, aiConnector, "Transcript text", "gRPC")

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
