# AI Connector Evaluation

Status: draft. This is the entry point for the evaluation suite. Each decision belongs to one linked topic
document. When every `must-decide` question is settled, these documents together are the approved evaluation
specification.

## Objective

Build a repeatable evaluation suite that sends a versioned corpus through the application's real AI extraction
and memory paths. Each run compares quality and operational behaviour with an approved baseline.

The suite must make a connector change answerable in four dimensions:

| Dimension | Question                                                                  |
|-----------|---------------------------------------------------------------------------|
| Quality   | Did the message produce the intended proposals or correctly produce none? |
| Behaviour | Which MCP tools were called, how often, and with which result?            |
| Latency   | How long did the turn, provider round-trips, and MCP calls take?          |
| Cost      | How many input and output tokens did the provider report?                 |

This evaluates probabilistic application behaviour. It complements deterministic unit, integration, and system
tests. It does not replace their pass/fail contract.

## Specification Map

The specification is split by subject:

| Document                                                | Owns                                                                          |
|---------------------------------------------------------|-------------------------------------------------------------------------------|
| [Application boundary](application-boundary.md)         | components exercised, entry point, terminal outcome, and inspection boundary  |
| [Environment and fixtures](environment-and-fixtures.md) | evaluation user, catalogue, currency, date, memory, and reset rules           |
| [Corpus and case format](cases.md)                      | English corpus composition, case schema, tags, and expected outcomes          |
| [Case steps](case-steps.md)                             | steps files, case binding, and lifecycle shapes                               |
| [Memory and retrieval](memory-and-retrieval.md)         | retrieval scenarios and their effect on later messages                        |
| [Oracle and scoring](scoring.md)                        | proposal matching, case verdicts, aggregate quality measures, and gate policy |
| [Observability](observability.md)                       | latency, provider calls, tokens, MCP calls, metric sources, and attribution   |
| [Artifacts and baselines](artifacts-and-baselines.md)   | run metadata, stored files, comparisons, and baseline lifecycle               |
| [Execution policy](execution-policy.md)                 | ordering, repetitions, timeouts, and local invocation                         |

## Specification Completion

- [x] Every `must-decide` question has an answer in its owning document.
- [ ] The hundred-case corpus and its expected outcomes are reviewed.
- [ ] The application boundary and fixture are approved.
- [ ] Case verdicts and aggregate scores are unambiguous.
- [ ] Every required measurement has a precise definition and attribution rule.
- [ ] Run artifacts contain enough metadata for a valid comparison.
- [ ] Execution and baseline lifecycle rules are approved.

## Open Questions / Blockers

None.
