# Review: Change an Entry's Category

**1 bug, 3 refactoring candidates, 8 manual checks. Nothing critical.**

## Bug

**`web-app` — the citation guardrail passes a citation shape it exists to catch**

- **Given** `src/conventions.test.ts`, whose `CITATION` pattern covers `ST|RU|RI|RS|GU|GI|GS` followed by two
  digits, and `[DQPB]` followed by one or two
- **When** a comment cites an acceptance scenario by number — `A26` — and the suite runs
- **Then** the run should fail, naming the line, as it does for `D34` or `RU03`
- **Actual** it passes. One such citation was written during this task and was removed by hand rather than by the
  guardrail
- **Fix** add `A` to the alternation · `web-app/src/conventions.test.ts`

## Refactoring candidate

| Module | What | Why the task left it |
|--------|------|----------------------|
| `web-app` | `dayHeaders()` returns only collapsed headers, so no helper reaches an expanded one | A focus assertion on an open day header had to query inline. The helper is shared and outside this plan's diff · `src/testing/accordion.ts` |
| `ledger-service` | `whenRefileAnswersRowAlreadyCarryingAdmittedCategory_thenAnswerIsThatRowAndNothingRefused` does not arrange the condition it names — it stubs the same entry and category as the `RECORDED` happy path | Collapsing it would change what a test asserts, which the refactor pass does not do · `ChangeExpenseCategoryUseCaseTest` |
| both | The three archived plans cite `D`-numbers that no longer exist | The design was rewritten while the plans ran: its decisions now stop at `D35` and most of the rest became the `F1`–`F34` **Design Findings** table. A reader opening an archived plan hits dead citations · `docs/implemented/22-change-an-expense-category/` |

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
