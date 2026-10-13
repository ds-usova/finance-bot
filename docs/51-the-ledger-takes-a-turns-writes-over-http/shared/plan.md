# Plan: The Ledger Takes a Turn's Writes over gRPC — the shared schema

**Format:** 2
**Affected Modules:** `ledger-service`, `ai-connector-service`
**Spec:** [The Ledger Takes a Turn's Writes over gRPC](../spec.md)

Both builds compile every file under `proto/`, so the new service file and the catalogue field reach both. The
operations themselves are the ledger's, in its own plan. The connector only compiles the new types in this task.

## Architecture Decisions

No architectural placement decisions.

## Step-by-Step Implementation Map (To-Do List)

### Stabilization

#### API Contract

- [ ] ST01 · Create `proto/ledger_turn.proto`, as [Turn operations](../turn-operations.md) gives the service
  (DN05, a package of its own):
  - writes: `proto/ledger_turn.proto`
  ```proto
  syntax = "proto3";

  package bot.finance.ledger.v1;

  option java_multiple_files = true;
  option java_package = "bot.finance.ledger.adapter.grpc.v1";
  option java_outer_classname = "LedgerTurnProto";

  // Files a turn's results as the turn's caller. Every call carries the credential the ledger minted for the
  // turn as metadata, `authorization: Bearer <token>`, and acts for the person and the message it names.
  // INVALID_ARGUMENT refuses this one call; every other status ends the turn.
  service LedgerTurnService {
    rpc FileExpenseProposal(FileExpenseProposalRequest) returns (FileExpenseProposalResponse);
    rpc RecordSpendingPeriod(RecordSpendingPeriodRequest) returns (RecordSpendingPeriodResponse);
  }

  message FileExpenseProposalRequest {
    // One of the caller's groupings.
    string grouping = 1;
    // A category filed under that grouping.
    string category = 2;
    // What was bought.
    string description = 3;
    // Who it was bought from; absent or blank is no merchant.
    optional string merchant = 4;
    // In the currency's main unit, as the message wrote it: 1-18 digits, optionally a dot and 1-4 digits.
    // Surrounding whitespace is dropped; no sign, exponent or digit grouping.
    string amount = 5;
    // ISO 4217, three letters.
    string currency_code = 6;
  }

  message FileExpenseProposalResponse {
    int64 id = 1;
    string grouping = 2;
    string category = 3;
    string description = 4;
    // Absent when the entry has no merchant.
    optional string merchant = 5;
    string amount = 6;
    string currency_code = 7;
    // The instant the entry was stored, in UTC, as RFC 3339.
    string created_at = 8;
  }

  message RecordSpendingPeriodRequest {
    // The first day, inclusive, written `YYYY-MM-DD`.
    string from = 1;
    // The last day, inclusive, written `YYYY-MM-DD`; not before `from`.
    string to = 2;
  }

  message RecordSpendingPeriodResponse {
    string from = 1;
    string to = 2;
  }
  ```
- [ ] ST02 · Add the catalogue to `ExtractIntentsRequest` in `proto/intent_extraction.proto`, as
  [Turn operations](../turn-operations.md) gives it (DN01, the catalogue on the request), and reword the comment
  on `category_groupings`:
  - writes: `proto/intent_extraction.proto`
  ```proto
    // The groupings the caller's categories are filed under; must be non-empty. Names exactly the groupings
    // `catalogue` names, in the same order. An expense is filed under one of a grouping's categories, never
    // under the grouping itself.
    repeated string category_groupings = 2;
  ```
  ```proto
    // Every grouping holding at least one category, groupings in alphabetical order, each with the names of
    // its categories in alphabetical order.
    repeated GroupingCategories catalogue = 6;
  }

  message GroupingCategories {
    string grouping = 1;
    // At least one.
    repeated string categories = 2;
  }
  ```

#### Interface-First / Build Stabilization

**Configuration**

- [ ] ST03 · Exclude `bot/finance/ledger/adapter/grpc/v1/**` from the coverage filter in
  `ledger-service/build.gradle`, beside `bot/finance/ai/adapter/grpc/v1/**`
  - writes: `ledger-service/build.gradle`
- [ ] ST04 · Exclude `bot/finance/ledger/adapter/grpc/v1/**` from the coverage filter in
  `ai-connector-service/build.gradle`, beside `bot/finance/ai/adapter/grpc/v1/**`; `tools/agent-test/README.md`
  names both excluded paths · after: ST03
  - writes: `ai-connector-service/build.gradle`, `tools/agent-test/README.md`
- [ ] ST05 · Add `bot.finance.ledger..` to the packages `domain` and `application` may not depend on, in the
  ledger's `CleanArchitectureTest.domainAndApplicationStayFrameworkAgnostic`, and to the generated schema
  packages the ledger's architecture conventions name beside `bot.finance.ai..` and `bot.finance.api..`
  - writes: `ledger-service/src/test/java/bot/finance/architecture/CleanArchitectureTest.java`,
    `ledger-service/docs/conventions/architecture.md`
- [ ] ST06 · Add `bot.finance.ledger..` to the same list in the connector's `CleanArchitectureTest`, and to the
  banned packages its architecture conventions list
  - writes: `ai-connector-service/src/test/java/bot/finance/ai/architecture/CleanArchitectureTest.java`,
    `ai-connector-service/docs/conventions/architecture.md`

#### Closing item

- [ ] ST07 · Both modules compile, pass `CleanArchitectureTest`, and keep their existing suites green:
  `tools/agent-test/agent-test.sh --module ledger-service --all` and
  `tools/agent-test/agent-test.sh --module ai-connector-service --all`
  - writes: none

## Open Questions

None.
