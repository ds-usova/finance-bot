# The workflow framework

`skills/` holds the skills, `agents/` the sub-agents they spawn, `scripts/` the mechanics they share, and
`templates/` the material more than one of them reads.

**A skill is a directory holding `SKILL.md`.** The name comes from the directory, so the frontmatter carries
only `description` and `argument-hint`. A new skill registers when a session starts; an edit to one already
loaded takes effect immediately.

**Where an extracted fragment goes is decided by how many read it.** A skill grows past what one file should
carry, and part of it is lifted out:

| Read by            | Lives in                                    |
|--------------------|---------------------------------------------|
| that skill alone   | beside its `SKILL.md`, in the same directory |
| more than one      | `templates/`                                |

The directory is the record of ownership, so the next extraction has the answer in front of it rather than a
flat folder to copy.

[`STRATEGY.md`](STRATEGY.md) states what the framework is trying to achieve. This file states what keeps it
portable, since these files are pulled into other repositories as a plugin.

## A skill names the subject, never the arrangement

A skill states **what** the repository must tell it — the build command, the test-type mapping, the sub-agent models,
how documentation is written — and never how that repository files the answer. "What the module conventions say
about diagram format governs this run" travels, and so does "follow the conventions index to wherever that
lives" — a repository keeping everything in one file has an index one line long. "Read
`docs/conventions/diagrams.md`" does not travel: it names a path the next repository has no reason to have.

The same holds for anything a skill invokes: it lives in `scripts/` and assumes nothing about the tree around
it.

## A skill points at a rule it does not own

Where the repository writes a rule down, the skill names the file and lets the agent read it. A paraphrase in a
prompt is a second copy of the rule that can drift from the first, and it drifts in the one place no review
looks — a sub-agent's instructions.

What a skill *does* own — its stage order, its guardrails, what a report must contain — it states outright.

## A rule the repository does not cover falls back to the skill

Every skill carries defaults for a repository that says nothing: PlantUML with the C4 standard library, the
session's own model, generic layer names. A missing conventions file is a degraded run, never a failed one.
