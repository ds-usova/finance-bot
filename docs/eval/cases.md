# Corpus and Case Format

| Property | Rule                                  |
|----------|---------------------------------------|
| Size     | about one hundred messages            |
| Language | English                               |
| Identity | stable case id                        |
| Coverage | tags name the tested dimension values |
| Excluded | multilingual extraction               |

## Composition

Each case combines values from independent dimensions.

| Dimension          | Cases                                                                                                                                                                                                       |
|--------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Intent             | • expense statement<br>• spending question<br>• expense and question in one message<br>• no spending intent                                                                                                 |
| Expense count      | • none<br>• one<br>• several                                                                                                                                                                                |
| Currency           | • explicit ISO code<br>• explicit currency name<br>• explicit currency symbol<br>• assumed default `HUF`<br>• different currencies in one message<br>• missing or ambiguous currency that cannot be assumed |
| Amount             | • integer<br>• decimal<br>• thousands separator<br>• colloquial or spelled-out amount<br>• amount preserved as written<br>• missing or ambiguous amount                                                     |
| Merchant           | • present<br>• absent<br>• shared by several expenses<br>• ambiguous boundary between merchant and description                                                                                              |
| Description        | • explicit item or service<br>• terse description<br>• separate descriptions for several expenses<br>• ambiguous description                                                                                |
| Category selection | • clear grouping<br>• close category candidates<br>• catch-all grouping<br>• category known only after lookup<br>• different categories in one message<br>• no valid category can be established            |
| Spending period    | • today<br>• yesterday<br>• last week<br>• this month<br>• since a weekday<br>• explicit period<br>• one explicit day                                                                                       |
| Wording            | • direct statement<br>• conversational phrasing<br>• negation<br>• correction<br>• quoted spending<br>• incomplete statement<br>• prompt-like instruction                                                   |
| Learned history    | • no history<br>• similar approved example<br>• similar discarded example<br>• several candidate examples<br>• no candidate above the similarity threshold                                                  |

- Select representative combinations rather than the full cross-product.
- Use tags to expose per-dimension regressions.
- Keep the derived application outcome in `expected`, not in the coverage table.

## Case Format

```yaml
id: single-expense-huf-001
tags: [ single-expense, explicit-currency, food ]
message: "Lunch was 3,200 forints"
fixture: catalogue-v1
expected:
  messageOutcome: RECORDED
  proposals:
    count: 1
    items:
      - grouping: { noneOf: [ Miscellaneous ] }
        category: { noneOf: [ Uncategorized Expenses ] }
        description: { ignored: true }
        merchant: { ignored: true }
        amount: { equals: "3200" }
        currency: { equals: HUF }
notes: "Checks an amount written with a grouped thousands separator."
```

| Field                      | Rule                                                |
|----------------------------|-----------------------------------------------------|
| `id`                       | stable across corpus revisions                      |
| `tags`                     | dimension values covered by the case                |
| `message`                  | English text filed through the application boundary |
| `fixture`                  | preconditions owned by the named fixture            |
| `expected.messageOutcome`  | required terminal outcome                           |
| `expected.proposals.count` | required number of proposals                        |
| `expected.proposals.items` | field constraints for the proposals                 |
| `notes`                    | why the case belongs in the corpus                  |

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
