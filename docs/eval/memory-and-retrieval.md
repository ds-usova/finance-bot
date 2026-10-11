# Memory and Retrieval

## Scope

| Item                | Requirement                                         |
|---------------------|-----------------------------------------------------|
| Evaluation boundary | full learning cycle                                 |
| Learning source     | proposal decisions made inside the case             |
| Target              | a later message                                     |
| Observed result     | recalled examples and target-message classification |

## Required Coverage

| Scenario                        | Expected behaviour                                                   |
|---------------------------------|----------------------------------------------------------------------|
| approved example                | guides a later ambiguous classification                              |
| discarded example               | is not treated as a positive preference                              |
| no sufficiently similar example | classification does not depend on learned history                    |
| multiple candidate examples     | meaningful similarity order is observable                            |
| conflict with current input     | retrieved history does not override the current message or catalogue |
| no-history control              | the same target message is evaluated without learned history         |

## Classification Expectation

| Item                     | Rule                                                                        |
|--------------------------|-----------------------------------------------------------------------------|
| Memory-specific category | may require an exact category when the case proves a learned classification |

## Evaluation

| Data                                                          | Role                                                  |
|---------------------------------------------------------------|-------------------------------------------------------|
| application-visible result declared by the case               | golden expectation; participates in the verdict       |
| recalled message ids, decisions, order, and similarity values | diagnostic trace; does not participate in the verdict |

Diagnostic retrieval data is recorded as defined by [Observability](observability.md).

## Learned-Decision Availability

| Item              | Rule                                                                     |
|-------------------|--------------------------------------------------------------------------|
| Start             | the Ledger accepts the approve or discard action                         |
| Probe             | poll the AI Connector database directly                                  |
| Message condition | the learning message has a non-null embedding                            |
| Expense condition | its recorded expenses have the expected `ACCEPTED` or `DISCARDED` status |
| Ready             | both the message and expense conditions hold                             |
| Target filing     | only after readiness is confirmed                                        |
| Timeout           | owned by [Execution policy](execution-policy.md)                         |

The database probe synchronizes the case; it is not a golden expectation and does not replace evaluation through
the application boundary.
