# Design Log

## Concerns

Grilled (2026-10-14): grill-design

| Concern             | Verdict | Why                                                                                                                     |
|---------------------|---------|-------------------------------------------------------------------------------------------------------------------------|
| Failure modes       | clear   | the lookup does no I/O; a ledger outage now first shows at recording, and fails the turn as before (`ledger-mcp.md`)    |
| Idempotency & retry | clear   | a lookup stores nothing, so a repeat answers alike (AC05); recording is unchanged                                       |
| Concurrency         | finding | the chat client is shared across turns; the catalogue must not be (DF05)                                                |
| Recovery            | clear   | the lookup has one effect and no store                                                                                  |
| Data                | finding | matching is exact on both sides; a grouping stored with no categories is answered differently (DF03, DF07)              |
| Contract compat     | finding | proto and prompt unchanged; the answer's wire form and the duplicate tool name are pinned (DF01, DF02, DF04)            |
| Lifecycle           | clear   | no entity; the catalogue lives only for the turn                                                                        |
| Authorization       | finding | the person and the message are known only from a verified token (DF06); recording is still checked by the ledger        |
| Security            | clear   | the model's name is a map key and is echoed in the refusal, as the ledger does today                                    |
| Observability       | finding | the ledger's `list_categories` series stops; DN03 — a connector meter by outcome; DN01 — evaluation counting            |
| Limits              | clear   | the catalogue is already on the request; `ai-connector-service/docs/conventions/testing.md` measures no performance     |
| Business invariants | clear   | an offered grouping is always in the catalogue (`ExtractIntentsCommand.java`); the ledger re-checks at recording        |
| Stack-neutral       | pass    | the artifact names wire tool names and refusals only; its redundant alt block became the answers table                  |

## Findings

| #    | Question                                                     | Answer                                                                                                           | Evidence                                                                                  |
|------|--------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------|
| DF01 | Does a connector-side `list_categories` contradict ADR 0008? | yes, its "one owner of the schema" consequence; whether a record follows is the plan's approval question         | `docs/adr/0008-…-to-the-model.md`; `ai-connector-service/docs/conventions/agent.md`       |
| DF02 | What exactly is the model answered?                          | the ledger's answer and refusal texts, and its tool name, description and argument, to the letter                | `ListCategoriesMcpTool.java`, `ListCategoriesUseCase.java`, `ListCategoriesCommand.java`  |
| DF03 | A grouping the ledger stores with no categories?             | refused as not stored, where the ledger answers an empty list; nothing can be filed there                        | `GroupingRepositoryAdapter.java`; `proto/intent_extraction.proto` (`catalogue`)           |
| DF04 | The ledger's own `list_categories` while it is published?    | dropped by name before the tools are offered (DN02 — the ledger keeps its tool)                                  | `ai-connector-service/docs/contracts/out/ledger-mcp.md`; `AiExpenseRecordingAdapter.java` |
| DF05 | Can one turn's catalogue answer another's lookup?            | no: the lookup is built per turn from its own request, never held on the shared client                           | `ledger-mcp.md` ("two turns share the client, never the identity")                        |
| DF06 | Whom is a lookup counted against?                            | withdrawn: no lookup is counted per message (DN01 — the cases drop their lookup expectations)                    | `CallerTokenInterceptor.java`; `ai-connector-service/docs/conventions/architecture.md`    |
| DF07 | A category changed during the turn?                          | the catalogue is a snapshot; a removed one is refused at recording and retried, an added one not offered         | `CreateExpenseProposalUseCase.java`                                                       |
| DF08 | Which pages describe the lookup reaching the ledger?         | `ledger-mcp.md` and `extract-intents.md` at archive; `docs/eval/application-boundary.md` by the plan             | `ai-connector-service/docs/conventions/follow-up.md`; `docs/eval/application-boundary.md` |
| DF09 | Does the ledger's `list_categories` go?                      | deferred: kept and never offered (DN02); back when no other MCP client needs it, as BT11                         | `docs/backlog.md`, BT11; `CreateExpenseProposalMcpTool.java`                              |
| DF10 | Does a run's ledger call count change?                       | yes: in the tools mode it drops by the turn's lookups; a comparison with an older run shows it                   | `eval-runner/README.md`; `ledger-service/docs/contracts/in/operations.md`                 |
| DF11 | Which module carries the corpus edit?                        | none: the case files are no module's code; the plan carries them, as DF08's page                                 | `docs/implemented/53-the-runner-measures-a-classifier-turn/spec.md`                       |
| DF12 | Does `9-011`'s call budget still count lookups?              | deferred: the budget is unenforced and the case blocked; back with the per-turn budget (demo step 2)             | `docs/eval/corpus/cases/9-011-prompt-injection-call-amplification.yaml`                   |
| DF13 | Arguments the lookup cannot read?                            | refused with the no-grouping text, and the turn goes on, as the ledger refuses an argument's type today          | `ai-connector-service/docs/contracts/out/ledger-mcp.md` (refused calls)                   |

## Decision Bases

- **DN01:** The user chose dropping the lookup expectations over a connector-side call line read by the runner,
  here or in a later task; the runner reads a missing line as zero calls (`eval-runner/.../McpExpectations.java`),
  and task 53 already dropped `7-004`'s for the same reason (its DN01).
- **DN02:** The user chose keeping the ledger's tool over removing it here or in a separate task; removal would
  reach into `ledger-service`, and `CreateExpenseProposalMcpTool.java` still points its caller at the tool.
- **DN03:** The user chose a connector meter over none and over a meter with a dashboard panel; today the
  `list_categories` series lives on the ledger's tool meter (`ledger-service/docs/contracts/in/operations.md`), and
  the connector's operations contract (`ai-connector-service/docs/contracts/in/operations.md`) lists no lookup
  meter.
