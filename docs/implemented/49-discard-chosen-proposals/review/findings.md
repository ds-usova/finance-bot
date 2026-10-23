# Review: Discard Chosen Proposals

**1 deferred change open.**

## Deferred change

| # | Status | module | what | why |
|---|--------|--------|------|-----|
| DX01 | open · [BT06](../../../backlog.md) | `ledger-service` | a store failure while the transaction opens or commits answers 503, as a refused statement does, for both the acceptance and the discard | acceptByIds and discardByIds catch only inside their @Transactional body, and WebExceptionHandler maps no transaction exception, so such a failure reaches onUnexpected and answers 500; deferred by the design as DF07 |
| DX02 | done · directly, was BT07 | `ledger-service` | a report clearing the pool refuses or that fails leaves a log line or a meter | ReportClearingConfiguration's pool runs DiscardPolicy, and ExecutorReportClearingDispatcher ignores both RejectedExecutionException and a RuntimeException from the clearing, so a dropped clearing for an acceptance or a discard leaves no trace; deferred by the design as DF13 |
