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
- **Do not repeat within a document either.** If a rule appears in a section, a diagram label, and a step
  description, keep the one place a reader will look and drop the rest.
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

An architectural decision record follows [its own lifecycle](adr.md) on top of these.
