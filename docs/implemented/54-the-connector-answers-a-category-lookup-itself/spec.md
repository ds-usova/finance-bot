# The Connector Answers a Category Lookup Itself

**Format:** 2
**Approved:** ds-usova, 2026-10-14, 962fd643c407
**Affected Modules:** `ai-connector-service`

## Objective

Closes BT08

In the tools mode the model asks for one grouping's categories at a time, through `list_categories`, and every such
call is a round trip to the ledger. Since task 51 the extraction request carries the person's whole catalogue, so
the answer is already in hand when the model asks.

Measured against the code: the tool-calling turn passes the groupings and the catch-all on, and drops the
catalogue. Every `list_categories` call goes to the ledger over MCP. The ledger reads the same categories the
catalogue was built from, one grouping at a time. The catalogue holds only groupings with at least one category,
and the request offers the model exactly those groupings. A grouping the model may be told about is therefore always
in the catalogue.

This task answers `list_categories` inside the connector, from the catalogue. The model still asks one grouping at
a time, and the catalogue never enters its prompt (task 51, DN01 — the catalogue rides the request, not the
prompt).

## Requirements

- **RQ01:** In the tools mode, the model learns a grouping's categories without the connector calling the ledger.
- **RQ02:** The model is told the same categories, in the same order, that the ledger would have told it.
- **RQ03:** A name the catalogue does not hold as a grouping is refused the way the ledger refuses it, and the turn
  goes on.
- **RQ04:** Recording an expense and asking what was spent still reach the ledger's tools as published.
- **RQ05:** The model's prompt is unchanged: it still names the groupings and never the categories.
- **RQ07:** An operator sees how many lookups are answered and how many are refused.
- **RQ08:** No evaluation case holds a turn to a lookup count the evaluation can no longer see.

## Acceptance Scenarios

### A lookup in the tools mode

- **AC01:** A grouping the catalogue holds
  - Given: the request's catalogue holds the grouping `Food` with the categories `Groceries` and `Restaurants`
  - When: the model asks for `Food`'s categories
  - Then: the model is answered `Groceries` and `Restaurants`, in that order, in the form the ledger's lookup
    answers; the ledger receives no lookup
  - Proves: RQ01, RQ02

- **AC02:** A category's name asked as a grouping
  - Given: the catalogue holds `Groceries` as a category under `Food`, and no grouping named `Groceries`
  - When: the model asks for `Groceries`'s categories
  - Then: the call is refused, saying `Groceries` is a category, not a grouping; the turn goes on
  - Proves: RQ03

- **AC03:** A name the catalogue does not hold at all
  - Given: the catalogue holds neither a grouping nor a category named `Travel`
  - When: the model asks for `Travel`'s categories
  - Then: the call is refused, repeating `Travel` as a grouping that is not stored; the turn goes on
  - Proves: RQ03

- **AC04:** A blank name
  - Given: any catalogue
  - When: the model asks for the categories of a blank grouping name
  - Then: the call is refused as naming no grouping; the turn goes on
  - Proves: RQ03

- **AC05:** The same grouping asked twice in one turn
  - Given: the catalogue holds `Food`
  - When: the model asks for `Food`'s categories twice
  - Then: both calls are answered alike, and the ledger receives no lookup
  - Proves: RQ01

- **AC06:** One lookup tool is offered
  - Given: the ledger still publishes its own `list_categories`
  - When: the model is prompted
  - Then: it is offered exactly one `list_categories`, answered here, beside the ledger's other tools
  - Proves: RQ01, RQ04

- **AC07:** An expense is recorded after a lookup
  - Given: the model has looked up `Food`
  - When: it records an expense under `Food` / `Groceries`
  - Then: the recording reaches the ledger's `create_expense_proposal` as today
  - Proves: RQ04

- **AC08:** A spending question
  - Given: the message asks what was spent last week
  - When: the model asks for the summary
  - Then: the request reaches the ledger's `summarize_spending` as today
  - Proves: RQ04

- **AC09:** The prompt names no category
  - Given: a catalogue holding `Food` with `Groceries`
  - When: the model is prompted
  - Then: the prompt names the grouping `Food` and the catch-all, and names `Groceries` nowhere
  - Proves: RQ05

- **AC11:** Lookups on the connector's meters
  - Given: the connector's meters are scraped
  - When: a turn answers one lookup and refuses another
  - Then: the connector's lookup count grows by one under accepted and one under refused; the ledger's tool-call
    count does not grow
  - Proves: RQ07

### Evaluating a case

- **AC12:** A prompt-injection case against a tool-calling turn
  - Given: the connector runs in `tools` mode
  - When: case `9-007-prompt-like-instruction` is evaluated
  - Then: no `list_categories` check is recorded, and its other call checks, outcome and proposals are evaluated
    as today
  - Proves: RQ08

- **AC13:** The call-amplification case keeps its other bounds
  - Given: case `9-011-prompt-injection-call-amplification`
  - When: its expectations are read
  - Then: it bounds `create_expense_proposal` and `summarize_spending` as today, and `list_categories` not at all
  - Proves: RQ08

## Decisions

- **DN01:** How does the evaluation keep counting `list_categories` per message once the ledger no longer sees it?
  - Answer: It does not. Cases `9-007`–`9-011` drop their `list_categories` expectations, as `7-004` did in task
    53. The connector writes no per-message call line.
  - Basis: decided (user, 2026-10-14)

- **DN02:** Does the ledger keep serving its `list_categories` tool?
  - Answer: Yes. The ledger is unchanged, and the connector never offers the ledger's tool to the model. Removing it
    is BT11.
  - Basis: decided (user, 2026-10-14)

- **DN03:** What shows in production that lookups happen and how often they are refused?
  - Answer: A connector meter counts each lookup by outcome, accepted or refused. The connector's operations
    contract gains it. The ledger's dashboard is unchanged.
  - Basis: decided (user, 2026-10-14)

## Design Artifacts

- [Where a lookup is answered](where-a-lookup-is-answered.md) — the recording sequence with the lookup answered in
  the connector, and each name's answer.
