# The Plan Reader

`plan.sh` reads and updates a plan's checklist by item ID — what is done, what is next, what one step
says, and marking a step done or blocked.

## Why it exists

A plan runs to a thousand lines and thirty-odd checklist items, and during a run two operations happen constantly:
tick a box, and read one step. Both used to be text surgery. Ticking meant matching a bullet that often wraps
across lines, so it broke whenever an item's wording changed — which it does, whenever a decision mid-run edits
the plan. Reading one step meant reading the whole file, or grepping for line numbers and guessing where the step
ended.

An ID makes an item addressable, and this reads and writes them. It stores nothing of its own: the plan file stays
the single source of truth, and the dependency graph is parsed out of the `after:` fields each time it is needed
rather than kept in a second file that could drift.

## Where it lives

It ships **with the skills that use it**, at `scripts/plan/` under the plugin root — `${CLAUDE_PLUGIN_ROOT}` once
installed, `.claude/` in a plain checkout. `plan-parse.awk` sits beside it and is found relative to the script, so
the pair travels together.

The **plan** is found the other way round, from `git rev-parse --show-toplevel` (falling back to the working
directory). Once installed, this script runs from a cache directory outside any checkout, so nothing about the
project can be derived from where the script is.

## Usage

Run it with bash, from anywhere inside the project:

```
.claude/scripts/plan/plan.sh status
.claude/scripts/plan/plan.sh next
.claude/scripts/plan/plan.sh tick GU07
.claude/scripts/plan/plan.sh tick ST01 ST02 ST03
```

`show` and `tick` take a whole stage's worth of IDs in one call, so neither needs a loop around it. `tick`
resolves every ID before it writes any, and a name nothing defines ticks none of them.

| Command             | Effect                                                                                                    |
|---------------------|-----------------------------------------------------------------------------------------------------------|
| `status`            | Done/total per group, and the IDs still open.                                                             |
| `next [--all]`      | Items that can start now, longest remaining chain first. `--all` also lists what is waiting, and on what. |
| `show <ID>...`      | One item: its header and everything indented under it. Several print in order, blank-line separated.      |
| `tick <ID>...`      | Mark the items done. Saying so twice is not an error.                                                     |
| `block <ID> <note>` | Leave the item open; record the note under Open Questions / Blockers.                                     |
| `validate`          | See [What `validate` checks](#what-validate-checks).                                                      |
| `task [<path>]`     | Every plan the task holds, its done/total, and whether all are finished. Exit 0 means nothing is open.   |

Exit codes: **0** done, **1** no such item, `validate` found problems, or `task` found something open,
**2** bad usage.

**The plan is named as a bare path, on any subcommand**, and it comes last:

```
.claude/scripts/plan/plan.sh show GU07 docs/1-add-widget/plan.md
.claude/scripts/plan/plan.sh tick GU07 GU08 docs/1-add-widget/plan.md
```

`--file <plan>` does the same thing and is accepted anywhere the bare path is. Neither is required: without one,
the single plan in flight under `docs/` is used — a task owns a directory holding `design.md` and one plan per
module, `plan.md` for a single-module task and `<module>/plan.md` for each module of a multi-module one. An
archived plan under `docs/implemented/` has to be named explicitly.

A multi-module task therefore has several plans in flight, and every command names the one it addresses. IDs
restart per plan, so `GU07` can exist in two of them and is only meaningful with its plan's path.

### What `task` answers

Every other command reads one plan. `task` reads the directory that holds them, which is the only place a
question about the whole task can be answered.

```
.claude/scripts/plan/plan.sh task docs/18-add-widget
```

```
docs/18-add-widget
  module-a/plan.md                    47/47   complete
  shared/plan.md                       6/6    complete
  module-b/plan.md                    19/23   4 open
1 of 3 plans still open
```

**The argument is the task directory**, or nothing when only one task is in flight.

A plan path works too, and one caller needs it: a run given a single plan knows its own file and not the
directory above it. Working that directory out means knowing whether the plan sits one level under `docs/` or
two, which is the arithmetic this command exists to take over. So it walks up to the level directly under
`docs/` itself, and all three spellings reach the same answer.

A plan with no IDs at all counts as open, whether it is empty or predates the item format. Neither is a plan
anything should be concluded from.

### Item IDs

An item is `- [ ] <ID> · <text>`, the ID being a letter prefix and a number — `ST01`, `RU07`, `GI02`. The prefixes
and the numbering rule belong to the plan format, defined by the `plan-task` skill this ships with.

### What `validate` checks

| Check                                                                        | Catches                                                      |
|------------------------------------------------------------------------------|--------------------------------------------------------------|
| Duplicate IDs, items with no ID                                              | an item nothing can address                                  |
| `after:` naming an ID nothing defines, dependency cycles                     | a schedule that never becomes eligible                       |
| `after:` reaching into a group the plan lists later                          | a stage waiting on work a later stage owns                   |
| A `given:` / `when:` / `then:` whose value is empty, `—`, `TBD` or `N/A`     | a scenario a step agent cannot implement                     |
| An `update:` bullet on an **open** item naming a method found nowhere        | a plan written against remembered code                       |
| A finding with no `Resolution:`, or an unrecognized one                      | a review that skipped the mechanical/decision classification |
| A `mechanical` finding whose `Action:` is empty and that is not `Escalated:` | a fix the orchestrator was meant to apply and did not        |

The `update:` check greps the tree once per method named, excluding `build/`, `.git/`, `.gradle/`,
`node_modules/`, `target/`, `out/` — and **every `*-plan-*.md`**, this plan above all: the plan names the method
itself, so a search including it would confirm each name against the text under test. A method a plan creates and
then updates in the same run is the one false positive; say `update:` only of a test that exists, which is what
the format means by it.

**A ticked item is skipped.** Its `update:` bullets describe work that already happened, and a bullet saying to
rename or drop a method is exactly why that method is no longer in the tree — so checking it reports the step's
success as a defect. Validating after a red phase used to raise one such report per rename.

What it cannot check: whether a **class** a step names exists, since a plan names the classes it is about to
create; and whether a step's claim about a file is *true*, only that its scenarios are filled in. Those stay the
`review-plan` pass's job.

### What `next` schedules

`next` answers with the items whose `after:` dependencies are all ticked, in the **earliest group that still has
open work** — groups run in the order the plan lists them, so a post-implementation step with no dependencies is
not eligible while the green phase is open.

They come back ranked by the longest chain of still-open work that leads out of each one. Under a parallelism cap
that ranking is the scheduling order: the deepest chain, not the cap, is what sets how long the phase takes, so
starting a leaf ahead of the head of a deep chain costs a whole wave.

**Scope it when the run covers only part of the plan.** Unscoped, `next` moves to the following group as soon as
the current one is fully ticked — right for a whole-plan run, wrong for one confined to a phase, which would start
receiving the next phase's work instead of learning it had finished:

```
plan.sh next --group red
plan.sh next --group green --section unit --section integration
```

`--group` and `--section` match any part of the heading, case-insensitively, and `--section` may be repeated. An
ambiguous `--group` lists the candidates instead of guessing. When everything in scope is ticked, the answer is
`every item in scope is ticked` — the completion signal for that stage.

The section filter also matters inside a single group: system-test items commonly carry no `after:` edges, because
they depend on everything and plans express that by ordering rather than by listing every ID. Nothing in the graph
holds them back, so a green-phase query that does not name its sections offers them in the first wave.

### Portability

It runs on macOS, Linux and Windows. What that costs:

- **`bash`, `awk`, `sed`, `git`, `find`, `grep`** — nothing else, and no GNU-only spellings. The parser is strict
  POSIX awk (checked with `gawk --posix`), so `mawk` and BSD `awk` serve as well as `gawk`.
- **Edits go through a sibling temp file, not `sed -i`**, whose argument differs between GNU and BSD: on BSD the
  flag consumes the next word as a backup suffix, so the GNU form quietly does something else.
- **Windows** needs a **Git Bash** prompt, which supplies all of the above.
- `*.sh` and `*.awk` must be pinned to LF in `.gitattributes` — a CRLF checkout fails on the first line — and
  `plan.sh` must be committed with mode `755`, or a Unix clone cannot run it. `bash plan.sh …` works either way.

Whoever installs this has to allow the script in their own permission settings — `Bash(.claude/scripts/plan/plan.sh:*)`
for a checkout — since permissions are the consumer's, not the plugin's.

## Where it stops

It parses the plan's **shape**, not its meaning: an item is a checkbox with an ID, a dependency is an ID after
`after:`. It cannot tell whether an edge is *right* — only whether the ID it names exists. The same holds for the
checks added to `validate`: a filled-in `then:` may still be wrong, and a method that exists may still be the
wrong one to update.

Bullets inside fenced code blocks are skipped, so a plan quoting its own step format does not acquire phantom
items from the example.

`next` degrades quietly on a plan with a dependency cycle: everything in the cycle waits forever and simply never
appears. That is what `validate` is for, and why it is worth running once when the plan is written rather than
only when something looks wrong.
