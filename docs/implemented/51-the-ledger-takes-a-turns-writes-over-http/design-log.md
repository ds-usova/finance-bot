# Design Log: The Ledger Takes a Turn's Writes over gRPC

## Concerns

Grilled (2026-10-13): grill-design, twice — the second pass after the move to gRPC (DN03).

| Concern             | Verdict                                                              | Why                                                                                    |
|---------------------|----------------------------------------------------------------------|----------------------------------------------------------------------------------------|
| Failure modes       | `UNAVAILABLE` leaves nothing behind; every status mapped, `INTERNAL` last | the entry and its outbox row commit together (DF01, DF13)                         |
| Idempotency & retry | not idempotent, as the tools                                         | `ledger-service/docs/contracts/in/mcp.md`; task 52 never retries                       |
| Concurrency         | single-row writes; the catalogue cannot change mid-turn              | no write path to a category exists after a user is created (DF07)                     |
| Recovery            | a late call behaves as a late tool call does today                   | the ledger reads rows back by message reference after the call (DF09)                  |
| Data                | no column, no migration; amount, days and instant pinned             | the amount rule is the tools' (DF06); wire forms follow the existing proto (DF18)      |
| Contract compat     | additive: a second service in its own package; two pages change      | DN05, DF08, DF14                                                                       |
| Lifecycle           | the existing proposal and spending query, unchanged                  | same use cases; late rows follow today's stranded and unreported paths                |
| Authorization       | identity from the token only, carried with the call                  | the request carries no person and no message (DF16)                                    |
| Security            | one check, the tools' validators; no browser route; the line off by default | DF05, DF15, DF17, DN07                                                         |
| Observability       | server call metrics by method and status; one WARN per refusal       | DF11; the tools' meters stay untouched for RQ06                                        |
| Limits              | one expense or period per call; the catalogue is 77 rows             | DF07; performance is not measured in this module                                       |
| Business invariants | never filed under a grouping; a pending entry has a message          | enforced in the shared use case and value types                                        |
| Stack-neutral       | pass                                                                 | operations, fields, statuses, settings and a log line only                             |

## Findings

| #    | Question                                                      | Answer                                                                                                  | Evidence                                                                                                  |
|------|---------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| DF01 | Which mapping gives the operations their statuses?            | Their own, after the connector's gRPC status mapping, with the tools' reasons                            | `ai-connector-service/.../adapter/grpc/GrpcStatusConfiguration.java`, `CreateExpenseProposalMcpTool.java` |
| DF02 | What does a reversed period answer?                           | "the period ends before it starts", as the tool                                                         | `ledger-service/.../domain/value/SpendingPeriod.java`                                                     |
| DF03 | Where is "no message reference → unauthenticated" enforced?   | In the turn service's own check; on the shared decoder it would refuse `list_categories`                | `ledger-service/.../adapter/mcp/ListCategoriesMcpTool.java`, `AuthenticatedCaller.java`                   |
| DF05 | Can the browser or its proxy reach the operations?            | No; both proxy `/api` only, and the operations listen on a port published to no host                    | `web-app/nginx.conf`, `web-app/vite.config.ts`, `ledger-service/Dockerfile`                               |
| DF06 | What is a valid amount string?                                | The tools' rule, written into the artifact                                                              | `ledger-service/.../adapter/mcp/ExpenseProposalToolMapper.java`                                            |
| DF07 | Which read yields grouping → categories?                      | The person's categories with their grouping names, ordered by grouping then name                        | `ledger-service/.../adapter/persistence/CategoryEntityRepository.java`                                    |
| DF08 | Which pages change with the catalogue on the request?         | The ledger's out-contract, the connector's in-contract, the proto comment on `category_groupings`       | `ledger-service/docs/contracts/out/ai-connector.md`, `proto/intent_extraction.proto`                      |
| DF09 | Do the token's lifetime and the call deadline still nest?     | Yes: token, then the ledger's deadline, then `LEDGER_TURN_TIMEOUT`                                      | `ledger-service/src/main/resources/application.yaml`, `ledger-service/docs/contracts/out/ai-connector.md` |
| DF10 | Does a second caller of the filing contradict ADR 0008?       | Task 52's DN06 decides: a new record beside it                                                          | `docs/adr/0008-the-connector-hands-expense-recording-to-the-model.md`                                     |
| DF11 | What proves in production that the operations ran?            | The server's call timer by method and status, and the WARN per refusal; the operations page gains it    | `ai-connector-service/docs/contracts/in/operations.md` (`grpc_server_seconds`)                            |
| DF12 | Does connector-side reading of `list_categories` belong here? | Deferred: the tool-calling turn keeps asking the ledger; filed as BT08                                  | `docs/backlog.md`                                                                                         |
| DF13 | What does an exception no handler maps answer?                | `INTERNAL`, mapped explicitly; the framework would answer `UNKNOWN`                                     | `ai-connector-service/.../adapter/grpc/GrpcStatusConfiguration.java`                                      |
| DF14 | What do both builds add for the new package?                  | Coverage exclusions in both, and the package in both architecture tests' bans                           | `ledger-service/build.gradle`, `ai-connector-service/build.gradle`, `CleanArchitectureTest.java`          |
| DF15 | Which validators does the gRPC check use?                     | The tools' exactly, against the ledger's own public key; not the connector's, which lacks the lifetime ceiling and is gated by memory | `ledger-service/.../adapter/security/SecurityConfiguration.java`, `CallerTokenConfiguration.java` |
| DF16 | How do the caller and the message reach the handler and the line? | Carried with the gRPC call; read in the security package; the line takes them as arguments          | `AuthenticatedCaller.java`, `McpCallLine.java`, `ledger-service/docs/conventions/architecture.md`         |
| DF17 | Can the existing security setup run a second check?           | Deferred to the plan: exactly one check runs, and AC08 is tested against the real server               | `ledger-service/build.gradle` (security starters)                                                         |
| DF18 | Which wire forms do days, amount and the instant take?        | Days and amount as strings, as the existing proto; the instant as RFC 3339 UTC; merchant optional       | `proto/intent_extraction.proto`                                                                           |
| DF19 | Does the suite still boot with a gRPC server?                 | Only with the test port set to 0, as the management port is; the server starters join the build         | `ledger-service/src/test/resources/application-test.yaml`, `ledger-service/build.gradle`                  |
| DF20 | How far does the call-line rename reach?                      | The line writer and its three callers; the runner's pattern, fixtures, README and `eval.sh` comment. The `mcpCalls` keys stay | `McpCallLine.java`, `eval-runner/.../observe/McpCallLog.java`, `docs/eval/cases.md`  |
| DF21 | Do the evaluation runner's tests run in CI?                   | No; it is outside the CI matrix, so they run by hand                                                    | `.github/workflows/build.yml`                                                                             |

## Decision Bases

- **DN01:** The user chose the request over a third operation: the ledger already reads the categories in one
  query (`CategoryEntityRepository.java`), and a third operation would add a call and a failure branch to every
  classifier turn. The user kept the catalogue out of the tool-calling prompt, since a model shown every category
  starts inventing its own; a connector-side `list_categories` was filed as BT08 instead.
- **DN02:** The user chose one line for every turn call over a second `mcp_call`-shaped line or none: the line is
  not about MCP but about what a turn did. The runner's parser is the only reader (`eval-runner/.../McpCallLog.java`),
  so the rename lands with it.
- **DN03:** The user chose gRPC over HTTP endpoints outside `/api`: the pair already shares one Protocol Buffers
  schema (`proto/intent_extraction.proto`), a port the web app never proxies (`web-app/nginx.conf`) closes the
  browser out by construction, and the browser's generated client (`web-app/src/api/generated/ledger-api.d.ts`)
  never sees the operations. The cost is a gRPC server in the ledger. Its check takes the shape of the connector's
  interceptor and the validators of the ledger's tools (DF15).
- **DN04:** `eval-runner` had no `docs/conventions.md`, which planning needs. The user had `init-conventions` run
  for it before this task is planned, over planning it from the README alone. It ran on 2026-10-13.
- **DN05:** The user chose a package of its own over reusing `bot.finance.ai.v1`: the existing package names the
  connector's adapter (`proto/intent_extraction.proto`), and a service the ledger serves would carry the wrong
  owner's name on the wire. The cost is the entries DF14 lists.
- **DN06:** The user chose a setting with the next free port over a fixed one. Ports 1000–1005, 1010 and 1011 are
  taken (`README.md`), and a test run needs to move it (DF19).
- **DN07:** The user chose a line off by default over one written everywhere. Today `bot.finance` logs at debug
  (`ledger-service/src/main/resources/logback.xml`), so a person's id and spending text reach every deployment's
  log. Only the evaluation stack needs the line (`eval-runner/compose.eval.yaml`).
