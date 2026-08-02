# Conventions > Writing Documentation

Applies to READMEs, `docs/conventions/**`, `docs/contracts/**`, `docs/usecases/**`, `docs/domain/**`, and design
and plan files in `docs/`.

- **Concise and on point.** Every sentence earns its place. Cut preamble, restatement, and hedging.
- **State a fact once.** Each fact has one owning document; everywhere else links to it. A README does not
  re-explain what a conventions file defines, and a plan does not restate conventions — it references them.
- **Do not repeat within a document either.** If a rule appears in a section, a diagram label, and a step
  description, keep the one place a reader will look and drop the rest.
- **Docs describe what a thing does and why it matters to a reader** — never how it is wired. No DI, bean
  registration, annotation, or framework mechanics in a README or a diagram label.
- **No justification prose.** Give the rule, not the argument for it, unless the reasoning changes what someone
  would do.
- **Say what is, not what isn't.** Describe the thing; do not enumerate what the module lacks.
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
