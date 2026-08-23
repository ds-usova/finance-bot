---
description: Bring a module's dependencies up to date, one module by default or several on request. Reads the manifest, finds what is behind and what is vulnerable with whatever the conventions name, reads each release's migration guide, writes an upgrade file with one step per dependency, stops for approval, then applies them — one agent per module, concurrently — with the suite that is already green as the guardrail. A migration that cannot be finished is kept back and written down, never forced.
argument-hint: [ a module, several modules, a dependency name, or the path of an existing upgrade.md ]
---

# Upgrade Dependencies

Move the libraries a module depends on to newer versions without changing what the module does. Libraries only:
the language runtime, the build tool, the wrapper and the base image are not this skill's.

## When Not To Use It

- **A new dependency**, or one dropped — that is a design decision, `design-task` then `plan-task`.
- **Behaviour a new version makes possible** — a feature. The bump is this skill; using it is a task.
- **A bug in the module's own code** that a bump happens to expose — `fix-bug`, once the bump has landed.
- **A JDK, Node, Gradle wrapper, Docker base image or CI runner** — out of scope.

## The Two Kinds of Step

| Kind      | What it does                                                                                        |
|-----------|-----------------------------------------------------------------------------------------------------|
| `bump`    | moves one version line and nothing else; the suite is green before and after                        |
| `migrate` | moves the version and carries the code and configuration the release's migration guide asks for     |

A `migrate` step names its guide. Its grammar is [`step-format.md`](step-format.md). **The suite green at the
baseline and green after every step is the whole guardrail**; an upgrade writes no `red` step.

## Input Resolution

The argument is a module — one, by default. Several modules are several arguments. A dependency name narrows the
survey to it. **A path to an existing `upgrade.md` resumes it** under
[`resuming.md`](../../templates/resuming.md), which replaces Phase 0 and Phase 1: it re-reads nothing its
`## Kept back` and `## Attempts` already settle.

Read the repository-wide conventions and `<module>/docs/conventions.md` for every module named. Beyond the build
and test commands, the parallelism and the commit policy, they answer four questions this skill has no default
for. Where they answer, the answer binds; where they are silent, the fallback in the table applies and the
closing report says so.

| Question                       | The conventions may say                                        | Fallback where silent                                                          |
|--------------------------------|----------------------------------------------------------------|--------------------------------------------------------------------------------|
| How dependencies are updated   | a documented flow, a script, an order — anything at all        | the phases below                                                               |
| What lists outdated versions   | a plugin, a script, a command                                  | the stack's own tool, then the manifest read against the registry — see below  |
| What lists vulnerabilities     | a scanner and how it is run                                    | the stack's own audit command where it has one; otherwise none, and say so     |
| Which versions are routine     | "patch and minor with every change; a major is its own story"  | every newer version is proposed and the user picks in Phase 2                  |

**A documented flow outranks everything on this page.** Where the conventions describe how the module updates
its dependencies, follow that and use this skill for what it leaves unsaid.

**Nothing is guessed into a build file.** A scanner or a versions plugin the conventions do not name is not added
to run the survey. Where the module has none, the report names what could be added — the stack's audit command,
a versions plugin, a CVE scanner — as an option for the user, and the run continues without it.

### Finding What Is Behind

In order, stopping at the first that answers:

1. what the conventions name;
2. what the stack ships — `npm outdated` and `npm audit`, `pip list --outdated` and `pip-audit`, a
   `dependencyUpdates` or `dependencyCheckAnalyze` task the build already declares;
3. the manifest itself — `build.gradle`, `libs.versions.toml`, `pom.xml`, `package.json`, `requirements.txt`,
   `go.mod`, `Cargo.toml` — each declared version looked up in its registry (WebFetch against Maven Central,
   npm, PyPI, and the like).

A version pinned by a BOM or a platform is surveyed through the BOM: the row is the BOM, and the libraries it
carries are listed under it.

## Phase 0 — Baseline

**The affected modules are clean.** Uncommitted work under one: name the files and stop. Uncommitted work
elsewhere is left alone.

Full build and full suite of every affected module with its own commands. Green: record the commit and, per
module, the total and skipped counts. Anything red: stop, change nothing, report. A whole-suite run that already
answers for this commit is read, not repeated.

## Phase 1 — Survey, Read the Guides, Write the Files

**Survey.** Every declared dependency, its current version, the newest version its registry offers, and the
newest the conventions call routine. Every known vulnerability the scanner reports, with its identifier and the
version that fixes it. A dependency already at its newest gets no row.

**Read the guides.** For every dependency proposed to move, find the release notes or migration guide covering
the range from the current version to the target — the project's changelog, its `UPGRADING.md`, its
documentation site. What each says binds the step:

- nothing breaking, or nothing that reaches this module — a `bump`;
- a renamed setting, a removed class, a changed default, a new required call — a `migrate`, and the step lists
  each change and where in this module it lands;
- no guide found — a `bump` marked `guide: none found`, and the step's suite run is what stands for it.

**Write the files.** The upgrade owns `docs/<n>-<name>/`, `<n>` one more than the highest `<number>-*` in
`docs/` and `docs/implemented/`.

| The upgrade reaches                  | The directory holds                                                                        |
|--------------------------------------|--------------------------------------------------------------------------------------------|
| one module                           | `upgrade.md`, steps included                                                               |
| several                              | `upgrade.md` without steps, and `<module>/steps.md` for each                               |
| several, through one version catalog | one more: `shared/steps.md`, holding the catalog's bumps, applied before any module's file  |

**Each steps file is owned by exactly one agent.** `upgrade.md` is what a fresh session resumes from. What it
holds is [`the-files.md`](the-files.md). `upgrade.sh` (`scripts/upgrade/` under the plugin root, README
beside it) reads, ticks and validates them.

Run `upgrade.sh validate --file <each file>` until it exits 0 before presenting anything.

## Phase 2 — Stop

Present the files and stop. Nothing touches a build file or a source file until the user asks for the steps to
be applied.

**Where the conventions leave the choice open, ask it here**, once, via `AskUserQuestion` with `multiSelect`:
each proposed dependency is an option, its current and target version and its guide's verdict in the
description. A dependency left unselected keeps its row in `## Survey` with `Status: deferred` and gets no
step. A major the conventions call "its own story" is listed and not offered.

Open Questions beyond that are rare: a guide that offers two migration paths, a vulnerability whose fix is only
in a major. Ask them in the same batch and write each answer in as `- A:`.

## Phase 3 — Apply

**`upgrade.sh validate` exits 0 on every steps file before the first build file is touched**, again after any
answer written in Phase 2.

1. **`shared/steps.md` first, alone**, where there is one, by its own `upgrade-deps-module` agent given every
   module on the catalog. Its exit: every module on the catalog compiles and its suite stands where phase 0 left
   it. Nothing else starts until it lands.
2. **One `upgrade-deps-module` agent per steps file, concurrently**, spawned and waited for as
   [`templates/sub-agents.md`](../../templates/sub-agents.md) says. Each gets its file path, its module, its
   phase-0 figures, `upgrade.md`, and the conventions by name. Cap the count and pick the model by what the
   conventions say about parallelism and sub-agent models.
3. **What happens inside an agent is its own** — its steps, its attempts, its kept-back entries, its ticks.
   Never edit a file an agent owns while it runs.

**A step reaches an agent as `upgrade.sh show <ID> --file <steps>`**, never as a prompt retelling it.

**A migration that cannot be finished is kept back, not forced.** A guide can ask for a class that carries a
bug of its own, a setting the module's framework does not yet honour, an API the module's other dependencies
still bind. After three failed attempts on one change, the agent stops on it: the version stays at the target
where the module compiles and is green on the old API — the guide's change written into `## Kept back` with
what was tried and what would unblock it. Where the module is not green on the old API either, the version goes
back to where it was, the step is `abandoned — <why>`, and the survey row says `Status: blocked`. Every attempt
on the way is an entry in `## Attempts`, in the shape [`attempts.md`](../../templates/attempts.md) gives.

**An agent that returns blocked changes the plan, not the rules**: it returns for a step whose kind is wrong, a
change the guide asks for that lands in another module, a test asserting the old behaviour that nobody foresaw,
or a refusal from [`applying-a-step.md`](applying-a-step.md). Amend the files, stop for approval again as in
Phase 2, re-spawn that module's agent; it starts at its first unticked step.

### What Is Never Done

- A test is never deleted or weakened to make a step green. A test that asserts a behaviour the new version
  changed is reported and the step goes back to the user; whether the assertion or the version moves is theirs.
- Behaviour is never changed under cover of a `migrate` step. What the guide names is the boundary.
- A dependency is never added, removed or replaced with another. That is a design decision.
- Nothing outside the steps is improved because it was nearby.
- A change that could not be applied is never dropped in silence.

## Phase 4 — Finish

1. **Full build and full suite of every affected module, green**, the skipped count back to phase 0's.
2. **The manifest says what the steps claimed.** Read the diff from `**Baseline:**` scoped to the affected
   modules: every version line it moves is a step's, and every source file it touches is named in a `migrate`
   step's `files:`. A version moved under no step is a defect whatever the suite says.
3. **Re-run the survey** with the same tools as Phase 1. Every row selected in Phase 2 now reads `done`,
   `kept back` or `blocked`; the vulnerability list is empty of what the steps claimed to fix. Write the result
   into `## Survey`.
4. **Whatever the modules' build conventions require of a finished change** — a coverage guardrail, a formatting
   gate. A guardrail that fails blocks the archive.
5. **Write `review/findings.md`** in the shape [`findings.md`](../../templates/findings.md) gives — a
   deprecation the guide announced that this run did not act on, a kept-back change and what would unblock it, a
   manual check where a bump changes runtime behaviour no test reaches. An upgrade with nothing open still gets
   the file.
6. **Archive** once `upgrade.sh status` reports no open step in any steps file: move `docs/<n>-<name>/` into
   `docs/implemented/`, and commit the move where the conventions commit at all. A file with an `abandoned`
   step or a `kept back` entry still archives; what it left is in
   `review/findings.md` and the closing report.
7. **What the conventions run over finished work**, in their order, each entry once, each handed the archived
   `upgrade.md`.

## Version Control

Whether and how this run commits is the conventions' **Version Control** rules. Missing or silent means no
commits. Several module agents commit into one history at once; follow what the rules say about scoping and a
concurrent commit.

## Report

[`report-format.md`](report-format.md).
