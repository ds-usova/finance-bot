---
description: Turn finished work into the documentation that outlives it — per-service use-case docs with diagrams, the in/out contracts with other systems, the conventions it invalidated, and a record for each decision it made, in whatever form the repository's conventions give one. Takes an implemented plan, a finished rework, or a fixed bug. Writes files and reports back; narrates nothing.
argument-hint: [ an implemented plan, rework, or bug file path ]
---

# Archive Knowledge

Read finished work and the code it produced; write what outlives both — what the system does for its users,
what it promises the systems around it, and which decisions are settled.

Writes files, reports back, narrates nothing. Asks nothing: an input that will not resolve is a line in the
report.

## Artifacts

| Artifact      | Location                                                                       | Written by        |
|---------------|--------------------------------------------------------------------------------|-------------------|
| Use-case docs | `<service>/docs/usecases/<use-case>.md`                                        | service sub-agent |
| Domain docs   | `<service>/docs/domain/<type>.md`                                              | service sub-agent |
| Contracts in  | `<service>/docs/contracts/in/<interface>.md`                                   | service sub-agent |
| Contracts out | `<service>/docs/contracts/out/<counterpart>.md`                                | service sub-agent |
| Configuration | `<service>/docs/configuration.md`                                              | service sub-agent |
| Conventions   | wherever the conventions index puts the page whose rule the change broke       | orchestrator      |
| Decision records | wherever the repository's conventions put one, in the tier they choose            | orchestrator   |
| Link updates  | root and service READMEs, `conventions/orientation.md`                         | orchestrator      |

Each is new or updated in place. A second file on the same subject is a defect.

## Input

1. **The finished work**, and how to read it. Two kinds arrive here, and each answers this run's inputs, its
   gate, its inventory and its decision-record authorization differently:

   | Handed a    | Read                                                 |
   |-------------|------------------------------------------------------|
   | `plan.md`   | [`from-plan.md`](from-plan.md), beside this file     |
   | `rework.md` | [`from-rework.md`](from-rework.md), beside this file |
   | `bug.md`    | [`from-fix.md`](from-fix.md), beside this file       |

   Read the one that matches before anything else, and treat it as part of these instructions. A file that is
   neither: report that and write nothing.
2. **The diff it produced**, and the files it touched. The finished work is the lead; **the code is the source
   of truth**. Every sentence written must be traceable to code that exists; where the two disagree the code
   wins and the discrepancy goes in the report.
3. **`<module>/docs/conventions.md`** per affected module — what it says about **Sub-Agent Models**,
   **Parallelism**, **Version Control**, **Diagram Format**, and **how documentation is written** governs this
   run. Read it through to whatever states each of those; the writing rules below are a reminder of what matters
   most, never a substitute for the repository's own. What it does not cover falls back to this file.

   **A sub-agent is pointed at the rule, not told a paraphrase of it.** Name the file that owns a rule and let
   the agent read it there; a restatement in a prompt is a second copy that can be wrong, in the one place no
   review looks.
4. **The existing corpus** — root README, service READMEs, `docs/usecases/`, `docs/contracts/`, `docs/adr/`.

## Gate

Whatever the input's own file states, and nothing is written until it passes. Otherwise write nothing and report
why.

## Stage 1 — Survey

Working material for the sub-agents, not a proposal. **Where each inventory comes from is the input's own
file**; what each artifact is, and what it must say, is below.

**Use case** — one usecase class: the application-layer class implementing an inbound port, one document each.
Each document lists collaborators in both directions — who asks for it, and what it depends on — and reads as
product documentation: an analyst is the audience, so the usecase class is the only code name any of it carries.

**Domain** — one per type in the module's **domain layer**, entities and value objects alike. The domain layer
only: a command or any other application-layer carrier gets no page of its own, and what it constrains belongs
to the **Outcomes** of the use case that receives it. Each says what the type represents in the product's words,
lists the invariants under which it refuses to exist, and names what it is made of and what holds it. A value
object earns a page as much as an entity does, and a type with little to say gets a short one.

Invariants belong here and nowhere else. No use-case page repeats one.

**A domain page's sections are the repository's documentation conventions', not this skill's.** Where they
require an entity to document its lifecycle, write it from the module's use cases — the one that creates the
type, the one that changes it, the one that removes it — and state the absence where nothing does. That sweep is
what surfaces an entity nothing ever removes, or one two different use cases create.

**A lifecycle is written from the whole module, never from this run's work list.** A change that touched a
domain type and no use case leaves that section exactly as it stands: read off an empty work list it becomes
"never created, never removed", overwriting the correct rows in place.

**Contract** — one per edge to a system outside the service, another service in this repository included.
Direction is from that service's side: **in** is what it serves or receives, **out** what it calls or consumes.
Every edge is written from both sides in the same run.

Walk the service's outbound ports and its README's C3, and write a page for any edge that has none yet — a
database the service has always talked to has an undocumented contract just as much as one added yesterday. The
tell is a use-case page's Collaborators table with a dash where a contract link belongs.

**A page is stale as often as it is missing.** The test is whether anything in the diff changed what the page
already says, never whether the edge is new. A prompt template, a query, a tool argument, a status code or a
field name that moved invalidates every page naming it. Grep each contract page for the artifacts the diff
touched before concluding it needs no update.

**Configuration** — one document per service and never more, covering the knobs an operator sets from outside
the build. The whole file is re-read against the code whenever it is touched at all, since it is the only page
anyone consults before a deploy.

**Conventions** — a page under the repository's or a module's conventions whose **rule** the change contradicts.
A change that removes the thing a conventions page recommends leaves that page instructing the next agent to
rebuild it.

**Decision record** — the candidates the input authorizes, no more. What one is and what earns it are the
repository conventions'; most changes authorize none.

## Stage 2 — Service Documentation

One `archive-service-docs-step` sub-agent per affected service, never two in the same `docs/` folder. Pass each:
its slice of the work list marked new or update, the finished work's path, its diff scope, this file's Stage 1
rules for every artifact that slice holds, the module conventions including what they say about writing
documentation, and for each edge the counterpart, which side this service is on, and the path of the use-case
document on the other side. Those paths come from the whole work list, so an agent can link a file a parallel
agent is still writing.

**That agent is written for a plan.** Where the finished work is not one, say so in the prompt: what the file
is, that it carries no planned-versus-implemented axis, and that wherever its instructions say "the plan" they
mean this file.

Model: the one **Sub-Agent Models** names for deciding work. Respect the **Parallelism** cap. Spawn and wait as
[`templates/sub-agents.md`](../../templates/sub-agents.md) says.

**Guardrail**: every listed file exists, none outside its service, every diagram a fenced block in the language
the module conventions' **Diagram Format** section names, and nothing restated that a schema, a conventions file,
or a README already owns — the database schema diagram excepted, since the migrations hold no current state to
link to.

## Stage 3 — Decision Records

Yours, not a service agent's: a decision spanning services cannot be assembled from two agents that each saw
half of it.

**The repository's conventions own the decision record entirely** — whether it has them at all, what earns one,
where it goes, how it is numbered, what it looks like, how much of it an agent may write, and how one is
superseded. Follow the conventions index to that page and do what it says. Nothing about the artifact is
restated here, because a second copy is the one that goes stale. A repository whose conventions describe no such
record skips this stage and says so in the report.

**Record exactly what the input authorizes, and no others.** What counts as an authorization, and where the
candidates come from, is the input's own file. A decision this run believes deserves a record that is not
authorized goes in the **report**, unwritten, naming the decision and the page that holds it today — so one that
should exist is visible as a proposal rather than appearing as a fact nobody approved.

**Gate — name the document that would otherwise own the fact**, for each authorized item as you write it and
for anything you are about to propose. Say which existing page would hold this if the record did not exist: a
contract page, a use-case page's Outcomes, a domain page's invariants. That page owns it, and there is no
record. **Write the gate's answer into the report** either way.

This gate is the skill's, because it is the same rule every other stage runs on — one fact, one owner. Where the
conventions state a stricter test of their own, theirs wins.

Where the run's own work is what a candidate decides, put the facts a writer will need — the classes, the
constraint names, the migration — in the **report**, not in the record.

## Stage 4 — Align the Conventions

Yours, like the decision records: a conventions page binds every module, so it is not handed to an agent that
saw one service. Unlike them, you write this in full — a conventions page states a rule that is already true,
not a judgement about why it was chosen.

- **The trigger is the rule, not the wording.** Rewrite a page whose rule the change made false. A page whose
  examples merely aged is left alone.
- **Rewrite it to what is true now**, in the page's own voice, following the repository's documentation
  conventions. Never append a note about what it used to say.
- **Never widen a rule while correcting it.** The edit says what this change made true, and nothing about cases
  it did not touch.
- **A page named in an approved `docs:` line has been agreed.** The user read that line before the work started,
  so the page is rewritten like any other.
- **A page nothing named, whose rule records a deliberate policy rather than a description, is a report line,
  not an edit.** Name it and stop.

## Stage 5 — Reconcile

- Both sides of every new edge exist and agree on transport, operations, and failures.
- Cross-service collaborator links resolve and point back at each other: a use case linked as a collaborator
  lists the linking one in return.
- Root README: C1/C2 only when a container, an external system, or an edge between them appeared or went.
- Service README: C3 when components changed; link the new docs; boundary prose moves into the contract file
  and leaves a link.
- `conventions/orientation.md` — **Documentation References** points at `docs/adr/`, the configuration page,
  and the new folders.
- Links resolve, including the relative paths out of a decision record — where the conventions put records in
  more than one tier, two records reach the same shared file by different paths.

## Stage 6 — Review and Tighten

First spawn the `review-docs` agent, once per affected service, on that service's `docs/` folder. It writes
nothing: it reports each fact written on the wrong page, with the page that owns it and the line to write there.

- **You apply every move and delete**, page by page, in the shape the finding gives.
- Answer what it escalates against the code, or carry it into the report unanswered, naming what is missing.
- A finding you decline stays in the report with the reason.

Then run the `tighten` skill on every file this run *wrote* — a page created from scratch, or one whose section
was rewritten — one file per invocation. Tighten last, so a file is tightened once, in its final state.

- Take the cuts it reports. It removes words, never rules.
- Restore anything it dropped that no other page states. Name the restore in the report.
- A file another stage did not touch is not tightened here. Neither is one that only gained a link, a row or a
  cell: it was tight before this run and is not re-read for it. List those files in the report as skipped.

## Stage 7 — Commit

Per the conventions' **Version Control** rules, documentation prefix. Silent or against: no commit.

## Report

Files created and updated, by service · what `review-docs` found, what you applied, and what it escalated ·
what the tighten pass cut and what was restored · decision records written, each with its one-line decision, the
gate's answer, and the facts a writer will need · decisions proposed but not recorded, each with the page
holding the fact today · configuration that changed ·
**conventions pages rewritten, each with the rule that stopped being true, and any page left alone because its
rule was a policy nobody agreed to drop** ·
discrepancies between the finished work and the code · anything left unwritten, and why.

No production code, no tests, no edit to the plan or the rework file. A discrepancy is recorded, never fixed.
