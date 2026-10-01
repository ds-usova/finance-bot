# Review: Malformed Response Errors Carry a Catalogue Key

**1 refactoring candidate, done · directly.**

## Refactoring candidate

| #  | Status          | Module    | What                                                                                  | Why                                                                                                                                                                                                                    |
|----|-----------------|-----------|----------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| R1 | done · directly | `web-app` | give `SettingsPage.report` and `ExpensesPage.report`/`onChangeCategory` a shared home | Extracted as `pages/errorText.ts`'s `refusalText`, called by all three sites; `web-app/docs/conventions/architecture.md` gained one line naming `pages/` as the home for a helper shared by more than one page, the same way `components/` already hosts one shared by more than one component. |
