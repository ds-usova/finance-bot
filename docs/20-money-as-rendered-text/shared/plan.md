# Plan: Money as Rendered Text — the shared schema

**Affected Modules:** `ledger-service`, `web-app`
**Design:** [Money Crosses the Browse API as Rendered Text](../design.md)

Both modules generate from [`openapi/ledger-api.yaml`](../../../openapi/ledger-api.yaml) at build time, so the
schema and every call site it breaks are settled here. Neither module's plan can compile until this one is done.

## Components

No class is designed here. This plan changes one schema file and then stabilizes the code on both sides of it
until each module builds green again.

| Schema          | Change                                                                         |
|-----------------|--------------------------------------------------------------------------------|
| `RenderedMoney` | new — `amount`, `currency`, `separator`, all required                          |
| `DayTotal`      | new — `day`, `amounts`, both required                                          |
| `Expense`       | drops `amountMinorUnits` and `currency`, gains a required `money`              |
| `ExpensePage`   | gains a required `dayTotals`                                                   |

The specification declared no shared component names, so the ledger's generator derived each model name from the
operation and the position the schema sat at — `ListExpenses200ResponseItemsInner` for an entry
([Build](../../../ledger-service/docs/conventions/build.md)). This plan gives every schema a real
`components/schemas` entry in `openapi/ledger-api.yaml`. The names below are what the generator actually emitted,
read back by ST22 and ST23:

| Schema          | `ledger-service` was                | `ledger-service` is           | `web-app`                                |
|-----------------|-------------------------------------|-------------------------------|------------------------------------------|
| `Expense`       | `ListExpenses200ResponseItemsInner`  | `Expense`                     | `components['schemas']['Expense']`       |
| `RenderedMoney` | —                                   | `RenderedMoney`               | `components['schemas']['RenderedMoney']` |
| `DayTotal`      | —                                   | `DayTotal`                    | `components['schemas']['DayTotal']`      |
| `ExpenseStatus` | nested `StatusEnum`                 | `ExpenseStatus`               | `components['schemas']['ExpenseStatus']` |
| `ExpensePage`   | `ListExpenses200Response`           | `ListExpenses200Response`     | `components['schemas']['ExpensePage']`   |
| `Category`      | `ListCategories200ResponseInner`    | `ListCategories200ResponseInner` | `components['schemas']['Category']`   |
| `Grouping`      | `ListGroupings200ResponseInner`     | `ListGroupings200ResponseInner` | `components['schemas']['Grouping']`    |
| `Session`       | `CurrentSession200Response`         | `CurrentSession200Response`   | `components['schemas']['Session']`       |
| `Problem`       | not referenced from Java            | `ListExpenses400Response`     | `components['schemas']['Problem']`       |

The first four are the ones this task needs, and they came out named. The last five are each an operation's
top-level response schema, which the generator names positionally whatever the component block says, because
every operation is reached through an externally-`$ref`ed `openapi/paths/*.yaml` file — B2 records why, and ST22
records that it is the accepted end state. Their component-named classes are generated but referenced by nothing.

The web app's names are unchanged: `openapi-typescript` hoists every entry of the component block under its own
name. No caller outside this repository reads either build's names.

## Step-by-Step Implementation Map (To-Do List)

### Stabilization

#### API Contract

- [x] ST01 · Add the `RenderedMoney` schema to `openapi/components/schemas/expense.yaml`, exactly as the design's
  [shared schema](../design.md#the-shared-schema) states it:
  ```yaml
  RenderedMoney:
    type: object
    description: >-
      One figure, rendered for a reader and answered in parts. A caller shows each part as given, joins them as
      `currency` + `separator` + `amount`, and parses none of them.
    properties:
      amount:
        type: string
        description: >-
          The digits alone, grouped and at this currency's own scale. It never carries the currency.
      currency:
        type: string
        description: >-
          The label that names this currency to an English reader. A symbol where the currency has one, and its
          ISO 4217 code where it does not. Never a key to match on — a key is what `currencyCode` would be, and
          nothing needs one yet.
      separator:
        type: string
        description: >-
          What goes between the label and the digits, so a caller decides nothing. Empty where the two read
          together, a single space where they do not.
    required:
      - amount
      - currency
      - separator
    example:
      amount: '1,245.00'
      currency: 'CHF'
      separator: ' '
  ```
- [x] ST02 · Add the `DayTotal` schema to the same file:
  ```yaml
  DayTotal:
    type: object
    description: What one UTC day of this page recorded, rendered for a reader.
    properties:
      day:
        type: string
        format: date
        description: The UTC day the entries were recorded on.
      amounts:
        type: array
        description: >-
          That day's recorded spend within this page, one figure per currency, ordered by currency code. Nothing
          is converted between currencies.
        items:
          $ref: '#/RenderedMoney'
    required:
      - day
      - amounts
    example:
      day: '2026-01-14'
      amounts:
        - amount: '12.50'
          currency: '€'
          separator: ''
  ```
- [x] ST03 · In `Expense`, drop the `amountMinorUnits` and `currency` properties and both of their `required`
  entries, and add `money` in their place — `$ref: '#/RenderedMoney'`, required. Replace the two dropped keys in
  the schema's `example` with `money: { amount: '12.50', currency: '€', separator: '' }`.
- [x] ST04 · In `ExpensePage`, add a required `dayTotals` — an array of `$ref: '#/DayTotal'`, described as one
  element per UTC day the answered page covers, newest first. Rewrite the schema's `example` so both of its items
  carry `money` instead of the two dropped keys, and add a `dayTotals` list holding the recorded item's day.

The four items below give every schema a real component name. They are numbered from the end of the plan because
an ID is never renumbered once written; read them in the order they are listed.

- [x] ST19 · Add a `components: schemas:` block to `openapi/ledger-api.yaml`, beside the `securitySchemes` it
  already holds. One entry per schema, each `$ref`ing the layered file that defines it — `ExpenseStatus`,
  `Expense`, `ExpensePage`, `RenderedMoney` and `DayTotal` from `components/schemas/expense.yaml`, `Category` and
  `Grouping` from `category.yaml`, `Session` and `TelegramLoginPayload` from `session.yaml`, `Problem` from
  `problem.yaml`.
- [x] ST20 · Repoint every schema `$ref` in `openapi/paths/expenses.yaml`, `categories.yaml`, `groupings.yaml`
  and `session.yaml` at the root document's component — `'../ledger-api.yaml#/components/schemas/<Name>'`. The
  parameter and response `$ref`s are untouched.
- [x] ST21 · Do the same for the three `Problem` refs in `openapi/components/responses/errors.yaml` and for every
  same-file `#/<Name>` ref inside `components/schemas/expense.yaml` — `ExpensePage.items`, `Expense.money` and
  `DayTotal.amounts`. A nested schema reached by a same-file ref is what the generator names positionally, so
  none may be left.
- [x] ST22 · Run `ledger-service/gradlew -p ledger-service openApiGenerate` and read back
  `ledger-service/build/generated/sources/openapi/`. Four models must be named for their component **and used at
  the point of use** — `Expense`, `RenderedMoney`, `DayTotal` and `ExpenseStatus`. Confirm it by reading the
  field declarations, not the file list: `ListExpenses200Response` declares `List<Expense> items` and
  `List<DayTotal> dayTotals`, and `Expense` declares an `ExpenseStatus status` and a `RenderedMoney money`.

  The other five — `ExpensePage`, `Category`, `Grouping`, `Session`, `Problem` — generate as files that nothing
  references, while the operations keep `ListExpenses200Response`, `ListCategories200ResponseInner`,
  `ListGroupings200ResponseInner`, `CurrentSession200Response` and `ListExpenses400Response`. Each is the
  top-level response schema of an operation defined in an externally-`$ref`ed `openapi/paths/*.yaml` file, which
  the generator names positionally whatever the component block says. That is the accepted end state. Do not
  chase it by inlining the path files.
- [x] ST23 · Finish the component block: inline `ExpenseStatus` and `TelegramLoginPayload` into
  `openapi/ledger-api.yaml` beside the eight already there, so no entry `$ref`s a layered file. Repoint
  `openapi/components/parameters/expense-filters.yaml` at `'../../ledger-api.yaml#/components/schemas/ExpenseStatus'`.
  Then remove the four schema files, which nothing reaches any more, in one `git rm`:
  `openapi/components/schemas/expense.yaml`, `category.yaml`, `session.yaml`, `problem.yaml`. Re-run
  `openApiGenerate` and confirm ST22's four names survive.
- [x] ST24 · Update the **File Locations** entry in
  `ledger-service/docs/conventions/architecture.md:77` — schemas live in `openapi/ledger-api.yaml` under
  `components/schemas`, while paths, parameters and responses stay layered under `openapi/paths/` and
  `openapi/components/`. The generated-Java sentence beside it is unchanged.

#### Interface-First / Build Stabilization

**Interface & Signature Sync**

- [x] ST05 · Rename the one generated model that ST22 leaves renamed: `ListExpenses200ResponseItemsInner` becomes
  `Expense`, in `ExpenseWebMapper` and `ExpenseWebMapperTest`. Its nested `StatusEnum` is now the standalone
  `bot.finance.api.model.ExpenseStatus`, so `ListExpenses200ResponseItemsInner.StatusEnum.valueOf(...)` becomes
  `ExpenseStatus.valueOf(...)`.

  `ListExpenses200Response`, `ListCategories200ResponseInner`, `ListGroupings200ResponseInner` and
  `CurrentSession200Response` keep their names — ST22 says why — so `ExpensesController`, `CategoriesController`,
  `GroupingsController`, `CategoryWebMapper`, `CategoryWebMapperTest` and `SessionController` are untouched by
  this item.

  `bot.finance.api.model.Expense` now shares a simple name with `bot.finance.domain.model.Expense`, and
  `bot.finance.api.model.ExpenseStatus` with `bot.finance.domain.value.ExpenseStatus`. `ExpenseWebMapper` and its
  test see both of the second pair: import neither and qualify both, or import the domain one and qualify the
  generated one. Do not import both simple names into one file.
- [x] ST06 · Run `npm run generate:api` from `web-app/` and confirm `src/api/generated/ledger-api.d.ts` still
  exposes the same `components['schemas'][…]` names it does today. The file is untracked (`.gitignore:37`) and
  every `pre*` script regenerates it, so nothing is committed here.
- [x] ST07 · In `ledger-service/src/main/java/bot/finance/adapter/web/ExpenseWebMapper.java`, keep `toItem`'s
  existing mapping of every other field and replace the two dropped arguments with a `RenderedMoney`, built from
  empty strings and marked:
  ```java
  // TODO ledger-service GU03: render entry.money() and carry its amount, currency and separator here
  ```
- [x] ST08 · In the same class, `toResponse` now has to pass `dayTotals` to the generated response constructor.
  Keep its existing mapping, pass `List.of()`, and mark it:
  ```java
  // TODO ledger-service GU03: map page.dayTotals() into the generated day-total models
  ```
- [x] ST09 · In `ledger-service/src/test/java/bot/finance/adapter/web/ExpenseWebMapperTest.java`, the four
  assertions on `getAmountMinorUnits()` and `getCurrency()` inside
  `whenPageHasTwoEntriesWithDifferentStatuses_thenEveryFieldIsMapped()` no longer compile. Comment those four
  lines out, keeping the method, and annotate the method
  `@Disabled("ledger-service RU03: reworked to assert the entry's rendered money")`.
- [x] ST10 · In `web-app/src/testing/fixtures.ts`, `anExpense` sets `money: { amount: '12.50', currency: '€',
  separator: '' }` in place of the two dropped keys, and `anExpensePage` defaults `dayTotals: []`. Every caller
  that overrides neither is then unaffected.
- [x] ST11 · In `web-app/src/api/expenses.ts`, re-export both new schema types beside the five already there:
  ```ts
  export type RenderedMoney = components['schemas']['RenderedMoney'];
  export type DayTotal = components['schemas']['DayTotal'];
  ```
- [x] ST12 · In `web-app/src/components/expenseDays.ts`, `toDaySections` sums a field that no longer exists.
  Give it a second parameter `dayTotals: DayTotal[]`, delete the per-currency summing, and leave every section's
  `totals` empty. The day cutting, the entry order and the awaiting count stay exactly as they are. Drop the
  module's own `DayTotal` type, retype `ExpenseDay.totals` as `RenderedMoney[]`, and import both names from
  `../api/expenses` the way the file already imports `Expense`. Mark the gap:
  ```ts
  // TODO web-app GU01: give each section the figures answered for its day
  ```
- [x] ST13 · In `web-app/src/components/ExpenseList.tsx`, pass `page.dayTotals` as `toDaySections`' second
  argument.
- [x] ST14 · In `web-app/src/components/ExpenseDaySection.tsx`, `formatAmount` reads both dropped fields. Replace
  it with a render of `entry.money.amount` alone at the entry row and `total.amount` alone at the day heading,
  keeping every other part of the component — the day label, the counts, the badge, the classes — untouched, and
  mark it:
  ```tsx
  // TODO web-app GU02: join currency + separator + amount, and let the amount column size to its content
  ```
- [x] ST15 · In `web-app/src/components/ExpenseDaySection.test.tsx`, the local `aDay` helper's `totals` literals
  and every `anExpense` override naming a dropped key stop type-checking. Rewrite each literal into the new shape
  — a `totals` element becomes `{ amount, currency, separator }`, an entry override becomes a `money` object
  carrying the same figure — and skip with `it.skip` every test whose assertion is about the rendered figure,
  naming `web-app RU02` in a comment above it. A test asserting only the day label, the counts or a badge stays
  as it is.
- [x] ST16 · Do the same in `web-app/src/components/expenseDays.test.ts`: rewrite the `anExpense` overrides into
  the `money` shape, pass the second argument `toDaySections` now takes, and `it.skip` every test asserting a
  section's `totals`, naming `web-app RU01`. The day-cutting, ordering, awaiting-count and `relativeDay` tests
  stay green.
- [x] ST17 · Do the same in `web-app/src/components/ExpenseList.test.tsx`, whose three fixture entries name both
  dropped keys. No test there asserts a figure, so none is skipped.
- [x] ST18 · Get both modules back to their own gate. On the ledger, run `spotlessApply` per its
  [Build](../../../ledger-service/docs/conventions/build.md) — ST07, ST08 and ST09 change Java sources — then
  `tools/agent-test/agent-test.sh --module ledger-service --all`, and confirm
  `bot.finance.architecture.CleanArchitectureTest` is among the passes. On the web app, run `npm run verify` from
  `web-app/`, which is lint, format check, build and tests
  ([Building a Node Module](../../../docs/conventions/node-build.md)).

## Open Questions / Blockers

- **B1 (ST07, ST05):** The generated `Expense` takes a real `bot.finance.api.model.ExpenseStatus` enum, not the
  nested `ListExpenses200ResponseItemsInner.StatusEnum` it took before — `ExpenseStatus` is now a component of
  its own, so the generator emits it as a top-level type. `ExpenseWebMapper` already imports
  `bot.finance.domain.value.ExpenseStatus` and uses it in `toFilter` and `toStatus`, so the class needs both
  simple names at once. ST05's "no class imports both today, and none may start" covered `Expense` and
  `Category` and did not foresee this one.
  - Resolution: the domain `ExpenseStatus` keeps the import, and the generated one is written fully qualified at
    its single use site in `toItem`. Confined to `ExpenseWebMapper`; `ExpenseWebMapperTest` names no generated
    status.

- **B2 (ST22, ST05, Q1):** D33's premise does not hold for the ledger. `openapi-generator` keeps a schema's
  component name only where the **operation** is declared inside `ledger-api.yaml` itself. Every operation here
  is reached through an external `openapi/paths/*.yaml`, so any schema sitting at an operation's **top-level
  response** is renamed positionally regardless of whether its `components/schemas` entry is inline or a `$ref`.
  Nested schemas keep their names once their parent is inline.

  Verified against `ledger-service/build/generated/sources/openapi/`:

  | Schema          | Generated as                                             | Used by the API interface       |
  |-----------------|----------------------------------------------------------|---------------------------------|
  | `Expense`       | `Expense`                                                | yes — `List<Expense> items`     |
  | `RenderedMoney` | `RenderedMoney`                                          | yes                             |
  | `DayTotal`      | `DayTotal`                                               | yes — `List<DayTotal> dayTotals`|
  | `ExpenseStatus` | `ExpenseStatus`                                          | yes                             |
  | `ExpensePage`   | `ExpensePage` **and** `ListExpenses200Response`           | the positional one              |
  | `Category`      | `Category` **and** `ListCategories200ResponseInner`       | the positional one              |
  | `Grouping`      | `Grouping` **and** `ListGroupings200ResponseInner`        | the positional one              |
  | `Session`       | `Session` **and** `CurrentSession200Response`             | the positional one              |
  | `Problem`       | `Problem` **and** `ListExpenses400Response`               | the positional one              |

  The four component-named classes in the lower block are generated but referenced by nothing. Closing the gap
  would mean inlining every path operation into the root document and abandoning the `openapi/paths/` split — far
  beyond what ST22's repair sentence authorizes. The naming question is with the user.

  What this task actually needs did come out right: `RenderedMoney`, `DayTotal` and `Expense` are one named type
  each, which is what D33 was for and what the two module plans build on.
  - Consequence for the tree: `ListExpenses200ResponseItemsInner` no longer exists, so `ExpenseWebMapper` and
    `ExpenseWebMapperTest` **must** move to `Expense` or the module cannot compile. That single rename is done
    with ST07 to ST09. The other four call sites are untouched.

- **Q1:** The specification declares no shared component names, so the ledger's generator derives each from the
  operation and position. Should it instead be moved to real `components/schemas` entries so both sides get
  stable names? That is a change to every path file and every existing generated name.
  - A: Yes — do it in this task. ST19 to ST22 add the component block and repoint every `$ref`; ST05 renames the
    four existing generated models at their call sites. Recorded in the design as D33, which supersedes D32's
    answer: `RenderedMoney` now reaches the ledger as one type for both positions.
- **ST22 blocked:** Exit condition unachievable; see blocker B2. openapi-generator keeps a component name only where the operation is declared inside ledger-api.yaml, and every operation here lives in an external path file, so each operation top-level response is still named positionally. Naming decision escalated to the user.
- **ST05 blocked:** Rename list wrong for four of its five entries, for the same reason as ST22. ExpensePage, Category, Grouping and Session keep their positional generated names, so those call sites need no change at all. Only ListExpenses200ResponseItemsInner to Expense is real, and it is mandatory for the ledger to compile; it is carried out with ST07 and ST08. See blocker B2.

## Review Findings

- **F1:** ST06 said to commit `web-app/src/api/generated/ledger-api.d.ts`, which `.gitignore:37` ignores.
  - Resolution: mechanical
  - Action: applied — ST06 regenerates only, and says why nothing is committed.

- **F2:** Nothing re-exported `RenderedMoney` and `DayTotal` from `web-app/src/api/expenses.ts`, where every
  other schema type reaches `components/`.
  - Resolution: mechanical
  - Action: applied — added ST11 for the two re-exports, and had ST12 import both from `../api/expenses`.

- **F3:** ST05 read the three generated Java model names back and recorded them nowhere, leaving the ledger
  plan's later pipeline with only a prediction.
  - Resolution: mechanical
  - Action: applied — ST05 now writes them over the predicted names in the Components table.

- **F4:** The closing gate named only `CleanArchitectureTest` on the ledger and `typecheck` plus `test:run` on
  the web app, short of each module's own gate.
  - Resolution: mechanical
  - Action: applied — ST18 now runs `spotlessApply` and the ledger's full suite, and `npm run verify` for the
    web app.

- **F5:** The web-app plan's RU02 listed the catalogue-substitution test among those to un-skip, though ST15
  never skips it.
  - Resolution: mechanical
  - Action: applied — dropped that test from RU02's list in `web-app/plan.md`.
