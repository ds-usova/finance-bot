# Archiving a Rework

What `archive-knowledge` reads when the finished work is a `rework.md`. Everything else about the run is in the
skill itself.

## The Input

**The rework file** — the path given, else the one applied in this conversation. Read it whole, and every
`<module>/steps.md` and `shared/steps.md` beside it. Three things carry the run: **What the code does now**, the
step list with each step's kind, and every `docs:` line.

**The diff is the `**Baseline:**` commit in its header, against the tree as archived.**

**`review/findings.md`**, beside it, for what the rework left open.

## The Gate

The rework file is in its directory under `docs/implemented/`, has no `- [ ]`, and `review/findings.md` exists.

## Where the Survey's Inventory Comes From

**The diff is the filter**, since a rework's steps do not map onto layers the way a plan's do. Without it a run
writes a page for every domain type in the service.

| Artifact      | The inventory                                                                          |
|---------------|------------------------------------------------------------------------------------------|
| Use case      | the usecase classes the diff touched, and no others                                    |
| Domain        | the domain types the diff touched, and no others                                       |
| Contract      | every edge the service has, as for any change — a rework moves code that already talks |
| Configuration | updated where a step touched a knob                                                    |
| Conventions   | a page whose rule any step's edit contradicts, `docs:` line or not                     |

Which steps normally owe what:

| Step kind   | What it normally moves                                                            |
|-------------|-----------------------------------------------------------------------------------|
| `extract`   | structure only, so usually a diagram in a README rather than a page's content     |
| `pin`       | a conventions page, where the check now enforces what the page described in prose |
| `stabilize` | nothing on its own; whatever the step that cleared it changed                     |
| `inline`    | nothing, unless it relocated a file a README's diagram places                     |
| `tests`     | nothing, unless a conventions page described the shape it restructured            |

**A `docs:` line is a claim to check, not an instruction to obey.** The step named the page before the edit
existed, so read the page against the code and write what is true now.

## What Authorizes an ADR

An **Open Question** in the rework file, answered `yes`, asking whether a technical decision the rework settles
should be recorded. The commonest candidate is a structural rule — where a kind of code is now confined, what
may no longer depend on what — and a `pin` step is where one usually shows up.

A rework whose Open Questions hold no such answered question yields no ADR.
