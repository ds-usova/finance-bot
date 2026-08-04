# ADR 0011: The amount is scaled to minor units in the domain

- **Status:** Accepted
- **Date:** 2026-08-04
- **Source:** [The Expense Tool Takes the Amount as the User Wrote It](../../../docs/implemented/12-the-expense-tool-takes-the-amount-as-written/plan.md)

## Context

The expense tool takes the amount in the currency's main unit, as the user wrote it, while `Money` stores minor
units, so something has to scale one to the other by the currency's own number of decimal places. The adapter
reading the wire is the tempting home — it already knows the argument is text and already checks its shape — but
`Money.amount()` holds that same rule in the other direction, and splitting the pair across two layers means the
second adapter to need it copies the rule rather than calls it (D2).

## Decision

The conversion lives in the domain, as `Money.ofMajorUnits(BigDecimal, CurrencyCode)`, beside the `amount()` it
inverts. What a currency's scale is, and what exceeds it, is a fact about money.

What a JSON argument may look like stays in the adapter: `ExpenseProposalToolUtils` checks the text form and hands
the domain a `BigDecimal`. The factory never sees a wire string.

## Consequences

- A second inbound adapter taking an amount calls the factory instead of restating the scale rule.
- Every rejection the scaling can produce is an `InvalidMoneyException` raised in the domain, so the tool renders
  all of them through the one clause it already had.
- Must stay true: the domain factory takes a `BigDecimal` and never a wire string, and no adapter computes minor
  units itself.
