# Review: Accept Expenses from the Web App

A person can tick pending entries on the listing and accept them in one action; the ledger moves them, clears the
buttons on any report the acceptance emptied, and a turn is now named by the message that started it. Everything
below is open.

## ledger-service

- **`ClearEmptiedReportsCommand` does not validate itself** — against the convention that an inbound-port command
  does. `ClearEmptiedReportsUseCase.clear` reads `command.incomingMessageIds()` with no null check, so a null
  command or a null list raises `NullPointerException` rather than a domain exception. No live defect: its only
  caller, `AcceptExpensesUseCase`, always passes a non-null, non-empty list. From `B4`.
- **A bounds test leans on a bound the generated validator happens to name.** `WebExceptionHandlerTest`'s
  empty-array case asserts the string `100` in the message, which is the maximum-items bound rather than
  anything about an empty list. Regenerating the OpenAPI model could fail it with nothing broken. From `B5`.
- **`PendingCountProjection.pendingCount` is never read.** `findPendingCounts` selects `count(*)` and the adapter
  only asks which ids came back, so the projection and the counted column could both go in favour of
  `SELECT DISTINCT incoming_message_id`. Skipped as more than a behaviour-preserving pass should take on. From
  `B6`.
- **Three tables carry `incoming_message_id` as unbounded `TEXT`, and `proposal_report` two more.** The byte
  bound lives on `IncomingMessageId` because Postgres has no byte-width string type, which is right; but
  `conversation_id` and `sent_message_id` are bounded nowhere — not by the schema, not by a type, not by
  `ColumnLimits`. [ADR 0004](../../../ledger-service/docs/adr/0004-column-widths-are-checked-in-the-persistence-adapter.md)
  exists for exactly that case. Raised in conversation and left as acceptable for now.

## web-app

- **The screens still to look at**, carried from the design's D39. The suite runs under jsdom, which lays nothing
  out, so none of this is closed by a test:
    - a day panel mixing a `PENDING` and a `RECORDED` row at the narrowest supported width — the reserved gutter,
      and what the description truncates to;
    - the action bar in both themes, empty and holding the action;
    - the checkbox's focus ring under a keyboard alone, ticking and unticking without a pointer;
    - a full page of pending entries with every row ticked — what the two counts do at three digits;
    - a day header carrying its checkbox, the awaiting badge and the ticked badge at once, at the narrowest
      supported width, including the partly ticked state;
    - the day checkbox under a keyboard alone — whether it is reached before or after the trigger, and whether
      the day opens by mistake;
    - an acceptance watched from an open day section, for what replacing that day in place looks like;
    - the section's open-and-close animation with reduced motion turned on.

  Five of these were looked at during the run and answered with changes — the ticks now share one column, the
  day's tick sits over the entry rows' gutter, the row hover covers it, a day shows one badge rather than two,
  and the accept action is outlined rather than accent-filled. The rest are unchecked.
