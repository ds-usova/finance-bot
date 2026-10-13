# Spec: The Connector Files a Message's Spending with a Classifier

**Format:** 2
**Affected Modules:** `ai-connector-service`

## Objective

The language model files spending under the wrong category too often. This task is the second of two. Task 51
gives the connector the person's whole catalogue on each turn, and two operations to file an expense and record a
period itself.

Here the connector gains a second way to run a turn, chosen per deployment by `EXTRACTION_MODE`. The model only
reads the message: what was bought, from whom, for how much, and what period was asked about. A classifier — Jev,
TypeSafe's System One model — picks each expense's category from the catalogue, with a probability per category.
The connector then files the result through task 51's operations. The model is called once, with no tools, and
chooses no category.

Today's tool-calling turn stays the default and is unchanged. The ledger does not know which mode ran: the
request, the answer and every failure signal stay as they are, so the AI stays behind the connector.

## Requirements

- **RQ01:** With `EXTRACTION_MODE` at `classifier`, the model reads the expenses and the periods out of the
  message and chooses no category.
- **RQ02:** The classifier picks each expense's category from the person's whole catalogue.
- **RQ03:** An expense the classifier is not confident about, or cannot answer for, is still filed, under the
  catch-all.
- **RQ04:** Each expense read is filed, and each period recorded, through the ledger's turn operations, in the
  user's order. One the ledger refuses is left unrecorded, and the rest are still filed.
- **RQ05:** With `EXTRACTION_MODE` unset or at `tools`, the turn runs exactly as before.
- **RQ06:** The ledger sees the same contract in both modes: an empty answer when the turn was acted on, and the
  same failure statuses.
- **RQ07:** An operator can tell which mode ran each turn, how long the classifier took, and how often it was not
  confident.
- **RQ08:** The classifier receives only what DN03 admits of the message and the person's history.
- **RQ09:** A deployment whose mode settings cannot run does not start.

## Acceptance Scenarios

One entry point, the extraction call, run with `EXTRACTION_MODE` at `classifier` unless a scenario says
otherwise. Every branch is drawn in [The classifier turn](classifier-turn.md).

- **AC01:** one expense is filed under the classifier's choice
  - Given: a catalogue holding `Dining` with `Cafés` and `Restaurants`, `Transportation` with `Taxis/Uber`, and
    the message "coffee 4.50 EUR"
  - When: the classifier answers `Dining / Cafés` with confidence at or above `CLASSIFIER_MIN_CONFIDENCE`
  - Then: the provider call carries no tools and names no category. The classifier's question offers all three
    pairs. One expense is filed: `Dining`, `Cafés`, `coffee`, `4.50`, `EUR`. The call answers acted on.
  - Proves: RQ01, RQ02, RQ04

- **AC02:** several expenses keep the user's order
  - Given: the message "taxi 12 and lunch 15, both EUR"
  - When: the turn runs
  - Then: the classifier is called once, with one question per expense. The taxi is filed before the lunch.
  - Proves: RQ02, RQ04

- **AC14:** a read-out expense cannot be filed
  - Given: the model reads out an expense with a blank description, an amount that is not a positive decimal, or a
    currency that is not ISO 4217, beside one usable expense
  - When: the turn runs
  - Then: the unusable one is left unrecorded and logged, and never reaches the classifier or the ledger. The usable
    one is filed.
  - Proves: RQ04

- **AC03:** the classifier is not confident
  - Given: the message "4000 HUF for the thing"
  - When: the classifier's confidence is below `CLASSIFIER_MIN_CONFIDENCE`
  - Then: the expense is filed under the catch-all grouping, in its most probable category there —
    `Miscellaneous`, `Uncategorized Expenses` in every catalogue today. The low confidence is counted.
  - Proves: RQ03, RQ07

- **AC04:** the message asks what was spent
  - Given: the message "how much did I spend yesterday?" on a turn dated `2026-10-13`
  - When: the turn runs
  - Then: the period `2026-10-12` to `2026-10-12` is recorded. The classifier is not called. The call answers acted
    on.
  - Proves: RQ01, RQ04

- **AC05:** the message names nothing
  - Given: the message "hello"
  - When: the turn runs
  - Then: neither the classifier nor the ledger is called, and the call answers acted on
  - Proves: RQ06

- **AC06:** an amount with no currency
  - Given: the message "lunch 15", and no currency in it
  - When: the request carries an assumed currency, or carries none
  - Then: with one, the expense is filed in it. With none, the expense is left unrecorded and the turn goes on.
  - Proves: RQ04

- **AC07:** the ledger refuses one expense
  - Given: two expenses read, and the ledger refuses the first as an invalid argument
  - When: the turn files them
  - Then: the first is left unrecorded and logged, the second is filed, and the call answers acted on
  - Proves: RQ04, RQ06

- **AC15:** the ledger refuses a period
  - Given: a period read, and the ledger refuses it as an invalid argument
  - When: the turn records it
  - Then: the period is left unrecorded and logged, and the call answers acted on
  - Proves: RQ04

- **AC08:** the ledger fails the turn
  - Given: two expenses read
  - When: the ledger answers the first with any other failure status, or not inside `LEDGER_TURN_TIMEOUT` or what
    remains of the ledger's own call deadline
  - Then: neither the second expense nor any period is tried, and the call fails as unavailable. Anything filed
    before stays filed.
  - Proves: RQ06

- **AC09:** the provider fails or answers unreadably
  - Given: any message
  - When: the provider errors, times out, or answers something that does not fit the read-out shape
  - Then: nothing is filed, and the call fails as unavailable
  - Proves: RQ06

- **AC10:** the classifier fails
  - Given: at least one expense read
  - When: the classifier errors, refuses, does not answer inside `CLASSIFIER_TIMEOUT`, leaves a question
    unanswered, names an option the catalogue does not hold, answers a confidence outside `0..1`, or gives no
    probability for an option
  - Then: every usable expense is filed under the catch-all grouping, in its first category as the catalogue
    lists it, and the call answers acted on. The failure is counted. One WARN carries no text, and names the person
    and the message where the turn knows them.
  - Proves: RQ03, RQ06, RQ07

- **AC11:** what of the person reaches the classifier
  - Given: the memory is on, and the recall answers worked examples for this message
  - When: the classifier is asked
  - Then: the classifier's state carries the message text and, per example, each decided expense's description,
    merchant, grouping, category and whether it was accepted or discarded. No example's amount, currency or
    earlier message text reaches it. The model is given no examples, and the tool-calling prompt's examples are
    unchanged.
  - Proves: RQ02, RQ08

- **AC16:** the setting is refused at startup
  - Given: `EXTRACTION_MODE` is neither `tools` nor `classifier`; or it is `classifier` with no `TYPESAFE_API_KEY`,
    no `CLASSIFIER_MODEL`, no `LEDGER_TURN_TARGET`, or a `CLASSIFIER_MIN_CONFIDENCE` outside `0..1`
  - When: the service starts
  - Then: it refuses to start, naming the setting
  - Proves: RQ09

- **AC17:** the catalogue cannot place a fallback
  - Given: either mode
  - When: the request's catalogue has no entry for the catch-all grouping, or an entry with no category
  - Then: the call is refused as an invalid argument before the provider is called
  - Proves: RQ06

- **AC12:** the tool-calling mode is untouched
  - Given: `EXTRACTION_MODE` unset or at `tools`
  - When: any message is handed over
  - Then: the turn runs exactly as before, and the classifier is never called
  - Proves: RQ05

- **AC13:** each turn's mode is observable
  - Given: either mode
  - When: a turn ends
  - Then: the turn is counted under its mode and outcome. A classifier call's duration and confidence are recorded.
  - Proves: RQ07

## Decisions

- **DN01:** Where is an expense filed when the classifier is not confident?
  - Answer: under the catch-all grouping, in whichever of its categories the classifier found most probable.
  - Basis: decided (user, 2026-10-13)

- **DN02:** What does the turn do when the classifier fails?
  - Answer: files every usable expense under the catch-all grouping, in its first category as the catalogue lists
    it, and answers acted on.
  - Basis: decided (user, 2026-10-13)

- **DN03:** What of the message and the person's history may reach the classifier?
  - Answer: the message text, and the recalled examples' descriptions, merchants, groupings, categories and
    decisions. Never an amount or a currency.
  - Basis: decided (user, 2026-10-13)

- **DN04:** Do the evaluation runner and its cases change for a classifier turn?
  - Answer: not here. A later task does it, filed as BT09.
  - Basis: decided (user, 2026-10-13)

- **DN05:** Does the turn become asynchronous, so the ledger stops holding the call open for the whole turn?
  - Answer: not here. A later task across both services does it, filed as BT10.
  - Basis: decided (user, 2026-10-13)

- **DN06:** How does the classifier mode stand to ADR 0008, which made the model the ledger's only caller?
  - Answer: a new record sits beside it. ADR 0008 stays in force for the tool-calling mode.
  - Basis: decided (user, 2026-10-13)

## Design Artifacts

- [The classifier turn](classifier-turn.md) — the exchange, and how a category is chosen.
- [The two calls](the-two-calls.md) — what the model reads out, what the classifier is asked, and the settings.
