# AI Connector Service

The [Finance Bot](../README.md) system's boundary with the AI provider. It takes a line of user text over gRPC
and puts it to a language model with the Ledger Service's expense proposal tool attached, so the model records
each expense the message names — one call per expense, as the caller whose token arrived with the request.

It holds no state: a call carries its own text, its own closed set of categories, and its own credential.

For C1 (System Context) and C2 (Container) see the [root README](../README.md#architecture); C3 is below.
Package structure is in the
[Architecture & Layering conventions](docs/conventions/architecture.md#package-structure).

### Use Cases

- [Record the spending a user's message names](docs/usecases/extract-intents.md)

### Contracts

- [Ledger Service — intent extraction](docs/contracts/in/intent-extraction.md) (inbound)
- [AI provider — recording spending](docs/contracts/out/ai-provider.md) (outbound)
- [Ledger Service — the expense proposal tool](docs/contracts/out/ledger-mcp.md) (outbound)

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

Container(ledger, "Ledger Service", "Java, Spring Boot", "Calls this service", $tags="callerExternal")
System_Ext(aiProvider, "AI Provider", "OpenAI-compatible chat completions API", $tags="aiExternal")

Container_Boundary(aiConnector, "AI Connector Service (Java, Spring Boot)") {
  Component(grpcService, "Intent Extraction Endpoint", "gRPC endpoint", "Serves the extraction call", $tags="callerExternal")
  Component(tokenInterceptor, "Caller Token Interceptor", "gRPC interceptor", "Holds the call's token for its duration", $tags="callerExternal")
  Component(extractIntentsPort, "Extract Intents Port", "Interface", "Inbound port", $tags="portIn")
  Component(useCase, "Extract Intents Use Case", "Plain Java", "Labels the caller's categories, hands the turn on", $tags="core")
  Component(currency, "Currency Code", "Domain value object", "An ISO 4217 code", $tags="core")

  Component(recordingPort, "Expense Recording Port", "Interface", "Outbound port", $tags="portOut")
  Component(recordingAdapter, "Expense Recording Adapter", "Spring AI ChatClient", "Prompts the model with the ledger's tools attached", $tags="aiExternal")
  Component(toolClient, "Ledger Tool Client", "MCP client", "Calls the tools as the turn's caller", $tags="callerExternal")
}

Rel(ledger, grpcService, "ExtractIntents + token", "gRPC")
Rel_U(grpcService, tokenInterceptor, "Token held by")
Rel_R(grpcService, extractIntentsPort, "Invokes")
Rel_L(useCase, extractIntentsPort, "Implements", $tags="implements")
Rel_D(grpcService, currency, "Validates the assumed currency with")

Rel_R(useCase, recordingPort, "Uses")
Rel_L(recordingAdapter, recordingPort, "Implements", $tags="implements")
Rel_R(recordingAdapter, aiProvider, "Message, categories, tool schema", "HTTPS")
Rel_U(recordingAdapter, toolClient, "Attaches the ledger's tools from")
Rel(toolClient, tokenInterceptor, "Reads the token from")
Rel_L(toolClient, ledger, "create_expense_proposal", "MCP over HTTP")

SHOW_LEGEND()
@enduml
```

## Running Locally

Because gRPC server reflection is enabled, the running service can be explored
without a copy of the schema:

A call carries its categories as a name and its grouping, and is refused without a bearer token — the one the
service calls the ledger back with.

```bash
grpcurl -plaintext localhost:1001 list
grpcurl -plaintext \
  -H 'authorization: Bearer <jwt>' \
  -d '{"text":"spent 15 on lunch","known_categories":[{"name":"Lunch","parent_name":"Food"}],"default_currency":"EUR"}' \
  localhost:1001 bot.finance.ai.v1.IntentExtractionService/ExtractIntents
```
