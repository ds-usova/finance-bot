# Review: the name cache holds one category row per id

**1 of 3 refactoring candidates still open. No open bugs.** Every entry is `ledger-service`.

## Refactoring candidate

| #  | Status    | What                                                                                     | Why the rework left it                                                                                                                                                                                                                                                                     |
|----|-----------|------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| R1 | done · 25 | `CategoryNameReader` no longer reads names                                               | It answers one `category` row — a name and a parent id — for a grouping and a category alike, so its name states a role it stopped having. `CategoryRowReader` and `CategoryRowReaderTest` is the rename, and both files sat outside R01's boundary, which is why the step left them. |
| R2 | open      | `CategoryNames` and `CategoryRow` sit in `adapter/cdc`, imported by `adapter/persistence` | An outbound persistence adapter depends on a type owned by the capture adapter. The direction predates this rework, and `CategoryRow` was added in the same shape rather than opening the question mid-step. Either type moving to `adapter/persistence` inverts it, and the resolver imports it instead. |
| R3 | done · 25 | No test follows a rename through capture to the enrichment a consumer reads               | `CategoryNameResolverTest` proves the eviction and the re-read; `ChangeStreamReaderTest` proves a rename reaches the stream as a `category` event. Nothing joined them — that a spending entry written after a grouping rename carries the new grouping name was asserted nowhere. |
