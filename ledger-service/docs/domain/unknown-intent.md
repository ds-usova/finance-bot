# Unknown intent

An action the service could not make sense of, carrying why.

## Invariants

- A reason is present and not blank.

## Made of / held by

The reason the action could not be made sense of.

- [Intent](intent.md) — one of the three kinds.
- [AI Connector Service — intent extraction](../contracts/out/ai-connector.md) — what turns an answer's entry
  into one of these, and what the reason then says.
