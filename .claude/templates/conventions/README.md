# Conventions templates

The module tier of a repository's conventions, one file per concern. Copy the set to `<module>/docs/conventions/`
and the index to `<module>/docs/conventions.md`, then replace every placeholder with the module's real facts.

`init-conventions` fills these in from the repository itself; filling them in by hand works the same way.

| Template          | Copy to                              | Answers                                                        |
|-------------------|--------------------------------------|----------------------------------------------------------------|
| `conventions.md`  | `<module>/docs/conventions.md`       | which file settles which concern                               |
| `orientation.md`  | `<module>/docs/conventions/`         | what the module is built from, and what to read first          |
| `architecture.md` | `<module>/docs/conventions/`         | where code goes, which layer may depend on which, what to draw |
| `testing.md`      | `<module>/docs/conventions/`         | what each test layer targets, and how a test is written        |
| `code-style.md`   | `<module>/docs/conventions/`         | the idioms production code follows, and what cleanup may touch |
| `build.md`        | `<module>/docs/conventions/`         | the exact commands that compile, test, and check the module    |
| `agent.md`        | `<module>/docs/conventions/`         | how a coding agent commits, parallelizes, and files its plans  |

A section that does not apply to the module says so explicitly rather than being deleted — a missing answer
should never have to be guessed.

The repository tier — the rules binding every module — has no template: what belongs there is whatever the
repository turns out to share, and it is written from what is found rather than from a list.
