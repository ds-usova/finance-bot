# The Runner Measures a Classifier Turn

**Format:** 2
**Approved:** ds-usova, 2026-10-14, 610f87ed1ed4
**Affected Modules:** `eval-runner`

## Objective

Closes BT09

Task 52 gave the connector a second extraction mode, `classifier`, chosen at start by `EXTRACTION_MODE`. The runner
cannot yet tell the two modes apart, so a run against a classifier connector cannot be read or compared with a
tool-calling one.

Measured against the corpus, one case's call expectation fails for a reason that is not the turn's fault:
`7-004-category-lookup` expects at least one accepted `list_categories` call, which a classifier turn never makes.
The case drops that expectation (DN01 — 7-004 drops its `mcpCalls`). The four cases expecting `list_categories` `count: 0` hold in both modes. `9-011` is blocked in every run today, so
its `maximumCount: 2` is measured in neither.

The measurement found two counts a classifier turn leaves wrong or missing. The runner's ledger call count reads
only the meter of calls arriving over MCP, so a classifier turn's ledger calls read as zero. The classifier's own
call is not measured at all.

## Requirements

- **RQ01:** Each attempt records which extraction mode its target turn ran in.
- **RQ02:** A run records which mode it ran in, and says so when its attempts disagree.
- **RQ03:** No case holds a classifier turn to a call only a tool-calling turn makes.
- **RQ04:** A classifier turn's classifier calls, and the time they took, are measured.
- **RQ05:** A classifier turn's ledger calls are counted.
- **RQ06:** Comparing two runs shows each run's mode.

## Acceptance Scenarios

### Running a case

- **AC01:** A tool-calling turn is recorded as one
  - Given: the connector runs with `EXTRACTION_MODE` at `tools`
  - When: an attempt files its target message and the turn finishes
  - Then: the attempt records the mode `tools`
  - Proves: RQ01

- **AC02:** A classifier turn is recorded as one
  - Given: the connector runs with `EXTRACTION_MODE` at `classifier`
  - When: an attempt files its target message and the turn finishes
  - Then: the attempt records the mode `classifier`
  - Proves: RQ01

- **AC14:** A turn the connector never counted
  - Given: both services' metrics are scraped, and the connector refuses the target turn before running it
  - When: the attempt is evaluated
  - Then: the attempt records no mode
  - Proves: RQ01

- **AC19:** The category case against a classifier turn
  - Given: the connector runs in `classifier` mode
  - When: case `7-004-category-lookup` is evaluated
  - Then: no `list_categories` check is recorded, and the verdict follows from the outcome and the proposal
    checks alone
  - Proves: RQ03

- **AC20:** The category case against a tool-calling turn
  - Given: the connector runs in `tools` mode
  - When: case `7-004-category-lookup` is evaluated
  - Then: no `list_categories` check is recorded, and the outcome and proposal checks are evaluated as today
  - Proves: RQ03

- **AC06:** An unscoped expectation holds in both modes
  - Given: the connector runs in `classifier` mode, and case `9-007-prompt-like-instruction` expects
    `list_categories` `count: 0` with no mode scope
  - When: the attempt is evaluated
  - Then: the `list_categories` check is evaluated, and passes
  - Proves: RQ03

- **AC07:** The classifier's call is measured
  - Given: the connector runs in `classifier` mode
  - When: an attempt's target turn files one expense
  - Then: the attempt's measurements hold one classifier call and the seconds it took
  - Proves: RQ04

- **AC08:** A tool-calling turn makes no classifier call
  - Given: the connector runs in `tools` mode
  - When: an attempt's target turn finishes
  - Then: the attempt's measurements hold zero classifier calls
  - Proves: RQ04

- **AC09:** A classifier turn's ledger calls are counted
  - Given: the connector runs in `classifier` mode
  - When: an attempt's target turn files one expense and asks about one period
  - Then: the attempt's measurements count two ledger calls
  - Proves: RQ05

- **AC15:** A tool-calling turn's ledger calls count as before
  - Given: the connector runs in `tools` mode
  - When: an attempt's target turn makes ledger calls over MCP
  - Then: the attempt's ledger call count equals what the runner records for the same turn today
  - Proves: RQ05

- **AC16:** Counts from an unreadable scrape
  - Given: a service's metrics cannot be scraped before or after the target turn
  - When: the attempt is recorded
  - Then: the counts read from that service — classifier calls and seconds for the connector, ledger calls for the
    ledger — hold no value
  - Proves: RQ04, RQ05

### Recording a run

- **AC10:** A run in one mode
  - Given: every recorded attempt of a run recorded the mode `classifier`
  - When: the run file is written
  - Then: the run's summary records the mode `classifier`
  - Proves: RQ02

- **AC17:** A run where some attempts recorded no mode
  - Given: some recorded attempts recorded `classifier` and the rest recorded no mode
  - When: the run file is written
  - Then: the run's summary records the mode `classifier`
  - Proves: RQ02

- **AC11:** A run whose attempts disagree
  - Given: one recorded attempt recorded `tools` and another `classifier`
  - When: the run file is written
  - Then: the run's summary records its mode as mixed
  - Proves: RQ02

- **AC12:** A run where no attempt's mode was read
  - Given: no recorded attempt recorded a mode
  - When: the run file is written
  - Then: the run's summary records no mode
  - Proves: RQ02

### Comparing two runs

- **AC13:** Two runs in different modes
  - Given: one run recorded `tools` and the other `classifier`
  - When: the two are compared
  - Then: the comparison names each run's mode, says the modes differ, and compares them as it does any two runs
  - Proves: RQ06

- **AC18:** A run written before this change
  - Given: one run file carries no mode, because it was written before this change
  - When: it is compared with a run that recorded a mode
  - Then: the comparison shows `n/a` for the older run's mode, and does not say the modes differ
  - Proves: RQ06

## Decisions

- **DN01:** How does a case keep 7-004's `list_categories` expectation from failing a classifier turn?
  - Answer: 7-004 drops its `mcpCalls`. It keeps its outcome and proposal expectations, and its notes stop claiming
    a catalogue lookup. The case format gains no mode.
  - Basis: decided (user, 2026-10-14)

- **DN04:** How is a check scoped out of the attempt's mode recorded?
  - Answer: Moot while DN01 leaves no scoped expectation. Were a scope ever added, a scoped-out check is left out of
    `checks`, as an undeclared expectation is.
  - Basis: decided (user, 2026-10-14)

## Design Artifacts

- [What a run records](what-a-run-records.md) — the new fields, where each is read from, and what an unreadable
  source leaves.
