# Bug: a stored currency code the service cannot read is answered as a store outage

**Affected Modules:** `ledger-service`
**Source:** [Review: Set a default currency](../40-set-a-default-currency/review/findings.md) — the Bug
section; `docs/backlog.md` row `B1`
**Baseline:** `6c69c071` — `ledger-service`: 1194 tests, 1 skipped, that skip being the disabled reproduction
`UserPreferenceRepositoryAdapterTest#whenRowHoldsUnrecognisedCode_thenNothingAnswered`. Docker was up, so every
container-backed class ran.
**Attempts:** bug.md · —, fix.md · —

## What happens

- **Given** a `user_preference` row whose `default_currency_code` is not a code `Currency.getInstance` accepts —
  reachable by a write that bypasses the endpoint, since the column is `VARCHAR(3)` with no check constraint, or
  by a code a later JDK stops recognising
- **When** that person reads their preferences, or sends a message
- **Then** the stored code is not usable, so the person has no currency to assume — the read answers nothing
  chosen, and the turn runs with no currency
- **Actual** the read answers `503`, and the store was available and answered

## How it reproduces

`UserPreferenceRepositoryAdapterTest` stores `ZZZ` directly through `UserPreferenceRowUtils`, the way a write
bypassing the endpoint would, and asks the adapter for the code:

```
tools/agent-test/agent-test.sh --module ledger-service \
  --tests "bot.finance.adapter.persistence.UserPreferenceRepositoryAdapterTest"
```

```
- UserPreferenceRepositoryAdapterTest$FindDefaultCurrency > when the row holds a code the JDK does not recognise - then nothing is answered
    type: bot.finance.domain.exception.PersistenceFailedException
    message: bot.finance.domain.exception.PersistenceFailedException: failed to find default currency for user 3
    at bot.finance.adapter.persistence.UserPreferenceRepositoryAdapter.findDefaultCurrency(UserPreferenceRepositoryAdapter.java:23)
    Caused by: bot.finance.domain.exception.InvalidMoneyException: Unrecognized ISO 4217 currency code: ZZZ
    at bot.finance.domain.value.CurrencyCode.<init>(CurrencyCode.java:17)
    at bot.finance.adapter.persistence.UserPreferenceEntity.toDefaultCurrency(UserPreferenceEntity.java:11)
    at bot.finance.adapter.persistence.UserPreferenceRepositoryAdapter.findDefaultCurrency(UserPreferenceRepositoryAdapter.java:21)
```

It failed on both runs. The test is `@Disabled("R01: …")` in the tree until `R01` enables it, so the baseline is
green.

## Why it happens

1. `UserPreferenceRepositoryAdapter.findDefaultCurrency` holds the query **and** the mapping inside one `try`:
   `findById(userId).map(UserPreferenceEntity::toDefaultCurrency)` is a single expression on
   `UserPreferenceRepositoryAdapter.java:21`.
2. `UserPreferenceEntity.toDefaultCurrency` constructs `new CurrencyCode(defaultCurrencyCode)`, and that record's
   compact constructor throws `InvalidMoneyException` for a code `Currency.getInstance` refuses
   (`CurrencyCode.java:17`).
3. `InvalidMoneyException` is a `RuntimeException`, so the adapter's `catch (RuntimeException e)` catches a
   mapping failure it was written to guard a query against, and rethrows it as `PersistenceFailedException`
   (`UserPreferenceRepositoryAdapter.java:23`).
4. `WebExceptionHandler.onPersistenceFailed` answers `503` with "the service is temporarily unable to complete
   the request" (`WebExceptionHandler.java:137`).

Every link is the reproduction's own cause chain, quoted above.

**The promise broken** is [Read a person's preferences](../../../ledger-service/docs/usecases/read-the-preferences.md)'s
**Storage failed** outcome — *the store cannot be reached*. The store was reached and answered a row; the value in
it was the problem, and the caller is told to retry something retrying cannot fix.

**The fix answers nothing chosen, and warns.** A code this service cannot read gives the person no currency to
assume, which is exactly the state the **Nothing chosen** outcome already describes, and the one
[Act on a user's message](../../../ledger-service/docs/usecases/handle-incoming-message.md)'s **Currency unread**
outcome already requires of a turn. The dropped value is logged as a warning, so a stored code nothing recognises
leaves a trace rather than disappearing.

**Rejected: letting `InvalidMoneyException` out of the adapter**, which the exception table maps to `400`. The
caller of a read sent no value, so a refusal names nothing they can correct, and the same exception would then
escape `HandleIncomingMessageUseCase.readDefaultCurrency` — which catches only `PersistenceFailedException` — and
end a turn that its **Currency unread** outcome says must run.

## What the fix must not break

- A stored code the JDK recognises is still answered, upper-cased — `UserPreferenceRepositoryAdapterTest`'s
  `whenPreferenceRowHoldsEur_thenEurAnswered` and `whenRowHoldsLowercaseEur_thenCurrencyCodeOfEurAnswered`.
- A person with no preference row is still answered nothing — `whenUserHasNoPreferenceRow_thenNothingAnswered`.
- A genuine store failure still reaches the caller as `PersistenceFailedException`: the `try` still guards the
  query itself.
- `replaceDefaultCurrency` still refuses an unrecognised code at the endpoint, where `PreferencesWebMapper` builds
  the `CurrencyCode` from the request body and the caller gets `400`.
- A turn whose preference read fails still runs with no currency and is delivered —
  `HandleIncomingMessageUseCaseTest`'s
  `whenSendersPreferenceReadThrowsPersistenceFailedException_thenRequestCarriesNoCurrencyAndTurnDelivered`.

## Attempts
