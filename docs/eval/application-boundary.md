# Application Boundary

## Profile

| Item              | Rule                                                    |
|-------------------|---------------------------------------------------------|
| Name              | `application`                                           |
| Observed boundary | the outcome visible to the person using the application |
| Excluded          | direct invocation of the AI Connector                   |

## Message Path

```plantuml
@startuml AI-Evaluation-Path
actor Evaluator
participant "Ledger REST API" as REST
participant "Ledger written-message worker" as Worker
participant "AI Connector gRPC API" as Connector
participant "AI provider" as Provider
participant "Ledger MCP tools" as MCP
database "Ledger database" as DB

Evaluator -> REST : file written message
REST -> DB : store waiting message
Worker -> DB : claim message
Worker -> Connector : text, groupings, currency, identity
Connector -> Provider : prompt and tool definitions
Provider -> MCP : list_categories / create_expense_proposal
MCP -> DB : read categories / store proposal
Provider --> Connector : final answer
Connector --> Worker : turn complete
Worker -> DB : settle message outcome
Evaluator -> REST : poll message
@enduml
```

## Completion and Result

| Item                | Requirement                                                           |
|---------------------|-----------------------------------------------------------------------|
| Completion          | the message reaches a terminal outcome                                |
| Terminal outcomes   | `RECORDED`, `PARTIAL`, `NOTHING_IDENTIFIED`, `ANSWERED`, `FAILED`     |
| Proposal precondition | `GET /api/v1/expenses?status=PENDING&limit=1` returns `total: 0` |
| Proposals             | all items returned after terminal outcome by `GET /api/v1/expenses?status=PENDING&limit=100` |
| Completeness          | the response's `total` equals its number of `items`; otherwise the case ends with an error |
| Category resolution   | map each proposal's `categoryId` through `GET /api/v1/categories` |
| Attribution basis     | verified empty pending state and no unrelated traffic |
| Invalid attribution   | time windows or an unverified precondition |

## Memory Cases

[Case steps](case-steps.md) defines the message, decision, propagation, and inspection steps inside this boundary.
