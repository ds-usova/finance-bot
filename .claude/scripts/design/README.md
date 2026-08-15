# The Design Reader

`design.sh` reads a design file's decisions and answers the question every stage after it asks: is this settled, or
does something still need deciding?

## Why it exists

A design file exists to be *finished before the plan starts*. Its whole value is that the judgment calls — what
happens when the database is down, what a duplicate request does, what the migration does to existing rows — are
answered while they still cost a paragraph, instead of after twenty-five checklist items depend on them.

That only holds if "finished" is checkable. Left to a reading, a `must-decide` slips through, a decision arrives
with a basis but no answer, or an assumption is written with nothing behind it — and the cost lands later, wherever
someone invents their own answer to the same question. `settled` is the one-word verdict anything downstream can
gate on, and `validate` is the shape check that keeps the format worth gating on.

It stores nothing of its own and edits nothing: the design file stays the single source of truth, and a decision is
answered by whoever makes it.

## Where it lives

It ships **with the skills that use it**, at `scripts/design/` under the plugin root — `${CLAUDE_PLUGIN_ROOT}` once
installed, `.claude/` in a plain checkout. `design-parse.awk` sits beside it and is found relative to the script, so
the pair travels together.

The **design file** is found the other way round, from `git rev-parse --show-toplevel` (falling back to the working
directory). Once installed, this script runs from a cache directory outside any checkout, so nothing about the
project can be derived from where the script is.

## Usage

Run it with bash, from anywhere inside the project:

```
.claude/scripts/design/design.sh settled
.claude/scripts/design/design.sh validate
.claude/scripts/design/design.sh show D4 D7
```

| Command          | Effect                                                                              |
|------------------|-------------------------------------------------------------------------------------|
| `settled`        | Exit 0 when nothing is still `must-decide`; exit 1 and list what is.                 |
| `status`         | How many decisions rest on each basis.                                              |
| `show <ID>...`   | One decision: its question, `Answer:` and `Basis:`. Several print blank-line separated. |
| `validate`       | See [What `validate` checks](#what-validate-checks).                                |

Exit codes: **0** done, **1** no such entry, not settled, or `validate` found problems, **2** bad usage.

`--file <design>` picks the design. Without it, the single `docs/<n>-<task>/design.md` is used — a task owns a
directory, holding `design.md` and `plan.md`. An archived design under `docs/implemented/<n>-<task>/design.md`
has to be named explicitly.

### Decision entries

An entry is three lines — the question, the answer, and what the answer rests on:

```
- **D4:** What does the caller see when the database is unavailable mid-write?
- Answer: PersistenceFailedException propagates; the transaction rolls back and nothing is stored.
- Basis: assumed — ExpenseRepositoryAdapter classifies every non-constraint DataAccessException this way.
```

`D<n>` is assigned once and never renumbered. The four bases and what each obliges belong to the design format,
defined by the `design-task` skill this ships with; a worked example is at
`.claude/skills/design-task/example-design.md`.

### What `validate` checks

| Check                                                                    | Catches                                                  |
|--------------------------------------------------------------------------|----------------------------------------------------------|
| The `**Affected Modules:**` line, and every required section, in order   | a design missing the context the plan reads it for       |
| Duplicate `D` IDs, an entry outside the Decisions section                | a decision nothing can address                           |
| A Design Findings row numbered below the row above it                    | a row inserted at the wrong line — the table reads in ascending order |
| A missing or repeated `Answer:` / `Basis:`                               | an entry no gate can classify                            |
| A basis that is not one of the four, or one with nothing after it        | an assumption with no evidence — a `must-decide` in disguise |
| A `must-decide` carrying an answer, or any other basis carrying none     | an entry whose two halves disagree                       |
| A Design Findings section with no `Grilled (<date>):` line               | a design the grill never saw                             |
| A missing or out-of-order section, `## Acceptance Scenarios` included    | a design a plan cannot be written from                   |

A `must-decide` entry is **not** a problem here: a design in flight is expected to have them, and that is exactly
what `settled` is for. `validate` asks whether the file is well-formed; `settled` asks whether it is finished.

A clean run prints the design's size — `####` sections under **Proposed Solution**, scenarios, decisions,
findings. The counts are a mirror, not a gate: a design carrying more than one subject is split into one task per
subject (`design-task`, **One Subject per Task**), and the counts are what show the moment to do it.

Placeholder values — empty, `-`, `—`, `TBD`, `N/A` — count as unfilled, the same set `plan.sh` rejects in a
`given:`/`when:`/`then:`.

### Portability

The constraints are `plan.sh`'s, for the same reasons: **`bash`, `awk`, `sed`, `git`, `find`** and nothing else, no
GNU-only spellings, strict POSIX awk (checked with `gawk --posix`), Git Bash on Windows, `*.sh` and `*.awk` pinned
to LF in `.gitattributes`, and `design.sh` committed with mode `755`. `bash design.sh …` works either way.

Whoever installs this has to allow the script in their own permission settings —
`Bash(.claude/scripts/design/design.sh:*)` for a checkout — since permissions are the consumer's, not the plugin's.

## Where it stops

It parses the design's **shape**, not its meaning. It cannot tell whether an `assumed` basis is true, whether the
evidence cited actually says what the entry claims, or whether the decision is a good one. A well-formed design
that is wrong about the codebase validates cleanly — that is what the `grill-design` pass and the reader are for.

Bullets inside fenced code blocks are skipped, so a design quoting its own entry format does not acquire phantom
decisions from the example.
