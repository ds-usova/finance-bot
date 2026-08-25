# Review: Malformed Response Errors Carry a Catalogue Key

**1 refactoring candidate open.**

## Refactoring candidate

| #  | Status | Module    | What                                                                          | Why                                                                                                                                                                                                                                                                                                          |
|----|--------|-----------|----------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| R1 | open   | `web-app` | give `SettingsPage.report` and `ExpensesPage.report`/`onChangeCategory` a shared home | The four functions are now near-identical error-triage callbacks — `instanceof MalformedResponseError`, then `instanceof ApiError`, then a plain `Error` fallback — repeated across two pages. This task's own refactor pass considered extracting a shared helper and declined: the conventions name only two extraction targets (`components/` for shared rendering, `api/` for shared calls), and neither fits a non-rendering function that isn't an API call. [Task 44](../../implemented/44-the-browser-owns-its-refusal-wording/review/findings.md) added the same shape of branching to these four functions and left it unextracted for the same reason; this task's third branch is the second time the pattern has repeated without a home. |
