# Case Steps

## Files

| File       | Contents                              |
|------------|---------------------------------------|
| case YAML  | case inputs and expectations          |
| steps YAML | ordered actions that execute the case |

## Binding

| Rule         | Requirement                                           |
|--------------|-------------------------------------------------------|
| Relationship | each case references exactly one dedicated steps file |
| Reuse        | a steps file is not shared by multiple cases          |
| Composition  | steps from different cases are not merged             |
| Order        | actions execute in their declared order               |
| Verdict data | expected outcomes remain in the case YAML             |

## Case Boundary

| Phase         | Action                                                     | Required state                                                                                     |
|---------------|------------------------------------------------------------|----------------------------------------------------------------------------------------------------|
| opening       | verify the case fixture                                    | fixture matches [Fixture v1](environment-and-fixtures.md#fixture-v1) and no pending expenses exist |
| execution     | run the declared steps                                     | only case-owned state is created                                                                   |
| closing       | collect evidence, then remove all data created by the case | cleanup attempted after pass, fail, error, or cancellation                                         |
| postcondition | verify the restored fixture                                | no case data or pending expense remains                                                            |
| hand-off      | allow the next case to start                               | postcondition passed                                                                               |

## Lifecycle Shapes

### Single-Message Case

| Step | Action                        | Expected state             |
|------|-------------------------------|----------------------------|
| 1    | file the target message       | target processing started  |
| 2    | wait for the terminal outcome | target processing complete |
| 3    | inspect application state     | case evidence collected    |

### Memory Case

| Step | Action                                                   | Expected state             |
|------|----------------------------------------------------------|----------------------------|
| 1    | file one or more learning messages                       | pending proposals created  |
| 2    | approve or discard each proposal as declared by the case | decisions recorded         |
| 3    | wait for the decisions to become available to retrieval  | learning state ready       |
| 4    | file the target message                                  | target processing started  |
| 5    | wait for the terminal outcome                            | target processing complete |
| 6    | inspect recalled examples and application state          | case evidence collected    |

## Dependencies

| Concern                                            | Owner                                                   |
|----------------------------------------------------|---------------------------------------------------------|
| case schema and field constraints                  | [Corpus and case format](cases.md)                      |
| initial state and case isolation                   | [Environment and fixtures](environment-and-fixtures.md) |
| message completion and resulting application state | [Application boundary](application-boundary.md)         |
| memory behaviour                                   | [Memory and retrieval](memory-and-retrieval.md)         |
| pass, fail, and error verdicts                     | [Oracle and scoring](scoring.md)                        |
| measurements                                       | [Observability](observability.md)                       |
