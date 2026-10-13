# Plan Log: Discard Chosen Proposals — `ledger-service`

## Review Findings

- **RF01:** Several RI01 and RI02 cases expected only "nothing", so they passed against ST05's stub and were never
  seen red.
  - Resolution: mechanical
  - Action: applied — the `RECORDED` and other-person ids joined RI01's and RI02's first case beside rows that are
    removed, the repeat became one case whose first call must answer, and the stub-passing cases were dropped.

- **RF02:** RU01's "`discarded` plus `missing` is 3" case ran the path the first case runs and asserted less.
  - Resolution: mechanical
  - Action: applied — dropped.

- **RF03:** GI01 and GI02 both implement `ExpenseRepositoryAdapter.discardByIds` with nothing ordering them.
  - Resolution: mechanical
  - Action: applied — GI02 runs after GI01.

- **RF04:** RI03 tested AC07's missing cookie but not a cookie this service did not sign.
  - Resolution: mechanical
  - Action: applied — added the unsigned cookie, built through `BrowserSessions`, to RI03's 401 case.

- **RF05:** RI03 assumed `1.5` answers 400, unverified against the generated `List<@Min(1L) Long>` binding.
  - Resolution: mechanical
  - Action: applied — `1.5` is now observed first and asserted as it binds, not listed among the refusals.

- **RF06:** AC09's 404 was tested in RU01, RI03 and RS01, while `WebExceptionHandlerTest` already owns the shared
  `EntityNotFoundException` mapping.
  - Resolution: decision
  - Action: resolved — dropped from RI03; a shared component's mapping belongs to its own test
    (`ledger-service/docs/conventions/testing.md`, Testing Style), and the acceptance's endpoint carries no 404
    case in `ExpensesControllerTest` either.

- **RF07:** The Red Phase coverage note carried reasoning beside the two holders.
  - Resolution: mechanical
  - Action: applied — cut to the two test cases that hold AC12 and AC13.

## Run Log

- **RL01 (RI03):** expected passes against the stub, each refused before the controller runs, so the port is
  never reached: `ExpensesControllerTest$Validation.whenDiscardIdsBreakTheirOwnBound_thenResponseIs400NamingIdsAndPortNeverCalled`
  for absent, `null`, empty, 101 ids, `0` and `-1` (the generated contract's bean validation);
  `whenTheDiscardBodyCannotBeRead_thenResponseIs400AndPortNeverCalled` for `"abc"` and a non-JSON body (Jackson);
  `ExpensesControllerTest$ErrorMapping.whenDiscardIsPostedWithoutSessionCookie_thenUnauthorizedAndPortNeverCalled`,
  `whenDiscardIsPostedWithUnsignedSessionCookie_thenUnauthorizedAndPortNeverCalled` and
  `whenDiscardIsPostedWithoutCsrfToken_thenForbiddenAndPortNeverCalled` (the web-session chain ST07 configured).
- **RL02 (RI03):** hypothesis: `{"ids":[1.5]}` binds as `1` and reaches the port with 200, as
  `whenTheDiscardCarriesAFractionalId_thenItBindsTruncatedAndReachesThePort` asserts — settled by that test's
  run once the controller is implemented.
- **RL03 (GI02):** `ExpenseRepositoryAdapterEventsTest` passed on GI01's implementation of `discardByIds`, in the
  same verification run; no agent was spawned for it. That run also passed the fractional-id test RL02 names.
- **RL04 (RI03):** the full suite after green failed `DisplayNameConventionsTest.aTestMethodsDisplayNameStaysUnderTheLimit`
  on two 126-character `@DisplayName`s RI03 wrote in `ExpensesControllerTest`; re-delegated to `red-integration`
  to shorten them.
  - Resolved: both names shortened under the limit; the full suite is green at 1365.
- **RL05 (GU01):** the refactor pass left `DiscardExpensesUseCase`'s near-copy of `AcceptExpensesUseCase`, and
  the row-to-message mapping `acceptByIds` and `discardByIds` share, unmerged: either change reaches code this plan
  did not touch.

## Suite Runs

- baseline · tree c468bb6ce1f6d8b3b0539a0e2f0eb31f6ad048b0 · total 1335 · skipped 0 · green · paths: ledger-service
- stabilization · tree 2993e8a7c368ff06e1fc9085fbaa64a86e377ce7 · total 1335 · skipped 0 · green · paths: ledger-service

- green · tree 64c024eeb44402c748592d998ce5bbf9526021e9 · total 1365 · skipped 0 · green · paths: ledger-service
- refactor · tree f196c6e8bcddb02f5fe0bb393ce310f086af1acc · total 1365 · skipped 0 · green · paths: ledger-service
## Stubs

Marker: `stub-intent:`

- `ledger-service/src/main/java/bot/finance/application/usecase/DiscardExpensesUseCase.java`
- `ledger-service/src/main/java/bot/finance/adapter/persistence/ExpenseRepositoryAdapter.java`
- `ledger-service/src/main/java/bot/finance/adapter/web/ExpensesController.java`
