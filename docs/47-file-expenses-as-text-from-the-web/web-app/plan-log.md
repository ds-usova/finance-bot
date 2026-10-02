# Plan Log: File Expenses as Text from the Web — `web-app`

## Review Findings

- **RF01:** ST03, ST04 and ST07 lacked the `after:` markers on the items whose types they use.
  - Resolution: mechanical
  - Action: applied — `after: ST01` on ST03, `after: ST03` on ST04, `after: ST02` on ST07.

- **RF02:** ST04 named the filing callback `file()` while ST03's props name it `onFile()`.
  - Resolution: mechanical
  - Action: applied — ST04's `WriteIn` now carries `onFile()`.

- **RF03:** ST03 and ST06 put decision and finding ids inside the text their stub comments carry.
  - Resolution: mechanical
  - Action: applied — removed the ids and said the comments cite none.

- **RF04:** ST07 and ST08 carried their own reasoning, and ST08's claim about `conventions.test.ts` was wrong.
  - Resolution: mechanical
  - Action: applied — cut both to what they create or run; ST08 names `conventions.test.ts` among what it runs.

- **RF05:** RU01's empty-list scenario differed from the list-of-one only in an input the function does not branch on.
  - Resolution: mechanical
  - Action: applied — dropped it.

- **RF06:** Seven scenarios in RU02, RU03 and RU04 passed against the stubs.
  - Resolution: mechanical
  - Action: applied — each now asserts something a stub cannot produce, as the reviewer proposed.

- **RF07:** RU03's first filing scenario asserted an earlier note gone with no earlier note in its `given:`.
  - Resolution: mechanical
  - Action: applied — dropped the clause.

- **RF08:** RU03's three-asks scenario could not fail "not again after DONE" within 15 seconds.
  - Resolution: mechanical
  - Action: applied — the `when:` is now 20 seconds.

- **RF09:** No scenario covered the line while filing is still unanswered.
  - Resolution: mechanical
  - Action: applied — added an RU03 filing case with `fileMessage` held.

- **RF10:** AC25's focus staying in the field was covered nowhere.
  - Resolution: mechanical
  - Action: applied — added to RU06's `RECORDED` scenario.

- **RF11:** RU06's AC25 scenario used a day already on the page, leaving a first page not holding it unexercised.
  - Resolution: mechanical
  - Action: applied — its `given:` is now a first page under an empty filter not holding the day.

- **RF12:** RU06 repeated the hook's 401 load-time branch, which RU03 owns.
  - Resolution: mechanical
  - Action: applied — dropped it from RU06.

- **RF13:** RU06's premise set the empty-list answer where `vi.resetAllMocks()` would undo it.
  - Resolution: mechanical
  - Action: applied — the premise now sets it in the file's `beforeEach`.

- **RF14:** RU06's placement scenario did not pin the line before the "moved on" line and the awaiting headline.
  - Resolution: mechanical
  - Action: applied — extended its `given:` and `then:`.

- **RF15:** Reading a filed day back after `RECORDED` or `PARTIAL` can be refused or answer 401, and no scenario said
  what the page then does.
  - Resolution: decision
  - Action: resolved — the page's acceptance read back already catches into `report`, giving the listing's banner
    or `sessionExpired` (`web-app/src/pages/ExpensesPage.tsx` `rereadTouchedDays`); added both RU06 scenarios.

## Run Log

- **RL01 (RU02):** expected pass — `WriteInLine.test.tsx` "the field > takes a line break on Shift+Enter without
  filing" passes against the stub: it asserts `onFile` is never called, and a bare textarea takes the line break.
- **RL02 (RU03):** expected pass — `useWrittenMessage.test.tsx` "filing > files nothing from an editable line
  holding only spaces" passes against the stub: it asserts `fileMessage` is never called.
- **RL03 (RU03):** expected pass — `useWrittenMessage.test.tsx` "filing > files nothing from a held line holding a
  text" passes against the stub: it asserts `fileMessage` is never called.
- **RL04 (RU02):** hypothesis: the reading line is queried by `getByLabelText('Reading your text: …')`, which
  fits an `aria-label` or `aria-labelledby` — settled by the markup `WriteInLine` gets in green.
- **RL05 (RU03):** hypothesis: the held-line row relies on `onTextChange` setting the text while the line is
  held — settled by reading the hook once it is implemented.

## Caveats

- **CV01:** `plan.sh` refused at the plugin path by the project hook — "No allow rule covers this command" — and
  absent at the allowlisted `C:/Users/rieic/Projects/tdd-sdlc/scripts/plan/plan.sh`, from plan-task's first
  `validate` on. Validation by hand.

## Suite Runs

- baseline · tree 06fe7bff94e06247fc12814ed0600e501e821a1c · total 290 · skipped 0 · green · paths: web-app
- stabilization · tree 05e2af1602ed0663982933dcdd5d1ba8d00e505e · total 290 · skipped 0 · green · paths: web-app

## Stubs

Marker: `stub-intent:`

- `web-app/src/api/messages.ts`
- `web-app/src/components/WriteInLine.tsx`
- `web-app/src/pages/useWrittenMessage.ts`
- `web-app/src/components/ExpenseDaySection.tsx`
- `web-app/src/components/expenseDays.ts`
