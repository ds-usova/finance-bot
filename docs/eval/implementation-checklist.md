# Implementation Checklist

Development work required to execute the approved specification. These tasks do not belong to specification completion.

- [ ] **Local environment:** provision fresh Ledger and AI Connector databases and isolated Redis state; configure
  the normal application services to use them. Reuse the application's observability stack rather than creating an
  evaluation-only stack. Dispose of evaluation-owned state after the run, not shared infrastructure.
  See [Environment and fixtures](environment-and-fixtures.md) and the
  [step-by-step local environment plan](implementation/local-env-setup.md).
- [ ] **User setup — runner:** automate sign-in through the existing session API using a synthetic login payload
  signed with the isolated environment's test bot token; retain session/CSRF cookies and set HUF through preferences.
- [ ] **Discard — Ledger REST API:** implement the approved operation to discard pending proposals by ID and publish
  the decision for memory. See [Application boundary](application-boundary.md).
- [ ] **Runner:** load case/steps YAML, execute a selected subset or the full corpus, poll outcomes, read all proposal
  pages, resolve categories, and apply declared expectations. See [Case steps](case-steps.md) and [Oracle and scoring](scoring.md).
- [ ] **Memory synchronization — runner:** poll the AI Connector database with read-only credentials until learning
  decisions and embeddings are ready. See [Memory and retrieval](memory-and-retrieval.md#learned-decision-availability).
- [ ] **Cleanup — runner:** delete case-owned data directly from both PostgreSQL databases and verify fixture
  restoration, accounting for in-flight processing and change-stream events. See [Fixture restoration](environment-and-fixtures.md#fixture-restoration).
- [ ] **Measurements — services and runner:** implement correlation, timings, usage capture, MCP/recall records,
  sanitized payloads, and trace collection. See [Observability](observability.md#required-changes).
- [ ] **Call limits — AI Connector and runner:** enforce declared provider/MCP budgets before dispatch; check that
  enforcement is available before running the amplification case. See [Execution policy](execution-policy.md).
- [ ] **Artifacts and comparison — runner:** generate raw records and reports, compare runs, and support explicit
  baseline approval into the repository. See [Artifacts and baselines](artifacts-and-baselines.md).
- [ ] **Calibration:** measure learning availability and total duration; configure the approved memory-readiness
  timeout before recording the first baseline. See [Execution policy](execution-policy.md).
