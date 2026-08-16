# AI Connector Service

The [Finance Bot](../README.md) system's boundary with the AI provider. It takes a line of user text over gRPC
and puts it to a language model with the Ledger Service's tools attached, so the model records each expense the
message names, as the caller whose token arrived with the request.

What the service keeps of a call is the message itself, stored under the person and the message the caller's
token names, for as long as `MEMORY_MAX_AGE` allows.

For C1 (System Context) and C2 (Container) see the [root README](../README.md#architecture); C3 is below.
Package structure is in the
[Architecture & Layering conventions](docs/conventions/architecture.md#package-structure).

### Use Cases

- [Record the spending a user's message names](docs/usecases/extract-intents.md)
- [Purge messages past their retention](docs/usecases/purge-messages.md)

### Contracts

- [Ledger Service — intent extraction](docs/contracts/in/intent-extraction.md) (inbound)
- [AI provider — recording spending](docs/contracts/out/ai-provider.md) (outbound)
- [Ledger Service — the ledger's tools and its key set](docs/contracts/out/ledger-mcp.md) (outbound)
- [The connector's database](docs/contracts/out/database.md) (outbound)

### Running It

- [Configuration](docs/configuration.md) — the environment variables a deployment supplies.

### C3 — Component

```plantuml
@startuml C3-Component-AiConnectorService
!include <C4/C4_Component>

AddElementTag("callerExternal", $bgColor="#1c94e0", $fontColor="#ffffff", $borderColor="#125d8c")
AddElementTag("aiExternal", $bgColor="#8e44ad", $fontColor="#ffffff", $borderColor="#5f2f74")
AddElementTag("portIn", $bgColor="#16a085", $fontColor="#ffffff", $borderColor="#0e6655", $legendText="inbound port (interface)")
AddElementTag("portOut", $bgColor="#7f8c8d", $fontColor="#ffffff", $borderColor="#566573", $legendText="outbound port (interface)")
AddElementTag("core", $bgColor="#2c3e50", $fontColor="#ffffff", $borderColor="#1b2631", $legendText="application core")
AddRelTag("implements", $lineStyle="dashed")

Container(ledger, "Ledger Service", "Java, Spring Boot", "Calls this service, publishes its signing key", $tags="callerExternal")
System_Ext(aiProvider, "AI Provider", "OpenAI-compatible chat completions API", $tags="aiExternal")
ContainerDb(store, "finance_ai", "PostgreSQL", "The messages received")

Container_Boundary(aiConnector, "AI Connector Service (Java, Spring Boot)") {
  Component(grpcService, "Intent Extraction Endpoint", "gRPC endpoint", "Serves the extraction call", $tags="callerExternal")
  Component(tokenInterceptor, "Caller Token Interceptor", "gRPC interceptor", "Verifies the call's token and holds it for its duration", $tags="callerExternal")
  Component(tokenVerifier, "Caller Token Verifier", "JWT decoder", "Checks the token against the ledger's key set, reads the person and the message", $tags="callerExternal")
  Component(extractIntentsPort, "Extract Intents Port", "Interface", "Inbound port", $tags="portIn")
  Component(useCase, "Extract Intents Use Case", "Plain Java", "Registers the message, then hands the turn on", $tags="core")
  Component(currency, "Currency Code", "Domain value object", "An ISO 4217 code", $tags="core")

  Component(recordingPort, "Expense Recording Port", "Interface", "Outbound port", $tags="portOut")
  Component(recordingAdapter, "Expense Recording Adapter", "Spring AI ChatClient", "Prompts the model with the ledger's tools attached", $tags="aiExternal")
  Component(toolClient, "Ledger Tool Client", "MCP client", "Calls the tools as the turn's caller", $tags="callerExternal")
  Component(storePort, "Message Store Port", "Interface", "Outbound port", $tags="portOut")
  Component(storeAdapter, "Message Store Adapter", "Spring Data JDBC", "Keeps each message under its person and message id", $tags="core")
}

Rel(ledger, grpcService, "ExtractIntents + token", "gRPC")
Rel_U(grpcService, tokenInterceptor, "Token held by")
Rel_R(tokenInterceptor, tokenVerifier, "Verifies through")
Rel_U(tokenVerifier, ledger, "GET /.well-known/jwks.json", "HTTP")
Rel_R(grpcService, extractIntentsPort, "Invokes")
Rel_L(useCase, extractIntentsPort, "Implements", $tags="implements")
Rel_D(grpcService, currency, "Validates the assumed currency with")

Rel_R(useCase, recordingPort, "Uses")
Rel_L(recordingAdapter, recordingPort, "Implements", $tags="implements")
Rel_R(recordingAdapter, aiProvider, "Message, groupings, tool schemas", "HTTPS")
Rel_U(recordingAdapter, toolClient, "Attaches the ledger's tools from")
Rel(toolClient, tokenInterceptor, "Reads the token from")
Rel_L(toolClient, ledger, "list_categories, create_expense_proposal", "MCP over HTTP")
Rel_D(useCase, storePort, "Registers through")
Rel_U(storeAdapter, storePort, "Implements", $tags="implements")
Rel_D(storeAdapter, store, "INSERT, DELETE", "JDBC")

SHOW_LEGEND()
@enduml
```

## Running Locally

Because gRPC server reflection is enabled, the running service can be explored without a copy of the schema:

```bash
grpcurl -plaintext localhost:1001 list
grpcurl -plaintext \
  -H 'authorization: Bearer <jwt>' \
  -d '{"text":"spent 15 on lunch","category_groupings":["Dining","Miscellaneous"],"catch_all_grouping":"Miscellaneous","current_date":"2026-08-05","default_currency":"EUR"}' \
  localhost:1001 bot.finance.ai.v1.IntentExtractionService/ExtractIntents
```
