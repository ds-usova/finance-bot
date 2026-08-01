# Conventions > ADR Lifecycle

Applies to every ADR, repo-root and per-service.

An ADR is append-only: it is never deleted, and its **Context** and **Decision** are never rewritten. Only
`Status:` and **Consequences** grow, and only in one of three ways.

| What happened                                           | `Status:`                         | Consequences            |
|---------------------------------------------------------|-----------------------------------|-------------------------|
| A later decision reverses it                            | flips to `Superseded by ADR MMMM` | unchanged               |
| It stops mattering, nothing replaces it                 | flips to `Deprecated`             | one line of why         |
| What it applied to goes away, the decision still stands | unchanged                         | one dated line appended |

A reversing ADR carries `Supersedes: NNNN` and gets its own new number; the one it replaces is the one that
flips to `Superseded by`.
