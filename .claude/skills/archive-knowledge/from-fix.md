# Archiving a Bug Fix

What `archive-knowledge` reads when the finished work is a `bug.md`. Everything else about the run is in the
skill itself.

## The Input

**The bug file** — the path given, else the one fixed in this conversation. Read it whole. Three things carry the
run: **What happens**, **Why it happens**, and **What the fix must not break**.

**Every `fix.md` beside it**, one per module, for the step list and every `docs:` line. `fix.sh task <the
directory>` lists them.

**The diff is the `**Baseline:**` commit in `bug.md`, against the tree as archived.**

## The Gate

`bug.md` is in its directory under `docs/implemented/`, and no `fix.md` beside it holds a `- [ ]`.

## Where the Survey's Inventory Comes From

**The diff is the filter.** A fix's steps do not map onto layers the way a plan's do.

| Artifact      | The inventory                                                                            |
|---------------|------------------------------------------------------------------------------------------|
| Use case      | the usecase classes the diff touched, and no others                                      |
| Domain        | the domain types the diff touched, and no others                                         |
| Contract      | every edge a `stabilize` step moved, and every edge a `green` step changed the answer of |
| Configuration | updated where a step touched a knob                                                      |
| Conventions   | a page whose rule any step's edit contradicts, `docs:` line or not                       |

Which steps normally owe what:

| Step kind   | What it normally moves                                                                                                               |
|-------------|--------------------------------------------------------------------------------------------------------------------------------------|
| `green`     | a use-case **Outcome**, a contract's failure description, a domain invariant — the page said the wrong behaviour was the behaviour |
| `stabilize` | a contract, where it moved a signature or a message shape                                                                            |
| `red`       | nothing — it writes a test                                                                                                         |

**A page that documented the bug is the case this run exists for.** A fix is the one kind of work whose finished
state contradicts something already written down as true. Read every page the diff's classes own, not only the
ones a `docs:` line named: the step named the page before it knew what the fix would be.

**The `## Attempts` log is never archived.** It stays in the directory; nothing in it is a fact about the system.

## What Authorizes an ADR

An **Open Question** in `bug.md`, answered `yes`, asking whether a technical decision the fix settles should be
recorded. A bug fix rarely settles one. The commonest candidate is a mechanism the fix
introduced to make a whole class of the bug impossible, such as where idempotency is now enforced.

**"We fixed a bug" is never an ADR.** Nor is the diagnosis. A fix whose Open Questions hold no answered question
of that kind yields no ADR.
