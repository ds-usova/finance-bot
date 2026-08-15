---
description: Set up a repository's conventions documentation — the repo-wide index and its topic files, and a per-module index and its section files — with every statement deduced from what the repository already does. Asks only what the tree cannot answer.
argument-hint: [ module path, or nothing to cover the whole repository ]
---

# Initialize Conventions

Give a repository the conventions documents the rest of this framework reads: what the stack is, how it is built and
tested, how its code and documents are written, and how an agent works it. Every statement comes from evidence in
the tree — this skill records what the repository already does, and never invents a convention for it.

**Split, not one file.** A reader — human or agent — needs one concern at a time: a red-phase run needs testing and
build, a refactor pass needs code style, a design needs architecture and diagrams. One file per concern, reachable
from an index, means each reader opens two files instead of one long one.

## 1. Survey Before Writing

Read the repository first. A conventions file written from a guess is worse than no file, because everything
downstream trusts it.

| Subject          | Where the answer already is                                                                  |
|------------------|-----------------------------------------------------------------------------------------------|
| Modules          | build files, workspace/settings manifests, top-level directories holding their own build file |
| Tech stack       | dependency manifests and lock files, framework entry-point classes, container/compose files   |
| Build & test     | build scripts, CI workflow files, `tools/`-style wrapper scripts, task definitions            |
| Dependencies     | manifest and lock file, a versions or audit plugin in the build, a comment pinning a version   |
| Package layout   | the source tree itself — the real folders, not an idealized version of them                   |
| Architecture     | an existing dependency-rule test or lint config; failing that, the import directions in code  |
| Test conventions | the existing test classes — naming, assertion library, base classes, fixtures, containers     |
| Code style       | formatter/linter config, and the idioms repeated across existing production classes           |
| Version control  | `git log` — the real subject-line format and granularity, not a preferred one                 |
| Documentation    | the existing READMEs and docs pages — how they are already written                            |

Rules for the survey:

- **Cite from the tree.** Every line written in step 3 traces to a file, a config entry, or a repeated idiom.
- **Two examples make a convention.** One class doing something is that class; the same shape in several is a rule.
- **Never guess.** What the tree does not answer is written as `TBD — <what to confirm>` and collected for step 4.
- **Name the setting, not its value.** A version or a timeout is referred to by the file that pins it.
- **One survey agent per module** when the repository has several, run in parallel, each reporting findings rather
  than writing files. A single-module repository is surveyed in this session. No conventions file exists yet to
  name a sub-agent model, so the session's own model applies.

## 2. The Arrangement

Two tiers. A rule that binds every module lives at the repository root; a module's own file extends it and never
contradicts it.

**Repository root** — `docs/conventions.md` as the index, `docs/conventions/` holding one file per rule the survey
found binding on every module. That set is the repository's, not this framework's: write a file for what step 1
actually found, and none for what it did not. A repository that writes no decision records gets no page about
them.

What such a file typically turns out to be — as illustration, never a checklist to fill:

| A repository that…                               | tends to get                                                        |
|--------------------------------------------------|---------------------------------------------------------------------|
| has a house style for its READMEs and docs pages | a page on how documents are written                                 |
| draws diagrams in its documents                  | a page naming the language, its includes, and what each level shows |
| keeps decision records                           | a page on how one is numbered, superseded, and deprecated           |
| runs two or more modules on one stack            | a page on compiling, testing, and inspecting dependencies on it     |
| has one history for all its modules              | a page on when work is committed, and what a message says           |
| builds and tests every module on one machine     | a page on how much may run at once, across all of them              |

A single-module repository keeps the build facts in the module tier, and may have no root tier at all.

**Each module** — `<module>/docs/conventions.md` as the index, `<module>/docs/conventions/` holding:

| File              | Holds                                                                                      | Read by                      |
|-------------------|--------------------------------------------------------------------------------------------|------------------------------|
| `orientation.md`  | tech stack, and the documentation worth reading before changing anything                   | anyone arriving cold         |
| `architecture.md` | package layout, layer boundaries and the rule between them, file locations, diagram format | design, planning, guardrails |
| `testing.md`      | which parts fall into unit / integration / system, tooling, naming, assertion and style   | every red and green phase    |
| `code-style.md`   | production-code idioms, and what a refactoring pass prioritizes and must leave alone       | green and refactor phases    |
| `build.md`        | this module's exact compile, single-test, full-suite and architecture-test commands        | every phase                  |
| `follow-up.md`    | what runs once a change is complete, and what documents it earns                           | the last stage, archiving    |
| `agent.md`        | commit behaviour, sub-agent models, this module's own parallelism caps                     | the orchestrating skills     |

**Every agent-only fact belongs in `agent.md`** and nowhere else. The other files are documentation for a person
who happens to also be read by an agent: they describe the module, never a workflow, and never mention agents.

**The test for `agent.md` is whether the fact survives without an agent.** What runs when a change is finished
survives — a person doing the work by hand runs it too — so it is `follow-up.md`, not an agent fact. A sub-agent
model does not survive, and belongs in `agent.md`.

The `conventions/` templates — under `${CLAUDE_PLUGIN_ROOT}/templates/` when this framework is installed as a
plugin, under `.claude/templates/` in a plain checkout — are the module tier as blank files, one per row above
plus the index. Their placeholders are the questions to answer; the survey supplies the answers.

## 3. Write the Files

**The index carries no content.** It states what the module or repository is, then one line per section file:
a link, an em dash, and the concerns that file settles — enough to pick the right file without opening two.
It also links the tier above it.

**Each section file opens with a breadcrumb heading** — `# [Conventions](../conventions.md) > Testing Conventions`
at the module tier, `# Conventions > Writing Documentation` at the root — then one line saying what it governs,
then the rules.

**Link instead of repeating.** A fact has one owning file; every other file links to it. Build commands live in
`build.md` even when `testing.md` is what needs them.

How the pages themselves are written is governed by the repository's own documentation conventions — which this
run may be creating. Where none exist yet: labelled lists and one-line bullets, a table wherever a rule has
conditions and outcomes, no worked examples inside a rule, and no justification prose unless the reasoning changes
what someone would do.

Create only the directories a tier actually needs, and never overwrite an existing conventions file — an existing
file is surveyed and extended in place, with what it already settles left alone.

## 4. Put the Gaps to the User — in One Batch

Collect every `TBD` and ask them in a single `AskUserQuestion` round, each with the options the repository makes
defensible and a recommendation first. Retry each against the tree once before it reaches the user: a gap the code
answers is filled with its evidence and never asked.

Write the answers into the files. The chat answer is not the record.

Anything still unanswered stays in the file as `TBD — <what to confirm>`, visible rather than guessed.

## 5. Hand Over

- List the files created and the files extended.
- **Report what was deduced and from what** — one clause of evidence per non-obvious rule, so the user can catch a
  wrong reading before the rest of the framework inherits it.
- List every remaining `TBD`.
- **Stop.** Do not change code, reformat existing sources to match a rule just written, or run build commands
  beyond what the survey needed.
