# Plan Log: The Ledger Takes a Turn's Writes over gRPC — the shared schema

## Review Findings

- **RF01:** Three documents restating the lists ST03–ST06 change were in no step's `writes:`.
  - Resolution: mechanical
  - Action: applied — the two architecture conventions pages joined ST05 and ST06, `tools/agent-test/README.md` joined ST04.

- **RF02:** The plan names `ai-connector-service`, which the spec's **Affected Modules** omits, though ST04 and
  ST06 change its build and its architecture test (DF14, both builds compile all of `proto/`).
  - Resolution: decision
  - Action: answered — "Add it to the spec". The spec's **Affected Modules** gained `ai-connector-service`, and the
    spec was approved again.

- **RF03:** The proto message `CatalogueEntry` and the ledger's record `application/dto/CatalogueEntry` share a
  name, so `IntentProtoMapper` must spell one of them fully qualified. The artifact names only the fields, so the
  message may still be renamed before the schema lands, for example `GroupingCategories`.
  - Resolution: decision
  - Action: answered — "Rename the proto message". ST02's message is `GroupingCategories`.

- **RF04:** The catalogue's bounds and the operations' blank `category` and unreadable days had no refusal
  scenario in the ledger plan.
  - Resolution: decision
  - Action: resolved — `category_groupings` is derived from the catalogue, so the two cannot disagree; an entry
    always holds a category because `CategoryEntityRepository.findCategoryEntriesForUser` joins each category to
    its grouping, which ledger RI01 asserts; a blank grouping is refused by the checks ST04 keeps, under the
    converted tests of `IntentExtractionRequestTest`. An unreadable day is refused by the use case as
    `InvalidSpendingPeriodException` (`SummarizeSpendingUseCaseTest`, "when the written dates do not make a
    period"), whose INVALID_ARGUMENT mapping ledger RI04 already asserts. A blank category passes the tools' mapper
    and reaches the use case, which refuses it as no category of that name under the grouping, the
    `InvalidCategoryException` RI04 maps — unverified.
