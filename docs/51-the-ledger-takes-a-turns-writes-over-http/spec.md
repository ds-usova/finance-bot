# Spec: The Ledger Takes a Turn's Writes over gRPC

**Format:** 2
**Approved:** ds-usova, 2026-10-14, a847d93923c6
**Affected Modules:** `ledger-service`, `ai-connector-service`, `eval-runner`

## Objective

The language model files spending under the wrong category too often. Two tasks move that choice to a classifier
that answers with a probability per category, and leave the model to read the message.

- **Task 51, this one:** the ledger gives the connector what it needs to file a turn's results itself.
- **Task 52:** the connector reads the message with the model, picks each category with the classifier and files
  the result, behind a deployment setting. The tool-calling turn stays the default.

The connector filing results itself needs two things the ledger does not offer today. It needs the person's whole
catalogue up front, since no model asks for one grouping's categories. It needs to file an expense and record a
period as the turn's caller without the tool protocol. The same rules must apply as for the tools. A browser must
never reach these operations. Nothing calls them until task 52 lands.

Every call made for a turn — through a tool or through these operations — leaves one log line of one shape, which
the evaluation runner reads.

## Requirements

- **RQ01:** The holder of a turn's credential can file a pending expense under a grouping and a category, and gets
  the stored entry back.
- **RQ02:** The holder of a turn's credential can record a period the message asked about, and the message's
  report totals it.
- **RQ03:** An expense or a period the tools would refuse is refused here too, for the same reason, and nothing is
  stored.
- **RQ04:** Only a turn's credential is accepted, and a browser has no way to reach the operations.
- **RQ05:** The connector receives every grouping and the categories under it with each turn.
- **RQ06:** The ledger's tools answer and record exactly as before.
- **RQ07:** Where a deployment enables it, every call made for a turn, whichever way it arrives, leaves one line of
  one shape, and the evaluation runner checks a case's expected calls against those lines.

## Acceptance Scenarios

Four entry points: the two operations in [Turn operations](turn-operations.md), the extraction request, and the
evaluation runner reading a run's log.

### Filing an expense

- **AC01:** an expense is filed
  - Given: a person whose catalogue holds `Dining` with `Cafés`, and a credential minted for their message
  - When: the caller files `Dining`, `Cafés`, `coffee`, no merchant, `4.50`, `EUR`
  - Then: the call answers the stored entry. The entry is stored pending under that message, and the message's
    report lists it.
  - Proves: RQ01

- **AC02:** the grouping is unknown
  - Given: the person has no grouping named `Snacks`
  - When: the caller files an expense under `Snacks`
  - Then: the call is refused as an invalid argument repeating `Snacks`, and nothing is stored
  - Proves: RQ03

- **AC03:** the category is not under that grouping
  - Given: `Dining` holds no category named `Fuel`
  - When: the caller files an expense under `Dining`, `Fuel`
  - Then: the call is refused as an invalid argument naming both, and nothing is stored
  - Proves: RQ03

- **AC04:** a field breaks an expense's invariants
  - Given: a credential minted for a person's message
  - When: the caller files a blank description, a malformed currency code, or an amount its currency cannot record
  - Then: the call is refused as an invalid argument naming the field at fault, and nothing is stored
  - Proves: RQ03

### Recording a period

- **AC06:** a period is recorded
  - Given: a credential minted for a person's Telegram message
  - When: the caller records `2026-10-12` to `2026-10-12`
  - Then: the call answers the two days. The message's report opens with that day's totals.
  - Proves: RQ02

- **AC07:** a period is unreadable
  - Given: a credential minted for a person's message
  - When: the caller records a day not written `YYYY-MM-DD`, or a last day before the first
  - Then: the call is refused as an invalid argument, and nothing is stored. An unreadable day is named with its
    value. A reversed period is refused as ending before it starts.
  - Proves: RQ03

### Either operation

- **AC05:** the store is unavailable
  - Given: the store refuses reads or writes
  - When: the caller files a valid expense, or records a valid period
  - Then: the call fails as unavailable, naming nothing internal, and nothing is stored
  - Proves: RQ03

- **AC08:** the caller holds no turn credential
  - Given: either operation
  - When: the call carries no credential, a session token, an expired token, or a token of another issuer or
    audience
  - Then: the call is refused as unauthenticated, and nothing is stored
  - Proves: RQ04

- **AC09:** the credential names no message
  - Given: a token that verifies but carries no message reference
  - When: the caller calls either operation
  - Then: the call is refused as unauthenticated, and nothing is stored
  - Proves: RQ04

- **AC10:** the credential names no stored person
  - Given: a token whose subject no stored user carries
  - When: the caller calls either operation
  - Then: the call is refused as not found, saying the caller is unknown, and nothing is stored. A subject that is
    not a stored user's id at all is refused as an invalid argument, naming the request invalid.
  - Proves: RQ03

- **AC14:** the browser cannot reach the operations
  - Given: the web app as served, its proxy included
  - When: a page asks for any address the operations answer on
  - Then: no route leads there: the operations listen on a port of their own, which the web app never proxies
  - Proves: RQ04

### The extraction request

- **AC11:** the turn carries the catalogue
  - Given: a person whose catalogue holds groupings with categories, and one grouping with none
  - When: a Telegram message or a text filed from the web is handed to the connector
  - Then: the request carries every grouping holding a category, each with its categories by name. The grouping
    with none is absent. The grouping names and the catch-all are sent as before.
  - Proves: RQ05

- **AC12:** the tools are untouched
  - Given: the connector runs its tool-calling turn
  - When: the model lists categories, files an expense and records a period through the tools
  - Then: each call answers and stores what it did before this change
  - Proves: RQ06

### The call line

- **AC16:** the call line is off by default
  - Given: `TURN_CALL_LOG_ENABLED` unset
  - When: a turn files an expense either way
  - Then: no call line is written, and the call answers as it otherwise would
  - Proves: RQ07

- **AC13:** every call made for a turn leaves one line
  - Given: `TURN_CALL_LOG_ENABLED` on
  - When: a turn lists categories and files an expense through the tools, and another files an expense and records
    a period through the operations
  - Then: each call leaves one line naming the operation, the way it arrived, its outcome, the person, the message
    and the arguments. The tool's line and the operation's line for the same filing differ only in the way.
  - Proves: RQ07

- **AC15:** the evaluation runner reads the line
  - Given: a case expecting one accepted `create_expense_proposal` call
  - When: the run's log holds one such line, arrived either way
  - Then: the case's call expectation passes. A run whose log holds only lines of the old shape fails it.
  - Proves: RQ07

## Decisions

- **DN01:** How does the connector get the person's catalogue?
  - Answer: on the extraction request, as a field of its own. The tool-calling turn still asks one grouping at a
    time and never puts the catalogue in its prompt.
  - Basis: decided (user, 2026-10-13)

- **DN02:** Does a call to these operations write the line the evaluation runner matches?
  - Answer: yes, and the line becomes one shape for every call made for a turn, tools included, naming the way it
    arrived. The runner reads the new shape.
  - Basis: decided (user, 2026-10-13)

- **DN03:** How does the connector reach these operations?
  - Answer: over gRPC, as a second service in the shared Protocol Buffers schema, on a port of the ledger's own.
  - Basis: decided (user, 2026-10-13)

- **DN04:** `eval-runner` has no conventions file. How is it planned?
  - Answer: its conventions are set up with `init-conventions` before this task is planned.
  - Basis: decided (user, 2026-10-13)

- **DN05:** Which Protocol Buffers package does the turn service live in?
  - Answer: its own, `bot.finance.ledger.v1`, in a file of its own.
  - Basis: decided (user, 2026-10-13)

- **DN06:** Which port does the ledger serve the turn operations on, and what sets it?
  - Answer: `LEDGER_GRPC_PORT`, default `1006`, published to no host.
  - Basis: decided (user, 2026-10-13)

- **DN07:** Is the call line, which carries a person's id and spending text, written in every deployment?
  - Answer: no. `TURN_CALL_LOG_ENABLED` turns it on, off by default; the evaluation stack turns it on.
  - Basis: decided (user, 2026-10-13)

## Design Artifacts

- [Turn operations](turn-operations.md) — the catalogue, the two operations, who may call, and the call line.
