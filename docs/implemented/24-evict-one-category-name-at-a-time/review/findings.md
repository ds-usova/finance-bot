# Review: the name cache holds one category row per id

**3 refactoring candidates. No open bugs.** Every entry is `ledger-service`.

## Refactoring candidate

| #  | What                                                                    | Why the rework left it                                                                                                                                                                                                                                                                     |
|----|-------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| R1 | `CategoryNameReader` no longer reads names                              | It answers one `category` row — a name and a parent id — for a grouping and a category alike, so its name states a role it stopped having. `CategoryRowReader` and `CategoryRowReaderTest` is the rename, and both files sit outside R01's boundary, which is why the step left them. |
| R2 | `CategoryNames` and `CategoryRow` sit in `adapter/cdc`, imported by `adapter/persistence` | An outbound persistence adapter depends on a type owned by the capture adapter. The direction predates this rework, and `CategoryRow` was added in the same shape rather than opening the question mid-step. Either type moving to `adapter/persistence` inverts it, and the resolver imports it instead. |
| R3 | No test follows a rename through capture to the enrichment a consumer reads | `CategoryNameResolverTest` proves the eviction and the re-read; `ChangeStreamReaderTest` proves a rename reaches the stream as a `category` event. Nothing joins them — that a spending entry written after a grouping rename carries the new grouping name is asserted nowhere. |
