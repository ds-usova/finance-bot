# Category classifier — picking each expense's category (HTTPS)

In the classifier mode, the service asks TypeSafe's System One classifier which of the person's categories each
expense read out of a message belongs to.

- **Counterpart:** TypeSafe's System One API — the address, the credential and the model are
  [configuration](../../configuration.md)
- **Transport:** HTTPS, JSON over HTTP/1.1
- **Schema:** none held here — [TypeSafe's API documentation](https://docs.typesafe.ai) owns the request and
  answer format

## Operations

| Endpoint             | Used by                                                                         |
|----------------------|---------------------------------------------------------------------------------|
| `POST /v1/systemone` | [Record the spending a user's message names](../../usecases/extract-intents.md) |

## What is sent

- One call per turn, for all of its expenses.
- Only when at least one usable expense was read.
- The credential as a bearer token.

| Part                        | Holds                                                             |
|-----------------------------|-------------------------------------------------------------------|
| `model`                     | `CLASSIFIER_MODEL`                                                |
| `state`                     | the message's text, and the recalled examples' decided expenses   |
| `questions`                 | one `choice` question per expense, keyed by its position from `0` |
| a question's `instructions` | the expense's description, and its merchant when one was read     |
| a question's `criteria`     | one option per category in the catalogue                          |
| an option's key             | `<grouping> / <category>`                                         |
| an option's value           | the grouping                                                      |

## What of a person's history is sent

| Of each recalled example               | Sent                  |
|----------------------------------------|-----------------------|
| the earlier message's text             | never                 |
| a decided expense's description        | yes                   |
| a decided expense's merchant           | yes, where it has one |
| a decided expense's grouping, category | yes, where it has one |
| accepted or discarded                  | yes                   |
| a decided expense's amount, currency   | never                 |

With nothing recalled, the state carries the message's text and no example.

## What is read back

| Per question    | Used for                                                |
|-----------------|---------------------------------------------------------|
| `choice`        | the category filed when confident                       |
| `confidence`    | compared with `CLASSIFIER_MIN_CONFIDENCE`, and recorded |
| `probabilities` | the most probable catch-all category when not confident |

- An option is mapped back to its [category choice](../../domain/category-choice.md) through the catalogue the
  request was built from, never by splitting its key.
- An absent or non-numeric `confidence` or probability reads as `0`.
- What is filed for each answer is [the use case's Outcomes](../../usecases/extract-intents.md#outcomes).

## Failures

| Condition                                                                     | What this side does                         |
|-------------------------------------------------------------------------------|---------------------------------------------|
| No connection, or no answer, inside `CLASSIFIER_TIMEOUT`                      | the call fails                              |
| Any status but a success                                                      | the call fails; one `WARN` names the status |
| An empty body, or one that is not JSON                                        | the call fails                              |
| A question left unanswered, answered twice, or keyed outside those asked      | the call fails                              |
| A chosen option, or one given a probability, that the catalogue does not hold | the call fails                              |
| A confidence outside `0..1`, or a catalogue option given no probability       | the call fails                              |

- A failed call is never retried.
- It never fails the turn. What is filed instead is
  [the use case's Outcomes](../../usecases/extract-intents.md#outcomes).

## Compatibility

- A catalogue holding more options than a choice question takes fails every call.
- Changing `CLASSIFIER_MODEL` changes what is filed without changing any schema.
- Anything serving `/v1/systemone` in this format is reached by pointing `TYPESAFE_BASE_URL` at it.
