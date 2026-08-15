# The Upgrade Reader

`upgrade.sh` reads and updates an upgrade's checklist by step ID — what is done, what one step says, marking a
step done, and whether the file's grammar and its attempt log hold.

## Why it exists

An upgrade's step format is decided by its kind. A `bump` owes `files:` and `guide:` and may carry no
`change:`; a `migrate` owes a `change:` per guide item, each naming where it lands. Every one of those mistakes
is silent — a `change:` on a `bump` is a source edit nobody approved, and a `migrate` with no `change:` is a
bump that will edit code by feel.

The second reason is addressing. A step handed to a sub-agent has to arrive as the file wrote it, not as a
prompt remembered it. `show` is what makes that possible.

The third is the attempt log. An attempt without its evidence, or filed under a step nothing defines, is a
rumour the next session has to reproduce; `validate` refuses it.

## Where it lives

It ships **with the skill that uses it**, at `scripts/upgrade/` under the plugin root —
`${CLAUDE_PLUGIN_ROOT}` once installed, `.claude/` in a plain checkout. `upgrade-parse.awk` sits beside it and is
found relative to the script, so the pair travels together.

The **upgrade file** is found the other way round, from `git rev-parse --show-toplevel` (falling back to the
working directory).

## Usage

Run it with bash, from anywhere inside the project:

```
.claude/scripts/upgrade/upgrade.sh status
.claude/scripts/upgrade/upgrade.sh show U03
.claude/scripts/upgrade/upgrade.sh tick U02 U04
.claude/scripts/upgrade/upgrade.sh validate
```

| Command        | Effect                                                                                     |
|----------------|--------------------------------------------------------------------------------------------|
| `status`       | Done/total, the IDs still open, and the IDs abandoned.                                     |
| `show <ID>...` | One step: its header and everything indented under it. Several print blank-line separated. |
| `tick <ID>...` | Mark the steps done. Every ID is resolved before any is written.                           |
| `validate`     | See [What `validate` checks](#what-validate-checks).                                       |

Exit codes: **0** done, **1** no such step or `validate` found problems, **2** bad usage.

`--file <upgrade>` names the file, and is accepted on every subcommand. Without one, the single `upgrade.md` in
flight under `docs/` is used. A multi-module upgrade's `<module>/steps.md`, an archived upgrade under
`docs/implemented/`, and one of two upgrades in flight at once all have to be named explicitly.

### Step IDs

A step is `- [ ] <ID> · <kind> · <text>`, the ID being a letter prefix and a number — `U01`, `U02`. A step
whose header carries `abandoned` is reported apart from the open ones. The kinds and the numbering rule belong
to the upgrade format, defined by the `upgrade-deps` skill this ships with.

`files:` and `test-files:` carry their paths as bullets under the label rather than as text beside it, so their
label line is empty by design. Every other label keeps its value on its own line.

### What `validate` checks

| Check                                                          | Catches                                               |
|----------------------------------------------------------------|-------------------------------------------------------|
| Duplicate IDs, a step with no ID                               | a step nothing can address                            |
| A kind the format does not define                              | a typo that silently exempts the step from every rule |
| A labelled line the kind does not take                         | `change:` on a `bump` — a source edit nobody approved |
| A labelled line the kind owes and does not carry               | a `migrate` with no `change:`, a step with no `guide:` |
| A value left empty, `TBD`, `—`, or still in `<angle brackets>` | an agent given no instruction                         |
| A `files:` or `test-files:` with no bullet under it            | a boundary that names nothing                         |
| A `change:` with nothing after the middot                      | a guide item that never said where it lands           |
| `needs:` naming a step nothing defines                         | a reference to a step that was renumbered or dropped  |
| An attempt missing `why:`, `result:`, `evidence:` or `ruled-out:` | a failure the next session has to reproduce        |
| An `evidence:` with no fenced block under it                   | a failure described instead of pasted                 |
| An attempt filed under a step nothing defines                  | a log entry that belongs to nothing                   |
| A fenced block that never closes                               | pasted output that swallowed the rest of the file     |
| An Open Question whose `- A:` is empty                         | a run about to start on a decision nobody made        |

Bullets inside fenced code blocks are skipped, so an upgrade quoting the step format does not acquire phantom
steps from the example, and evidence pasted under an attempt is never read as steps.

## Where it stops

It parses the file's **shape**, not its meaning. It cannot tell whether a `guide:` is the right release's,
whether a `change:` is what the guide actually asks, or whether the version in the header is the newest. Those
are the questions phase 2's approval and the step's own suite run exist to answer.

## Portability

As `rework.sh`: **`bash`, `awk`, `sed`, `git`, `find`, `grep`**, no GNU-only spellings, strict POSIX awk, edits
through a sibling temp file. Windows needs a **Git Bash** prompt. `*.sh` and `*.awk` are pinned to LF in
`.gitattributes` and `upgrade.sh` is committed with mode `755`.

Whoever installs this has to allow the script in their own permission settings —
`Bash(.claude/scripts/upgrade/upgrade.sh:*)` for a checkout.
