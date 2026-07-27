# ADR 0003: One message yields an ordered list the service never judges

- **Status:** Accepted
- **Date:** 2026-07-27
- **Source:** [Initialize the AI Connector Service](../implemented/2-plan-init-ai-connector-service.md)

## Context

A single sentence can ask for several things — *"create a Travel category and put 50 euros of taxi in it"* is a
category creation and an expense. Returning one intent per call would either lose the second action or force
the caller to split a sentence it cannot parse, which is the work this service exists to do.

Once the answer is a list, three questions follow that the schema cannot settle. What happens when one entry of
three is unusable. What an expense may be filed under when the category it names is created by the very same
message. And whether the service refuses a set of intents that cannot sensibly be carried out together — a
category deleted and an expense filed under it — or hands it over as said.

## Decision

The answer is a list in the order the user expressed the actions, and the service never reorders it. It is
never empty: a message asking for nothing yields one entry marked unknown, carrying the reason.

Entries are assembled independently. An answer that cannot be made sense of becomes an unknown entry in its own
position and its neighbours are unaffected, so unknown is per entry and never a failed call.

Every category the message itself names extends the set an expense may be filed under, for every entry —
whatever its position and whatever action was asked for it. Position is not a parsing question: a user who
names the expense before the category has still named the category. Neither is the action: whether deleting a
category and filing an expense under it can both be done is a feasibility judgement.

The service parses; it does not judge. No entry is compared against another, and the caller decides what to do
with a list that contradicts itself.

The rules a caller reads are in
[the intent extraction contract](../../ai-connector-service/docs/contracts/in/intent-extraction.md#semantics);
what happens to each answer is in
[the use case](../../ai-connector-service/docs/usecases/extract-intents.md).

## Consequences

The caller has one shape to read and one code path: a list, always populated, whose bad entries say why. A
partly understood message stays partly useful instead of failing whole.

The Ledger Service inherits the consistency problem. Executing the list in sequence, it may meet an expense
naming a category created later in the same list, or one the same list deletes, and it — not this service —
decides whether to reorder, refuse, or ask the user. Adding conflict detection here later would turn requests
that succeed today into rejections.

What must stay true: order is the user's, the list is never empty, and an unusable answer is an entry rather
than a status.
