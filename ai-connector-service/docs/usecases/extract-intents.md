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

A third kind stands for what could not be read: **unknown**, carrying the reason that entry was rejected.

Nothing else is extracted. A message about anything but a category or an expense produces unknown.

## Collaborators

| Direction | Collaborator                                           | Through                                                   | For                                                    |
|-----------|--------------------------------------------------------|-----------------------------------------------------------|--------------------------------------------------------|
| in        | [Ledger Service](../contracts/in/intent-extraction.md) | [Intent extraction](../contracts/in/intent-extraction.md) | turning what a user typed into actions on their ledger |
| out       | [AI provider](../contracts/out/ai-provider.md)         | [Intent inference](../contracts/out/ai-provider.md)       | reading the actions out of the text                    |

## Flow

1. The text and the categories go to the AI provider.
2. The provider answers with one raw answer per action it found.
3. Every category the message itself names joins the categories available to it.
4. Each answer becomes one intent, assembled on its own.
5. An expense's category is matched against that combined set, ignoring case.
6. An amount stated without a currency takes the assumed currency.
7. An answer that cannot be used becomes an unknown entry in its own position.

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
- Ordering, a never-empty answer and per-entry unknown are in
  [Intent extraction](../contracts/in/intent-extraction.md#semantics).

## Outcomes

| Outcome              | When                                       | Result                                          |
|----------------------|--------------------------------------------|-------------------------------------------------|
| Intents extracted    | the provider finds one or more actions     | one entry per action, in the user's order       |
| Entry not understood | one answer is missing or unusable          | that position is unknown; its neighbours stand  |
| Nothing found        | the provider finds no action               | a single unknown entry with a reason            |
| Extraction failed    | the provider is unreachable or unreadable  | no intents — extraction is unavailable          |

## Sequence

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
