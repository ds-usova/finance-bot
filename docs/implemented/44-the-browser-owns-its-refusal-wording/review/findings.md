# Review: The Browser Owns Its Refusal Wording

**1 refactoring candidate open.**

## Refactoring candidate

| #  | Status | Module    | What                                                                                | Why                                                                                                                                                                                                            |
|----|--------|-----------|--------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| R1 | open   | `web-app` | move the malformed-2xx-body diagnostic literals off their hardcoded English wording | `expenses.ts` and `preferences.ts` throw `new Error(...)` with hardcoded English (`'the acceptance answered with no counts'` and its siblings) when a 2xx response's body doesn't match what the client expects — the same class of untranslatable literal this task retired from every refusal path, deferred as outside this design's objective (a refusal's `Problem.message`, not a malformed success body). See [design F8](../design.md). |
