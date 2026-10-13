# Plan: The Ledger Takes a Turn's Writes over gRPC — `eval-runner`

**Format:** 2
**Affected Modules:** `eval-runner`
**Spec:** [The Ledger Takes a Turn's Writes over gRPC](../spec.md)

The runner reads the ledger's call line in its new shape. A case's `mcpCalls` stay keyed by operation, so the
corpus does not change. A run file keeps its shape too, so the viewer and earlier runs still read it.

## Architecture Decisions

No architectural placement decisions.

## Step-by-Step Implementation Map (To-Do List)

### Stabilization

#### Interface-First / Build Stabilization

**Interface & Signature Sync**

- [x] ST01 · Rename the record `observe/McpCall` to `TurnCall`, its component `tool` to `operation`, still written
  to a run file as `tool`; rename `observe/McpCallLog` to `TurnCallLog`, and its test class `McpCallLogTest` to
  `TurnCallLogTest`. Every caller follows: `Attempt`, `Wiring`, `TargetTurn`, `CaseExecutor`, `Oracle`,
  `McpExpectations` and `OracleTest`, a field named `mcpCallLog` becoming `turnCallLog`. `Attempt.mcpCalls` keeps
  its name, the run file's key
  - writes: `eval-runner/src/main/java/bot/finance/eval/observe/McpCall.java`,
    `eval-runner/src/main/java/bot/finance/eval/observe/TurnCall.java`,
    `eval-runner/src/main/java/bot/finance/eval/observe/McpCallLog.java`,
    `eval-runner/src/main/java/bot/finance/eval/observe/TurnCallLog.java`,
    `eval-runner/src/test/java/bot/finance/eval/observe/McpCallLogTest.java`,
    `eval-runner/src/test/java/bot/finance/eval/observe/TurnCallLogTest.java`,
    `eval-runner/src/main/java/bot/finance/eval/Attempt.java`,
    `eval-runner/src/main/java/bot/finance/eval/Wiring.java`,
    `eval-runner/src/main/java/bot/finance/eval/execution/TargetTurn.java`,
    `eval-runner/src/main/java/bot/finance/eval/execution/CaseExecutor.java`,
    `eval-runner/src/main/java/bot/finance/eval/oracle/Oracle.java`,
    `eval-runner/src/main/java/bot/finance/eval/oracle/McpExpectations.java`,
    `eval-runner/src/test/java/bot/finance/eval/oracle/OracleTest.java`

**Configuration**

- [x] ST02 · In `eval-runner/compose.eval.yaml`, give `ledger-service` the environment
  `TURN_CALL_LOG_ENABLED: "true"` (DN07, the evaluation stack turns the line on)
  - writes: `eval-runner/compose.eval.yaml`

#### Closing item

- [x] ST03 · `tools/agent-test/agent-test.sh --module eval-runner --all` stays green; the module has no
  architecture test
  - writes: none

### Red Phase

#### TDD Unit Red Phase

- [x] RU01 · `TurnCallLog` · test: `TurnCallLogTest` · covers: `parse()`, `callsSince()` · scenarios: AC15
  - `parse()`:
    - given: a log holding only `mcp_call` lines for the message
      when: parse() is called
      then: it reads no call
    - update: premise — the class's fixtures hold `mcp_call tool=…` lines under the logger prefix
      `b.f.adapter.mcp.McpCallLine` · rewrite each as `turn_call operation=… via=mcp …` with the same values,
      under the prefix `b.f.adapter.logging.TurnCallLog`; the assertions stand
  - `callsSince()`:
    - update: `readsFromTheMarkOnward()` — the line appended after the mark becomes a `turn_call` line with
      `via=grpc` under the `TurnCallLog` prefix; also assert the call is accepted and carries one argument

### Green Phase

#### TDD Unit Green Phase

- [x] GU01 · `TurnCallLog` · test: `TurnCallLogTest`

### Post-Implementation Steps

#### Documents

- [ ] PI01 · The [README](../../../eval-runner/README.md), the header comment of `eval-runner/eval.sh` and the
  javadoc of `TurnCall` and `TurnCallLog` name the `turn_call` line where they name `mcp_call`. The README's
  "Ledger console" row also says the ledger must run with `TURN_CALL_LOG_ENABLED=true`, since a stack `eval.sh`
  did not start writes no line otherwise
  - writes: `eval-runner/README.md`, `eval-runner/eval.sh`,
    `eval-runner/src/main/java/bot/finance/eval/observe/TurnCall.java`,
    `eval-runner/src/main/java/bot/finance/eval/observe/TurnCallLog.java`

## Open Questions

None.
