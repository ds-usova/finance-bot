# The workflow framework

`commands/` holds the skills — init-conventions, design, plan, implement, archive, retro. `agents/` holds the sub-agents they
spawn, `scripts/` the mechanics they share, `templates/` the examples they point at.

These files are pulled into other repositories as a plugin. What follows is what keeps them portable.

## A skill names the subject, never the arrangement

A skill states **what** the repository must tell it — the build command, the test layers, the sub-agent models,
how documentation is written — and never how that repository files the answer. "What the module conventions say
about diagram format governs this run" travels; "follow the files the conventions index links" does not, because
the next repository keeps one file where this one keeps eight.

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
