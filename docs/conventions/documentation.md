# Conventions > Writing Documentation

Applies to READMEs, `docs/conventions/**`, `docs/contracts/**`, `docs/usecases/**`, `docs/domain/**`, and the
`design.md` and `plan.md` in each `docs/<n>-<task-name>/`.

- **Concise and on point.** Every sentence earns its place. Cut preamble, restatement, and hedging.
- **State a fact once.** Each fact has one owning document; everywhere else links to it. A README does not
  re-explain what a conventions file defines, and a plan does not restate conventions — it references them.
- **One idea per sentence.** A sentence that joins two clauses with a dash, a semicolon, or a second "and" is
  two sentences. Length is the most common reason a page goes unread, and a page nobody reads documents nothing.
- **A consumer's contract page links the provider's; it never restates it.** `contracts/in/` owns the
  interface — what it takes, what it answers, what it refuses. `contracts/out/` links to that page and describes
  only this service's own side: what it sends and when, what it does with the answer, and how it behaves when
  the call fails. Listing the other side's fields, their types, or their validation rules is the duplication
  this rule exists to stop.
- **Where a schema exists, it is the contract, and the page links it.** An OpenAPI or proto file already states
  every parameter, every field, every type and every bound. A page that repeats them is a second copy that no
  build checks, so it is wrong the first time the schema moves. The page carries the tags, the operations, who
  implements each, and the link.
- **A failure belongs in the schema too.** A response's `description` says what raises it and what the message
  names; a table of conditions in prose says the same thing where no generator, no client and no documentation
  browser will ever read it.
- **An example goes in the schema, as an `example`.** It is then an artifact — validated against its own schema,
  rendered by every viewer, and available to a caller writing against the API. A JSON block pasted into a
  markdown page is checked by nobody.
- **A store's contract page documents the schema, not the statements run against it.** Tables, columns, indexes
  and what a column means; never a row per query. A query list grows with every use case, duplicates what those
  pages already say, and is the section that goes stale first.
- **No query plan.** Which index a read leads with, what is a lookup and what is a sort, how a page is cut — none
  of it is the interface, and all of it changes under a version upgrade nobody documents.
- **Do not repeat within a document either.** If a rule appears in a section, a diagram label, and a step
  description, keep the one place a reader will look and drop the rest.
- **Prose never restates a diagram.** A page carrying a component diagram does not also narrate which class calls,
  implements, or wraps which. The text carries what the diagram cannot: a field, a rule, a failure, a setting.
- **Docs describe what a thing does and why it matters to a reader** — never how it is wired. No DI, bean
  registration, annotation, or framework mechanics in a README or a diagram label.
- **No justification prose.** Give the rule, not the argument for it, unless the reasoning changes what someone
  would do.
- **Say what is, not what isn't.** Describe the thing; do not enumerate what the module lacks.
- **Prefer a table or a diagram to a paragraph.** A rule with conditions and outcomes is a table. A flow whose
  shape carries meaning is a diagram. Prose is for what neither can hold — why a rule exists, and what a reader
  would otherwise get wrong. A page that is mostly paragraphs is a page whose structure was not found.
- **Name the setting, not its current value.** A configurable value is referred to by the property or
  environment variable that sets it, never by what it happens to be set to — "for as long as
  `spring.grpc.client.channel.ai-connector.default.deadline` allows", not "for sixty seconds". The value has one
  owning document, the module's `docs/configuration.md`, and a page that repeats it is wrong the first time
  someone tunes it.
- Diagram labels are a few words. If a label needs a clause, the diagram is carrying prose that belongs in text
  — or nowhere. The format itself is [Diagrams](diagrams.md).
- **A new rule joins its siblings.** Before adding one, find where the rules of its kind already live and put it
  there — a rule filed on its own is a rule the next reader, and the next agent, does not find. `CLAUDE.md` is
  not one of those homes: it carries how an agent works this repository, not what the repository's documents
  must look like.

## A Domain Page

A page under `docs/domain/` carries **Invariants** and **Made of / held by**. An **entity** — a type with
identity, whose page describes something the store keeps rows of — carries one more section.

- **`## Lifecycle`, on every entity page.** A table of what brings the entity into being, what changes it, and
  what removes it, each naming the use case that does it. Where nothing does, the row says so: "never changed"
  and "never removed except with its user" are facts a reader needs and cannot infer.
- **The section is mandatory; a diagram inside it is not.** Add a state diagram when the entity has more than
  one state, or more than one way out. One state and one way out is a table row, and drawing it spends a screen
  saying what the row already said.
- **A value object gets no Lifecycle section.** A value is constructed and validated, never created or removed —
  which is what **Invariants** already covers. Adding one describes a life it does not have.
- **Do not invent a state the schema does not have.** Where the "state" is really a different table or a
  different type, the diagram says so. A status drawn on an entity that has no status column documents a model
  nobody implemented.
- **A lifecycle crossing two entities is drawn once**, on the page where the branch happens, and the other page
  links it.

An architectural decision record follows [its own lifecycle](adr.md) on top of these.
