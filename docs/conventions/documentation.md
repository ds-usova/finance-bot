# Conventions > Writing Documentation

Applies to READMEs, `docs/conventions/**`, `docs/contracts/**`, `docs/usecases/**`, `docs/domain/**`, and every
document in a `docs/<n>-<name>/` directory — the `design.md`, each `plan.md`, a `rework.md`, a `bug.md` and each
`fix.md` beside it, an `upgrade.md` and its `steps.md` files, and what `review/` holds.

- **Every sentence earns its place.** Cut preamble, restatement and hedging.
- **State a fact once.** Each fact has one owning document; everywhere else links to it.
- **One idea per sentence.** A sentence that joins two clauses with a dash, a semicolon, or a second "and" is
  two sentences.
- **A consumer's contract page links the provider's; it never restates it.** `contracts/in/` owns the
  interface — what it takes, what it answers, what it refuses. `contracts/out/` describes only this service's
  own side: what it sends and when, what it does with the answer, and how it behaves when the call fails.
- **Where a schema exists, it is the contract, and the page links it.** The page carries the tags, the
  operations, who implements each, and the link. Never a parameter, a field, a type or a bound.
- **A failure belongs in the schema too**, in a response's `description`: what raises it, and what the message
  names.
- **An example goes in the schema, as an `example`.** Never a JSON block pasted into a markdown page.
- **A store's contract page documents the schema, not the statements run against it.** Tables, columns, indexes
  and what a column means; never a row per query.
- **The mapping, never the lifecycle.** What a column holds and how a domain type sits in a row belongs here.
  When a row is written, when it is read and when something deletes it is a use case's behaviour, and its page
  owns it.
- **No query plan.** Which index a read leads with, what is a lookup and what is a sort, how a page is cut.
- **Do not repeat within a document either.** Keep the one place a reader will look and drop the rest.
- **Prose never restates a diagram.** A page with a diagram does not also narrate what it draws. The text
  carries what the diagram cannot: a field, a rule, a failure, a setting.
- **Docs describe what a thing does and why it matters to a reader** — never how it is wired. No DI, bean
  registration, annotation, or framework mechanics in a README or a diagram label.
- **A conventions page names no agent.** A rule that holds only because an agent does it — committing as the work
  goes, a cap on concurrent work, what one orchestration level may not do — belongs in the module's
  `conventions/agent.md`. The exception is a page whose whole subject is how much may run at once,
  [Parallelism](parallelism.md).
- **A conventions page states the project's facts, never the framework's.** What the machine allows, what a list
  contains, what a command does. Which level runs a step, when it runs relative to archiving, what a pipeline may
  not do: those belong to `.claude/`.
- **`agent.md` is documentation too.** Define a thing by its meaning, not by the workflow stage that reaches for
  it.
- **No justification prose.** Give the rule, not the argument for it, unless the reasoning changes what someone
  would do.
- **Say what is, not what isn't.** Describe the thing; do not enumerate what the module lacks.
- **A page documents what is served now.** A retired path, a dropped field, an operation that no longer exists:
  none of them belongs on a page, and least of all a note on how one now fails.
- **Prefer a table or a diagram to a paragraph.** A rule with conditions and outcomes is a table. A flow whose
  shape carries meaning is a diagram. Prose is for what neither can hold — why a rule exists, and what a reader
  would otherwise get wrong.
- **Name the setting, not its current value** — "for as long as
  `spring.grpc.client.channel.ai-connector.default.deadline` allows", not "for sixty seconds". The value's one
  owning document is the module's `docs/configuration.md`.
- **Diagram labels are a few words.** If a label needs a clause, the diagram is carrying prose that belongs in
  text — or nowhere. The format itself is [Diagrams](diagrams.md).
- **A new rule joins its siblings.** Before adding one, find where the rules of its kind already live and put it
  there. `CLAUDE.md` is not one of those homes: it carries how an agent works this repository, not what the
  repository's documents must look like.

## A Use-Case Page

- **In and Out are lists**, one item per thing that goes in or comes out. Never one line with the items
  separated by a symbol.
- **Prerequisites.** What must already hold for the use case to run — the caller is authenticated, a user is
  stored under that identity — one line each. Two or three is a full section.
- **A page carries the flow diagram, Prerequisites, Outcomes, Collaborators, Components and References**, and
  nothing else. Components is the C3 of the components, ports and external systems the use case touches.
- **References is the last section**, and it is links only: the ADRs that decided how this use case works, and
  the use cases a reader needs next. One line each, each saying in a few words why it is worth opening. A
  collaborator the table already lists is not repeated, and a page with nothing to point at carries no section.
- **Outcomes is a table** of condition and result. It is the one place a reader learns what the use case answers
  when things go wrong.
- **The page documents this use case and no other.** Never contrast it with a sibling. Naming another use case
  as a **Collaborator**, or pointing at where an id gets its name, is a link and not a description.

## A Domain Page

A page under `docs/domain/` carries **Invariants** and **Made of / held by**. An **entity** — a type with
identity, whose page describes something the store keeps rows of — carries one more section.

- **An invariant is a bound, not a sentence.** Name the part, state the bound, stop: `limit` — `1..100`, default
  `50`. `PENDING | RECORDED` beats "two values and no others". `PENDING -> RECORDED only` beats a sentence about
  what never happens.
- **An invariant is what the type refuses, never what the refusal says.** The wording, the status and the field
  named back to a caller are the boundary's, and its contract page owns them.
- **Prose is for the invariant that has no symbol** — what the type refuses to be, and what a reader would
  otherwise get wrong. Three of those under a table of bounds is a full page.
- **Invariants is a table of `Field | Bound`**, one row per component, each field named as the code names it. A
  field bounded by another domain type links that type from its own cell. A type with no fields to bound — an
  enum — states its values and its transitions as bullets instead.
- **A bound belonging to no single field goes under the table**, never in it: how two stored instances compare,
  what the currency scales, what a reader would otherwise get wrong.
- **Made of / held by is a link list, never a second pass over the fields.** One line naming what the type is
  composed of, then one link per domain type, contract page and use case that touches it, each saying in a few
  words what it does with the type. The fields are the Invariants table's; repeating them here is the commonest
  way this page goes wrong.

- **`## Lifecycle`, on every entity page.** A table of what brings the entity into being, what changes it, and
  what removes it, each naming the use case that does it. Where nothing does, the row says so: "never changed",
  "never removed except with its user".
- **The section is mandatory. A diagram inside it is not** — add a state diagram when the entity has more than
  one state, or more than one way out.
- **A value object gets no Lifecycle section.** A value is constructed and validated, never created or removed.
- **Do not invent a state the schema does not have.** Where the "state" is really a different table or a
  different type, the diagram says so.
- **A lifecycle crossing two entities is drawn once**, on the page where the branch happens, and the other page
  links it.

An architectural decision record follows [its own lifecycle](adr.md) on top of these.
