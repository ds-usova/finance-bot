# Extraction mode

Which turn the service runs on every message it is handed.

## Invariants

- `TOOLS | CLASSIFIER`
- One mode for the life of the process; no transition.

## Made of / held by

A name.

- [Record the spending a user's message names](../usecases/extract-intents.md) — runs the turn the mode names.
- [Health and meters](../contracts/in/operations.md#meters) — every turn is counted under its mode.
- [Configuration](../configuration.md) — `EXTRACTION_MODE` sets it.
