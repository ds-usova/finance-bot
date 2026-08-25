# `web-app` steps

**Affected Module:** `web-app`
**Rework:** [An entry is refiled by its id alone](../rework.md)

## Steps

- [x] R03 · tests · the two path tests R01 disabled become one: a category change patches `/api/v1/expenses/12` for an entry with id 12, whatever its status, with the document, the CSRF header and the cookies the first test already asserts
  - test-files:
    - `web-app/src/api/expenses.test.ts`
  - survives: a category change patches `/api/v1/expenses/<id>` with a `replace` of `/categoryId`, the CSRF header and the cookies · `expenses.test.ts` with `fetch` stubbed
  - measures: tests skipped in this module 2 -> 0
  - needs: R01
