# Review: The Ledger Takes a Turn's Writes over gRPC

**1 refactoring candidate open.**

## Refactoring candidate

| # | Status | module | what | why |
|---|--------|--------|------|-----|
| RX01 | open | `ledger-service` | remove CategoryEntityRepository.findNonEmptyGroupingNames | the catalogue read replaced it; 1 declaration and 0 callers in ledger-service/src, left by the refactor pass as outside its diff |
