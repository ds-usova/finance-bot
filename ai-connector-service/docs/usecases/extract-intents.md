# Act on the actions in a user's message

- **In:** the user's text · the categories they already have, each with its grouping · an assumed currency
  (optional) · the caller's token
- **Out:** nothing — a call that returns has been acted on
- **Why:** a user records spending by writing a sentence instead of filling a form

*Implemented by `ExtractIntentsUseCase`.*

## What is read out of the message

Every entry names one thing acted on and one action on it.

| Acted on | Actions                      | Carries                                               | Acted on further        |
|----------|------------------------------|-------------------------------------------------------|-------------------------|
| Category | create, read, update, delete | its name · a new name, when renaming                  | no — logged and skipped |
| Expense  | create                       | a category · its grouping · an amount · a description | proposed to the ledger  |
| Expense  | read, update, delete         | a category · an amount · a description, each optional | no — logged and skipped |

**Unknown** is the third kind: an entry that could not be read, carrying why. Logged and skipped.

Nothing else. A message about anything but a category or an expense is unknown.

## Collaborators

| Direction | Collaborator                                                                                 | Through                                                   | For                                         |
|-----------|----------------------------------------------------------------------------------------------|-----------------------------------------------------------|---------------------------------------------|
| in        | [Ledger Service](../../../ledger-service/docs/usecases/handle-incoming-message.md)           | [Intent extraction](../contracts/in/intent-extraction.md) | acting on what a user typed, as that user   |
| out       | [AI Provider](../contracts/out/ai-provider.md)                                               | [Intent inference](../contracts/out/ai-provider.md)       | reading the actions out of the text         |
| out       | [Expense Proposal Tool](../../../ledger-service/docs/usecases/create-an-expense-proposal.md) | [Expense proposal tool](../contracts/out/ledger-mcp.md)   | recording each expense the message asks for |

## Rules

- A caller's token is required. Without one nothing happens and the model is never prompted.
- The token is opaque: held for the call, put on every proposal, never parsed, logged or stored.
- Categories are a closed set: the caller's, plus the ones the message asks to create.
- The service never proposes a category. An invented one is refused.
- A category named anywhere in the message counts for every entry, before it or after it.
- A category answer that failed to assemble contributes nothing.
- The closed set reaches the model as `Grouping > Category` labels.
- An answer naming a category is matched by label first, then by bare name.
- A bare name matching several categories is unknown, naming the labels to retry with.
- Matching ignores case; the caller's spelling is what travels on.
- A matched category carries its grouping onward; a category the message asks to create has none.
- Only an expense to record is proposed. Every other entry is logged by what it acted on and what it asked for.
- Expenses are proposed in the order the user said them.
- The first proposal the ledger refuses or cannot take ends the turn. Earlier proposals stand.
- A message asking for nothing this service does is not a failure.
- An amount with no currency and no assumed currency is unknown.
- What each action requires is the [expense](../domain/expense-intent.md) and
  [category](../domain/category-intent.md) intents' own rule.

## Outcomes

| Outcome               | When                                                  | Result                                                             |
|-----------------------|-------------------------------------------------------|--------------------------------------------------------------------|
| Turn acted on         | every expense to record was accepted                  | an empty answer                                                    |
| Nothing to act on     | the message asks for nothing this service does        | an empty answer; each entry logged                                 |
| Entry skipped         | one answer is unusable or names no placeable category | that entry logged; the rest of the message still acted on          |
| Proposal refused      | the ledger will not record an expense                 | the turn stops; the caller is told the precondition failed         |
| Ledger unreachable    | a proposal cannot be delivered                        | the turn stops; the caller is told the service is unavailable      |
| Extraction failed     | the provider is unreachable or its answer unreadable  | nothing is proposed; the caller is told the service is unavailable |
| Caller not identified | the call arrives with no token                        | refused before the provider is called                              |

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
  Component(grpcService, "Intent Extraction gRPC Service", "gRPC endpoint", "Serves the extraction call", $tags="callerExternal")
  Component(extractIntentsPort, "Extract Intents Port", "Interface", "Inbound port", $tags="portIn")
  Component(useCase, "Extract Intents Use Case", "Plain Java", "Assembles each intent, then acts on it", $tags="core")
  Component(intent, "Intent / Money", "Domain value objects", "Intent hierarchy, money", $tags="core")

  Component(inferencePort, "Intent Inference Port", "Interface", "Outbound port", $tags="portOut")
  Component(proposalPort, "Expense Proposal Port", "Interface", "Outbound port", $tags="portOut")
  Component(aiAdapter, "AI Intent Inference Adapter", "Spring AI ChatClient", "Prompts the model, returns raw answers", $tags="aiExternal")
  Component(mcpAdapter, "MCP Expense Proposal Adapter", "Spring AI MCP client", "Calls the tool as the caller", $tags="callerExternal")
}

Rel_R(ledger, tokenInterceptor, "ExtractIntents + token", "gRPC")
Rel_R(tokenInterceptor, grpcService, "Passes the call on")
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

Lay_D(inferencePort, proposalPort)
Lay_D(aiAdapter, mcpAdapter)

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
else the call carries a token
    Service -> Provider : the text and the categories as labels

    alt the provider fails
        Provider --> Service : failure
        Service --> Caller : extraction unavailable
    else the provider answered
        Provider --> Service : one raw answer per action
        Service -> Service : collect the categories the message names
        loop each answer, in the user's order
            alt an expense to record
                Service -> Tool : the expense, as the token's subject
                alt refused
                    Tool --> Service : tool error
                    Service --> Caller : precondition failed
                else the ledger is unreachable
                    Service --> Caller : unavailable
                else recorded
                    Tool --> Service : the stored proposal
                end
            else anything else, or unusable
                Service -> Service : log the entry and move on
            end
        end
        Service --> Caller : acted on
    end
end
@enduml
```
