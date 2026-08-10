# Review: Accept Expenses from the Web App

**Two bugs, four refactoring candidates, eight screens to look at.**

## Bug

| What breaks                                                                                                                                                                         | Proposal                                                                | Where                                          |
|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------|------------------------------------------------|
| The store fails while the clearing reads where reports were posted, so some reports keep live buttons and every message queued behind the failing one is skipped. Nothing is logged | One `catch` around the per-message read, as the counts read already has | `ClearEmptiedReportsUseCase`, `ledger-service` |
| The log says a report was cleared for a message that never had one                                                                                                                  | Log it at debug, which is what D40 asks for                             | `ClearEmptiedReportsUseCase`, `ledger-service` |

Both were found by the archiving pass. `RU04` covered the counts read and neither of these.

## Refactoring candidate

| What                                                                                                                              | Why the task left it                                                                                                                                                                |
|-----------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `ClearEmptiedReportsCommand` validates nothing, so a null list would be an `NPE` rather than a domain refusal                     | Unreachable: its only caller always passes a non-null, non-empty list · `B4`                                                                                                       |
| `WebExceptionHandlerTest`'s empty-array case asserts the string `100`                                                             | That is the maximum-items bound, so regenerating the schema fails it with nothing broken · `B5`                                                                                    |
| `PendingCountProjection.pendingCount` is selected and never read                                                                  | `SELECT DISTINCT` would replace it, which is more than a behaviour-preserving pass takes on · `B6`                                                                                 |
| `proposal_report.conversation_id` and `sent_message_id` are bounded by nothing — not the schema, not a type, not `ColumnLimits` | Raised in conversation and left as acceptable. [ADR 0004](../../../ledger-service/docs/adr/0004-column-widths-are-checked-in-the-persistence-adapter.md) is the case for closing it |

## Manual test

Carried from the design's D39. jsdom lays nothing out, so no test closes any of these.

- [ ] a day mixing a `PENDING` and a `RECORDED` row, at the narrowest supported width — the reserved gutter, and
      what the description truncates to
- [ ] the action bar in both themes, empty and holding the action
- [ ] the checkbox's focus ring under a keyboard alone, ticking and unticking without a pointer
- [ ] a full page of pending entries with every row ticked — what the two counts do at three digits
- [ ] a day header carrying its checkbox and its badge at once, at the narrowest supported width, including the
      partly ticked state
- [ ] the day checkbox under a keyboard alone — reached before or after the trigger, and whether the day opens
      by mistake
- [ ] an acceptance watched from an open day — what replacing that day in place looks like
- [ ] the section's open-and-close animation with reduced motion turned on

A first look during the run changed three of these before anyone signed them off: every tick now sits in one
leading gutter, a day header shows one badge rather than two, and the accept action is outlined rather than
accent-filled.
