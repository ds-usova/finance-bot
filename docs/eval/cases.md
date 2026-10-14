# Corpus and Case Format

| Property | Rule                                          |
|----------|-----------------------------------------------|
| Language | English                                       |
| Identity | stable case id                                |
| Coverage | each case names exactly one composition value |
| Excluded | multilingual extraction                       |
| Files    | one case per `corpus/cases/*.yaml` file       |

## Composition

Every case has one reason to change. Its `covers` value points to exactly one line below, and its golden
expectations assert only the behaviour needed for that line. Other characteristics of its message are incidental.
Each link names a dedicated case. `blocked` means its definition exists but the default fixture cannot establish
the required scenario. Blocked cases are excluded from execution and reported separately, never counted as passes.

| Dimension          | Coverage values and cases                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
|--------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1. Intent | • expense statement — [1-001](corpus/cases/1-001-expense-statement.yaml)<br>• spending question — [1-002](corpus/cases/1-002-spending-question.yaml)<br>• expense and question in one message — [1-003](corpus/cases/1-003-expense-and-question.yaml)<br>• no spending intent — [1-004](corpus/cases/1-004-no-spending-intent.yaml)                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| 2. Expense count | • none — [2-001](corpus/cases/2-001-no-expenses.yaml)<br>• one — [2-002](corpus/cases/2-002-one-expense.yaml)<br>• several — [2-003](corpus/cases/2-003-several-expenses.yaml)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| 3. Currency | • explicit ISO code — [3-001](corpus/cases/3-001-explicit-iso-currency.yaml)<br>• explicit currency name — [3-002](corpus/cases/3-002-explicit-currency-name.yaml)<br>• explicit currency symbol — [3-003](corpus/cases/3-003-explicit-currency-symbol.yaml)<br>• assumed default `HUF` — [3-004](corpus/cases/3-004-assumed-default-currency.yaml)<br>• different currencies in one message — [3-005](corpus/cases/3-005-different-currencies.yaml)<br>• missing or ambiguous currency that cannot be assumed — [3-006](corpus/cases/3-006-unresolvable-currency.yaml)                                                                                                                                                                                                                          |
| 4. Amount | • integer — [4-001](corpus/cases/4-001-integer-amount.yaml)<br>• decimal — [4-002](corpus/cases/4-002-decimal-amount.yaml)<br>• thousands separator — [4-003](corpus/cases/4-003-thousands-separator.yaml)<br>• colloquial or spelled-out amount — [4-004](corpus/cases/4-004-spelled-out-amount.yaml)<br>• amount preserved as written — [4-005](corpus/cases/4-005-amount-preserved.yaml)<br>• missing or ambiguous amount — [4-006](corpus/cases/4-006-unresolvable-amount.yaml)                                                                                                                                                                                                                                                                                                              |
| 5. Merchant | • present — [5-001](corpus/cases/5-001-merchant-present.yaml)<br>• absent — [5-002](corpus/cases/5-002-merchant-absent.yaml)<br>• shared by several expenses — [5-003](corpus/cases/5-003-shared-merchant.yaml)<br>• ambiguous boundary between merchant and description — [5-004](corpus/cases/5-004-merchant-description-boundary.yaml)                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| 6. Description | • explicit item or service — [6-001](corpus/cases/6-001-explicit-description.yaml)<br>• terse description — [6-002](corpus/cases/6-002-terse-description.yaml)<br>• separate descriptions for several expenses — [6-003](corpus/cases/6-003-separate-descriptions.yaml)<br>• ambiguous description — [6-004](corpus/cases/6-004-ambiguous-description.yaml)                                                                                                                                                                                                                                                                                                                                                                                                                              |
| 7. Category selection | • clear grouping — [7-001](corpus/cases/7-001-clear-grouping.yaml)<br>• close category candidates — [7-002](corpus/cases/7-002-close-category-candidates.yaml)<br>• catch-all grouping — [7-003](corpus/cases/7-003-catch-all-grouping.yaml)<br>• category the message does not name — [7-004](corpus/cases/7-004-category-lookup.yaml)<br>• different categories in one message — [7-005](corpus/cases/7-005-different-categories.yaml)                                                                                                                                                                                                                                                                                                                                                       |
| 8. Spending period | • today — [8-001](corpus/cases/8-001-period-today.yaml)<br>• yesterday — [8-002](corpus/cases/8-002-period-yesterday.yaml)<br>• last week — [8-003](corpus/cases/8-003-period-last-week.yaml)<br>• this month — [8-004](corpus/cases/8-004-period-this-month.yaml)<br>• since a weekday — [8-005](corpus/cases/8-005-period-since-weekday.yaml)<br>• explicit period — [8-006](corpus/cases/8-006-period-explicit-period.yaml)<br>• one explicit day — [8-007](corpus/cases/8-007-period-explicit-day.yaml)<br>• omitted year defaults to current year — [8-008](corpus/cases/8-008-period-assumed-year.yaml)<br>• far-future date — [8-009](corpus/cases/8-009-period-far-future.yaml)<br>• date before 1970 — [8-010](corpus/cases/8-010-period-before-1970.yaml)                                              |
| 9. Wording | • direct statement — [9-001](corpus/cases/9-001-direct-statement.yaml)<br>• conversational phrasing — [9-002](corpus/cases/9-002-conversational-wording.yaml)<br>• negation — [9-003](corpus/cases/9-003-negated-spending.yaml)<br>• correction — [9-004](corpus/cases/9-004-corrected-amount.yaml)<br>• quoted spending — [9-005](corpus/cases/9-005-quoted-spending.yaml)<br>• incomplete statement — [9-006](corpus/cases/9-006-incomplete-statement.yaml)<br>• prompt-like instruction — [9-007](corpus/cases/9-007-prompt-like-instruction.yaml), [9-008](corpus/cases/9-008-prompt-injection-other-user.yaml), [9-009](corpus/cases/9-009-prompt-injection-recipe.yaml), [9-010](corpus/cases/9-010-prompt-injection-role-spoofing.yaml), [9-011](corpus/cases/9-011-prompt-injection-call-amplification.yaml) |
| 10. Learned history | • no history — [10-001](corpus/cases/10-001-no-history-control.yaml)<br>• similar approved example — [10-002](corpus/cases/10-002-approved-history.yaml)<br>• similar discarded example — [10-003](corpus/cases/10-003-discarded-history.yaml)<br>• several candidate examples — [10-004](corpus/cases/10-004-multiple-history-candidates.yaml)<br>• unrelated learned example — [10-005](corpus/cases/10-005-unrelated-history.yaml)<br>• explicit current category overrides history — [10-006](corpus/cases/10-006-current-input-overrides-history.yaml) |

- Select representative combinations rather than the full cross-product.
- Each case declares exactly one `covers` value.
- Reference a case from exactly one composition line.
- Do not assert incidental message characteristics.
- Keep the derived application outcome in `expected`, not in the coverage table.

## Case Numbering

| Part | Rule |
|------|------|
| Format | `<dimension>-<local-number>-<slug>`, e.g. `3-001-explicit-iso-currency` |
| Dimension | fixed numeric namespace shown in Composition; do not renumber dimensions when adding one |
| Local number | three digits, allocated independently within each dimension |
| New case | use the next unused local number, even when its coverage line is inserted earlier in the table |
| Stability | do not renumber existing cases or reuse retired numbers |
| Default order | sort dimension and local number numerically, not filenames lexicographically |
| Binding | case filename and `id`, steps filename and `caseId` use the same full identifier |

## Case Format

```yaml
id: 1-001-expense-statement
covers: intent.expense-statement
message: "Lunch was 3,200 forints"
fixture: catalogue-v1
steps: ../steps/1-001-expense-statement.yaml
expected:
  messageOutcome: RECORDED
  proposals:
    count: 1
notes: "Checks that an expense statement creates one proposal."
```

| Field                      | Rule                                                                          |
|----------------------------|-------------------------------------------------------------------------------|
| `id`                       | stable across corpus revisions                                                |
| `covers`                   | the single composition value tested by the case                               |
| `message`                  | English text filed through the application boundary                           |
| `fixture`                  | `catalogue-v1` refers to [Fixture v1](environment-and-fixtures.md#fixture-v1) |
| `steps`                    | path to this case's dedicated steps YAML                                      |
| `expected.messageOutcome`  | required terminal outcome                                                     |
| `expected.proposals.count` | required number of proposals                                                  |
| `expected.proposals.items` | field constraints for the proposals                                           |
| `notes`                    | why the case belongs in the corpus                                            |

## Additional Case Fields

| Field                                           | Meaning                                                                                                                |
|-------------------------------------------------|------------------------------------------------------------------------------------------------------------------------|
| `status: blocked`, `blockedReason`              | case cannot run until the stated fixture limitation is resolved                                                        |
| `learning`                                      | case-owned learning inputs and user decisions; each entry has a unique `id`                                            |
| `learning[].proposalCount`                      | required setup proposal count; mismatch is a setup error                                                               |
| `learning[].classification`                     | grouping and category the user selects through the expense category API before resolving proposals                     |
| `learning[].decision`                           | `approve` or `discard`, applied to every proposal of that learning message                                             |
| `expected.proposals.items` omitted              | check proposal count only; no per-field matching                                                                       |
| `expected.proposalRelations.distinctCategories` | all target proposals have different category IDs                                                                       |
| `expected.mcpCalls.<tool>.count`                | exact number of attempted calls, including rejected calls                                                              |
| `expected.mcpCalls.<tool>.maximumCount`         | maximum attempted calls, including rejected calls                                                                      |
| `executionLimits`                               | enforced per-target call budgets; reaching the limit prevents further dispatch                                         |
| `executionLimits.providerChatCalls`             | positive integer; maximum provider chat requests for the target turn, including retries                                |
| `executionLimits.mcpCalls`                      | positive integer; maximum MCP call attempts across all tools for the target turn, including rejected calls and retries |
| `expected.mcpCalls.<tool>.acceptedCount`        | exact number of successful calls for the target message                                                                |
| `expected.mcpCalls.<tool>.minimumAcceptedCount` | minimum number of successful calls for the target message                                                              |
| `expected.mcpCalls.<tool>.arguments`            | constraints on successful call arguments; retry failures remain diagnostic                                             |

Tool names are matched by the bare MCP name, ignoring transport prefixes. Only declared MCP expectations affect
the verdict. Missing evidence for a declared MCP expectation is an evaluation error. Learning calls are excluded
from target-message checks. In memory cases, target proposal reads exclude proposals from learning messages.

Budget values allow that many dispatches; an attempt to dispatch another call stops the turn with an error.
Learning calls do not consume target budgets. Enforcement and setup checks are defined in
[Execution policy](execution-policy.md).

### Proposal Values

These are the actual values compared with constraints in `expected.proposals.items`.

| Field            | Representation                                                                               |
|------------------|----------------------------------------------------------------------------------------------|
| `money.amount`   | REST-rendered string, e.g. `"850.00"` or `"3,200.00"`; not a YAML number                     |
| `money.currency` | REST-rendered currency label, e.g. `"HUF"` or `"€"`; not necessarily an ISO code             |
| `merchant`       | merchant text returned by REST                                                               |
| `description`    | description text returned by REST                                                            |
| `category`       | category name resolved from the proposal's `categoryId` through the catalogue API; not an ID |
| `grouping`       | grouping name associated with that category in the catalogue; not an ID                      |

`learning[].classification.category` and `.grouping` also use catalogue names; the runner resolves them to IDs
before applying the category change. Text and rendered-money comparisons are exact unless the constraint
explicitly permits alternatives. `absent: true` means missing or null; an empty string is not absent.
`present: true` requires a non-null, non-blank value. `ignored: true` performs no check.

### Date Values

Date tokens in MCP argument constraints resolve from the UTC date captured before target filing.

| Token                     | Value                                               |
|---------------------------|-----------------------------------------------------|
| `run-date`                | captured UTC date                                   |
| `run-date-minus-1-day`    | captured date minus one day                         |
| `previous-week-monday`    | Monday of the previous completed Monday–Sunday week |
| `previous-week-sunday`    | Sunday of that previous week                        |
| `current-month-first-day` | first day of the captured date's month              |
| `most-recent-friday`      | latest Friday on or before the captured date        |
| `current-year-october-10` | October 10 in the captured UTC date's year          |
| `current-year-october-12` | October 12 in the captured UTC date's year          |
| ISO date literal          | exact declared date                                 |

The UTC rollover rule is defined in [Environment and fixtures](environment-and-fixtures.md#date-sensitive-cases).

[Case steps](case-steps.md) owns lifecycle files and step sequences. [Oracle and scoring](scoring.md)
owns how constraint results produce a verdict and aggregate scores.

## Field Constraints

| Constraint | Passes when                                |
|------------|--------------------------------------------|
| `equals`   | the actual value equals the declared value |
| `oneOf`    | the actual value equals one declared value |
| `noneOf`   | the actual value equals no declared value  |
| `present`  | the actual value is present and non-blank  |
| `absent`   | the actual value is absent                 |
| `ignored`  | the field does not affect the verdict      |

Ordinary extraction cases use only the constraints needed to express their purpose. A category need not be exact
when the requirement is that classification avoid `Uncategorized Expenses`.
