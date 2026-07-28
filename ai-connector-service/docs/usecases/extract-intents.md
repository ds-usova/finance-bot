# Extract the intents in a user's message

- **In:** the user's text · the categories they already have · an assumed currency (optional)
- **Out:** one entry per action the message asks for, in the order the user said them
- **Why:** a user records money by writing a sentence instead of filling a form

*Implemented by `ExtractIntentsUseCase`.*

## What is extracted

Every entry names one thing acted on and one action on it.

| Acted on | Actions                      | Carries                                |
|----------|------------------------------|----------------------------------------|
| Category | create, read, update, delete | its name · a new name, when renaming   |
| Expense  | create, read, update, delete | a category · an amount · a description |

**Unknown** is the third kind: an entry that could not be read, carrying why.

Nothing else. A message about anything but a category or an expense comes back unknown.

## Collaborators

| Direction | Collaborator                                           | Through                                                   | For                                                    |
|-----------|--------------------------------------------------------|-----------------------------------------------------------|--------------------------------------------------------|
| in        | [Ledger Service](../contracts/in/intent-extraction.md) | [Intent extraction](../contracts/in/intent-extraction.md) | turning what a user typed into actions on their ledger |
| out       | [AI provider](../contracts/out/ai-provider.md)         | [Intent inference](../contracts/out/ai-provider.md)       | reading the actions out of the text                    |

## Rules

- Categories are a closed set: the caller's, plus the ones the message asks to create.
- The service never proposes a category. An invented one is refused.
- A category named anywhere in the message counts for every entry, before it or after it.
- A category answer that failed to assemble contributes nothing.
- Recording an expense requires an amount and a category. Reading and deleting require neither.
- Renaming a category requires the new name.
- An entry missing what its action requires is unknown, and names the missing piece.
- An amount with no currency and no assumed currency is unknown.
- An amount with more decimal places than its currency is unknown. Never rounded.
- Entries are never compared with one another.
- Ordering, a never-empty answer, per-entry unknown, category matching and the assumed currency are in
  [Intent extraction](../contracts/in/intent-extraction.md#semantics).

## Outcomes

| Outcome              | When                                       | Result                                          |
|----------------------|--------------------------------------------|-------------------------------------------------|
| Intents extracted    | the provider finds one or more actions     | one entry per action, in the user's order       |
| Entry not understood | one answer is missing or unusable          | that position is unknown; its neighbours stand  |
| Nothing found        | the provider finds no action               | a single unknown entry with a reason            |
| Extraction failed    | the provider is unreachable or unreadable  | no intents — extraction is unavailable          |

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

Container(ledger, "Ledger Service", "Java, Spring Boot", "Calls this service", $tags="callerExternal")
System_Ext(aiProvider, "AI Provider", "OpenAI-compatible chat completions API", $tags="aiExternal")

Container_Boundary(aiConnector, "AI Connector Service (Java, Spring Boot)") {
  Component(grpcService, "Intent Extraction gRPC Service", "@GrpcService", "Serves the ExtractIntents RPC", $tags="callerExternal")
  Component(protoUtils, "Intent Proto Utils", "Static mapper", "Domain intents to protobuf response", $tags="callerExternal")
  Component(extractIntentsPort, "Extract Intents Port", "Interface", "Inbound port", $tags="portIn")
  Component(useCase, "Extract Intents Use Case", "Plain Java", "Turns each raw answer into a validated intent", $tags="core")

  Component(inferencePort, "Intent Inference Port", "Interface", "Outbound port", $tags="portOut")
  Component(aiAdapter, "AI Intent Inference Adapter", "Spring AI ChatClient", "Prompts the model, returns raw answers", $tags="aiExternal")
}

Rel(ledger, grpcService, "ExtractIntents", "gRPC")
Rel_R(grpcService, extractIntentsPort, "Invokes")
Rel_D(grpcService, protoUtils, "Maps via")
Rel_L(useCase, extractIntentsPort, "Implements", $tags="implements")

Rel_R(useCase, inferencePort, "Uses")
Rel_L(aiAdapter, inferencePort, "Implements", $tags="implements")
Rel_R(aiAdapter, aiProvider, "Prompt + JSON schema", "HTTPS")

SHOW_LEGEND()
@enduml
```

## Flow

```plantuml
@startuml ExtractIntents-Sequence
participant "Ledger Service" as Caller
participant "AI Connector Service" as Service
participant "AI Provider" as Provider

Caller -> Service : text, categories, assumed currency
Service -> Provider : the text and the categories

alt actions found
    Provider --> Service : one raw answer per action
    Service -> Service : collect the categories the message names
    loop each answer, in its own place
        alt the answer holds together
            Service -> Service : assemble the intent
        else the answer cannot be used
            Service -> Service : unknown entry with the reason
        end
    end
    Service --> Caller : the intents, in the user's order
else nothing found
    Provider --> Service : no actions
    Service --> Caller : one unknown entry
else provider fails
    Provider --> Service : failure
    Service --> Caller : extraction unavailable
end
@enduml
```
