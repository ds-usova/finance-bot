---
description: Turn a finished plan into the documentation that outlives it — per-service use-case docs with diagrams, the in/out contracts with other systems, and the ADRs for the decisions it made. Writes files and reports back; narrates nothing.
argument-hint: [ implemented plan file path ]
---

# Archive Knowledge

Read a finished plan and the code it produced; write what outlives both — what the system does for its users,
what it promises the systems around it, and which decisions are settled.

Writes files, reports back, narrates nothing. Asks nothing: an input that will not resolve is a line in the
report.

## Artifacts

| Artifact      | Location                                               | Written by        |
|---------------|--------------------------------------------------------|-------------------|
| Use-case docs | `<service>/docs/usecases/<use-case>.md`                | service sub-agent |
| Contracts in  | `<service>/docs/contracts/in/<interface>.md`           | service sub-agent |
| Contracts out | `<service>/docs/contracts/out/<counterpart>.md`        | service sub-agent |
| Configuration | `<service>/docs/configuration.md`                      | service sub-agent |
| ADRs          | `docs/adr/<nnnn>-<slug>.md`                            | orchestrator      |
| Link updates  | root and service READMEs, `conventions/orientation.md` | orchestrator      |

Each is new or updated in place. A second file on the same subject is a defect.

## Input

1. **The plan** — the path given, else the one implemented in this conversation. Read it whole: an answered
   question under `Open Questions / Blockers` is often the clearest statement of a decision.
2. **The diff it produced**, and the files it touched. The plan is the lead; **the code is the source of
   truth**. Every sentence written must be traceable to code that exists; where the two disagree the code wins
   and the discrepancy goes in the report.
3. **`<module>/docs/conventions.md`** per affected module — its **Sub-Agent Models**, **Parallelism**, and
   **Version Control** sections govern this run.
4. **The existing corpus** — root README, service READMEs, `docs/usecases/`, `docs/contracts/`, `docs/adr/`.

## Gate

The plan is in `docs/implemented/`, has no `- [ ]`, and every blocker has a resolution. Otherwise write nothing
and report why.

## Stage 1 — Survey

Working material for the sub-agents, not a proposal.

**Use case** — one usecase class: the application-layer class implementing an inbound port, one document each.
The inventory is the module's usecase package, filtered to what this plan added or changed; the plan's unit
green-phase steps name them directly. Each document lists collaborators in both directions — who asks for it,
and what it depends on — and reads as product documentation: an analyst is the audience, so the usecase class
is the only code name any of it carries.

**Contract** — one per edge to a system outside the service, another service in this repository included.
Direction is from that service's side: **in** is what it serves or receives, **out** what it calls or consumes.
Every edge is written from both sides in the same run.

**Configuration** — one document per service, always updated when the plan added, removed, or changed a knob an
operator sets from outside the build. One document per service and never more: a plan that changed nothing
leaves it alone, and a plan that changed one variable still has the whole file re-read against the code, since
it is the only page anyone consults before a deploy.

**ADR** — a decision that constrains future change and whose *why* cannot be reconstructed from the code, the
schema, and the tests: a rejected alternative, an external constraint, a rule that looks arbitrary until you
know what it prevents. One decision per ADR; a plan yields one or two, often none.

## Stage 2 — Service Documentation

One sub-agent per affected service, never two in the same `docs/` folder. Prompt from
`.claude/commands/archive-service-docs-step.md` (under `${CLAUDE_PLUGIN_ROOT}` when installed as a plugin) plus:
its slice of the work list marked new or update, the plan path, its diff scope, the module conventions, and for
each edge the counterpart, which side this service is on, and the path of the use-case document on the other
side. The whole work list determines those paths, so an agent can link a file a parallel agent is still
writing.

Model: the one **Sub-Agent Models** names for deciding work. Respect the **Parallelism** cap.

**Guardrail**: every listed file exists, none outside its service, every diagram a fenced ` ```plantuml ` block,
and nothing restated that a schema, a conventions file, or a README already owns — the database schema diagram
excepted, since the migrations hold no current state to link to.

## Stage 3 — ADRs

Yours to write: a decision spanning services cannot be assembled from two agents that each saw half of it.

Number is one past the highest in `docs/adr/`, four digits, never reused.

**An ADR fits on one screen — roughly 20 lines, never more than 30.** It records one decision, and a reader
reaches for it to answer one question: why is it like this, and what may I not break? Everything past that
answer costs a re-read every time someone opens the file. A decision needing more room is two decisions.

```
# ADR <nnnn>: <the decision, stated as a fact>

- **Status:** Accepted
- **Date:** <YYYY-MM-DD>
- **Source:** [<plan title>](../implemented/<plan-file>.md)

## Context

<One paragraph: what forced a decision here instead of a default. Name the alternative only if it was
genuinely tempting.>

## Decision

<The rule, present tense. Two or three sentences.>

## Consequences

<What it costs and what must stay true. Two or three sentences, or a short bullet list.>
```

Write what the code cannot say. Skip anything a reader can get from the schema, the tests, or the diff — an ADR
that walks through the implementation has become a worse copy of it. Link to the contract and use-case docs
rather than restating them.

Reversing a decision writes a new ADR and marks the old one **Superseded by [ADR nnnn](nnnn-<slug>.md)** — never
edits it.

## Stage 4 — Reconcile

- Both sides of every new edge exist and agree on transport, operations, and failures.
- Cross-service collaborator links resolve and point back at each other: a use case linked as a collaborator
  lists the linking one in return.
- Root README: C1/C2 only when a container, an external system, or an edge between them appeared or went.
- Service README: C3 when components changed; link the new docs; boundary prose moves into the contract file
  and leaves a link.
- `conventions/orientation.md` — **Documentation References** points at `docs/adr/`, the configuration page,
  and the new folders.
- Links resolve, including the relative paths out of `docs/adr/`.

## Stage 5 — Commit

Per the module's **Version Control** policy, documentation prefix. Policy silent or against: no commit.

## Report

Files created and updated, by service · ADRs with their one-line decisions · configuration that changed ·
discrepancies between plan and code · anything left unwritten, and why.

No production code, no tests, no edit to the plan. A discrepancy is recorded, never fixed.
