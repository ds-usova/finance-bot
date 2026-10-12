# Case Steps

## Files

| File                          | Contents                              |
|-------------------------------|---------------------------------------|
| `corpus/cases/<case-id>.yaml` | case inputs and expectations          |
| `corpus/steps/<case-id>.yaml` | ordered actions that execute the case |

## Binding

| Rule         | Requirement                                           |
|--------------|-------------------------------------------------------|
| Relationship | each case references exactly one dedicated steps file |
| Reuse        | a steps file is not shared by multiple cases          |
| Composition  | steps from different cases are not merged             |
| Order        | actions execute in their declared order               |
| Verdict data | expected outcomes remain in the case YAML             |

## Step Format

- **Target message** — the message whose result is checked against the case's `expected` constraints.
- **Learning message** — a message filed before the target to prepare classification history. Its proposals are
  approved or discarded, and the case waits until that decision is available to memory.

Ordinary extraction cases have only a target message. Memory cases also have one or more learning messages.

```yaml
caseId: 1-001-expense-statement
steps:
  - action: verify-fixture
  - action: file-target-message
  - action: wait-for-terminal-outcome
    message: target
  - action: inspect-application-state
finally:
  - action: collect-evidence
  - action: cleanup-case-data
  - action: verify-fixture-restored
```

| Field        | Required                             | Rule                                                                                        |
|--------------|--------------------------------------|---------------------------------------------------------------------------------------------|
| `caseId`     | yes                                  | equals the id in the bound case YAML                                                        |
| `steps`      | yes                                  | non-empty ordered action list; first action is `verify-fixture`                             |
| `finally`    | yes                                  | ordered closing actions: `collect-evidence`, `cleanup-case-data`, `verify-fixture-restored` |
| `action`     | every entry in `steps` and `finally` | an action defined in Action Reference                                                       |
| `message`    | `wait-for-terminal-outcome` only     | `target` or an existing `learning[].id` from the bound case; no implicit default            |
| `learningId` | learning actions listed below only   | an existing `learning[].id` from the bound case                                             |

`learningId` is required for `file-learning-message`, `verify-learning-proposals`,
`set-learning-classification`, `resolve-learning-proposals`, and `wait-for-memory-readiness`.
Other actions take no parameters. Missing required parameters or unknown references are setup errors.

## Action Reference

### Triggers and User Actions

| Action                        | Required behaviour                                                                                  |
|-------------------------------|-----------------------------------------------------------------------------------------------------|
| `file-learning-message`       | file the case's `learning` entry selected by `learningId`; retain its message and proposal IDs      |
| `file-target-message`         | file `message` from the case YAML and retain its ID                                                 |
| `set-learning-classification` | resolve declared grouping/category through the catalogue API and apply it to the learning proposals |
| `resolve-learning-proposals`  | apply the learning entry's approve/discard decision through the application action                  |

### Waiting

| Action                      | Required behaviour                                                                                         |
|-----------------------------|------------------------------------------------------------------------------------------------------------|
| `wait-for-terminal-outcome` | poll the message named by `message` (`target` or a learning ID); `RECORDED` is required for learning setup |
| `wait-for-memory-readiness` | poll the persisted learning message and decision state using the readiness contract                        |

### State and Expectation Checks

`verify-call-budget-enforcement` checks that the case's `executionLimits` are enforced before provider and MCP
dispatch. If unavailable, stop with a setup error before filing the target message.

| Action                      | Required behaviour                                                                              |
|-----------------------------|-------------------------------------------------------------------------------------------------|
| `verify-fixture`            | verify Fixture v1 before filing any message                                                     |
| `verify-learning-proposals` | verify the learning entry's `proposalCount`; report mismatch as setup error                     |
| `inspect-application-state` | collect the target outcome and attributed proposals through REST; evaluate declared constraints |
| `inspect-mcp-calls`         | evaluate only declared target-message MCP expectations from correlated call records             |
| `verify-fixture-restored`   | verify the postcondition before the next repetition starts                                      |

### Evidence Collection

| Action                    | Required behaviour                                                                           |
|---------------------------|----------------------------------------------------------------------------------------------|
| `collect-recall-evidence` | collect diagnostic recall records; do not turn recall IDs or ordering into golden assertions |
| `collect-evidence`        | preserve results and available diagnostic evidence before cleanup                            |

### Cleanup

| Action              | Required behaviour                                   |
|---------------------|------------------------------------------------------|
| `cleanup-case-data` | remove all case-owned state, including learning data |

### Finally Execution

Every steps YAML has a `finally` action list. It runs after success, failure, error, or cancellation. Failure of
`collect-evidence` must not prevent cleanup; failure of cleanup must not prevent the restoration check.
The first setup failure stops normal steps and enters `finally`. Blocked cases do not execute any steps.

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
