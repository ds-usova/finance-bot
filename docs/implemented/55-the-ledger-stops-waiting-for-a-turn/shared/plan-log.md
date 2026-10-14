# Plan Log: The Ledger Stops Waiting for a Turn — the shared schema

## Review Findings

- **RF01:** ST01's `TurnEnd` comment left `TURN_END_UNSPECIFIED`'s reading unsaid.
  - Resolution: mechanical
  - Action: applied — the comment reads it as failed, beside an unknown value.

- **RF02:** ST01's service comment named only UNAVAILABLE as a reason to resend.
  - Resolution: mechanical
  - Action: applied — the comment adds no answer in time as a resend, and UNAUTHENTICATED as never.

## Suite Runs

- baseline · tree 048cc230856ecbf280af51b7f0d34a3096eab09a · total 569 · skipped 0 · green · paths: ai-connector-service
- baseline · tree 05fd1145a8cd5dca1a037b4123c7e61fef2142e1 · total 1447 · skipped 0 · green · paths: ledger-service
- stabilization · tree 048cc230856ecbf280af51b7f0d34a3096eab09a · total 569 · skipped 0 · green · paths: ai-connector-service

## Stubs

Marker: `stub-intent:`

