# Spec: Discard Chosen Proposals

**Format:** 2
**Affected Modules:** `ledger-service`

## Objective

A pending entry can be thrown away only by tapping Delete on its Telegram report, and that tap throws away every
entry the message produced. An entry filed as text from the web has no report, so it cannot be discarded at all.
Nothing reachable over HTTP discards one.

The evaluation runner signs in through the browser API and has no Telegram chat. Its memory cases teach the
connector by approving some proposals and discarding others (`10-003`), so it needs a discard it can call. This
change adds `POST /api/v1/expenses/discards`, the counterpart of the existing acceptance: the caller names pending
entries by id, and those are discarded exactly as a Telegram Delete discards them.

The page gets no Discard action here (DN01).

## Requirements

- **RQ01:** A signed-in person can discard chosen pending entries of theirs by id, one or several at once.
- **RQ02:** A discarded entry reaches the connector's memory as discarded, as a Telegram Delete does.
- **RQ03:** An id that names nothing of the caller's pending is counted, never refused, and never touches
  anything.
- **RQ04:** A person discards only their own entries.
- **RQ05:** A Telegram report whose entries are all resolved loses its buttons, whichever surface resolved them.
- **RQ06:** A refused, unauthenticated, forged or failed request discards nothing and publishes nothing.

## Acceptance Scenarios

### `POST /api/v1/expenses/discards`

- **AC01:** the chosen proposals are discarded
  - Given: the person is signed in and has three pending proposals
  - When: they post the ids of two of them
  - Then: the response is 200 with `discarded` 2 and `missing` 0, a later listing shows only the third as
    `PENDING`, and neither of the two as `RECORDED`
  - Proves: RQ01

- **AC02:** the connector learns of the discard
  - Given: the person discarded a pending proposal through this endpoint
  - When: the ledger's committed changes are published
  - Then: one `ProposalDiscarded` fact names that entry, and none names an entry that stayed pending; a fact the
    outbox could not record is dropped as for every other write (DF01)
  - Proves: RQ02

- **AC03:** the same ids are posted twice
  - Given: the person already discarded two proposals through this endpoint
  - When: they post the same two ids again
  - Then: the response is 200 with `discarded` 0 and `missing` 2, and no further fact is published
  - Proves: RQ03

- **AC04:** an id names a recorded expense
  - Given: the person is signed in and the id is a `RECORDED` entry's
  - When: they post it
  - Then: the response is 200 with `missing` 1, and the recorded entry stands
  - Proves: RQ03

- **AC05:** an id belongs to somebody else
  - Given: the id names another person's pending proposal
  - When: the signed-in person posts it
  - Then: the response is 200 with `discarded` 0 and `missing` 1, never 403, and the other person's proposal
    stands
  - Proves: RQ03, RQ04

- **AC06:** the list is unusable
  - Given: the person is signed in
  - When: they post an absent, null or empty list, more than 100 ids, a repeated id, a null id, an id below 1, a
    value that is not an integer, or a body that is not JSON
  - Then: the response is 400 naming what it refused, and nothing is discarded
  - Proves: RQ06

- **AC07:** nobody is signed in
  - Given: the request carries no session cookie, or one this service did not sign
  - When: it posts a list of ids
  - Then: the response is 401, and nothing is discarded
  - Proves: RQ04, RQ06

- **AC08:** the write carries no CSRF token
  - Given: a valid session cookie and no `X-XSRF-TOKEN` header
  - When: the request posts a list of ids
  - Then: the response is 403, and nothing is discarded
  - Proves: RQ06

- **AC09:** the session outlives its user row
  - Given: a valid session whose user row no longer exists
  - When: it posts a list of ids
  - Then: the response is 404, and nothing is discarded
  - Proves: RQ06

- **AC10:** the store is unavailable
  - Given: the person is signed in and the store refuses writes
  - When: they post a list of ids
  - Then: the response is 503 naming no table or statement, nothing is discarded, and no fact is published; a
    failure while the transaction opens or commits answers 500 instead (DF07)
  - Proves: RQ06

### The Telegram report

- **AC11:** a fully discarded report loses its buttons
  - Given: every pending proposal under one message is in the posted list, and that report's location was
    recorded when it was delivered
  - When: the discard succeeds
  - Then: the response is 200 straight away, and the buttons come off that report without its text changing
  - Proves: RQ05

- **AC12:** a partly discarded report keeps its buttons
  - Given: one of the two pending proposals under a message is in the posted list
  - When: the discard succeeds
  - Then: nothing is sent for that message, and its report's buttons still resolve the one left, as the existing
    clearing decides (DF09)
  - Proves: RQ05

- **AC13:** the report's buttons are tapped after a web discard
  - Given: every proposal under a message was discarded through this endpoint
  - When: the person taps Confirm or Delete on that report
  - Then: they get the existing nothing-to-resolve answer, and nothing is stored, as the existing resolution
    decides (DF09)
  - Proves: RQ05

## Decisions

- **DN01:** Does the expenses page get a Discard action in this task?
  - Answer: No. This task ships the endpoint only; the page action is a later task (DF14).
  - Basis: decided (user, 2026-10-12)

- **DN02:** What does a finished web-filed message report as its `days` once every entry it produced is
  discarded?
  - Answer: An empty list, as it is read today. The specification's `days` description says it can become empty
    once entries are discarded.
  - Basis: decided (user, 2026-10-12)

- **DN03:** Does the discard take its own request schema, or reuse `AcceptanceRequest`?
  - Answer: Its own `DiscardRequest`, with the same bounds and a description that says discard.
  - Basis: decided (user, 2026-10-12)

## Design Artifacts

- [REST API update](rest-api-update.md) — the new operation, its fields and its statuses.
- [Discarding the chosen proposals](discard-flow.md) — the branches from request to fact and report clearing.
