# Review: Accept Expenses from the Web App

**Two bugs, four refactoring candidates, eight screens to look at.**

## Bug

Both were found by the archiving pass, in the same use case. `RU04` covered the counts read and neither of these.

**`ledger-service` — a failed report lookup skips every message queued behind it, silently**

- **Given** one acceptance emptied two messages, and the store fails on the read that finds where their reports
  were posted
- **When** the clearing runs
- **Then** each message whose reports can be read has its buttons taken off, and the failure is logged
- **Actual** the exception leaves `clear` into the dispatcher's blanket catch. The first message is not cleared,
  no message after it is even looked at, and nothing is logged. Those reports keep live buttons
- **Fix** one `catch` around the per-message read, as the counts read above it already has ·
  `ClearEmptiedReportsUseCase`

**`ledger-service` — a message that never had a report is logged as cleared**

- **Given** an acceptance empties a message whose report was never recorded — one delivered before this change,
  or one whose delivery Telegram refused
- **When** the clearing runs for it
- **Then** nothing is sent, and it is logged at debug, which is what D40 asks for
- **Actual** nothing is sent, and it is logged at info as a report that was cleared. `allCleared` starts `true`
  and the loop body never runs
- **Fix** log the empty case at debug before the loop · `ClearEmptiedReportsUseCase`

## Refactoring candidate

| Module           | What                                                                                                                              | Why the task left it                                                                                                                                                                |
|------------------|-----------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `ledger-service` | `ClearEmptiedReportsCommand` validates nothing, so a null list would be an `NPE` rather than a domain refusal                     | Unreachable: its only caller always passes a non-null, non-empty list · `B4`                                                                                                       |
| `ledger-service` | `WebExceptionHandlerTest`'s empty-array case asserts the string `100`                                                             | That is the maximum-items bound, so regenerating the schema fails it with nothing broken · `B5`                                                                                    |
| `ledger-service` | `PendingCountProjection.pendingCount` is selected and never read                                                                  | `SELECT DISTINCT` would replace it, which is more than a behaviour-preserving pass takes on · `B6`                                                                                 |
| `ledger-service` | `proposal_report.conversation_id` and `sent_message_id` are bounded by nothing — not the schema, not a type, not `ColumnLimits` | Raised in conversation and left as acceptable. [ADR 0004](../../../ledger-service/docs/adr/0004-column-widths-are-checked-in-the-persistence-adapter.md) is the case for closing it |

## Manual test

All `web-app`, carried from the design's D39. jsdom lays nothing out, so no test closes any of these.

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
