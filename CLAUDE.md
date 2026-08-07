# Finance Bot — Instructions

## Tools

[`tools/`](tools/README.md) holds the scripts that answer recurring questions about this repository — what a
dependency contains, what a build did. Check its table before assembling a shell pipeline for the same thing.

## Reading files

Use the Read, Glob, and Grep tools to inspect files and directories — not `cat`, `find`, `sed`, or shell loops.
The file tools run unprompted; shell equivalents (especially wrapped in `cd ... &&` or `for f in ...; do cat; done`)
trigger permission prompts and can't be allowlisted around.

## Editing files

Use the Write and Edit tools for every change to a file's content, including a reformat. Re-padding a markdown
table after a cell grows is an Edit that re-emits the table, never a `python`, `sed`, or `awk` invocation written
to realign it — those always prompt, and an interpreter is never allowlisted.

## No `cd` prefix

Never prefix a shell command with `cd "<repo path>" &&` — the Bash tool already runs from the repo's working
directory, so every path in this repo (`tools/...`, `.claude/scripts/...`, `docs/...`) already resolves without it.
A `cd &&` wrapper, like `git -C`, a `for` loop, or an absolute quoted path, changes the literal command string and
breaks every allowlisted permission rule for the command that follows, forcing a manual approval every time.

## Temporary files

Scratch files — a sample input, a throwaway script, a file written only to try something — go in
`build/scratch/` at the repository root. Never in a system temp directory, and never in a session scratchpad
outside the repository.

`build/` is already ignored by git, so nothing there reaches a commit.

The reason is the whitelist. A command whose argument is an absolute path outside the repository is not covered
by the permission matcher and raises an approval prompt, even when the command itself is allowlisted — the
project's own hook stays silent there by design, since it can only refuse and never approve. A repo-relative
path never has that problem.

## Deleting files

Delete with one `git rm` naming every file, never one `rm` per file. The deletions land in the index where a
diff can see them, and removing a dozen classes costs one approval rather than a dozen.

## Writing docs and plans

The [repository-wide conventions](docs/conventions.md) govern every README, conventions file, contract,
use-case page, domain page, design and plan. Read them before writing or editing one.
