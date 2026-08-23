# Review: Meter the Extraction Pipeline

**No defects and no refactoring candidates open; two manual checks remain.**

## Manual test

Both checks are `infrastructure` — the dashboards' rendering is what no test in this repository can see.

**[ ] `infrastructure` — the ledger dashboard's new Extraction row renders against live meters**

- **Given** the compose stack up, a Telegram turn processed and a proposal accepted, Grafana open on the
  Ledger Service dashboard
- **When** it renders
- **Then** the four Extraction panels (tool calls, rejection reasons, turn outcomes, proposals resolved) show
  data, not "No data"

**[ ] `infrastructure` — the connector dashboard's new Extraction and Memory rows render against live meters**

- **Given** the same stack and traffic, Grafana open on the AI Connector Service dashboard
- **When** it renders
- **Then** the ten new panels — the latency pairs, token usage, tool errors, rounds per turn, the recall pair,
  pending entries and dropped deliveries — show data or an honest zero, not "No data"
