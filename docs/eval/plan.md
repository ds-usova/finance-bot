# AI Connector Evaluation

Status: ready for implementation. This is the entry point for the evaluation suite. Each decision belongs to one linked topic
document. Topic documents and case expectations have been reviewed, and the case/document consistency findings
have been resolved. Specification completion is finished; development tasks are tracked in the implementation checklist.

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
| [Corpus and case format](cases.md)                      | English corpus composition, case schema, coverage, and expected outcomes      |
| [Case steps](case-steps.md)                             | steps files, case binding, and lifecycle shapes                               |
| [Memory and retrieval](memory-and-retrieval.md)         | retrieval scenarios and their effect on later messages                        |
| [Oracle and scoring](scoring.md)                        | proposal matching, case verdicts, aggregate quality measures, and gate policy |
| [Observability](observability.md)                       | latency, provider calls, tokens, MCP calls, metric sources, and attribution   |
| [Artifacts and baselines](artifacts-and-baselines.md)   | run metadata, stored files, comparisons, and baseline lifecycle               |
| [Execution policy](execution-policy.md)                 | ordering, repetitions, timeouts, and local invocation                         |
| [Implementation checklist](implementation-checklist.md) | development tasks, including the missing REST discard operation               |

## Specification Completion

- [x] Review the topic documents and settle the evaluation design decisions.
- [x] Approve all 59 case messages and expectations in [Composition](cases.md#composition).
- [x] Check user setup and cleanup feasibility: automated session sign-in and direct PostgreSQL cleanup are documented.
- [x] Cross-check the approved YAML files against the documents: every field, action, assertion, and required artifact
  must have a defined meaning. Record specific discrepancies rather than restarting document review.
- [x] Resolve the discrepancies from that check in their owning documents; bring only new behaviour decisions to the user.
- [x] Mark the specification ready for implementation when the consistency check has no unresolved findings.

## Open Questions / Blockers

Consistency check completed on 2026-10-12: all 59 case files and 59 dedicated steps files parse successfully.
Numbering, composition links, case/steps bindings, learning references, proposal item counts, required MCP/budget
checks, and opening/finally sequences passed structural checks. No application or provider run was performed.

All recorded consistency findings are resolved. No open questions or blockers are currently recorded.
