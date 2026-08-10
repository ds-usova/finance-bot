# Review: Change an Entry's Category

**8 manual checks. Nothing else open — the bug and all three refactoring candidates were fixed after the run.**

## Fixed after the run

Kept as a record of what was found and what closed it; none of it is outstanding.

| Module | What was wrong | What closed it |
|--------|----------------|----------------|
| `web-app` | `src/conventions.test.ts` let an `A`-prefixed citation through the guardrail meant to catch it — its pattern covered `[DQPB]` but not `A`, so an `A26` in a comment passed. One had been written during this task and was removed by hand | `A` added to the alternation, the test's name and failure message widened to say "acceptance scenario", and the rule itself widened in `web-app/docs/conventions/code-style.md` so the guardrail and the convention agree |
| `web-app` | `dayHeaders()` returned only collapsed headers, so a focus assertion on an open day header had no helper and queried inline | Split into `collapsedDayHeaders()`, `openDayHeaders()` and a `dayHeaders()` that answers both; `ExpensesPage.test.tsx` now uses the helper · `src/testing/accordion.ts` |
| `ledger-service` | `whenRefileAnswersRowAlreadyCarryingAdmittedCategory_thenAnswerIsThatRowAndNothingRefused` did not arrange the condition it named — byte for byte the `RECORDED` happy path's arrangement | Deleted. A use case cannot observe the category a row carried *before* the write, so the condition is unarrangeable at that level; it is arranged where it is observable, in `ExpenseRepositoryAdapterTest.whenCalledWithSameCategory_thenAnswerCarriesRowAndOnlyUpdatedAtMoved` |
| `ledger-service` | `RefileReportedProposalSystemTest` asserted that a refile sends nothing to Telegram — the absence of behaviour no code path could produce, which is the same reason nothing asserts that a refile writes no `spending_query` row | Deleted, with its bot token. What it actually proved — a proposal refiled and then accepted records the refiled category — is two SQL statements agreeing, so it moved to `ExpenseProposalRepositoryAdapterTest.whenRefiledProposalIsThenAccepted_thenRecordedExpenseCarriesTheRefiledCategory`. The Telegram tap itself stays covered by `ResolveProposalsSystemTest` |
| both | The archived plans cited `D`-numbers the design no longer carries — it was rewritten mid-run, keeping only `D1`, `D3`, `D12`, `D34` and `D35` and moving the rest into an `F1`–`F34` table | Every citation remapped against that table across all three plans; the five surviving `D`-numbers left alone, as were the design's own cross-references to design 21 |

## Manual test

Every item is `web-app`, and none of it is visible to a suite that lays nothing out and resolves no colour. The
list is the design's `F25`. Start the app, sign in, open the expenses listing.

- [ ] A narrow row carrying a long description, a long merchant and a long category name: the merchant gives way
      before the control does, the control keeps a width a person can hit, and an over-long name truncates while
      the picker shows it in full.
- [ ] A row showing a refusal: the message sits under that row, the row grows to hold it, the rows around it do
      not shift under the pointer, and the page's own top banner is untouched.
- [ ] The row control at rest in both themes, on a `PENDING` row and on a `RECORDED` one — the ghost trigger, its
      chevron, the popup and the refusal message, at rest and on hover.
- [ ] The picker opened from a row near the bottom of a long scrolled listing: it opens against the row rather
      than off screen, takes a width of its own rather than the trigger's, and scrolls inside its own bound with
      a long tree.
- [ ] A row whose call is still out, beside the rows around it: busy without the category changing, still
      focusable rather than dead, and every other row's control visibly disabled rather than merely inert.
- [ ] The control reached and driven by keyboard alone: the trigger reachable and self-describing, the list
      walkable, a choice commits, and focus lands on the day section's header when a refiled row leaves the list.
- [ ] A refile watched under a category filter: the row leaves, that day is replaced in place with the figures
      the fresh read answered, and the pager still reads the numbers the original page answered.
- [ ] The picker opening and closing, and the row growing to hold a refusal, with reduced motion turned on.
