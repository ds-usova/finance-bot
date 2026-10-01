# claude-tdd

Boots Claude Code in this repository with the `tdd-sdlc` plugin loaded from its checkout beside the
repository. `--plugin-dir` loads the plugin's working tree, so the session runs the skills exactly as they
stand on disk; `--add-dir` makes that tree a working directory, so the session edits a plugin file without a
prompt. The split this serves — polish here, ship from a session opened in `tdd-sdlc` — is the migration
plan's, at [`.claude/PLUGIN-MIGRATION.md`](../../.claude/PLUGIN-MIGRATION.md).

| Command                                | What happens                                            |
|----------------------------------------|---------------------------------------------------------|
| `tools\claude-tdd\claude-tdd.bat`      | Claude Code starts at the repository root, plugin loaded |
| `tools\claude-tdd\claude-tdd.bat -r`   | any extra arguments pass through to `claude`             |

Run it from cmd, PowerShell, or a double-click — it is a Windows batch file, not one of the Git Bash tools,
and it is run by a person, never by an agent, so it has no permission entry.

## Exit codes

| Code  | Meaning                                                    |
|-------|------------------------------------------------------------|
| 1     | no `tdd-sdlc` checkout beside the repository               |
| other | whatever `claude` itself exits with                        |

## Where its guarantees stop

It does not clone or update the `tdd-sdlc` checkout, and it does not reload a running session — an edit to an
agent or hook still needs `/reload-plugins`. Committing, bumping and tagging the plugin stay with a session
opened in `tdd-sdlc` itself.
