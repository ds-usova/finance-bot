---
description: Implement a planned task end to end. Checks every plan is ready and every module is green, lands whatever crosses between the modules, then runs one pipeline agent per plan — concurrently — and finishes the task when the last one lands. Given a single plan, runs it the same way, as a task of one.
argument-hint: [ a task directory, or a single plan file ] [ optional section name ]
---

# Implement Plan

Use this skill when the user asks to implement a plan that was just discussed or is referenced by path — e.g.
"implement this plan", "implement docs/1-add-widget/plan.md".

**This skill runs the task. It never runs a plan itself.** Every plan is run by an
`implement-plan-module` sub-agent, which holds the stages, the guardrails and the tick policy. Your jobs are the
gates before anything starts, the seam that crosses modules, the pipelines, and finishing the task.

**Three levels, and each only coordinates the one below.**

| Level | Who                               | Owns                                                   |
|-------|-----------------------------------|--------------------------------------------------------|
| task  | this skill                        | the gates, the seam, the pipelines, finishing the task |
| plan  | `implement-plan-module`, one each | one plan's stages, guardrails and ticks                |
| step  | the step agents it spawns         | one class, one test class, one refactor pass           |

**Point a sub-agent at the rule; do not restate it.** A rule the repository writes down is passed as the file
that owns it, named so the agent reads it there — never as a remembered version of what that file says, which is
a second copy that can drift and drifts in the one place no review looks. The same applies to counts and
inventories drawn from the tree: read them, never recall them.

## Input Resolution

A task owns a directory: `docs/<n>-<task-name>/`, holding the `design.md`, one plan per module, and — where
anything crosses between them — a `shared/plan.md`. Phase 3 adds a `review/` folder to it: what the task left
open, and the evidence that everything else was measured.

| Invoked with     | The task directory is                                      |
|------------------|------------------------------------------------------------|
| a task directory | the one given                                              |
| a plan file      | `plan.sh task <that plan>` prints it, and every plan in it |
| nothing          | the plan referenced in the conversation, else ask          |

**A single plan is a task of one plan.** It takes exactly the phases below, and its one pipeline is spawned the
same way. Nothing here has a special case for it, because a plan file cannot tell you whether a sibling exists
and `plan.sh task` can.

Read the repository-wide conventions, and `<module>/docs/conventions.md` for every module the task's plans name.
Follow the conventions index to wherever they live. They answer the build and test commands, the parallelism
rules and the version-control policy this skill needs, and each pipeline reads its own module's again.

## Phase 0 — Two Gates, Before Anything Is Spawned

Both are hard. Run them in this order.

**Gate 1 — every plan is ready.** A plan is ready only when the user has closed the loops the planning phase
opened. Check every plan in the task directory, `shared/plan.md` included:

- **The design file** the plan's `**Design:**` header links: `design.sh settled --file <design>` exits 0. Run it
  once for the design the plans share, not once per plan. An unsettled decision means step agents will each
  invent their own answer to the same question, in different layers. The script ships with the `design-task`
  skill at `scripts/design/design.sh`.
- **Open Questions / Blockers**: every `- Q:` has a non-empty `- A:`, and every blocker recorded by a previous
  partial run has a resolution noted. An unanswered question means a step agent will hit exactly the ambiguity
  the planner already flagged.
- **Review Findings**: every `- **F<n>:**` has a non-empty `- Action:`. A deliberate "won't fix" counts — the
  point is that it was decided. A `mechanical` finding carrying `Action: applied — …` satisfies the gate on its
  own, since `plan-task` wrote it when it applied the fix. A `decision` finding, and anything marked
  `- Escalated:`, needs the user's answer. A plan whose review found nothing has its "no issues found" line
  instead, and that passes.
- If an `Action:` or `A:` prescribes a change to the plan's steps or scenarios, confirm the plan text was
  actually updated to match. A decision written next to a finding but never applied to the step is unresolved.
- **A plan edited since its review is offered a re-review, never given one.** Where a step, scenario or
  signature changed after the last **Review Findings** entry — an `Action:` applied by hand, an answer that
  reshaped a step — say so once and ask, via `AskUserQuestion`, whether to spawn `review-plan` on it before
  going on. Declined, the gate proceeds; accepted, its findings join the plan and are actioned like the rest.

**One unready plan stops the task, with nothing started.** List what is unresolved and ask the user. If they
resolve it in the conversation, write their answers into the plan file, apply the resulting step changes, and
only then proceed.

**This gate cannot live in a pipeline.** It promises that an unready plan changes no file. A pipeline cannot keep
that promise once a sibling is already writing.

**Gate 2 — every module is green.** Run the full build and entire test suite, including the architecture test,
of every module the task's plans name. Use the commands from each module's conventions.

- **Everything green**, the expected case: proceed. From here on, any failure is attributable to this task.
- **Anything already red**: stop immediately, before a file is touched. Report the failures — test name, error,
  suspected cause — and wait. Do not fix them: they predate the task, and fixing them is not its scope.

Keep the **total and skipped counts** of each module's suite. They are the figures every later guardrail compares
against, and each pipeline is handed its own module's.

**No pipeline repeats either gate.** By the time one starts, phase 1 has changed the tree.

**These figures are a measurement, not a formality, and a measurement is not repeated over an unchanged tree.**
A guardrail that would run a module's suite when nothing under that module has been written since the last full
run of it reads that run's figures instead. The condition is checkable: whoever is about to run knows what it
has written. This narrows nothing and skips no stage — a guardrail still gates the commit that follows it, and
still runs the whole suite the moment that module's files have moved. What it stops is the same suite answering
the same question twice, which on a module whose run takes minutes and starts a container is the largest
avoidable cost in a task.

## Phase 1 — The Seam, Alone

Present only when the design named an artifact more than one module reads at build time — a schema, a generated
contract, a repository-root file. Then the task holds `shared/plan.md`, and it is implemented first and alone,
by its own `implement-plan-module` agent.

- **It owns the seam, both sides' wiring to it, and whatever the change to it breaks** — the artifact, each
  consuming module's generation or build hookup, and every call site the regenerated code no longer satisfies. A
  removed parameter is still referenced by the code that read it, so a plan that lands only the schema leaves two
  modules that do not compile.
- **It stabilizes those call sites; it never reimplements them.** A changed signature keeps its logic and gains a
  `TODO`, a new method gets a stub with its intent comment, a test that cannot compile is disabled rather than
  removed. Behaviour is a module plan's, and reworking it here would swallow that plan.
- **Its exit guardrail is Stage 1's, over every module it lists** — they compile, their architecture tests pass,
  their pre-existing suites are still green, and nothing was lost. Not a bespoke compile check: a guardrail that
  only compiles proves nothing about what a regenerated contract did to behaviour that still had tests. Tell its
  agent that its Affected Modules are all of them, not one. A module whose files this plan never touched — one
  that only regenerates from the artifact and compiles clean — is answered by phase 0's figures under the rule
  above, since nothing under it has been written.
- **A disabled test's reason names the module plan and step that owes the rework** — `module-a/plan.md · RI03`.
  That is a reference for whoever reads the skip list, not a schedule: the pipeline that owns the step clears it
  during its own red phase.
- **A blocked shared plan stops the task here**, with no module pipeline started. That is the cheapest failure
  this skill can produce. Report it and stop.

## Phase 2 — One Pipeline Per Plan

Spawn one `implement-plan-module` sub-agent per module plan, in the shape
[`templates/sub-agents.md`](../../templates/sub-agents.md) gives. Give each its plan path, its module's phase-0
figures, and the section name if the user narrowed the run to one.

- **Nothing waits.** Phase 1 landed everything that crosses, so the module plans are independent by
  construction. One blocking does not stop the rest.
- **A pipeline that returns with children in flight is resumed, not restarted.** It picks up its own plan and
  ticks. Resuming is [`templates/sub-agents.md`](../../templates/sub-agents.md)'s **continue an agent** row, and
  a message left without its blocking read stalls the pipeline a second time.
- **A step agent's report can arrive here.** You are the level a grandchild's task-notification reaches, and the
  pipeline that spawned it never saw it. Relay what it says in the message that resumes the pipeline, rather than
  waiting for a report that has already been delivered to the wrong level.
- **How many start at once is the repository tier's answer.** One machine runs every module, and a module's own
  conventions cannot see what a sibling is doing. Read the **Parallelism** rules at the level that binds all the
  modules and start no more pipelines than they allow, starting the next as a running one finishes. If no such
  rules exist, start them all.
- **What happens inside a pipeline is its own.** Its module's cap, its stage order, its guardrails, its ticks.
  You reconcile nothing about a step and never edit a plan a pipeline owns.
- **Report per plan as each returns.** One finishing does not wait for another.

## Phase 3 — Finish The Task

When every pipeline has returned:

1. **Ask `plan.sh task docs/<n>-<task-name>/`.** It lists every plan the directory holds and exits 0 only when
   all of them are complete, `shared/plan.md` included. Anything else: leave the directory in place and summarize
   what is open. The phases end here.
2. **Write `review/findings.md`** — everything the task leaves open, from every plan at once. A person reading it
   learns what they are inheriting without opening a plan.

   Each plan's **Open Questions / Blockers** is the source. Lift what is **still open** — a confirmed defect no
   scenario covered, a gap the design never named, an inconsistency the change left behind. A blocker the run
   settled stays in its plan as that plan's history and never appears here; so does a question already answered.

   **An affected module's conventions may name something else that belongs here**, and that is read rather than
   remembered: a module whose suite cannot see a whole class of defect leaves the list of what a person still
   has to look at, which no step implemented and no test closed. Follow the conventions index to whatever the
   module says its finished work leaves open.

   The shape is [`findings.md`](../../templates/findings.md). A task fills all four of its sections.

   Write it before archiving, so the whole directory moves once and the folder is there for the evidence to
   land in.

   **Every `R` row it files is appended to `docs/backlog.md`**, one pointer each, in the shape
   [`backlog.md`](../../templates/backlog.md) gives — with the link written to the archived path, since that
   is where the file is about to move. The findings file stays the row's owner; the backlog is how the row is
   found once the task directory has left `docs/`.
3. **Archive**, on exit 0 and on nothing else: move the **whole task directory** — every `plan.md`, the
   `design.md` they link, `review/`, and anything else the task accumulated — into `docs/implemented/`. Moving the
   directory rather than the files keeps every link inside it working.
4. **Commit** per the Version Control policy. This is where its **squash-before-archiving** setting applies.
5. **What the conventions run over finished work.** Every affected module's conventions say what happens once a
   change is complete — a measurement, a documentation pass. Follow the conventions index to wherever they say
   it, and run that list in its order, passing each entry the archived plan. An entry listed by several affected
   modules runs once. Each states its own commit behaviour.

**Only this level can do any of it.** A pipeline sees one plan, so it can neither tell that the task is finished,
nor collect what the other plans left open, nor hand an archived plan to a step that needs one.

## Version Control

Whether this run commits at all, and how, is the conventions' **Version Control** rules. A repository has one
history however many modules it has, so expect them at the level that binds all of them.

**Missing or silent means no commits.** Never invent a commit policy — an uninvited commit is exactly the kind of
change a user managing their own history does not want.

**Several pipelines commit into that one history at once.** That is a constraint on the policy, not something
this skill resolves. Follow whatever it says about scoping a commit and about a concurrent one, and report a
refusal it does not cover rather than improvising a retry.

## Response Style

- One line per phase as it starts, and one per pipeline as it returns.
- A pipeline's own progress is its report, not yours to relay in full.
- Final summary: what each plan finished, whether the task was archived, and a pointer to `review/findings.md`
  rather than a second copy of what it says.
