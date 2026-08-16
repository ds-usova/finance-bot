# Record the spending a user's message names

- **In:**
  - the user's text
  - the groupings their categories are filed under
  - the grouping to fall back on
  - the day the turn runs on
  - an assumed currency (optional)
  - the caller's token
- **Out:** nothing
- **Why:** a user records spending, and asks what they spent, by writing a sentence instead of filling a form

*Implemented by `ExtractIntentsUseCase`.*

## Prerequisites

- The call carries a caller token.
- The token verifies against the ledger's published key set and names the person and the message it acts for.
- The ledger serves the tools the model calls.

## Collaborators

| Direction | Collaborator                                                                                                | Through                                                   | For                                             |
|-----------|-------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------|-------------------------------------------------|
| in        | [Ledger Service](../../../ledger-service/docs/usecases/handle-incoming-message.md)                          | [Intent extraction](../contracts/in/intent-extraction.md) | acting on what a user typed, as that user       |
| out       | [Ledger key set](../../../ledger-service/docs/contracts/in/mcp.md#how-a-caller-authenticates)               | [Ledger tools](../contracts/out/ledger-mcp.md)            | reading who the token acts for                  |
| out       | [Message store](../contracts/out/database.md)                                                               | [Database](../contracts/out/database.md)                  | keeping the message the caller handed over      |
| out       | [AI Provider](../contracts/out/ai-provider.md)                                                              | [Chat completions](../contracts/out/ai-provider.md)       | reading the message and deciding what to do     |
| out       | [Category Lookup Tool](../../../ledger-service/docs/usecases/list-categories.md)                            | [Ledger tools](../contracts/out/ledger-mcp.md)            | learning which categories a grouping holds      |
| out       | [Expense Proposal Tool](../../../ledger-service/docs/usecases/create-an-expense-proposal.md)                | [Ledger tools](../contracts/out/ledger-mcp.md)            | recording one expense                           |
| out       | [Spending Summary Tool](../../../ledger-service/docs/usecases/summarize-spending.md)                        | [Ledger tools](../contracts/out/ledger-mcp.md)            | asking for the totals over a period             |

## Outcomes

| Outcome                 | When                                                                              | Result                                                                               |
|-------------------------|-----------------------------------------------------------------------------------|--------------------------------------------------------------------------------------|
| Turn acted on           | the model finished the turn                                                       | an empty answer                                                                      |
| Message kept            | the token names a person and a message                                            | the message is stored under that pair before the model is called                     |
| Message already known   | the same person and message arrive a second time                                  | the first row and its text stand; the turn runs again                                |
| Message not kept        | the store refuses the write                                                       | an empty answer; the turn runs as if it had been kept                                |
| Nothing recorded        | the message names no spending                                                     | an empty answer                                                                      |
| Summary asked for       | the message asks what was spent over a period                                     | an empty answer                                                                      |
| Expense left unrecorded | the ledger refused it and its one corrected retry, or the message under-said it   | an empty answer; the rest of the message still recorded                              |
| Provider unavailable    | the provider is unreachable or errors                                             | the turn stops; the caller is told the service is unavailable                        |
| Ledger unreachable      | a tool cannot be reached, or the token is refused there                           | the turn stops; the caller is told the service is unavailable                        |
| Request unusable        | a required field is missing or unreadable                                         | refused before the provider is called                                                |
| Caller not identified   | no token arrives, or the token does not verify, or it names no person and message | refused before the provider is called                                                |
| Identity uncheckable    | the ledger's key set cannot be read within `LEDGER_JWKS_TIMEOUT`                  | refused before the provider is called; the caller is told the service is unavailable |

## Components

```plantuml
@startuml C3-Component-ExtractIntents
!include <C4/C4_Component>

AddElementTag("callerExternal", $bgColor="#1c94e0", $fontColor="#ffffff", $borderColor="#125d8c")
AddElementTag("aiExternal", $bgColor="#8e44ad", $fontColor="#ffffff", $borderColor="#5f2f74")
AddElementTag("storeExternal", $bgColor="#b9770e", $fontColor="#ffffff", $borderColor="#7d5109")
AddElementTag("portIn", $bgColor="#16a085", $fontColor="#ffffff", $borderColor="#0e6655", $legendText="inbound port (interface)")
AddElementTag("portOut", $bgColor="#7f8c8d", $fontColor="#ffffff", $borderColor="#566573", $legendText="outbound port (interface)")
AddElementTag("core", $bgColor="#2c3e50", $fontColor="#ffffff", $borderColor="#1b2631", $legendText="application core")

AddRelTag("implements", $lineStyle="dashed")

Container(ledger, "Ledger Service", "Java, Spring Boot", "Calls this service, publishes its key set, and is called back", $tags="callerExternal")
System_Ext(aiProvider, "AI Provider", "OpenAI-compatible chat completions API", $tags="aiExternal")
ContainerDb(database, "Message Store", "PostgreSQL", "The messages this service was handed", $tags="storeExternal")

Container_Boundary(aiConnector, "AI Connector Service (Java, Spring Boot)") {
  Component(tokenInterceptor, "Caller Token Interceptor", "gRPC interceptor", "Refuses an untokened or unverified call, holds the identity for the turn", $tags="callerExternal")
  Component(tokenVerifier, "Caller Token Verifier", "Token reader", "Verifies the token and reads the person and the message it names", $tags="callerExternal")
  Component(grpcService, "Intent Extraction Endpoint", "gRPC endpoint", "Serves the extraction call, validates the request", $tags="callerExternal")
  Component(extractIntentsPort, "Extract Intents Port", "Interface", "Inbound port", $tags="portIn")
  Component(useCase, "Extract Intents Use Case", "Plain Java", "Registers the message, hands the groupings, the day and the turn on", $tags="core")
  Component(currency, "Currency Code", "Domain value object", "An ISO 4217 code", $tags="core")

  Component(storePort, "Message Store Port", "Interface", "Outbound port", $tags="portOut")
  Component(storeAdapter, "Message Store Adapter", "Spring Data JDBC", "Writes the message under the person and the message it names", $tags="storeExternal")
  Component(recordingPort, "Expense Recording Port", "Interface", "Outbound port", $tags="portOut")
  Component(recordingAdapter, "Expense Recording Adapter", "Spring AI ChatClient", "Prompts the model with the ledger's tools attached", $tags="aiExternal")
  Component(toolClient, "Ledger Tool Client", "MCP client", "Lists the tools, calls them as the turn's caller", $tags="callerExternal")
  Component(failurePolicy, "Tool Failure Policy", "Plain Java", "Splits a refusal the model reads from a failure that ends the turn", $tags="callerExternal")
}

Rel_R(ledger, tokenInterceptor, "ExtractIntents + token", "gRPC")
Rel_D(tokenInterceptor, tokenVerifier, "Reads the token through")
Rel_L(tokenVerifier, ledger, "The published signing keys", "HTTPS")
Rel_R(tokenInterceptor, grpcService, "Passes the call on")
Rel_R(grpcService, extractIntentsPort, "Invokes")
Rel_D(grpcService, currency, "Validates the assumed currency with")
Rel_L(useCase, extractIntentsPort, "Implements", $tags="implements")
Rel_D(useCase, storePort, "Registers the message through")
Rel_U(storeAdapter, storePort, "Implements", $tags="implements")
Rel_R(storeAdapter, database, "The message, under its person and message id", "SQL")
Rel_R(useCase, recordingPort, "Uses")
Rel_R(recordingAdapter, recordingPort, "Implements", $tags="implements")
Rel_R(recordingAdapter, aiProvider, "Message, groupings, today, tool schemas", "HTTPS")
Rel_L(recordingAdapter, toolClient, "Attaches the ledger's tools from")
Rel_L(recordingAdapter, failurePolicy, "Ends a turn through")
Rel_D(toolClient, tokenInterceptor, "Reads the turn's token from")
Rel_L(toolClient, ledger, "The ledger's published tools", "MCP over HTTP")

Lay_D(useCase, currency)
Lay_D(storePort, storeAdapter)
Lay_D(recordingAdapter, toolClient)

SHOW_LEGEND()
@enduml
```

## Flow

Three diagrams: the turn every message goes through, then what happens inside it when the model records
spending, and when it answers a spending question.

### The turn

```plantuml
@startuml ExtractIntents-Turn
participant "Ledger Service" as Caller
participant "AI Connector Service" as Service
participant "Ledger Key Set" as KeySet
participant "Message Store" as Store
participant "Ledger Tools" as Tools
participant "AI Provider" as Provider

Caller -> Service : text, groupings, catch-all grouping, today, assumed currency, token
Service -> KeySet : the ledger's signing keys, on first use
KeySet --> Service : the keys, or nothing

alt no token, the token does not verify, or it names no person and message
    Service --> Caller : caller not identified
else the key set cannot be read in time
    Service --> Caller : unavailable
else the request cannot be used
    Service --> Caller : invalid argument
else the request is usable
    Service -> Store : keep this message, under that person and message
    Store --> Service : kept, already there, or refused
    Service -> Tools : list the tools, as the caller

    alt the ledger cannot be reached
        Tools --> Service : transport failure
        Service --> Caller : unavailable
    else the tools are known
        Service -> Provider : the standing instructions, the message, the groupings, the catch-all, today, the tool schemas

        alt the provider fails
            Provider --> Service : failure
            Service --> Caller : unavailable
        else the message names no spending and asks nothing
            Provider --> Service : an answer with no tool call
            Service --> Caller : acted on
        else the message names spending
            ref over Service, Tools, Provider : Recording spending
            Service --> Caller : acted on
        else the message asks what was spent
            ref over Service, Tools, Provider : Answering a spending question
            Service --> Caller : acted on
        end
    end
end
@enduml
```

### Recording spending

```plantuml
@startuml ExtractIntents-RecordingSpending
participant "AI Connector Service" as Service
participant "AI Provider" as Provider
participant "Category Lookup Tool" as Lookup
participant "Expense Proposal Tool" as Tool

loop each expense, in the user's order
    loop until a grouping answers its categories
        Provider -> Service : list this grouping's categories
        Service -> Lookup : the grouping, as the token's subject
        Lookup --> Service : the grouping's categories, or a refusal
        Service -> Provider : the result
    end

    Provider -> Service : record this expense
    Service -> Tool : the expense, as the token's subject

    alt refused
        Tool --> Service : what to retry with
        Service -> Provider : the refusal
        Provider -> Service : the corrected call, once
        Service -> Tool : the corrected expense
    else the ledger cannot be reached
        Tool --> Service : transport failure
    else recorded
        Tool --> Service : the stored proposal
        Service -> Provider : the result
    end
end
Provider --> Service : an answer with no further tool call
@enduml
```

### Answering a spending question

```plantuml
@startuml ExtractIntents-SpendingQuestion
participant "AI Connector Service" as Service
participant "AI Provider" as Provider
participant "Spending Summary Tool" as Summary

Provider -> Service : summarize this period
Service -> Summary : the first and last day, as the token's subject
Summary --> Service : the period accepted, or a refusal
Service -> Provider : the result
Provider --> Service : an answer with no further tool call
@enduml
```

## References

- [ADR 0008: The connector hands expense recording to the model](../../../docs/adr/0008-the-connector-hands-expense-recording-to-the-model.md) —
  why this service records nothing itself
- [ADR 0017: The connector verifies its caller token and keeps the message it names](../../../docs/adr/0017-the-connector-verifies-its-caller-token-and-keeps-the-message-it-names.md) —
  why the token is verified here and what the store is for
- [ADR 0010: A message reference rides the caller token](../../../ledger-service/docs/adr/0010-a-message-reference-rides-the-caller-token-not-the-extraction-request.md) —
  why the request carries no message id
- [Learn what the ledger did with a message](learn-message-outcome.md) — what becomes of the message once the
  ledger has acted on it
- [Delete the messages kept past their age](purge-messages.md) — what removes a kept message again
