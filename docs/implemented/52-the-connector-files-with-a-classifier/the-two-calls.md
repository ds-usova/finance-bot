# The Two Calls

## What the Model Reads Out

One chat-completions call with a JSON-schema response format and no tools. The schema is the connector's own, and
so are the instructions: none of the tool-calling turn's instructions reach it.

| Field                    | Holds                                                                         |
|--------------------------|-------------------------------------------------------------------------------|
| `expenses[]`             | one per expense the message says was paid, in the user's order; may be empty |
| `expenses[].description` | what was bought, in the user's words                                          |
| `expenses[].merchant`    | who it was bought from; null when the message names no one                    |
| `expenses[].amount`      | the amount as written, in the currency's main unit, as a decimal string       |
| `expenses[].currency`    | ISO 4217 when the message names or implies one; null otherwise                |
| `periods[]`              | one per period the message asks about; may be empty                           |
| `periods[].from`, `.to`  | `YYYY-MM-DD`                                                                  |

The instructions state the request's day and the assumed currency, and keep today's period rules: a period is in
UTC, anchored on the request's day, a week starts on Monday, and a single day has `from` equal to `to`. They name
no grouping, no category and no tool.

## What the Classifier Is Asked

One call per turn to `POST <TYPESAFE_BASE_URL>/v1/systemone`, only when at least one usable expense was read.

| Part                        | Holds                                                                                          |
|-----------------------------|------------------------------------------------------------------------------------------------|
| `model`                     | `CLASSIFIER_MODEL`                                                                             |
| `state`                     | the message text, and the recalled examples as the next table admits them (DN03)              |
| `questions`                 | one `choice` question per usable expense, keyed by its position in the read-out               |
| a question's `instructions` | which of the person's categories the expense belongs to, naming its description and merchant |
| a question's `criteria`     | one option per category in the catalogue                                                       |
| an option's key             | `<grouping> / <category>` — a category name repeats across groupings, so the pair is the key  |
| an option's value           | the grouping it is filed under                                                                 |

| Of each recalled example                   | Reaches the classifier |
|--------------------------------------------|------------------------|
| the earlier message's text                 | never                  |
| a decided expense's description, merchant  | yes                    |
| a decided expense's grouping, category     | yes                    |
| accepted or discarded                      | yes                    |
| a decided expense's amount, currency       | never                  |

An example carries the merchant the connector learned, absent where none was. The tool-calling prompt's examples
do not gain it.

| Read back, per question | Used for                                                               |
|-------------------------|------------------------------------------------------------------------|
| `choice`                | the category filed, when confident                                     |
| `confidence`            | compared with `CLASSIFIER_MIN_CONFIDENCE`, and recorded                |
| `probabilities`         | the most probable catch-all category, when not confident (DN01)        |

A question left unanswered, an option the catalogue does not hold, a confidence outside `0..1`, or an option with
no probability is a classifier failure (DN02). A choice question takes at most 255 options; every catalogue holds 77.

## Settings

Each joins the module's configuration table with its default.

| Setting                     | What it sets                                                                                                     | Default                   |
|-----------------------------|------------------------------------------------------------------------------------------------------------------|---------------------------|
| `EXTRACTION_MODE`           | `tools` or `classifier`; any other value stops startup                                                           | `tools`                   |
| `TYPESAFE_API_KEY`          | the classifier's credential; secret; required in `classifier` mode                                               | none                      |
| `TYPESAFE_BASE_URL`         | where the classifier is reached                                                                                  | `https://api.typesafe.ai` |
| `CLASSIFIER_MODEL`          | which model answers; required in `classifier` mode                                                               | none                      |
| `CLASSIFIER_TIMEOUT`        | how long one classifier call may take                                                                            | `3s`                      |
| `CLASSIFIER_MIN_CONFIDENCE` | the confidence at or above which the most probable option is filed; `0..1`, else startup stops                   | `0.5`                     |
| `LEDGER_TURN_TARGET`        | where the ledger's turn operations are reached over gRPC, as `static://host:port`; required in `classifier` mode | none                      |
| `LEDGER_TURN_TIMEOUT`       | how long one call to a ledger turn operation may take                                                            | `5s`                      |

Compose sets `LEDGER_TURN_TARGET` to `static://ledger-service:1006`, task 51's `LEDGER_GRPC_PORT`. In `tools`
mode none of these but `EXTRACTION_MODE` is read. Every timeout sits well inside the ledger's call
deadline.

## Meters

Registered whatever `MEMORY_ENABLED` says.

| Meter                                | Kind                 | Tags              | Records                                                   |
|--------------------------------------|----------------------|-------------------|-----------------------------------------------------------|
| `ai_turns_total`                     | counter              | `mode`, `outcome` | each turn once; `outcome` is `acted_on` or `unavailable`  |
| `ai_classifier_seconds`              | timer                | `outcome`         | each classifier call; `outcome` is `answered` or `failed` |
| `ai_classifier_confidence`           | distribution summary | —                 | each expense's confidence                                 |
| `ai_classifier_low_confidence_total` | counter              | —                 | each expense DN01 placed                                  |
