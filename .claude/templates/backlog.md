# The backlog

`docs/backlog.md`, at the repository root of `docs/`: every refactoring candidate still open across every
archived task and rework, in one table. A person opens it to learn what the repository owes itself without
opening a task's `review/findings.md`; a `rework` starts from a row in it.

**It holds pointers, never a second copy.** The task's `findings.md` owns the row — its text, its status, its
reasoning. The backlog carries one clause and a link, and a row leaves the backlog when the owning row's
`Status` leaves `open`.

Shape:

```
# Backlog

Refactoring candidates still open, one row per `R` row whose owner still says `open`. The owning
`review/findings.md` holds the finding; this file only points at it.

| Raised by | #  | Module     | What                              | Where                                          |
|-----------|----|------------|-----------------------------------|------------------------------------------------|
| task <n>  | R1 | `<module>` | <the row's what, cut to a clause> | [findings](implemented/<n>-<task>/review/findings.md) |
```

- **Raised by** — `task <n>` or `rework <n>`, the directory's number.
- **#** — the row's own id in its findings file, so `task <n> · R1` names it anywhere.
- **What** — one clause, the row's *what* cut to a line; the *why* stays with the owner.
- **Where** — a relative link from `docs/` to the owning `findings.md`.

Rows are appended in the order they are filed and never renumbered; a closed row is removed, not struck through.

Who writes it:

| Moment                                                | Who                          | Does                                                            |
|-------------------------------------------------------|------------------------------|-----------------------------------------------------------------|
| a task's `review/findings.md` is written              | `implement-plan`, phase 3    | appends one row per `R` row it filed                            |
| a rework closes the row it came from                  | `rework`, phase 4            | removes that row; leaves it if the owner's status stays `open`  |
| a person closes a row directly (`done · directly`)   | whoever set the status       | removes the row                                                 |
