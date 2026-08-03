# ADR 0011: The amount is scaled to minor units in the domain

- **Status:** Accepted
- **Date:** 2026-08-04
- **Source:** [The Expense Tool Takes the Amount as the User Wrote It](../../../docs/implemented/12-plan-the-expense-tool-takes-the-amount-as-written.md)

## Context

The expense tool takes the amount in the currency's main unit, as the user wrote it, while `Money` stores minor
units. Something has to scale one to the other, and the scale is the currency's own — ISO 4217 gives the forint
two fraction digits whatever Hungarian practice is.

The adapter that reads the wire is the obvious home: it already knows the argument is text and already checks its
shape. But `Money.amount()` holds the same rule in the other direction, so putting the inverse in an adapter
leaves one rule in two layers, and the second adapter to need it copies the rule rather than calls it.

## Decision

The conversion lives in the domain, as `Money.ofMajorUnits(BigDecimal, CurrencyCode)`, beside the `amount()` it
inverts. What a currency's scale is, and what exceeds it, is a fact about money.

What a JSON argument may look like stays in the adapter: `ExpenseProposalToolUtils` checks the text form and hands
the domain a `BigDecimal`. The factory never sees a wire string.

## Consequences

- A second inbound adapter taking an amount calls the factory instead of restating the scale rule.
- Every rejection the scaling can produce — a currency with no minor unit, an amount finer than its currency, an
  overflow — is an `InvalidMoneyException` raised in the domain, so the tool renders all of them through the one
  clause it already had.
- The two layers fail for different reasons and say so differently: a malformed argument is refused before any
  arithmetic runs, an unscalable amount after.
- Must stay true: the domain factory takes a `BigDecimal` and never a wire string, and no adapter computes minor
  units itself.
