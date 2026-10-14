# Design Log: The Connector Files a Message's Spending with a Classifier

## Concerns

Grilled (2026-10-13): grill-design, twice — the second pass after the decisions and the move to gRPC.

| Concern             | Verdict                                                          | Why                                                                                       |
|---------------------|------------------------------------------------------------------|-------------------------------------------------------------------------------------------|
| Failure modes       | provider, classifier, ledger and read-out failures each drawn    | AC08–AC10, AC14, AC15, AC17; the classifier runs before anything is filed                 |
| Idempotency & retry | nothing retried, nothing deduplicated, as in the tool mode       | task 51's `turn-operations.md`; a refusal is not retried (DN06)                           |
| Concurrency         | ledger calls stay on the turn's own call context                 | the token, the deadline and the cancellation travel together (DF10, DF27)                 |
| Recovery            | what was filed before a failure stands; the rest is cancelled    | the ledger reads back what landed; a cancelled filing never lands (DF12, DF27)            |
| Data                | amount stays text; currency checked before the classifier        | DF20; the catalogue is fixed at 77 options (DF07); an example gains its merchant (DF26)   |
| Contract compat     | the ledger's contract unchanged; the catalogue checked on arrival | RQ06; DF28                                                                               |
| Lifecycle           | entries are pending proposals; the learning loop still runs      | task 51 publishes the same proposal-created fact (DF06)                                   |
| Authorization       | identity only from the forwarded token                           | ADR 0017; the body carries no person                                                      |
| Security            | the message reaches a new third party                            | DN03; the key is a secret setting                                                         |
| Observability       | four `ai_` meters, ungated; one WARN per classifier failure      | DF13, DF25                                                                                |
| Limits              | one classifier call per turn; every timeout inside the deadline  | DF12; questions per call uncapped by what is known (DF24)                                 |
| Business invariants | filed under a category, never a grouping; order kept; pending    | ADR 0003; one filing at a time in the user's order                                        |
| Stack-neutral       | pass                                                             | wire paths, fields and settings only                                                      |

## Findings

| #    | Question                                                      | Answer                                                                                     | Evidence                                                                                          |
|------|---------------------------------------------------------------|--------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------|
| DF01 | Does the configured model support a response schema?         | The default does; one that cannot fails the turn as unavailable (AC09); the setting's note grows | `ai-connector-service/docs/configuration.md`, `ai-connector-service/docs/contracts/out/ai-provider.md` |
| DF02 | Which instructions does the read-out run under?               | Its own, keeping today's period rules                                                      | `ai-connector-service/src/main/resources/prompts/user-message.st`, `ChatClientConfiguration.java` |
| DF03 | Does the model get the recalled examples?                     | No; recall still runs to store the vector                                                  | `ai-connector-service/docs/usecases/recall-examples.md`                                           |
| DF04 | What does memory-off do to DN03?                              | No recall, so no examples; DN03 matters only with memory on                                | `ai-connector-service/docs/configuration.md` (`MEMORY_ENABLED`)                                   |
| DF06 | Does learning what became of a message still work?            | Yes; task 51's filing publishes the same fact                                              | `ai-connector-service/docs/usecases/learn-message-outcome.md`                                     |
| DF07 | A catalogue past 255 options?                                 | Deferred: every catalogue is the 77 seeded; revisit when a person can create categories    | `ledger-service/docs/domain/grouping.md`                                                          |
| DF08 | How is an option mapped back?                                 | Through the catalogue it was built from; names may hold `/`                                | `ledger-service/docs/domain/grouping.md` (`Taxis/Uber`)                                           |
| DF09 | An implausible classifier answer?                             | A classifier failure (DN02)                                                                | `AiMessageEmbeddingAdapter.java`, which fails a short embedding answer the same way              |
| DF10 | How does the token reach a ledger call run under a timeout?   | The call stays on the turn's thread with a per-call deadline; only the classifier's HTTP call, which needs no token, may move | `CallerTokenContext.java`, `AiMessageEmbeddingAdapter.java`                     |
| DF11 | Where are the turn operations reached?                        | At `LEDGER_TURN_TARGET`, over gRPC, since task 51's DN03 moved them off HTTP               | task 51's `turn-operations.md`                                                                    |
| DF12 | Do the new timeouts fit the ledger's deadline?                | Yes; fewer round trips than the tool mode. Past the deadline the filings left are cancelled, unlike the tool mode | `ledger-service/src/main/resources/application.yaml`, `ai-connector-service/docs/contracts/out/ai-provider.md` |
| DF13 | How are the meters named and gated?                           | `ai_` names, registered with memory off; the operations page changes                       | `ai-connector-service/docs/contracts/in/operations.md`                                            |
| DF14 | Does filing expenses before periods change a figure?          | No; a turn-ending failure also loses the unrecorded periods                                | `ledger-service/docs/usecases/handle-incoming-message.md`                                         |
| DF16 | Is the asynchronous turn this task's?                         | No; it rewrites a promise every caller relies on (DN05 decides whether it is filed)        | `ai-connector-service/docs/contracts/in/intent-extraction.md` "Compatibility"                     |
| DF18 | What does the repository say for DN01 and DN02?               | The only fallback is the catch-all, holding one category in every catalogue                | `ledger-service/docs/domain/grouping.md`, `ledger-service/docs/contracts/out/ai-connector.md`     |
| DF19 | Task 51's DN01 answered "a third operation"?                  | Closed: it answered the request; no ledger read is added                                   | task 51's DN01                                                                                    |
| DF20 | A currency the model invents?                                 | Checked as an ISO 4217 code before the classifier; else unrecorded                         | `ai-connector-service/docs/usecases/extract-intents.md` (the assumed currency's check)             |
| DF21 | What does `NOT_FOUND` from the ledger mean to the turn?       | The caller is unknown, so every later call fails too: it ends the turn. A subject that is no user id at all is `INVALID_ARGUMENT` per call, so the turn files nothing and answers acted on | task 51's `turn-operations.md` |
| DF24 | How many questions can one classifier call carry?             | Deferred: assumed uncapped; revisit if TypeSafe documents a cap                            | https://docs.typesafe.ai/primitives/choice                                                        |
| DF25 | What does someone paged about a classifier failure find?      | One WARN with no text, naming the person and the message where the turn knows them         | `ai-connector-service/docs/usecases/recall-examples.md`, `CallerTokenInterceptor.java` (memory off) |
| DF26 | Does a recalled example carry a merchant?                     | Stored and selected, then dropped; the example value gains an optional merchant, no migration | `V002__create_recorded_expense.sql`, `RecordedExpenseEntityRepository.java`, `ExampleExpense.java` |
| DF27 | Do the ledger calls inherit the inbound call's deadline?      | Yes: each ends at `LEDGER_TURN_TIMEOUT` or the ledger's deadline, whichever is first       | `CallerTokenInterceptor.java`, `ledger-service/src/main/resources/application.yaml`               |
| DF28 | What may the connector assume about the catalogue?            | A catch-all entry and no empty entry, checked on arrival in both modes                     | `proto/intent_extraction.proto`, task 51's `turn-operations.md`                                   |
| DF29 | What does the connector need to call the ledger over gRPC?    | The client starter and a named channel; stubs already generate from `proto/`               | `ai-connector-service/build.gradle`, `ledger-service/build.gradle`                                |
| DF30 | An answer with no probability for an option?                  | A classifier failure (DN02); DN01 needs every probability                                  | `ai-connector-service/docs/contracts/out/ai-provider.md`                                          |

## Decision Bases

- **DN01:** The user chose the catch-all over filing the most probable category anyway, or leaving the expense
  unrecorded. The catch-all is the repository's only fallback, "where spending falls when no other grouping fits"
  (`ledger-service/docs/domain/grouping.md`), and a person refiles a pending entry already
  (`ledger-service/docs/usecases/change-an-expense-category.md`). An unrecorded expense would turn the report
  partial for want of a category.
- **DN02:** The user chose the catch-all over failing the turn or rerunning it as a tool-calling turn. A failed call
  answers no probabilities, so the category is the catch-all's first as the catalogue lists it; every catalogue's
  catch-all holds one (`ledger-service/docs/domain/grouping.md`). The
  classifier is in early access, so outages are expected, and the expenses are already read. Failing would throw
  them away, as the provider's failure does (`ai-connector-service/docs/usecases/extract-intents.md`). A rerun would
  double the cost and the latency for the turns least able to afford either.
- **DN03:** The user chose the message plus the examples without amounts over the message alone, or the examples
  whole. A person's own filings are what makes a category theirs, the reason the recall exists
  (`ai-connector-service/docs/usecases/recall-examples.md`). Amounts and currencies say nothing about a category,
  and a second third party gets no more than it needs.
- **DN04:** The user chose a later task over widening this one. The runner is another module, and task 51 already
  moves it onto the shared call line. What remains is a classifier turn's expectations, such as a case expecting
  `list_categories` (`docs/eval/corpus/cases/7-004-category-lookup.yaml`). Filed as BT09.
- **DN05:** The user chose a later task over folding it in. An empty answer meaning "acted on" is a promise every
  caller relies on (`ai-connector-service/docs/contracts/in/intent-extraction.md`, "Compatibility"), and both
  modes and both services change with it. Filed as BT10.
- **DN06:** The user chose a new record beside ADR 0008 over superseding it. The tool-calling mode stays the
  default and keeps every consequence 0008 lists (`docs/adr/0008-the-connector-hands-expense-recording-to-the-model.md`).
  The new record covers the classifier mode only, whose refusals reach a program and are not retried.
- **DN07:** The user chose a strict start over treating an empty mode as `tools` and ignoring case; the module's
  other settings refuse a value they cannot run (`ai-connector-service/docs/configuration.md`).
- **DN08:** The user chose refusing over trusting the ledger; a repeated pair gives two options one key, so the
  classifier's answer could not be mapped back (`the-two-calls.md`, an option's key).
