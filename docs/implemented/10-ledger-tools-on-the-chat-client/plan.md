# Plan: The Ledger's Tools on the Chat Client

**Affected Modules:** `ai-connector-service`, `ledger-service`
**Design:** [The Ledger's Tools on the Chat Client](design.md)

## Step-by-Step Implementation Map (To-Do List)

### Stabilization

#### Interface-First / Build Stabilization

**Interface & Signature Sync**

- [x] ST01 · Delete the extraction vocabulary and everything that reads it, production and test together. Nothing
  below is replaced in place — the classes go, and the tests that name them go with them, because they cannot
  compile once the types are gone.
    - production, per the design's *What is deleted* table: `domain/value/Intent`, `ExpenseIntent`,
      `CategoryIntent`, `UnknownIntent`, `IntentTarget`, `Operation`, `Money`;
      `domain/exception/ExpenseProposalFailedException`; `application/dto/RawIntent`, `ProposedExpense`;
      `application/port/IntentInferencePort`, `ExpenseProposalPort`; `adapter/ai/ExtractedIntent`,
      `ExtractedIntents`; `adapter/ledger/McpExpenseProposalAdapter`, `LedgerMcpProperties`.
    - test classes deleted whole: `domain/value/MoneyTest`, `ExpenseIntentTest`, `CategoryIntentTest`,
      `UnknownIntentTest`, `IntentTargetTest`, `OperationTest`; `application/dto/ProposedExpenseTest`;
      `adapter/ledger/McpExpenseProposalAdapterTest`; `common/IntentFixtures`, `common/LedgerAdapterTest`,
      `common/LedgerAdapterContextTest`.
    - test classes emptied of **every** scenario and of the setup that wires the deleted ports, keeping only the
      class shell for the Red Phase to refill: `application/usecase/ExtractIntentsUseCaseTest`,
      `adapter/ai/AiIntentInferenceAdapterTest` (renamed with its target in ST02),
      `system/ExtractIntentsSystemTest`. None of the three has a scenario that survives the deletions — the two
      provider-failure ones call `infer(...)`, and the system class's five all enter a flow that no longer
      exists.
    - `adapter/grpc/IntentExtractionGrpcServiceTest` loses its two `ExpenseProposalFailedException` error-mapping
      scenarios, and its surviving `whenPortThrowsIntentInferenceException_thenFailsWithUnavailable` is retyped
      to `ExpenseRecordingFailedException` — import, `doThrow`, method name and `@DisplayName` — so the class
      compiles after ST02. The rest of the class stands.
    - `CurrencyCode`, `CurrencyCodeTest`, `ExtractIntentsCommand`, `KnownCategory` and their tests stay
      untouched.
- [x] ST02 · Rename and re-sign what survives, to build-green with stubs.
    - `domain/exception/IntentInferenceException` → `ExpenseRecordingFailedException`, same two constructors.
    - new `application/port/ExpenseRecordingPort` with the single method the design gives it.
    - `adapter/ai/AiIntentInferenceAdapter` → `AiExpenseRecordingAdapter`, now implementing
      `ExpenseRecordingPort`; body stubbed to a no-op with an inline comment naming what it is to do — render the
      user message from the template, prompt the chat client with the ledger's tool callbacks attached, log the
      answer at `debug`, and translate any `RuntimeException` into `ExpenseRecordingFailedException`. Its test
      class is renamed alongside it.
    - `adapter/ai/IntentExtractionProperties` → `ExpenseRecordingProperties`, bound to `ai.expense`; the
      `@EnableConfigurationProperties` in `ChatClientConfiguration` follows the rename.
    - `application/usecase/ExtractIntentsUseCase` keeps its name and its inbound port; its existing body is
      replaced by the null-check it already performs plus a `TODO` at the point where the port call and the INFO
      line go. Its constructor takes `ExpenseRecordingPort` and the logger factory.
    - `adapter/config/UseCaseConfiguration` — the `@Bean` method's parameters follow.
    - `adapter/grpc/GrpcStatusConfiguration` — the `ExpenseProposalFailedException` branch goes; the remaining
      branch maps `ExpenseRecordingFailedException` to `UNAVAILABLE`.
- [x] ST03 · Add the ledger-facing beans as stubs in `adapter/ledger`, each with an inline comment stating its
  intent: `LedgerMcpConfiguration`, declaring the two `McpClientCustomizer` beans; `CallerTokenMcpRequestCustomizer`
  and `LedgerToolFailureProcessor`, both `@Component` and component-scanned — the module's own adapter classes are
  annotated, never listed as `@Bean` methods (D28). Signatures and bean types are final here; bodies are filled in
  the Green Phase.

**Configuration**

- [x] ST04 · `ai-connector-service/src/main/resources/application.yaml` — replace the `ledger.mcp` block with the
  `spring.ai.mcp.client` block from the design's *Configuration* section, and move the prompt properties from
  `ai.intent` to `ai.expense`.
- [x] ST05 · Prompts — delete `prompts/extract-intents.st`, add `prompts/record-expenses.st` and rewrite
  `prompts/user-message.st`, both verbatim from the design's *Prompts* section.
- [x] ST06 · `ledger-service` — raise the `ai-connector` channel's `default.deadline` from `10s` to `60s` in
  `src/main/resources/application.yaml`, and extend `CreateExpenseProposalMcpTool`'s `amountMinorUnits`
  `@McpToolParam` description with the worked conversion the design gives it. Nothing else on that side changes.

**Shared Test Infrastructure**

- [x] ST07 · `common/ChatCompletionFixtures` — replace the intent-entry builders with the two answer shapes this
  change needs: a chat-completion response carrying one or more `tool_calls` on `choices[0].message` (each with
  an id, the tool's name and its arguments as a JSON string), and a plain-text answer with no tool call, which is
  how the model ends a turn. In `common/WireMockStubs`, `stubChatCompletion` now serves a full chat-completion
  body verbatim rather than escaping its argument into `message.content`, which is the structured-output shape
  and cannot carry a tool call; `stubMalformedChatCompletion` goes, its only caller deleted with the
  structured-output scenario. Add a scripted stub — a sequence of chat-completion responses served in order to
  successive requests, via WireMock scenario states — since every tool-calling turn is at least two provider
  round trips.
- [x] ST08 · `common/McpLedgerStubs` — keep the handshake, add a `tools/list` response advertising
  `create_expense_proposal` with the six arguments the ledger declares, and rework the tool-call stubs onto the
  outcomes this change needs: accepted, refused-then-accepted (the retry path), and refused-twice. The transport
  failure stub stays. Note in the class why the tool-call stubs must not assume a fresh session per call: one
  client now serves the whole process.
- [x] ST09 · `common/AiAdapterTest` — retarget it at `AiExpenseRecordingAdapter` and widen it to the stack the
  adapter now needs: `ChatClientConfiguration`, `LedgerMcpConfiguration`, `CallerTokenMcpRequestCustomizer`,
  `LedgerToolFailureProcessor`, and Spring AI's MCP client, streamable-HTTP transport and tool-callback
  autoconfigurations alongside the OpenAI ones already listed. Each of the three `adapter/ledger` classes is
  listed exactly once: `LedgerMcpConfiguration` declares neither of the other two, so the context holds one
  `ToolExecutionExceptionProcessor` definition. Its `DynamicPropertyRegistrar` registers the
  ledger connection's URL on the WireMock singleton as well as `spring.ai.openai.base-url`. `AbstractSystemTest`
  registers the same MCP property in place of `ledger.mcp.url`. `LedgerAdapterContextTest`'s
  boots-itself duty passes to `AiAdapterTest`, which needs its own context test unless RI01's happy path covers
  it first — one exchange through the MCP stub is what testing.md asks for, and RI01 performs one.
- [x] ST10 · Confirm `bot.finance.ai.architecture.CleanArchitectureTest` still passes: no rule is added or
  changed, and the MCP types this change moves stay inside `adapter/ledger`.

### Red Phase

#### TDD Unit Red Phase

- [x] RU01 · `ExtractIntentsUseCase` · test: `ExtractIntentsUseCaseTest` · covers: `extractIntents(command)`
    - `extractIntents(command)`:
        - given: a command carrying a text, three known categories and no assumed currency
          when: the use case runs
          then: the port is called once with that text, the three categories rendered as `Grouping > Category`
          labels in the command's order, and an empty assumed currency
        - given: a command carrying an assumed currency
          when: the use case runs
          then: the port receives that currency code
        - given: a command whose known categories are two entries sharing a name under different groupings
          when: the use case runs
          then: both labels reach the port, distinct and in order
        - given: a null command
          when: the use case runs
          then: it throws `InvalidValueException` and the port is never called
        - given: the port throws `ExpenseRecordingFailedException`
          when: the use case runs
          then: the exception propagates unchanged
        - given: the port returns normally
          when: the use case runs
          then: one INFO line is logged, naming how many categories were offered and carrying nothing from the
          message text
- [x] RU02 · `LedgerToolFailureProcessor` · test: `LedgerToolFailureProcessorTest` · covers:
  `process(ToolExecutionException)`
    - `process(ToolExecutionException)`:
        - given: a failure whose direct cause is an `IllegalStateException` carrying the ledger's refusal text —
          what `SyncMcpToolCallback` builds from a tool error result
          when: the processor runs
          then: it returns that text, so the model reads the refusal
        - given: a failure whose direct cause is an `McpError` — the protocol's own argument-binding failure
          when: the processor runs
          then: it returns its message rather than rethrowing, so the model corrects the call it just made
        - given: a failure whose direct cause is an `McpTransportException`
          when: the processor runs
          then: it rethrows, ending the turn
        - given: a failure whose cause is a plain `RuntimeException` wrapping an `McpTransportException` — the
          shape `LifecycleInitializer` produces when initialization fails
          when: the processor runs
          then: it rethrows, because the chain is walked rather than the direct cause matched
        - given: a failure whose cause is a checked `Exception`
          when: the processor runs
          then: it rethrows, as the framework's own processor does
- [x] RU03 · `CallerTokenMcpRequestCustomizer` · test: `CallerTokenMcpRequestCustomizerTest` · covers:
  `customize(builder, method, uri, body, context)`
    - `customize(builder, method, uri, body, context)`:
        - given: a transport context holding the turn's token, scheme included
          when: the customizer runs
          then: the built request carries it as the `Authorization` header, byte for byte — no re-scheming, no
          trimming
        - given: an empty transport context
          when: the customizer runs
          then: it throws `McpTransportException` and sets no header, so no request leaves unauthenticated

#### TDD Integration Red Phase

- [x] RI01 · `AiExpenseRecordingAdapter` · test: `AiExpenseRecordingAdapterTest` · covers:
  `record(text, knownCategoryLabels, assumedCurrency)`
    - `record(text, knownCategoryLabels, assumedCurrency)`:
        - given: the provider answers one `create_expense_proposal` tool call and the ledger accepts it, with a
          caller token held for the turn
          when: `record` is called
          then: the ledger receives exactly one tool call carrying the arguments the model sent, every request on
          the wire carries that token as `Authorization`, and the call returns without throwing
        - given: the same, with a `merchant` argument on the model's call
          when: `record` is called
          then: `merchant` reaches the ledger verbatim
        - given: labels, a text and an assumed currency
          when: `record` is called
          then: the provider's request carries `record-expenses.st` verbatim as the system message, and a user
          message holding the labels, the currency code and the text; its tool schema names
          `create_expense_proposal` with the six arguments the ledger declares
        - given: no assumed currency
          when: `record` is called
          then: the user message says an amount with no currency is left unrecorded, and names no currency code
        - given: the ledger answers the first tool call with a tool error result, then accepts the corrected one
          when: `record` is called
          then: the refusal text reaches the provider as that tool call's result, the second tool call is made,
          and the call returns without throwing — no exception escapes for a refusal
        - given: the ledger refuses the same expense twice
          when: `record` is called
          then: the call returns without throwing and no proposal is recorded for that expense — a refusal
          exhausted is an expense left unrecorded, not a failed turn
        - given: two turns run in succession under different caller tokens, against one long-lived client
          when: `record` is called for each
          then: each turn's tool call carries its own token, and neither carries the other's
        - given: the provider answers with text and no tool call
          when: `record` is called
          then: the call returns and the ledger receives no tool call
        - given: the provider responds 500
          when: `record` is called
          then: it throws `ExpenseRecordingFailedException`, not a Spring AI or HTTP-client exception
        - given: the ledger's endpoint fails the transport under the tool call
          when: `record` is called
          then: it throws `ExpenseRecordingFailedException`
        - given: no caller token is held for the turn
          when: `record` is called
          then: it throws `ExpenseRecordingFailedException`

  The tool-callback list is cached for the life of the context, so every scenario stubs the handshake and
  `tools/list` whether or not it expects them on the wire, and no scenario asserts on the *number* of MCP
  requests — only on the tool calls among them.

#### TDD System Test Red Phase

- [x] RS01 · `ExtractIntentsSystemTest` · covers: `ExtractIntents`
    - Happy Path:
        - given: the provider answers one `create_expense_proposal` tool call naming a category, a merchant, an
          amount in minor units and a currency, then a plain text answer; the stubbed ledger accepts
          when: a tokened request with a text and the default known categories arrives
          then: the RPC answers an empty response, and the ledger received one tool call carrying those
          arguments under the request's own token
    - Unhappy Path:
        - given: the provider responds with a server error
          when: a tokened request arrives
          then: the RPC fails with `UNAVAILABLE`
        - given: a request carrying no authorization metadata
          when: it arrives
          then: it fails with `UNAUTHENTICATED` and the provider is never called

### Green Phase

#### TDD Unit Green Phase

- [x] GU01 · `ExtractIntentsUseCase` · test: `ExtractIntentsUseCaseTest`
- [x] GU02 · `LedgerToolFailureProcessor` · test: `LedgerToolFailureProcessorTest`
- [x] GU03 · `CallerTokenMcpRequestCustomizer` · test: `CallerTokenMcpRequestCustomizerTest`

#### TDD Integration Green Phase

- [x] GI01 · `AiExpenseRecordingAdapter` · test: `AiExpenseRecordingAdapterTest` · after: GU02, GU03

  Implements the adapter and fills `LedgerMcpConfiguration`'s two beans — the transport customizer wiring, and the
  `transportContextProvider` reading `CallerTokenUtils.callerToken()` on the calling thread. The failure processor
  and the request customizer are GU02/GU03's own `@Component`s, and this test exercises both unmocked.

#### TDD System Test Green Phase

- [x] GS01 · `ExtractIntentsSystemTest` · covers: `ExtractIntents`

### Post-Implementation Steps

- [x] P01 · `ai-connector-service/docs/conventions/architecture.md` — the package tree's `domain/value` examples
  and its `adapter/ledger` line, per the design's D26.
- [x] P02 · `ai-connector-service/docs/conventions/code-style.md` — the *Adapter — AI* section: the
  structured-output bullets go, the per-call tool attachment rule (D3) arrives, and the surviving rule is that
  the adapter translates a failure at this boundary and stops.
- [x] P03 · `ai-connector-service/docs/conventions/testing.md` — the shared-infrastructure list (three entries
  deleted, `AiAdapterTest`'s description widened) and the outbound-integration layer mapping, which now covers an
  AI adapter with a stubbed ledger behind it. The unit layer's mapping gains the rule Q1 settles: an adapter-layer
  class doing something non-trivial is a unit target; one whose behaviour is trivial is left to its adapter's
  integration test.
- [x] P04 · `ai-connector-service/docs/configuration.md` — the `LEDGER_MCP_URL` note and the `OPENAI_MODEL` note,
  which must describe a model that calls tools rather than one that follows an answer shape (D22).
- [x] P05 · Delete the domain pages for the deleted values: `ai-connector-service/docs/domain/intent.md`,
  `expense-intent.md`, `category-intent.md`, `unknown-intent.md`, `intent-target.md`, `operation.md`,
  `money.md`. `currency-code.md` stays.
- [x] P06 · Write ADR: the connector hands the recording to the model, rather than extracting a structured answer
  and calling the ledger itself. It records why the move was made — the ledger writes every refusal as guidance to
  retry against, which only the caller of the tool can act on, and a program that assembles the call cannot read
  it — and what it costs: the model now owns the minor-units conversion and the category split, and nothing caps
  the tool loop (D11, D12, D27).

The use-case page, both contract pages, `ledger-service/docs/contracts/in/mcp.md` and
`ledger-service/docs/contracts/out/ai-connector.md` are `archive-knowledge`'s output and are not listed here.

## Open Questions / Blockers

- **Q1:** `ai-connector-service`'s testing conventions map the unit layer to `domain/`, `application/usecase/`,
  self-validating `application/dto` records, and pure `*Utils` mappers in an adapter package.
  `LedgerToolFailureProcessor` and `CallerTokenMcpRequestCustomizer` are neither: they are adapter-layer policy
  classes with real branching and no infrastructure, and RU02/RU03 test them as plain JUnit with no Spring
  context. Should `conventions/testing.md`'s unit-layer mapping be extended to name that kind of class, or should
  these two be proven only through RI01's wired context?
- A: convention update: if the class does something non-trivial and belongs to the adapter layer, then it can be tested
  with a unit test. If the behavior is rather trivial, consider testing it with an integration test.

- **Q2:** The design records D1 as an ADR candidate: *the connector hands the recording to the model instead of
  performing it itself*. Approve writing it? A `yes` adds a `Write ADR:` item to Post-Implementation Steps. If
  no, the fact lives on in the use-case page and this design file, and nothing downstream writes one.
- A: that's an adr. Provide the reasoning why we moved from structured output to the mcp integration. 

- **B1 (recorded, not blocking):** GI01 needed a check the design had proposed to drop.
  `AiExpenseRecordingAdapter.record` refuses an untokened turn itself, before prompting the model. D8 had made
  `CallerTokenMcpRequestCustomizer` the only guard, but that one fires only when a request is actually built —
  and a turn whose tool list is already cached (D4) and whose model answers without calling the tool builds
  none, so it would have returned successfully having recorded nothing. The design's D8 is amended to record
  both guards.

## Review Findings

- **F1:** ST01's "emptied of their obsolete scenarios" was under-scoped in all three test classes, leaving the
  module build-red at the end of Stabilization.
- Resolution: mechanical
- Action: applied — ST01 now empties `ExtractIntentsUseCaseTest`, `AiIntentInferenceAdapterTest` and
  `ExtractIntentsSystemTest` of every scenario and of the setup wiring the deleted ports, keeping only the class
  shell.

- **F2:** ST01 kept `IntentExtractionGrpcServiceTest`'s `whenPortThrowsIntentInferenceException_...`, which names
  the exception ST02 renames.
- Resolution: mechanical
- Action: applied — ST01 retypes that scenario to `ExpenseRecordingFailedException`, import through
  `@DisplayName`.

- **F3:** `LedgerToolFailureProcessor` was both component-listed in ST09 and declared as a `@Bean` in ST03, giving
  RI01's context two `ToolExecutionExceptionProcessor` candidates.
- Resolution: mechanical
- Action: applied — one definition each, per F4's resolution; ST09 states the invariant.

- **F4:** ST03 declared `LedgerToolFailureProcessor` as a `@Bean` method of `LedgerMcpConfiguration`, against the
  module's bean-declaration rule.
- Resolution: decision
- Action: resolved — `conventions/architecture.md` reserves `@Bean` methods for core and third-party classes and
  requires the module's own adapters to be annotated and component-scanned, which the design already applies to
  `CallerTokenMcpRequestCustomizer`. The processor becomes a `@Component`; ST03 says so, and the design records
  it as D28 with `LedgerMcpConfiguration` down to two beans.

- **F5:** D25's per-turn token isolation lost its only test with `McpExpenseProposalAdapterTest`, and gained none
  — exactly when one long-lived client makes it matter most.
- Resolution: mechanical
- Action: applied — RI01 gains a two-turns-two-tokens scenario.

- **F6:** D5's refused-twice outcome had no scenario, though ST08 builds the stub for it.
- Resolution: mechanical
- Action: applied — RI01 gains it: the call returns and nothing is recorded for that expense.

- **F7:** RS01's five scenarios overran `testing.md`'s one-happy-one-error mapping, two of them repeating RI01
  inside a gRPC envelope.
- Resolution: decision
- Action: resolved — `conventions/testing.md` maps the system layer to "one happy path and one representative
  error path per RPC", and the tool loop is the adapter's own behaviour. RS01 keeps the happy path, the
  provider-500 path and the untokened path; RI01 owns the no-tool-call and refusal-retry scenarios.

- **F8:** `LedgerMcpConfiguration` is stubbed in ST03 and is the target of no Red-phase step.
- Resolution: decision
- Action: resolved — `testing.md`'s outbound-integration layer tests an adapter by calling its own public
  methods, which a `@Configuration` class has none of; the module's rule for wiring is that it is proven by the
  context that boots it, as `LedgerAdapterContextTest` did. RI01's wired context is the intended coverage — its
  happy path and its no-token path cross both arms of the `transportContextProvider` branch, and RU03 owns what
  the customizer does with each.

- **F9:** ST07 reshaped `ChatCompletionFixtures` without addressing its two `WireMockStubs` callers, one of which
  escapes its argument into `message.content` and cannot carry a tool call.
- Resolution: mechanical
- Action: applied — ST07 now states that `stubChatCompletion` serves a full body verbatim and that
  `stubMalformedChatCompletion` goes.
