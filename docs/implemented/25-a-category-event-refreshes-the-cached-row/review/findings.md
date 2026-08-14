# Review: a category event refreshes the cached row it names

**1 refactoring candidate, deferred by the author. No open bugs.** The entry is `ledger-service`.

## Refactoring candidate

| #  | What                                                                                     | Why the rework left it                                                                                                                                                                                              |
|----|------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| R1 | `CategoryNames` and `CategoryRow` sit in `adapter/cdc`, imported by `adapter/persistence` | An outbound persistence adapter depends on a type the capture adapter owns. Carried from rework 24, where the author deferred it deliberately. Either type moving to `adapter/persistence` inverts it. |
