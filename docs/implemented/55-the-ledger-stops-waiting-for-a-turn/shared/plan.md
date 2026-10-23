# Plan: The Ledger Stops Waiting for a Turn — the shared schema

**Format:** 2
**Affected Modules:** `ledger-service`, `ai-connector-service`
**Spec:** [The Ledger Stops Waiting for a Turn](../spec.md)

Both builds compile every file under `proto/`, so the turn's end and the reworded hand-over answer reach both. The
end is served by the ledger and called by the connector, each in its own plan. Nothing here changes a generated
type an existing call site uses, so no call site needs stabilizing.

## Architecture Decisions

No architectural placement decisions.

## Step-by-Step Implementation Map (To-Do List)

### Stabilization

#### API Contract

- [x] ST01 · Add the turn's end to `proto/ledger_turn.proto`, as [What crosses](../the-turn-in-two-halves.md#what-crosses)
  gives it (DN05, three values; DF08, a service of its own), after the existing messages:
  - writes: `proto/ledger_turn.proto`
  ```proto
  // Tells the ledger how a turn it handed over ended. Carries the credential the ledger minted for the turn as
  // metadata, `authorization: Bearer <token>`, and ends the turn of the person and the message it names. Taking an
  // end twice, or for a turn already settled, changes nothing. UNAVAILABLE, or no answer in time: the end was not
  // taken; send it again. UNAUTHENTICATED: the credential does not hold; never send it again.
  service LedgerTurnEndService {
    rpc EndTurn(EndTurnRequest) returns (EndTurnResponse);
  }

  // How a turn ended. `TURN_END_UNSPECIFIED`, or a value the ledger does not know, is read as `TURN_END_FAILED`.
  enum TurnEnd {
    TURN_END_UNSPECIFIED = 0;
    // The turn finished.
    TURN_END_ACTED_ON = 1;
    // The provider, the ledger's tools or the turn operations failed the turn as unavailable, or the turn ran past
    // its bound.
    TURN_END_UNAVAILABLE = 2;
    // Anything else failed the turn.
    TURN_END_FAILED = 3;
  }

  message EndTurnRequest {
    TurnEnd end = 1;
  }

  // Empty: the end was taken.
  message EndTurnResponse {}
  ```
- [x] ST02 · Reword the answer of `ExtractIntents` in `proto/intent_extraction.proto` to acceptance, as
  [The connector's answer to a hand-over](../the-turn-in-two-halves.md#the-connectors-answer-to-a-hand-over) gives
  it (DN01, busy before accepting):
  - writes: `proto/intent_extraction.proto`
  ```proto
  // Empty: the turn was accepted and runs on; how it ended reaches the caller through
  // `bot.finance.ledger.v1.LedgerTurnEndService`. A turn already running for the same person and message is
  // accepted again and not run twice. RESOURCE_EXHAUSTED: as many turns run as the service allows; nothing was
  // accepted. Any other failure is a gRPC status.
  message ExtractIntentsResponse {}
  ```

#### Closing item

- [x] ST03 · Both modules compile, pass `CleanArchitectureTest`, and keep their existing suites green:
  `tools/agent-test/agent-test.sh --module ledger-service --all` and
  `tools/agent-test/agent-test.sh --module ai-connector-service --all`
  - writes: none

## Open Questions

None.
