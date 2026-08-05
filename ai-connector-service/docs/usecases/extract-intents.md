# Record the spending a user's message names

- **In:** the user's text · the groupings their categories are filed under · the grouping to fall back on ·
  the day the turn runs on · an assumed currency (optional) · the caller's token
- **Out:** nothing — a call that returns has been acted on
- **Why:** a user records spending, and asks what they spent, by writing a sentence instead of filling a form

*Implemented by `ExtractIntentsUseCase`.*

## Collaborators

| Direction | Collaborator                                                                                 | Through                                                   | For                                             |
|-----------|----------------------------------------------------------------------------------------------|-----------------------------------------------------------|-------------------------------------------------|
| in        | [Ledger Service](../../../ledger-service/docs/usecases/handle-incoming-message.md)           | [Intent extraction](../contracts/in/intent-extraction.md) | acting on what a user typed, as that user       |
| out       | [AI Provider](../contracts/out/ai-provider.md)                                               | [Chat completions](../contracts/out/ai-provider.md)       | reading the message and deciding what to do     |
| out       | [Category Lookup Tool](../../../ledger-service/docs/usecases/list-categories.md)             | [Ledger tools](../contracts/out/ledger-mcp.md)            | learning which categories a grouping holds      |
| out       | [Expense Proposal Tool](../../../ledger-service/docs/usecases/create-an-expense-proposal.md) | [Ledger tools](../contracts/out/ledger-mcp.md)            | recording one expense                           |
| out       | [Spending Summary Tool](../../../ledger-service/docs/usecases/summarize-spending.md)         | [Ledger tools](../contracts/out/ledger-mcp.md)            | asking for the totals over a period             |

## Rules

- A caller's token is required. Without one the model is never prompted.
- The token is opaque: held for the turn, carried on every tool call, never parsed, logged or stored.
- Spending is recorded and a question about what was spent is answered — no category is created, renamed or
  deleted.
- The model calls the recording tool once per expense, in the order the user said them.
- The caller's groupings are a closed set; a grouping name is never invented.
- An expense is filed under a category the ledger answers for one of those groupings, never under a grouping
  itself.
- A grouping's categories are asked for before an expense is filed under it.
- The caller designates one of the groupings as the catch-all, so every expense has somewhere to be filed.
- An expense the ledger refuses to record is corrected against the refusal and recorded once more.
- An expense refused a second time is left unrecorded, and the rest of the message is still recorded.
- A refused category lookup costs the expense nothing — the grouping's name is corrected and asked again.
- An expense whose amount, currency or category cannot be told from the message is left unrecorded.
- An amount stated with no currency takes the assumed currency; with none assumed the expense is left
  unrecorded.
- The merchant is recorded when the message names one.
- A message naming no spending is not a failure.
- The same message handled twice records its expenses twice.
- The day the turn runs on is required, and arrives with the request as a UTC calendar date.
- A relative period — "last week", "this month", "since Friday" — is worked out from that day, with the week
  starting on Monday.
- A question about what was spent is answered by asking the ledger to summarize one period, given as its first
  and last day.
- No amount is stated back to the user here — the totals are put in front of the user by the ledger.
- What a turn recorded, and what a period totals, is visible in the ledger, not here.

## Outcomes

| Outcome                 | When                                                                | Result                                                          |
|-------------------------|---------------------------------------------------------------------|-----------------------------------------------------------------|
| Turn acted on           | the model finished the turn                                         | an empty answer                                                 |
| Nothing recorded        | the message names no spending                                       | an empty answer                                                 |
| Summary asked for       | the message asks what was spent over a period                       | an empty answer; the ledger puts the totals in front of the user |
| Expense left unrecorded | the ledger refused to record it twice, or the message under-said it | an empty answer; the rest of the message still recorded         |
| Provider unavailable    | the provider is unreachable or errors                               | the turn stops; the caller is told the service is unavailable   |
| Ledger unreachable      | a tool cannot be reached, or the token is refused there             | the turn stops; the caller is told the service is unavailable   |
| Request unusable        | a required field is missing or unreadable                           | refused before the provider is called                           |
| Caller not identified   | the call arrives with no token                                      | refused before the provider is called                           |

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
  Component(useCase, "Extract Intents Use Case", "Plain Java", "Hands the groupings, the day and the turn on", $tags="core")
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
Rel_R(recordingAdapter, recordingPort, "Implements", $tags="implements")
Rel_R(recordingAdapter, aiProvider, "Message, groupings, today, tool schemas", "HTTPS")
Rel_L(recordingAdapter, toolClient, "Attaches the ledger's tools from")
Rel_L(recordingAdapter, failurePolicy, "Ends a turn through")
Rel_D(toolClient, tokenInterceptor, "Reads the turn's token from")
Rel_L(toolClient, ledger, "The ledger's published tools", "MCP over HTTP")

Lay_D(useCase, currency)
Lay_D(recordingAdapter, toolClient)

SHOW_LEGEND()
@enduml
```

## Flow

```plantuml
@startuml ExtractIntents-Sequence
participant "Ledger Service" as Caller
participant "AI Connector Service" as Service
participant "AI Provider" as Provider
participant "Category Lookup Tool" as Lookup
participant "Expense Proposal Tool" as Tool
participant "Spending Summary Tool" as Summary

Caller -> Service : text, groupings, catch-all grouping, today, assumed currency, token

alt no token
    Service --> Caller : caller not identified
else the request cannot be used
    Service --> Caller : invalid argument
else the request is usable
    Service -> Lookup : list the tools, as the caller

    alt the ledger cannot be reached
        Lookup --> Service : transport failure
        Service --> Caller : unavailable
    else the tools are known
        Service -> Provider : the standing instructions, the message, the groupings, the catch-all, today, the tool schemas

        alt the provider fails
            Provider --> Service : failure
            Service --> Caller : unavailable
        else the message names no spending and asks nothing
            Provider --> Service : an answer with no tool call
            Service --> Caller : acted on
        else the message asks what was spent
            Provider -> Service : summarize this period
            Service -> Summary : the first and last day, as the token's subject
            Summary --> Service : the period accepted, or a refusal
            Service -> Provider : the result
            Provider --> Service : an answer with no further tool call
            Service --> Caller : acted on
        else the message names spending
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
