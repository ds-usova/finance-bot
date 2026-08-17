# Review: The Model Reads the Connector's Memory

**2 bugs, 1 refactoring candidate open.** Nothing critical; the suite is green at 401 tests with none skipped.

## Bug

**`ai-connector-service` — an example's outcome reads `dıscarded` under a Turkish-locale JVM**

- **Given** the service running on a JVM whose default locale is Turkish, and a person with an earlier message
  holding a discarded expense
- **When** a new message is read and that earlier one is recalled as an example
- **Then** the example's line ends `discarded`, the word the standing instructions tell the model to expect
- **Actual** it ends `dıscarded` with a dotless ı, since the outcome's name is lowercased with no locale, and the
  model reads a label nothing defines
- **Fix** lowercase with `Locale.ROOT` · `adapter/ai/ExampleSectionRenderer` · unverified — no run used that
  locale

**`ai-connector-service` — a valid currency code is refused under a Turkish-locale JVM**

- **Given** the same JVM, and a caller sending an assumed currency in lower case, `ils`
- **When** the code is read into a `CurrencyCode`
- **Then** it is accepted as ILS
- **Actual** it uppercases to `İLS`, which no ISO 4217 lookup knows, and the turn is refused as an invalid
  argument
- **Fix** uppercase with `Locale.ROOT` · `domain/value/CurrencyCode` · unverified — no run used that locale, and
  the defect pre-dates this task

## Refactoring candidate

Every row is `ai-connector-service`.

| #  | Status | What                                                                                              | Why                                                                                                                                  |
|----|--------|---------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------|
| R1 | open   | split `AiMessageEmbeddingAdapterTest`'s short-vector-list and server-error scenarios into two tests | one method proves two conditions, against the one-condition-one-outcome rule in `conventions/testing.md`; the refactor pass deferred it because splitting moves the test count its own guardrail pins |
