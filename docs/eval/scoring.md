# Oracle and Scoring

The oracle compares the terminal written-message outcome and attributed proposals with the case's declared
constraints.

## Case Verdict

| Result | Meaning                                                                          |
|--------|----------------------------------------------------------------------------------|
| pass   | the message outcome, proposal count, and every declared field constraint match   |
| fail   | the run completes but observable application state does not match                |
| error  | setup, transport, provider, polling, inspection, or teardown prevents evaluation |

Proposal matching considers only constraints declared by the case. Undeclared and ignored fields do not affect
the verdict. Generated ids and timestamps never participate. When several proposals exist, each expected item
must match a different actual proposal.

If `items` is omitted, only the proposal count is checked. Declared proposal relations and MCP call expectations
also affect the verdict. Missing required trace evidence is an `error`, not a successful check.
Blocked cases are listed separately and excluded from verdict-rate denominators. Reports retain their IDs and
reasons so uncovered composition values remain visible.

`PARTIAL` and `FAILED` remain observable application outcomes, but no live-provider corpus case requires them as
its golden outcome. Either outcome makes the case an `error`, because it reports an execution failure rather than
the model's completed classification.

## Aggregate Scores

Every report calculates:

- case pass rate;
- proposal precision, recall, and F1 from matched expected and actual proposals;
- false-positive rate for cases expecting no proposal;
- message-outcome confusion matrix;
- pass rate per composition value;
- newly failing and newly passing cases relative to the selected baseline.

## Gate Policy

| Item              | Rule                                                              |
|-------------------|-------------------------------------------------------------------|
| Version 1 purpose | measure and approve the initial baseline                          |
| Output            | report scores and differences                                     |
| Quality threshold | none                                                              |
| Build impact      | evaluation results do not block the build                         |
| Future gate       | requires repeated-run evidence and an approved statistical policy |
