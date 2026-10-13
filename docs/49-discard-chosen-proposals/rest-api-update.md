# REST API Update

One operation joins the shared browser API under the `expenses` tag. It requires a session and a CSRF token, as
every write on this API does. The specification lives under repo-root `openapi/`; the ledger implements the
operation, and nothing in the web app calls it (DN01).

| Operation                        | Request                  | Response             |
|----------------------------------|--------------------------|----------------------|
| `POST /api/v1/expenses/discards` | a `DiscardRequest` (DN03) | 200 with a `Discard` |

| `DiscardRequest` field | Bound                                                                          |
|------------------------|--------------------------------------------------------------------------------|
| `ids`         | 1 to 100 ids, unique, each at least 1; the same bounds the acceptance enforces |

| `Discard` field | Meaning                                                                                  |
|-----------------|------------------------------------------------------------------------------------------|
| `discarded`     | how many pending proposals were thrown away                                              |
| `missing`       | how many ids named no pending proposal of the caller's — already resolved, or not theirs |

`discarded` plus `missing` always equals the number of ids the request carried.

`WrittenMessage.days` gains one clause: it becomes empty once every entry the message produced is discarded
(DN02).

## Statuses

| Status | When                                                                                                          |
|--------|---------------------------------------------------------------------------------------------------------------|
| 200    | the write ran, whatever it discarded — a discard that matches nothing is not a failure                        |
| 400    | `ids` absent, null or empty, over 100 ids, a repeated, null or sub-1 id, a non-integer, or a body that is not JSON |
| 401    | no session, refused before the operation is reached                                                           |
| 403    | no CSRF token                                                                                                 |
| 404    | the session outlives its user row                                                                             |
| 503    | the store refused the write; a failure opening or committing the transaction answers 500 (DF07)               |

## Stored Effect

| Before                          | After              | Fact published      |
|---------------------------------|--------------------|---------------------|
| a `PENDING` row of the caller's | the row is removed | `ProposalDiscarded` |
| a `RECORDED` row                | unchanged          | none                |
| another person's row            | unchanged          | none                |

The removal and its facts are written in one transaction, as the Telegram Delete's are. A fact the outbox cannot
record is dropped and the removal still commits (DF01).

## Documents Corrected

| Document                                          | Correction                                                        |
|---------------------------------------------------|-------------------------------------------------------------------|
| `ledger-service/docs/usecases/`, a new page       | discarding the proposals a person chose, beside the acceptance's  |
| `ledger-service/docs/usecases/clear-emptied-reports.md` | a discard hands its emptied messages over too (DF11)        |
| `ledger-service/docs/contracts/out/change-stream.md`    | `ProposalDiscarded` is also emitted by the web discard (DF11) |
| `ledger-service/docs/contracts/in/web-browse-api.md`    | the boundary carries a second write                         |
