# Conventions > Architecture Decision Records

Applies to every ADR, repo-root and per-service.

## What earns one

A decision constraining future change whose *why* cannot be reconstructed from the code, the schema and the
tests — an external constraint, or a rule that looks arbitrary until you know what it prevents. One decision per
ADR, and most changes record none.

**Name the document that would otherwise own the fact.** A contract page, a use-case page's outcomes, or a
domain page's invariants: that page owns it and there is no ADR. A rule statable without naming a technology, a
file layout or a type is not an ADR however consequential it is — an ADR records how the system is built, and
what the product does is documentation.

## Where it goes, and its number

- `<module>/docs/adr/<nnnn>-<slug>.md` — the decision's consequences stay inside one module;
- repo-root `docs/adr/<nnnn>-<slug>.md` — it constrains more than one module, or the repository itself.

The test: would changing this decision force a change in another module, or in a shared artifact — a shared
schema, the compose file, the repository layout? If yes it is repo-root; otherwise it belongs to the module.

The number is **one global sequence across both tiers** — four digits, one past the highest either tier holds,
never reused and never renumbered. Each tier's sequence therefore carries gaps, and a gap is expected rather
than a defect. It is assigned when the file is created, so a candidate nobody approved consumes none.

## Placed, then written

A record has two states, and both are legitimate.

**Placed** is the file with its number, its title, its metadata and, under each section, a brief saying what
that section has to carry. It is `Status: Proposed`, and it says a decision was made and is owed an
explanation. Placing one is finished work: it lands and is committed with the rest.

**Written** replaces each brief with the explanation and moves `Status:` to `Accepted`.

**Whoever made the decision writes it.** The value of an ADR is a judgement about why a system is the way it
is, and that judgement exists in one place — the person who weighed the alternatives. A paragraph reconstructed
afterwards from the diff reads exactly like a considered one, and the difference only shows years later, to a
reader who has no way to check.

```
# ADR <nnnn>: <the decision, stated as a fact>

- **Status:** Proposed
- **Date:** <YYYY-MM-DD>
- **Source:** [<title>](<the relative path to the work that decided it, from this ADR's own tier>)

> Placed, not yet written. Each section says what it has to carry.

## Context

<What forced a decision here instead of a default — one paragraph, never two. Name the alternative only if it
was genuinely tempting. Not what the codebase did or did not have at the time: the reader arrives years later,
to a tree that has moved on.>

## Decision

<The rule, present tense. Two or three sentences.>

## Consequences

<What it costs and what must stay true. Two or three sentences, or a short bullet list.>
```

## The shape

**An ADR fits on one screen — roughly 20 lines, never more than 30.** It records one decision, and a reader
reaches for it to answer one question: why is it like this, and what may I not break? A decision needing more
room is two decisions.

Write what the code cannot say. Skip anything a reader can get from the schema, the tests or the diff, and link
to the contract and use-case pages rather than restating them.

## Lifecycle

An ADR is append-only: it is never deleted, and its **Context** and **Decision** are never rewritten. Only
`Status:` and **Consequences** grow, and only in one of three ways.

| What happened                                           | `Status:`                         | Consequences            |
|---------------------------------------------------------|-----------------------------------|-------------------------|
| A later decision reverses it                            | flips to `Superseded by ADR MMMM` | unchanged               |
| It stops mattering, nothing replaces it                 | flips to `Deprecated`             | one line of why         |
| What it applied to goes away, the decision still stands | unchanged                         | one dated line appended |

A reversing ADR carries `Supersedes: NNNN` and gets its own new number; the one it replaces is the one that
flips to `Superseded by`. That flip is bookkeeping rather than judgement, and does not wait on the reversing
record being written.
