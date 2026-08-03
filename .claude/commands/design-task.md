---
description: Settle the design of a change before any plan exists — objective, solution, diagrams, and every judgment call the change requires, recorded as answered decisions. Runs the grill-design subagent, then puts only the genuinely open questions in front of the user.
argument-hint: [ description of the feature or task to design ]
---

# Design Task

Settle **what** the change does and **what it does when things go wrong**, and record every judgment call it makes
as an answered decision. A design question answered here costs a paragraph; the same question answered once
implementation steps exist rewrites them.

This skill produces one file and stops. It writes no checklist items, no test scenarios, and no step IDs.

> **Architecture Contract:** this framework requires the module to follow clean/hexagonal architecture — a
> dependency-free domain layer; an application layer of usecases implementing inbound ports and depending only on
> outbound ports; and adapters implementing the outbound ports and driving the inbound ones. The module's
> conventions file maps these abstract layers onto its concrete packages/folders.
>
> **Placing a class is this file's decision.** Every class the **Proposed Solution** names belongs to exactly one
> of those layers, and the component diagram is where the placement is checked — while it costs a line to move a
> class, rather than after implementation steps target it. A module that does not follow this architecture is
> outside what this framework describes.

## 1. Create the Design File

A task owns a directory under the repository-root `docs/`. Create it as `docs/<number>-<task-name>/` and write
the design inside it as `design.md` — `docs/7-create-expense/design.md`. The `plan.md` that `plan-task` writes
later joins it there, so the pair travels as one directory.

The directory carries the number and the task name; the files do not repeat them, the same way
`docs/conventions/` holds `testing.md` rather than `conventions-testing.md`.

> **Numbering rule:** `<number>` is one more than the highest already in use, scanning the directory names
> `<number>-*` in **both** `docs/` and `docs/implemented/`. The number and the task name are the change's, not
> this file's.

> **Archiving rule:** active work lives in `docs/`, completed work in `docs/implemented/`. A design is complete
> when the work it describes is, so it is never archived here — this skill leaves the directory in `docs/`.

## 2. Read Module Conventions

After determining the **Affected Modules**, read `<module>/docs/conventions.md` for every affected module, and the
repo-root `docs/conventions.md` if it exists. The conventions give the stack, the layer mapping, and the file
locations the **Proposed Solution** has to be written in terms of.

If a module has no conventions file, record a `must-decide` decision asking the user to create one from
`.claude/templates/conventions-template.md`. Never silently guess a module's conventions.

## 3. Read What Already Exists

A design written from memory of the codebase is the expensive failure mode: it invents a class that is already
there under another name, or misses the sibling feature whose shape this one should mirror. Before writing anything,
read the closest existing feature end to end — its domain types, its usecase, its adapters, its migration — and the
conventions that govern them. Name it in **Context**; every later section is allowed to say "as `X` does".

The same holds for a contract a **library generates** rather than the code declaring — a tool or endpoint schema
derived from a signature, a serializer's wire form, a generated client. What reaches the wire is the generator's
reading of the annotated declaration, not the declaration. Read the generator itself before the design fixes the
shape, decompiling it from the dependency if the source is not at hand.

## 4. Design Structure

The file MUST contain these sections, in this order.

### Affected Modules

A single line at the very top, immediately after the title:

```
**Affected Modules:** `module-a`, `module-b`
```

Only the top-level modules whose code, config, or migrations change. One module is still listed.

### Objective

What needs to be achieved, and why it matters to whoever asked. A short paragraph.

### Context

What already exists that this change builds on or mirrors, with links to the files. The reader arriving cold learns
here which existing feature is the model, and every "same as X" elsewhere in the file resolves against it.

### Proposed Solution

The architectural and implementation shape of the change, in terms of the module's real packages and file names.

- A database change includes the migration content in the module's migration format.
- An API contract change includes the endpoint and schema changes.
- Name the actual files in the paths the module's conventions give them. A design that says "a repository adapter"
  rather than naming the class and its package leaves the plan to invent the name.

Organize it by architectural layer when the change spans several.

#### Diagrams

Include diagrams whenever the change introduces new classes or a new flow; a one-line stub change needs none.

**What to write them in comes from the module's conventions file** — its **Diagram Format** entry names the
language, the fenced block's language tag, and any preamble a diagram needs. Where a module names none, use
PlantUML with the bundled C4-PlantUML standard library: fenced ` ```plantuml ` blocks, and the include for the
level being drawn — `!include <C4/C4_Component>` for a C3, `!include <C4/C4_Container>` for a C2. Angle brackets,
no `.puml` extension: that resolves against PlantUML's own bundled stdlib, needing neither a network fetch nor a
relative path. Where a renderer's PlantUML predates the bundled stdlib, fall back to the raw URL for the same file
(`https://raw.githubusercontent.com/plantuml-stdlib/C4-PlantUML/master/C4_Component.puml`, or `…/C4_Container.puml`).
See `.claude/templates/example-design.md` for working syntax.

What each diagram must **show**:

- **Component diagram (C4 level 3)** — always. Every new/changed class, grouped into four boundaries: **domain**,
  **application**, **adapter (inbound)**, **adapter (outbound)**. Those are the Architecture Contract's own layers,
  with the adapters split by direction because direction is what the contract turns on. Draw the dependency between
  each pair, pointing the way the dependency really runs. A design whose arrows leave the domain, or reach the
  application from an adapter by any route other than an inbound port, has drawn a contract violation — rework the
  placement now, while it is a line in a diagram.
- **Sequence diagram** — always. The flow from the entry point, through the usecase, to the outbound port(s), with
  `alt`/`else`/`end` fragments for the alternative branches: a validation failure, a not-found case, an outbound
  call erroring. Every branch a **Decisions** entry settles appears as a fragment, and those fragments are what the
  red phase turns into unhappy-path test scenarios. A straight-line happy path means the failure modes were never
  designed, and the tests for them will not exist either.
- **Container diagram (C4 level 2)** — only when **Affected Modules** lists more than one. Each module as a
  `Container(...)`, and what crosses between them: the call, the message, the shared table. A cross-module design
  carrying only a C3 shows two sets of classes and not the thing that joins them, which is exactly where the
  contract between the modules lives.

### Decisions

Every judgment call the change requires, one entry each, in this exact format:

```
- **D1:** [the question, one line]
- Answer: [what the change does]
- Basis: assumed — [the evidence in the repository] | decided — [what the user chose, and when] | deferred — [what
  is out of scope, and what would bring it back] | must-decide — [what the repository does not say]
```

Numbered `D1`, `D2`, … assigned once and never renumbered: an entry that is answered, withdrawn, or reversed keeps
its number, so a reference from a commit, an ADR, or the plan stays valid for the life of the change.

The four bases, and what each obliges:

| Basis         | Means                                                                             | `Answer:`                                                      |
|---------------|-----------------------------------------------------------------------------------|----------------------------------------------------------------|
| `assumed`     | the repository already answers it — sibling code, conventions, an ADR, the schema | written, with the evidence cited in `Basis:`                   |
| `decided`     | the user chose between defensible options                                         | written, with the choice attributed                            |
| `deferred`    | real, but out of scope for this change                                            | written as what happens instead, plus what would bring it back |
| `must-decide` | a product, operational, or business rule that exists nowhere yet                  | empty                                                          |

**Answer against the repository before asking.** An `assumed` entry with cited evidence is worth more than a
question, and a design that hands back fifteen open questions is a design that did no work. Ask only what the
repository genuinely cannot answer, and say in `Basis:` precisely what it does not say — so the user answers a
question rather than picks from a menu.

**The `assumed` basis is a claim, not a hedge.** Cite the file, class, or ADR. An assumption with no evidence line
is a `must-decide` wearing a disguise, and it will be found by the grill or, more expensively, in production.

**Reading code this repository does not own is not evidence of what it does at runtime.** Where a decision turns
on how a dependency behaves — which of its layers acts first, what it does with a value of the wrong shape — its
source shows what code exists, not what runs. `assumed` is available only when something in the tree already
exercises that path and what it was *observed* to produce is cited. Otherwise the entry is `deferred`, naming
what would settle it. At design time the subject of the question often does not exist yet, so `deferred` is the
expected answer and costs nothing: it tells the plan to assert the invariant rather than the mechanism, and tells
the step that eventually builds it to look before asserting.

The design is **settled** when no entry carries `Basis: must-decide`.

`design.sh` checks the result: missing or out-of-order sections, duplicate IDs, an entry outside the Decisions
section, a missing or repeated `Answer:`/`Basis:`, an unrecognized basis, a basis with nothing after it, a
`must-decide` carrying an answer, any other basis carrying none, and a Design Findings section the grill never
touched. `design.sh settled` answers the separate question — whether anything is still open. The script ships with
these instructions at `scripts/design/design.sh` — under `${CLAUDE_PLUGIN_ROOT}` when installed as a plugin, under
`.claude/` in a plain checkout.

Run `validate` before invoking the grill, and both it and `settled` again before handing the design over.

### Design Findings

Populated by the `grill-design` subagent in the next step — leave a placeholder while writing the rest. The grill
appends new `D<n>` entries to **Decisions** and records here what it examined and found nothing on, so a later
reader can tell an unasked question from a considered one:

```
Grilled (<date>): [categories with no finding, in a line]
```

See `.claude/templates/example-design.md` for a complete worked example of every section above.

## 5. Invoke the Grill Subagent

Once every section above is written, spawn the **`grill-design` agent** against the design file. Use the model the
module conventions' **Sub-Agent Models** section names for deciding work; without such a section, the default model.

Never grill the design in this context instead — the agent must judge the file as written, not the reasoning that
produced it, and this session holds that reasoning.

It appends its findings to **Decisions** as new entries and writes the
**Design Findings** line.

## 6. Put the Open Questions to the User — in One Batch

Read the file's **Decisions** section back after the grill has run and act on it:

- **Try every `must-decide` against the repository once more** before it reaches the user. The grill works in a
  fresh context and does not know what this session has already read. An entry the code answers becomes `assumed`
  with its evidence, and the user never sees it.
- **Ask the rest in a single round**, via `AskUserQuestion` — every remaining `must-decide` in one batch, each with
  the options that are actually defensible and a recommendation first. A drip of one question per turn is the cost
  this skill exists to remove.
- **Write the answers back into the file** as `Basis: decided — [choice] (user, <date>)` with `Answer:` filled in.
  The chat answer is not the record; the file is. Anything the user's answer invalidates elsewhere in the file — a
  sequence diagram branch, a paragraph of the solution — is corrected in the same edit.

## 7. Hand Over

- Present the design file.
- **Report what the grill added and what step 6 answered from the repository** — the `D` numbers and a clause each.
  A decision the user cannot see recorded is a decision the user cannot catch.
- **Run `design.sh validate` and `design.sh settled`** and report what they say.

- List any entry still `must-decide`, and say that the design is unfinished while any remains.
- **Stop.** Do not plan, write code, create other files, or run build commands.

**The design file is the whole handoff, so planning starts in a fresh session.** Everything the next stage needs is
in the file by construction; this session also holds what the file deliberately leaves out — a rejected
alternative, a question the grill raised and the repository answered, a shape considered and dropped. A planner
inheriting that plans partly from context nobody else can see, and the gap only shows up when someone reads the
design on its own. Planning cold is also the format's own test: a design a fresh session cannot plan from was
underspecified, which is worth discovering now rather than during implementation.

Say so when handing over, so the user knows the stop is the design's, not an unfinished job.
