# AI Connector Service

The [Finance Bot](../README.md) system's boundary with the AI provider. It takes a line of user text over gRPC,
reads the **intents** it expresses — what the user is acting on, what they want done, and the details — and acts
on each expense the message asks to record, by calling the Ledger Service's expense proposal tool as the caller
whose token arrived with the request.

It holds no state: a call carries its own text, its own closed set of categories, and its own credential.

For C1 (System Context) and C2 (Container) see the [root README](../README.md#architecture); C3 is below.
Package structure is in the
[Architecture & Layering conventions](docs/conventions/architecture.md#package-structure).

### Use Cases

- [Act on the actions in a user's message](docs/usecases/extract-intents.md)

### Contracts

- [Ledger Service — intent extraction](docs/contracts/in/intent-extraction.md) (inbound)
- [AI provider — intent inference](docs/contracts/out/ai-provider.md) (outbound)
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
  Component(grpcService, "Intent Extraction gRPC Service", "@GrpcService", "Serves the ExtractIntents RPC", $tags="callerExternal")
  Component(tokenInterceptor, "Caller Token Interceptor", "ServerInterceptor", "Holds the call's token for its duration", $tags="callerExternal")
  Component(extractIntentsPort, "Extract Intents Port", "Interface", "Inbound port", $tags="portIn")
  Component(useCase, "Extract Intents Use Case", "Plain Java", "Assembles intents, then acts on each expense", $tags="core")
  Component(intent, "Intent / Money", "Domain value objects", "Intent hierarchy, money", $tags="core")

  Component(inferencePort, "Intent Inference Port", "Interface", "Outbound port", $tags="portOut")
  Component(proposalPort, "Expense Proposal Port", "Interface", "Outbound port", $tags="portOut")
  Component(aiAdapter, "AI Intent Inference Adapter", "Spring AI ChatClient", "Prompts the model, returns raw answers", $tags="aiExternal")
  Component(mcpAdapter, "MCP Expense Proposal Adapter", "Spring AI MCP client", "Calls the tool as the caller", $tags="callerExternal")
}

Rel(ledger, grpcService, "ExtractIntents + token", "gRPC")
Rel_D(grpcService, tokenInterceptor, "Token held by")
Rel_R(grpcService, extractIntentsPort, "Invokes")
Rel_L(useCase, extractIntentsPort, "Implements", $tags="implements")
Rel_D(useCase, intent, "Assembles")

Rel_R(useCase, inferencePort, "Uses")
Rel_R(useCase, proposalPort, "Uses")
Rel_L(aiAdapter, inferencePort, "Implements", $tags="implements")
Rel_L(mcpAdapter, proposalPort, "Implements", $tags="implements")
Rel_R(aiAdapter, aiProvider, "Prompt + JSON schema", "HTTPS")
Rel_D(mcpAdapter, tokenInterceptor, "Reads the token from")
Rel_L(mcpAdapter, ledger, "create_expense_proposal", "MCP over HTTP")

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
