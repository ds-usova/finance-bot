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

| Dimension          | Coverage values and cases                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
|--------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Intent             | • expense statement — [001](corpus/cases/001-expense-statement.yaml)<br>• spending question — [002](corpus/cases/002-spending-question.yaml)<br>• expense and question in one message — [003](corpus/cases/003-expense-and-question.yaml)<br>• no spending intent — [004](corpus/cases/004-no-spending-intent.yaml)                                                                                                                                                                                                                             |
| Expense count      | • none — [005](corpus/cases/005-no-expenses.yaml)<br>• one — [006](corpus/cases/006-one-expense.yaml)<br>• several — [007](corpus/cases/007-several-expenses.yaml)                                                                                                                                                                                                                                                                                                                                                                              |
| Currency           | • explicit ISO code — [008](corpus/cases/008-explicit-iso-currency.yaml)<br>• explicit currency name — [009](corpus/cases/009-explicit-currency-name.yaml)<br>• explicit currency symbol — [010](corpus/cases/010-explicit-currency-symbol.yaml)<br>• assumed default `HUF` — [011](corpus/cases/011-assumed-default-currency.yaml)<br>• different currencies in one message — [012](corpus/cases/012-different-currencies.yaml)<br>• missing or ambiguous currency that cannot be assumed — [013](corpus/cases/013-unresolvable-currency.yaml) |
| Amount             | • integer — [014](corpus/cases/014-integer-amount.yaml)<br>• decimal — [015](corpus/cases/015-decimal-amount.yaml)<br>• thousands separator — [016](corpus/cases/016-thousands-separator.yaml)<br>• colloquial or spelled-out amount — [017](corpus/cases/017-spelled-out-amount.yaml)<br>• amount preserved as written — [018](corpus/cases/018-amount-preserved.yaml)<br>• missing or ambiguous amount — [019](corpus/cases/019-unresolvable-amount.yaml)                                                                                     |
| Merchant           | • present — [020](corpus/cases/020-merchant-present.yaml)<br>• absent — [021](corpus/cases/021-merchant-absent.yaml)<br>• shared by several expenses — [022](corpus/cases/022-shared-merchant.yaml)<br>• ambiguous boundary between merchant and description — [023](corpus/cases/023-merchant-description-boundary.yaml)                                                                                                                                                                                                                       |
| Description        | • explicit item or service — [024](corpus/cases/024-explicit-description.yaml)<br>• terse description — [025](corpus/cases/025-terse-description.yaml)<br>• separate descriptions for several expenses — [026](corpus/cases/026-separate-descriptions.yaml)<br>• ambiguous description — [027](corpus/cases/027-ambiguous-description.yaml)                                                                                                                                                                                                     |
| Category selection | • clear grouping — [028](corpus/cases/028-clear-grouping.yaml)<br>• close category candidates — [029](corpus/cases/029-close-category-candidates.yaml)<br>• catch-all grouping — [030](corpus/cases/030-catch-all-grouping.yaml)<br>• category known only after lookup — [031](corpus/cases/031-category-lookup.yaml)<br>• different categories in one message — [032](corpus/cases/032-different-categories.yaml)                         |
| Spending period    | • today — [033](corpus/cases/033-period-today.yaml)<br>• yesterday — [034](corpus/cases/034-period-yesterday.yaml)<br>• last week — [035](corpus/cases/035-period-last-week.yaml)<br>• this month — [036](corpus/cases/036-period-this-month.yaml)<br>• since a weekday — [037](corpus/cases/037-period-since-weekday.yaml)<br>• explicit period — [038](corpus/cases/038-period-explicit-period.yaml)<br>• one explicit day — [039](corpus/cases/039-period-explicit-day.yaml)<br>• omitted year defaults to current year — [040](corpus/cases/040-period-assumed-year.yaml)<br>• far-future date — [041](corpus/cases/041-period-far-future.yaml)<br>• date before 1970 — [042](corpus/cases/042-period-before-1970.yaml)                                                                 |
| Wording            | • direct statement — [043](corpus/cases/043-direct-statement.yaml)<br>• conversational phrasing — [044](corpus/cases/044-conversational-wording.yaml)<br>• negation — [045](corpus/cases/045-negated-spending.yaml)<br>• correction — [046](corpus/cases/046-corrected-amount.yaml)<br>• quoted spending — [047](corpus/cases/047-quoted-spending.yaml)<br>• incomplete statement — [048](corpus/cases/048-incomplete-statement.yaml)<br>• prompt-like instruction — [049](corpus/cases/049-prompt-like-instruction.yaml), [050](corpus/cases/050-prompt-injection-other-user.yaml), [051](corpus/cases/051-prompt-injection-recipe.yaml), [052](corpus/cases/052-prompt-injection-role-spoofing.yaml), [053](corpus/cases/053-prompt-injection-call-amplification.yaml)                       |
| Learned history    | • no history — [054](corpus/cases/054-no-history-control.yaml)<br>• similar approved example — [055](corpus/cases/055-approved-history.yaml)<br>• similar discarded example — [056](corpus/cases/056-discarded-history.yaml)<br>• several candidate examples — [057](corpus/cases/057-multiple-history-candidates.yaml)<br>• unrelated learned example — [058](corpus/cases/058-unrelated-history.yaml)                                                                                                            |

- Select representative combinations rather than the full cross-product.
- Each case declares exactly one `covers` value.
- Reference a case from exactly one composition line.
- Do not assert incidental message characteristics.
- Keep the derived application outcome in `expected`, not in the coverage table.

## Case Format

```yaml
id: 001-expense-statement
covers: intent.expense-statement
message: "Lunch was 3,200 forints"
fixture: catalogue-v1
steps: ../steps/001-expense-statement.yaml
expected:
  messageOutcome: RECORDED
  proposals:
    count: 1
notes: "Checks that an expense statement creates one proposal."
```

| Field                      | Rule                                                |
|----------------------------|-----------------------------------------------------|
| `id`                       | stable across corpus revisions                      |
| `covers`                   | the single composition value tested by the case     |
| `message`                  | English text filed through the application boundary |
| `fixture`                  | preconditions owned by the named fixture            |
| `steps`                    | path to this case's dedicated steps YAML            |
| `expected.messageOutcome`  | required terminal outcome                           |
| `expected.proposals.count` | required number of proposals                        |
| `expected.proposals.items` | field constraints for the proposals                 |
| `notes`                    | why the case belongs in the corpus                  |

## Additional Case Fields

| Field                                           | Meaning                                                                                            |
|-------------------------------------------------|----------------------------------------------------------------------------------------------------|
| `status: blocked`, `blockedReason`              | case cannot run until the stated fixture limitation is resolved                                    |
| `learning`                                      | case-owned learning inputs and user decisions; each entry has a unique `id`                        |
| `learning[].proposalCount`                      | required setup proposal count; mismatch is a setup error                                           |
| `learning[].classification`                     | grouping and category the user selects through the expense category API before resolving proposals |
| `learning[].decision`                           | `approve` or `discard`, applied to every proposal of that learning message                         |
| `expected.proposals.items` omitted              | check proposal count only; no per-field matching                                                   |
| `expected.proposalRelations.distinctCategories` | all target proposals have different category IDs                                                   |
| `expected.mcpCalls.<tool>.count` | exact number of attempted calls, including rejected calls |
| `expected.mcpCalls.<tool>.maximumCount` | maximum attempted calls, including rejected calls |
| `executionLimits` | enforced per-target call budgets; reaching the limit prevents further dispatch |
| `expected.mcpCalls.<tool>.acceptedCount`        | exact number of successful calls for the target message                                            |
| `expected.mcpCalls.<tool>.minimumAcceptedCount` | minimum number of successful calls for the target message                                          |
| `expected.mcpCalls.<tool>.arguments`            | constraints on successful call arguments; retry failures remain diagnostic                         |

Tool names are matched by the bare MCP name, ignoring transport prefixes. Only declared MCP expectations affect
the verdict. Missing evidence for a declared MCP expectation is an evaluation error. Learning calls are excluded
from target-message checks. In memory cases, target proposal reads exclude proposals from learning messages.

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
| `current-year-october-10` | October 10 in the captured UTC date's year            |
| `current-year-october-12` | October 12 in the captured UTC date's year            |
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
