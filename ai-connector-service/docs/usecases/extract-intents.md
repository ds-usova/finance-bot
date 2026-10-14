# Record the spending a user's message names

- **In:**
  - the user's text
  - the groupings their categories are filed under
  - the grouping to fall back on
  - every grouping's categories
  - the day the turn runs on
  - an assumed currency (optional)
  - the caller's token
- **Out:** nothing
- **Why:** a user records spending, and asks what they spent, by writing a sentence instead of filling a form

*Implemented by `ExtractIntentsUseCase`.*

Every turn runs in the [extraction mode](../domain/extraction-mode.md) `EXTRACTION_MODE` names:

| Mode                 | The turn                                                                                          |
|----------------------|---------------------------------------------------------------------------------------------------|
| `tools`, the default | the model files the spending itself, through the ledger's tools                                   |
| `classifier`         | the model reads the spending out, a classifier picks each category, this service files the result |

## Prerequisites

- The call carries a caller token.
- The token verifies against the ledger's published key set and names the person and the message it acts for.
- In the tools mode, the ledger serves the tools the model calls.
- In the classifier mode, the ledger serves its turn operations at `LEDGER_TURN_TARGET`.

## Collaborators

| Direction | Collaborator                                                                                         | Through                                                   | For                                                        |
|-----------|------------------------------------------------------------------------------------------------------|-----------------------------------------------------------|------------------------------------------------------------|
| in        | [Ledger Service, Telegram message](../../../ledger-service/docs/usecases/handle-incoming-message.md) | [Intent extraction](../contracts/in/intent-extraction.md) | acting on what a user typed, as that user                  |
| in        | [Ledger Service, web message](../../../ledger-service/docs/usecases/handle-written-message.md)       | [Intent extraction](../contracts/in/intent-extraction.md) | acting on what a user typed, as that user                  |
| out       | [Ledger key set](../../../ledger-service/docs/contracts/in/mcp.md#how-a-caller-authenticates)        | [Ledger tools](../contracts/out/ledger-mcp.md)            | reading who the token acts for                             |
| out       | [Message store](../contracts/out/database.md)                                                        | [Database](../contracts/out/database.md)                  | keeping the message the caller handed over                 |
| out       | [Recall the person's own worked examples](recall-examples.md)                                        | Recall Examples Port                                      | showing the model or the classifier how this person files  |
| out       | [AI Provider](../contracts/out/ai-provider.md)                                                       | [Chat completions](../contracts/out/ai-provider.md)       | acting on the message, or only reading it out              |
| out       | [Category Lookup Tool](../../../ledger-service/docs/usecases/list-categories.md)                     | [Ledger tools](../contracts/out/ledger-mcp.md)            | tools mode: learning which categories a grouping holds     |
| out       | [Expense Proposal Tool](../../../ledger-service/docs/usecases/create-an-expense-proposal.md)         | [Ledger tools](../contracts/out/ledger-mcp.md)            | tools mode: recording one expense                          |
| out       | [Spending Summary Tool](../../../ledger-service/docs/usecases/summarize-spending.md)                 | [Ledger tools](../contracts/out/ledger-mcp.md)            | tools mode: asking for the totals over a period            |
| out       | [Category Classifier](../contracts/out/classifier.md)                                                | [System One](../contracts/out/classifier.md)              | classifier mode: picking each expense's category           |
| out       | [Expense Filing](../../../ledger-service/docs/usecases/create-an-expense-proposal.md)                | [Ledger turn operations](../contracts/out/ledger-turn.md) | classifier mode: filing one expense                        |
| out       | [Period Recording](../../../ledger-service/docs/usecases/summarize-spending.md)                      | [Ledger turn operations](../contracts/out/ledger-turn.md) | classifier mode: recording a period the message asks about |

## Outcomes

### Either mode

| Outcome                 | When                                                                              | Result                                                                                       |
|-------------------------|-----------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------|
| Message kept            | the token names a person and a message                                            | the message is stored under that pair before the model is called                             |
| Message already known   | the same person and message arrive a second time                                  | the first row and its text stand; the turn runs again                                        |
| Message not kept        | the store refuses the write                                                       | the turn runs as if it had been kept                                                         |
| Examples offered        | the recall answered examples                                                      | they reach the model in the tools mode, and the classifier in the classifier mode            |
| No examples to offer    | the recall answered no examples                                                   | the model is told this person has no comparable earlier message; the classifier is told none |
| Examples not looked for | the recall answered nothing at all                                                | the turn runs with no examples                                                               |
| Request unusable        | a required field is missing or unreadable, or the catalogue cannot be used        | refused before the provider is called                                                        |
| Caller not identified   | no token arrives, or the token does not verify, or it names no person and message | refused before the provider is called                                                        |
| Identity uncheckable    | the ledger's key set cannot be read within `LEDGER_JWKS_TIMEOUT`                  | refused before the provider is called; the caller is told the service is unavailable         |
| Turn counted            | the turn is acted on, or ends with the provider or the ledger unavailable         | [counted once](../contracts/in/operations.md#meters) under its mode and that outcome         |

What makes a request or a catalogue unusable is [the contract's](../contracts/in/intent-extraction.md#failures).

### Tools mode

| Outcome                 | When                                                                            | Result                                                        |
|-------------------------|---------------------------------------------------------------------------------|---------------------------------------------------------------|
| Turn acted on           | the model finished the turn                                                     | an empty answer                                               |
| Nothing recorded        | the message names no spending                                                   | an empty answer                                               |
| Summary asked for       | the message asks what was spent over a period                                   | an empty answer                                               |
| Expense left unrecorded | the ledger refused it and its one corrected retry, or the message under-said it | an empty answer; the rest of the message still recorded       |
| Provider unavailable    | the provider is unreachable or errors                                           | the turn stops; the caller is told the service is unavailable |
| Ledger unreachable      | a tool cannot be reached, or the token is refused there                         | the turn stops; the caller is told the service is unavailable |

### Classifier mode

| Outcome               | When                                                                                                             | Result                                                                                                         |
|-----------------------|------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------|
| Turn acted on         | every usable expense and every period was tried                                                                  | an empty answer                                                                                                |
| Nothing named         | the read-out holds no expense and no period                                                                      | an empty answer; neither the classifier nor the ledger is called                                               |
| Expense unusable      | a read-out expense is no [expense to file](../domain/expense-to-file.md), or has no currency and none is assumed | left unrecorded and never classified; one `WARN` naming its place in the read-out and the reason               |
| Currency assumed      | a read-out expense has no currency, and the request assumes one                                                  | filed in the assumed currency                                                                                  |
| Confident             | the expense's confidence is at or above `CLASSIFIER_MIN_CONFIDENCE`                                              | filed under the classifier's choice                                                                            |
| Not confident         | the expense's confidence is below it                                                                             | filed under the catch-all grouping, in its most probable category there                                        |
| Classifier failed     | the call [fails](../contracts/out/classifier.md#failures)                                                        | each expense under the catch-all grouping, in its first category; one `WARN` naming the person and the message |
| Classifier timed      | the classifier answers or fails                                                                                  | its duration [recorded](../contracts/in/operations.md#meters)                                                  |
| Confidence recorded   | the classifier answers                                                                                           | each expense's confidence [recorded](../contracts/in/operations.md#meters); each one not confident counted     |
| Period asked for      | the message asks what was spent over a period                                                                    | the period recorded once every expense was tried                                                               |
| Filing refused        | the ledger refuses one expense or period as an invalid argument                                                  | that one left unrecorded; one `WARN`; the rest still filed                                                     |
| Ledger fails the turn | the ledger answers any other status, or not [in time](../contracts/out/ledger-turn.md#deadlines)                 | the turn stops; nothing further is filed; what was filed stands; the caller is told the service is unavailable |
| Provider unavailable  | the provider fails, or its read-out does not fit the shape                                                       | the turn stops with nothing filed; the caller is told the service is unavailable                               |

## Components

### The front of the turn

```plantuml
@startuml C3-Component-ExtractIntents
!include <C4/C4_Component>

AddElementTag("callerExternal", $bgColor="#1c94e0", $fontColor="#ffffff", $borderColor="#125d8c")
AddElementTag("storeExternal", $bgColor="#b9770e", $fontColor="#ffffff", $borderColor="#7d5109")
AddElementTag("portIn", $bgColor="#16a085", $fontColor="#ffffff", $borderColor="#0e6655", $legendText="inbound port (interface)")
AddElementTag("portOut", $bgColor="#7f8c8d", $fontColor="#ffffff", $borderColor="#566573", $legendText="outbound port (interface)")
AddElementTag("core", $bgColor="#2c3e50", $fontColor="#ffffff", $borderColor="#1b2631", $legendText="application core")

AddRelTag("implements", $lineStyle="dashed")

Container(ledger, "Ledger Service", "Java, Spring Boot", "Calls this service, publishes its key set", $tags="callerExternal")
ContainerDb(database, "Message Store", "PostgreSQL", "The messages this service was handed", $tags="storeExternal")

Container_Boundary(aiConnector, "AI Connector Service (Java, Spring Boot)") {
  Component(tokenInterceptor, "Caller Token Interceptor", "gRPC interceptor", "Refuses an untokened or unverified call, holds the identity for the turn", $tags="callerExternal")
  Component(tokenVerifier, "Caller Token Verifier", "Token reader", "Verifies the token, reads the person and the message", $tags="callerExternal")
  Component(grpcService, "Intent Extraction Endpoint", "gRPC endpoint", "Serves the extraction call, validates the request", $tags="callerExternal")
  Component(extractIntentsPort, "Extract Intents Port", "Interface", "Inbound port", $tags="portIn")
  Component(useCase, "Extract Intents Use Case", "Plain Java", "Registers the message, recalls examples, runs the turn", $tags="core")
  Component(storePort, "Message Store Port", "Interface", "Outbound port", $tags="portOut")
  Component(storeAdapter, "Message Store Adapter", "Spring Data JDBC", "Writes the message under its person and message", $tags="storeExternal")
  Component(recallPort, "Recall Examples Port", "Interface", "Outbound port", $tags="portOut")
  Component(recallUseCase, "Recall Examples Use Case", "Plain Java", "Answers this person's closest earlier messages, or nothing", $tags="core")
  Component(turnMeters, "Turn Meters", "Interface", "Counts each turn under its mode", $tags="portOut")
  Component(toolTurn, "Tool-Calling Turn", "Plain Java", "The tools mode", $tags="core")
  Component(classifierTurn, "Classifier Turn", "Plain Java", "The classifier mode", $tags="core")
}

Rel_R(ledger, tokenInterceptor, "ExtractIntents + token", "gRPC")
Rel_D(tokenInterceptor, tokenVerifier, "Reads the token through")
Rel_L(tokenVerifier, ledger, "The published signing keys", "HTTPS")
Rel_R(tokenInterceptor, grpcService, "Passes the call on")
Rel_D(grpcService, extractIntentsPort, "Invokes")
Rel_U(useCase, extractIntentsPort, "Implements", $tags="implements")
Rel_D(useCase, storePort, "Registers the message through")
Rel_U(storeAdapter, storePort, "Implements", $tags="implements")
Rel_D(storeAdapter, database, "The message, under its person and message id", "SQL")
Rel_D(useCase, recallPort, "Recalls the examples through")
Rel_U(recallUseCase, recallPort, "Implements", $tags="implements")
Rel_R(useCase, turnMeters, "Counts the turn through")
Rel_D(useCase, toolTurn, "Runs, tools mode")
Rel_D(useCase, classifierTurn, "Runs, classifier mode")

Lay_D(storePort, storeAdapter)
Lay_D(recallPort, recallUseCase)

SHOW_LEGEND()
@enduml
```

### The tool-calling turn

```plantuml
@startuml C3-Component-ExtractIntents-ToolCalling
!include <C4/C4_Component>

AddElementTag("callerExternal", $bgColor="#1c94e0", $fontColor="#ffffff", $borderColor="#125d8c")
AddElementTag("aiExternal", $bgColor="#8e44ad", $fontColor="#ffffff", $borderColor="#5f2f74")
AddElementTag("portOut", $bgColor="#7f8c8d", $fontColor="#ffffff", $borderColor="#566573", $legendText="outbound port (interface)")
AddElementTag("core", $bgColor="#2c3e50", $fontColor="#ffffff", $borderColor="#1b2631", $legendText="application core")

AddRelTag("implements", $lineStyle="dashed")

Container(ledger, "Ledger Service", "Java, Spring Boot", "Serves the tools", $tags="callerExternal")
System_Ext(aiProvider, "AI Provider", "OpenAI-compatible chat completions API", $tags="aiExternal")

Container_Boundary(aiConnector, "AI Connector Service (Java, Spring Boot)") {
  Component(toolTurn, "Tool-Calling Turn", "Plain Java", "Hands the message to the model", $tags="core")
  Component(recordingPort, "Expense Recording Port", "Interface", "Outbound port", $tags="portOut")
  Component(recordingAdapter, "Expense Recording Adapter", "Spring AI ChatClient", "Prompts the model with the ledger's tools attached", $tags="aiExternal")
  Component(toolClient, "Ledger Tool Client", "MCP client", "Lists the tools, calls them as the turn's caller", $tags="callerExternal")
  Component(failurePolicy, "Tool Failure Policy", "Plain Java", "Splits a refusal the model reads from a failure that ends the turn", $tags="callerExternal")
  Component(tokenInterceptor, "Caller Token Interceptor", "gRPC interceptor", "Holds the turn's token", $tags="callerExternal")
}

Rel_D(toolTurn, recordingPort, "Records through")
Rel_U(recordingAdapter, recordingPort, "Implements", $tags="implements")
Rel_R(recordingAdapter, aiProvider, "Message, groupings, today, tool schemas", "HTTPS")
Rel_D(recordingAdapter, toolClient, "Attaches the ledger's tools from")
Rel_L(recordingAdapter, failurePolicy, "Ends a turn through")
Rel_L(toolClient, tokenInterceptor, "Reads the turn's token from")
Rel_D(toolClient, ledger, "The ledger's published tools", "MCP over HTTP")

SHOW_LEGEND()
@enduml
```

### The classifier turn

```plantuml
@startuml C3-Component-ExtractIntents-Classifier
!include <C4/C4_Component>

AddElementTag("callerExternal", $bgColor="#1c94e0", $fontColor="#ffffff", $borderColor="#125d8c")
AddElementTag("aiExternal", $bgColor="#8e44ad", $fontColor="#ffffff", $borderColor="#5f2f74")
AddElementTag("portOut", $bgColor="#7f8c8d", $fontColor="#ffffff", $borderColor="#566573", $legendText="outbound port (interface)")
AddElementTag("core", $bgColor="#2c3e50", $fontColor="#ffffff", $borderColor="#1b2631", $legendText="application core")

AddRelTag("implements", $lineStyle="dashed")

System_Ext(aiProvider, "AI Provider", "OpenAI-compatible chat completions API", $tags="aiExternal")
System_Ext(classifier, "Category Classifier", "TypeSafe System One", $tags="aiExternal")
Container(ledger, "Ledger Service", "Java, Spring Boot", "Serves the turn operations", $tags="callerExternal")

Container_Boundary(aiConnector, "AI Connector Service (Java, Spring Boot)") {
  Component(classifierTurn, "Classifier Turn", "Plain Java", "Reads out, classifies, files each expense and period", $tags="core")
  Component(turnMeters, "Turn Meters", "Interface", "Times the classifier, records each confidence", $tags="portOut")
  Component(readOutPort, "Spending Read-Out Port", "Interface", "Outbound port", $tags="portOut")
  Component(readOutAdapter, "Spending Read-Out Adapter", "Spring AI ChatClient", "Reads the expenses and periods out, no tools", $tags="aiExternal")
  Component(classifierPort, "Category Classifier Port", "Interface", "Outbound port", $tags="portOut")
  Component(classifierAdapter, "Category Classifier Adapter", "HTTP client", "One question per expense", $tags="aiExternal")
  Component(ledgerTurnPort, "Ledger Turn Port", "Interface", "Outbound port", $tags="portOut")
  Component(ledgerTurnAdapter, "Ledger Turn Adapter", "gRPC client", "Files an expense, records a period, as the turn's caller", $tags="callerExternal")
  Component(tokenInterceptor, "Caller Token Interceptor", "gRPC interceptor", "Holds the turn's token", $tags="callerExternal")
}

Rel_R(classifierTurn, turnMeters, "Records through")
Rel_D(classifierTurn, readOutPort, "Reads out through")
Rel_D(classifierTurn, classifierPort, "Classifies through")
Rel_D(classifierTurn, ledgerTurnPort, "Files through")
Rel_U(readOutAdapter, readOutPort, "Implements", $tags="implements")
Rel_U(classifierAdapter, classifierPort, "Implements", $tags="implements")
Rel_U(ledgerTurnAdapter, ledgerTurnPort, "Implements", $tags="implements")
Rel_D(readOutAdapter, aiProvider, "Message, today, read-out shape", "HTTPS")
Rel_D(classifierAdapter, classifier, "POST /v1/systemone", "HTTPS")
Rel_D(ledgerTurnAdapter, ledger, "FileExpenseProposal, RecordSpendingPeriod", "gRPC")
Rel_R(ledgerTurnAdapter, tokenInterceptor, "Reads the turn's token from")

Lay_D(readOutPort, readOutAdapter)
Lay_D(classifierPort, classifierAdapter)
Lay_D(ledgerTurnPort, ledgerTurnAdapter)

SHOW_LEGEND()
@enduml
```

## Flow

### The turn

```plantuml
@startuml ExtractIntents-Sequence
participant "Ledger Service" as Caller
participant "AI Connector Service" as Service
participant "Ledger Key Set" as KeySet
participant "Message Store" as Store
participant "Recall" as Recall

Caller -> Service : text, groupings, catch-all, catalogue, today, assumed currency, token
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
    Service -> Recall : this person's own worked examples
    Recall --> Service : the examples, none of them, or nothing looked for

    alt tools mode
        ref over Service : The tool-calling turn
    else classifier mode
        ref over Service : The classifier turn
    end
end
@enduml
```

### The tool-calling turn

```plantuml
@startuml ExtractIntents-ToolCalling
participant "Ledger Service" as Caller
participant "AI Connector Service" as Service
participant "Ledger Tools" as Tools
participant "AI Provider" as Provider

Service -> Tools : list the tools, as the caller

alt the ledger cannot be reached
    Tools --> Service : transport failure
    Service --> Caller : unavailable
else the tools are known
    Service -> Provider : the standing instructions, the message, the examples, the groupings, the catch-all, today, the tool schemas

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
@enduml
```

#### Recording spending

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

#### Answering a spending question

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

### The classifier turn

```plantuml
@startuml ExtractIntents-Classifier
participant "Ledger Service" as Caller
participant "AI Connector Service" as Service
participant "AI Provider" as Provider
participant "Category Classifier" as Classifier
participant "Expense Filing" as Filing
participant "Period Recording" as Period

Service -> Provider : the message, today, the assumed currency; no tools

alt the provider fails, or answers outside the shape
    Provider --> Service : failure
    Service --> Caller : unavailable
else read out
    Provider --> Service : expenses in the user's order, periods
    Service -> Service : drop the unusable expenses

    opt at least one usable expense
        Service -> Classifier : the message, the examples, one question per expense
        alt answered
            Classifier --> Service : per expense, a choice, a confidence, probabilities
            Service -> Service : the choice, or the catch-all when not confident
        else fails
            Classifier --> Service : failure
            Service -> Service : every expense to the catch-all
        end
    end

    loop each usable expense, in the user's order
        Service -> Filing : the expense under its category, as the caller
        alt filed
            Filing --> Service : the stored proposal
        else refused for this call
            Filing --> Service : invalid argument; left unrecorded
        else refused for the turn
            Filing --> Service : any other status, or nothing in time
            Service --> Caller : unavailable
        end
    end

    loop each period
        Service -> Period : the first and last day, as the caller
        alt recorded
            Period --> Service : the stored period
        else refused for this call
            Period --> Service : invalid argument; left unrecorded
        else refused for the turn
            Period --> Service : any other status, or nothing in time
            Service --> Caller : unavailable
        end
    end

    Service --> Caller : acted on
end
@enduml
```

## References

- [ADR 0008: The connector hands expense recording to the model](../../../docs/adr/0008-the-connector-hands-expense-recording-to-the-model.md) —
  why the tools mode records nothing itself
- [ADR 0021: In the classifier mode the connector reads with the model, picks each category with a classifier and files through the ledger's turn operations](../../../docs/adr/0021-in-the-classifier-mode-the-connector-reads-with-the-model-picks-each-category-with-a-classifier-and-files-through-the-ledgers-turn-operations.md) —
  why the classifier mode exists beside it
- [ADR 0017: The connector verifies its caller token and keeps the message it names](../../../docs/adr/0017-the-connector-verifies-its-caller-token-and-keeps-the-message-it-names.md) —
  why the token is verified here and what the store is for
- [ADR 0010: A message reference rides the caller token](../../../ledger-service/docs/adr/0010-a-message-reference-rides-the-caller-token-not-the-extraction-request.md) —
  why the request carries no message id
- [Learn what the ledger did with a message](learn-message-outcome.md) — what becomes of the message once the
  ledger has acted on it
- [Delete the messages kept past their age](purge-messages.md) — what removes a kept message again
