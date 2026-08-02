# Record the spending a user's message names

- **In:** the user's text · the categories they already have, each with its grouping · an assumed currency
  (optional) · the caller's token
- **Out:** nothing — a call that returns has been acted on
- **Why:** a user records spending by writing a sentence instead of filling a form

*Implemented by `ExtractIntentsUseCase`.*

## Collaborators

| Direction | Collaborator                                                                                 | Through                                                   | For                                             |
|-----------|----------------------------------------------------------------------------------------------|-----------------------------------------------------------|-------------------------------------------------|
| in        | [Ledger Service](../../../ledger-service/docs/usecases/handle-incoming-message.md)           | [Intent extraction](../contracts/in/intent-extraction.md) | acting on what a user typed, as that user       |
| out       | [AI Provider](../contracts/out/ai-provider.md)                                               | [Chat completions](../contracts/out/ai-provider.md)       | reading the message and deciding what to record |
| out       | [Expense Proposal Tool](../../../ledger-service/docs/usecases/create-an-expense-proposal.md) | [Expense proposal tool](../contracts/out/ledger-mcp.md)   | recording one expense                           |

## Rules

- A caller's token is required. Without one the model is never prompted.
- The token is opaque: held for the turn, carried on every tool call, never parsed, logged or stored.
- Spending is recorded and nothing else — no category is created, renamed or deleted, and nothing is read back
  for the user.
- The model calls the tool once per expense, in the order the user said them.
- The caller's categories are a closed set, offered as `Grouping > Category` labels; a name is never invented.
- The set carries a catch-all, so every expense has somewhere to be filed.
- An expense refused by the ledger is corrected against the refusal and tried once more.
- An expense refused a second time is left unrecorded, and the rest of the message is still recorded.
- An expense whose amount, currency or category cannot be told from the message is left unrecorded.
- An amount stated with no currency takes the assumed currency; with none assumed the expense is left
  unrecorded.
- The merchant is recorded when the message names one.
- A message naming no spending is not a failure.
- The same message handled twice records its expenses twice.
- What a turn recorded is visible in the ledger, not here.

## Outcomes

| Outcome                 | When                                                      | Result                                                        |
|-------------------------|-----------------------------------------------------------|---------------------------------------------------------------|
| Turn acted on           | the model finished the turn                               | an empty answer                                               |
| Nothing recorded        | the message names no spending                             | an empty answer                                               |
| Expense left unrecorded | the ledger refused it twice, or the message under-said it | an empty answer; the rest of the message still recorded       |
| Provider unavailable    | the provider is unreachable or errors                     | the turn stops; the caller is told the service is unavailable |
| Ledger unreachable      | the tool cannot be reached, or the token is refused there | the turn stops; the caller is told the service is unavailable |
| Caller not identified   | the call arrives with no token                            | refused before the provider is called                         |

## Components

```plantuml
@startuml C3-Component-ExtractIntents
!include <C4/C4_Component>

AddElementTag("callerExternal", $bgColor="#1c94e0", $fontColor="#ffffff", $borderColor="#125d8c")
AddElementTag("aiExternal", $bgColor="#8e44ad", $fontColor="#ffffff", $borderColor="#5f2f74")
AddElementTag("portIn", $bgColor="#16a085", $fontColor="#ffffff", $borderColor="#0e6655", $legendText="inbound port (interface)")
AddElementTag("portOut", $bgColor="#7f8c8d", $fontColor="#ffffff", $borderColor="#566573", $legendText="outbound port (interface)")
AddElementTag("core", $bgColor="#2c3e50", $fontColor="#ffffff", $borderColor="#1b2631", $legendText="application core")

AddRelTag("implements", $lineStyle="dashed")

Container(ledger, "Ledger Service", "Java, Spring Boot", "Calls this service, and is called back", $tags="callerExternal")
System_Ext(aiProvider, "AI Provider", "OpenAI-compatible chat completions API", $tags="aiExternal")

Container_Boundary(aiConnector, "AI Connector Service (Java, Spring Boot)") {
  Component(tokenInterceptor, "Caller Token Interceptor", "gRPC interceptor", "Refuses an untokened call, holds the token for the turn", $tags="callerExternal")
  Component(grpcService, "Intent Extraction Endpoint", "gRPC endpoint", "Serves the extraction call, validates the request", $tags="callerExternal")
  Component(extractIntentsPort, "Extract Intents Port", "Interface", "Inbound port", $tags="portIn")
  Component(useCase, "Extract Intents Use Case", "Plain Java", "Labels the caller's categories, hands the turn on", $tags="core")
  Component(currency, "Currency Code", "Domain value object", "An ISO 4217 code", $tags="core")

  Component(recordingPort, "Expense Recording Port", "Interface", "Outbound port", $tags="portOut")
  Component(recordingAdapter, "Expense Recording Adapter", "Spring AI ChatClient", "Prompts the model with the ledger's tools attached", $tags="aiExternal")
  Component(toolClient, "Ledger Tool Client", "MCP client", "Lists the tools, calls them as the turn's caller", $tags="callerExternal")
  Component(failurePolicy, "Tool Failure Policy", "Plain Java", "Splits a refusal the model reads from a failure that ends the turn", $tags="callerExternal")
}

Rel_R(ledger, tokenInterceptor, "ExtractIntents + token", "gRPC")
Rel_R(tokenInterceptor, grpcService, "Passes the call on")
Rel_R(grpcService, extractIntentsPort, "Invokes")
Rel_D(grpcService, currency, "Validates the assumed currency with")
Rel_L(useCase, extractIntentsPort, "Implements", $tags="implements")
Rel_R(useCase, recordingPort, "Uses")
Rel_L(recordingAdapter, recordingPort, "Implements", $tags="implements")
Rel_R(recordingAdapter, aiProvider, "Message, categories, tool schema", "HTTPS")
Rel_D(recordingAdapter, toolClient, "Attaches the ledger's tools from")
Rel_D(recordingAdapter, failurePolicy, "Ends a turn through")
Rel_D(toolClient, tokenInterceptor, "Reads the turn's token from")
Rel_L(toolClient, ledger, "create_expense_proposal", "MCP over HTTP")

Lay_D(useCase, currency)
Lay_D(recordingAdapter, toolClient)
Lay_D(toolClient, failurePolicy)

SHOW_LEGEND()
@enduml
```

## Flow

```plantuml
@startuml ExtractIntents-Sequence
participant "Ledger Service" as Caller
participant "AI Connector Service" as Service
participant "AI Provider" as Provider
participant "Expense Proposal Tool" as Tool

Caller -> Service : text, categories with their groupings, assumed currency, token

alt no token
    Service --> Caller : caller not identified
else the request cannot be used
    Service --> Caller : invalid argument
else the request is usable
    Service -> Tool : list the tools, as the caller

    alt the ledger cannot be reached
        Tool --> Service : transport failure
        Service --> Caller : unavailable
    else the tools are known
        Service -> Provider : the recording instructions, the message, the categories, the tool schema

        alt the provider fails
            Provider --> Service : failure
            Service --> Caller : unavailable
        else the message names no spending
            Provider --> Service : an answer with no tool call
            Service --> Caller : acted on
        else the message names spending
            loop each expense, in the user's order
                Provider -> Service : record this expense
                Service -> Tool : the expense, as the token's subject

                alt refused
                    Tool --> Service : what to retry with
                    Service -> Provider : the refusal
                    Provider -> Service : the corrected call, once
                    Service -> Tool : the corrected expense
                else the ledger cannot be reached
                    Tool --> Service : transport failure
                    Service --> Caller : unavailable
                else recorded
                    Tool --> Service : the stored proposal
                    Service -> Provider : the result
                end
            end
            Provider --> Service : an answer with no further tool call
            Service --> Caller : acted on
        end
    end
end
@enduml
```
