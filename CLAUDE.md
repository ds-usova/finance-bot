# Finance Bot — Instructions

## Tools

[`tools/`](tools/README.md) holds the scripts that answer recurring questions about this repository — what a
dependency contains, what a build did. Check its table before assembling a shell pipeline for the same thing.

## Reading files

Use the Read, Glob, and Grep tools to inspect files and directories — not `cat`, `find`, `sed`, or shell loops.
The file tools run unprompted; shell equivalents (especially wrapped in `cd ... &&` or `for f in ...; do cat; done`)
trigger permission prompts and can't be allowlisted around.

## Writing docs and plans

Applies to READMEs, `docs/conventions/**`, and plan files in `docs/`.

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
- Diagram labels are a few words. If a label needs a clause, the diagram is carrying prose that belongs in text
  — or nowhere.
