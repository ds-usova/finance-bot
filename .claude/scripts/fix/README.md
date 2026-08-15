# The Fix Reader

`fix.sh` reads and updates a bug fix's checklist by step ID — what is done, what one step says, marking a step
done, whether the file's grammar holds, and whether every module's fix is complete.

## Why it exists

A fix's step format is decided by its kind. Three kinds, and each owes some labelled lines and may not carry
others: a `red` step owes `reproduces:` and may not name a production file, a `green` step owes `fixes:` and may
not name a test file at all. Every one of those mistakes is silent — a `green` step that quietly edits the test
it is proven by turns the whole verification model into a formality.

It also reads the `## Attempts` log, where the same silence costs more: an attempt written without the output
that killed it is a rumour the next session has to reproduce, and nothing else in the repository would notice.

The second reason is addressing. A step handed to a sub-agent has to arrive as the file wrote it, not as a prompt
remembered it. `show` is what makes that possible.

There is no scheduling command. A fix runs every `stabilize` step, then every `red` one, then every `green` one,
in every fix — so there is nothing for a `next` to compute.

## Where it lives

It ships **with the skill that uses it**, at `scripts/fix/` under the plugin root — `${CLAUDE_PLUGIN_ROOT}` once
installed, `.claude/` in a plain checkout. `fix-parse.awk` sits beside it and is found relative to the script, so
the pair travels together.

The **fix file** is found the other way round, from `git rev-parse --show-toplevel` (falling back to the working
directory). Once installed, this script runs from a cache directory outside any checkout, so nothing about the
project can be derived from where the script is.

## Usage

Run it with bash, from anywhere inside the project:

```
.claude/scripts/fix/fix.sh status
.claude/scripts/fix/fix.sh show R01
.claude/scripts/fix/fix.sh tick S01 R01
.claude/scripts/fix/fix.sh validate --file docs/7-double-charge/bug.md
.claude/scripts/fix/fix.sh task docs/7-double-charge/
```

| Command        | Effect                                                                                     |
|----------------|--------------------------------------------------------------------------------------------|
| `status`       | Done/total, and the IDs still open.                                                        |
| `show <ID>...` | One step: its header and everything indented under it. Several print blank-line separated.  |
| `tick <ID>...` | Mark the steps done. Every ID is resolved before any is written.                           |
| `validate`     | See [What `validate` checks](#what-validate-checks).                                       |
| `task`         | Every fix file the bug directory holds, its done/total, and whether all of them are done.  |

Exit codes: **0** done, **1** no such step, `validate` found problems, or `task` found something open, **2** bad
usage.

`--file <fix>` names the file, and is accepted on every subcommand but `task`, which takes the bug directory or a
fix file positionally. Without one, the single `fix.md` in flight under `docs/` is used. A `bug.md` is always
named explicitly — `validate` reads it for its Attempts section — and so is an archived file under
`docs/implemented/`, or one of two fixes in flight at once.

### Step IDs

A step is `- [ ] <ID> · <kind> · <text>`, the ID being a letter prefix and a number — `S01` for `stabilize`, `R01`
for `red`, `G01` for `green`. The kinds and the numbering rule belong to the fix format, defined by the `fix-bug`
skill this ships with.

`files:` and `test-files:` carry their paths as bullets under the label rather than as text beside it, and
`evidence:` carries a fenced block under it, so those label lines are empty by design. Every other label keeps its
value on its own line.

### What `validate` checks

| Check                                                          | Catches                                                    |
|----------------------------------------------------------------|------------------------------------------------------------|
| Duplicate IDs, a step with no ID                               | a step nothing can address                                 |
| A kind the format does not define                              | a typo that silently exempts the step from every rule      |
| A labelled line the kind does not take                         | `test-files:` on a `green` step, which is the one thing it may not edit |
| A labelled line the kind owes and does not carry               | a `red` step with no `reproduces:` — a failure nobody named |
| A value left empty, `TBD`, `—`, or still in `<angle brackets>` | a step agent given no instruction                          |
| A `files:` or `test-files:` with no bullet under it            | a boundary that names nothing                              |
| `needs:`, `disables:` or `fixes:` naming a step nothing defines | a green step paired with a reproduction that was dropped   |
| A duplicate attempt number                                     | two entries the log cannot tell apart                      |
| An attempt missing `why:`, `result:`, `evidence:` or `ruled-out:` | a failure recorded without what it settles               |
| An `evidence:` with no fenced block under it                   | an attempt whose output nobody kept                        |
| An attempt filed under neither `diagnosis` nor a defined step  | a log entry attached to a step that was renumbered         |
| An Open Question whose `- A:` is empty                         | a run about to start on a decision nobody made             |

**A duplicate ID's own block is not judged.** The second `R01` is reported and its lines are skipped, since
attributing them to an ID that already means something else would report the same step twice. Fix the ID and run
again.

Bullets inside fenced code blocks are skipped, so a fix quoting the step format does not acquire phantom steps
from the example — and an attempt's evidence, which is a fence by design, contributes none either.

## Where it stops

It parses the file's **shape**, not its meaning. It cannot tell whether a `red` step's test actually reaches the
bug, whether `reproduces:` describes the symptom the user reported, or whether an attempt's evidence is the run
it claims to be. Those are the questions phase 2's approval and the step's own red run exist to answer.

## Portability

It runs on macOS, Linux and Windows. What that costs:

- **`bash`, `awk`, `sed`, `git`, `find`, `grep`** — nothing else, and no GNU-only spellings. The parser is strict
  POSIX awk, so `mawk` and BSD `awk` serve as well as `gawk`.
- **Edits go through a sibling temp file, not `sed -i`**, whose argument differs between GNU and BSD.
- **Windows** needs a **Git Bash** prompt, which supplies all of the above.
- `*.sh` and `*.awk` must be pinned to LF in `.gitattributes` — a CRLF checkout fails on the first line — and
  `fix.sh` must be committed with mode `755`, or a Unix clone cannot run it. `bash fix.sh …` works either way.

Whoever installs this has to allow the script in their own permission settings —
`Bash(.claude/scripts/fix/fix.sh:*)` for a checkout — since permissions are the consumer's, not the plugin's.
