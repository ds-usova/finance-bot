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
Provider -> Connector : list_categories
Connector --> Provider : categories from the request
Provider -> MCP : create_expense_proposal
MCP -> DB : store proposal
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
| Proposals             | after terminal outcome, read all pages of `GET /api/v1/expenses?status=PENDING&limit=100&offset=0`, advancing `offset` by the number of returned items |
| Completeness          | the number of unique proposal IDs across all pages equals `total`; a changed `total`, duplicate ID, or empty page before completion is an inspection error |
| Target selection      | exclude tracked learning-proposal IDs from the complete result before evaluating target expectations |
| Category resolution   | map each proposal's `categoryId` through `GET /api/v1/categories` |
| Attribution basis     | verified empty pending state and no unrelated traffic |
| Invalid attribution   | time windows or an unverified precondition |

## Memory Cases

[Case steps](case-steps.md) defines the message, decision, propagation, and inspection steps inside this boundary.
