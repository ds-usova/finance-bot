# Plan Log: The Ledger Takes a Turn's Writes over gRPC — `eval-runner`

## Review Findings

- **RF01:** RU01's `via=grpc` parse scenario added no coverage the rewritten fixtures did not already give.
  - Resolution: mechanical
  - Action: applied — dropped it; the `readsFromTheMarkOnward()` update now also asserts acceptance and an argument.

- **RF02:** RU01's fixture rewrite kept the `McpCallLine` logger prefix the ledger no longer writes.
  - Resolution: mechanical
  - Action: applied — the premise and the appended line use the `TurnCallLog` prefix.

- **RF03:** PI01 did not tell a reader running against another stack that the line needs `TURN_CALL_LOG_ENABLED`.
  - Resolution: mechanical
  - Action: applied — PI01's README edit says the ledger must run with it on.

- **RF04:** `McpCall`, its `tool()` component and `McpCallLog` now read calls that arrived over gRPC too, so their
  names no longer fit; the plan neither keeps them on record nor renames them. Keeping them matches the corpus key
  `mcpCalls`, `McpExpectations` and the `mcpCalls.<tool>` check names, which the spec leaves unchanged. Renaming
  (`TurnCall`, `TurnCallLog`, `operation()`) changes RU01 and GU01's target and test classes.
  - Resolution: decision
  - Action: answered — "Rename to TurnCall / TurnCallLog / operation()". ST01 renames them and their callers;
    RU01 and GU01 target `TurnCallLog`. The run file keeps its `tool` key, which `run-viewer.html` and earlier
    runs read.

## Suite Runs

- baseline · tree 01227be1deff13957ca968d22a81016fa701df6a · total 20 · skipped 0 · green · paths: eval-runner
- stabilization · tree 16e73c9815f77f97a10d95ca0b834a4ca79152bd · total 20 · skipped 0 · green · paths: eval-runner

- green · tree a1d0a1890deb720ef236238589fb68902166c9f3 · total 21 · skipped 0 · green · paths: eval-runner
## Stubs

Marker: `stub-intent:`

