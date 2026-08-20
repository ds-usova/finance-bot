# ADR 0006: An expense proposal is a table and an entity of its own

- **Status:** Superseded by ADR 0018
- **Date:** 2026-07-30
- **Source:** [Create an Expense Proposal](../../../docs/implemented/7-create-expense-proposal/plan.md)

## Context

A proposal is a spending record that has been assembled but not yet accepted into the ledger. It carries the same
fields an expense carries, so the cheaper option is a status column on `expense` — `PROPOSED` alongside `ACCEPTED`
— and no new entity, adapter or migration. That makes the ledger's own table the place where unaccepted rows live,
and every read of a user's spending a read that must filter them out.

## Decision

`expense_proposal` is its own table, mirroring `expense` column for column, behind its own `ExpenseProposal`
entity, outbound port and persistence adapter.

## Consequences

- A read of `expense` needs no status predicate: every row in it is spending the user accepted.
- Proposals are short-lived and can be cleared without touching the ledger's table.
- A proposal can grow columns an expense has no use for — a rejection reason, an expiry — without widening
  `expense`.
- Accepting a proposal becomes a write to two tables rather than one column update, and the two schemas have to be
  kept in step by hand: a column added to one is a decision about the other.
