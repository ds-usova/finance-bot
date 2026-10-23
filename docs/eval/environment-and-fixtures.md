# Environment and Fixtures

## Environment Lifecycle

| Concern    | Rule                                                                                                                   |
|------------|------------------------------------------------------------------------------------------------------------------------|
| Scope      | one ephemeral environment per evaluation run                                                                           |
| Creation   | newly created and isolated before the run                                                                              |
| Disposal   | evaluation-owned resources and state discarded after success, failure, or cancellation; shared infrastructure retained |
| Reuse      | no application state survives into a later run                                                                         |
| Production | production services and data are never used                                                                            |
| Traffic    | only traffic caused by the evaluation is allowed                                                                       |

### Infrastructure Reuse

| Component                         | Rule                                                                                                                   |
|-----------------------------------|------------------------------------------------------------------------------------------------------------------------|
| Ledger and AI Connector databases | fresh evaluation databases; never connect the benchmark to production data                                             |
| Redis state                       | isolate evaluation sessions, queues, and application keys from ordinary application state                              |
| Application services              | use the normal application implementation with evaluation configuration, not evaluation-specific replacements          |
| Observability                     | reuse the application's stack and instrumentation; no separate evaluation-only stack                                   |
| Shared telemetry                  | other application traffic may coexist in telemetry storage; identify evaluation records by environment and run context |
| Disposal boundary                 | remove only evaluation-owned resources and state; do not stop or clear shared observability infrastructure             |

## Fixture v1

| Part                       | Value                                                              |
|----------------------------|--------------------------------------------------------------------|
| User                       | a newly initialized evaluation user                                |
| Catalogue                  | groupings and categories created by normal new-user initialization |
| Default currency           | `HUF`                                                              |
| Clock                      | the application's current UTC date; no override                    |
| Chat model                 | declared by the run                                                |
| Model parameters           | declared by the run                                                |
| Memory                     | enabled, with no learned history at case start                     |
| Existing messages          | none                                                               |
| Existing expenses          | none                                                               |
| Existing embeddings        | none                                                               |
| Existing outcomes          | no approved or discarded proposals                                 |
| Existing recalled examples | none                                                               |

## User Setup

| Step | Action                                                                                     |
|------|--------------------------------------------------------------------------------------------|
| 1    | configure a test bot token in the isolated environment and disable Telegram polling        |
| 2    | obtain the CSRF cookie through `GET /api/v1/session`; an unauthenticated `401` is expected |
| 3    | submit a synthetic login payload signed with that test token to `POST /api/v1/session`     |
| 4    | retain the session/CSRF cookies; normal initialization creates the default catalogue       |
| 5    | set default currency to HUF through `PUT /api/v1/preferences`                              |

No real Telegram account or manual browser login is required. Real-provider credentials remain configured for
chat and embeddings; session credentials and the test bot token never enter run artifacts.

## Catalogue

| Rule        | Requirement                                                     |
|-------------|-----------------------------------------------------------------|
| Source      | use the application's default grouping and category names       |
| Duplication | keep no separate catalogue copy in the evaluation specification |

## Case Isolation

| Case type  | Starting state                    | State allowed inside the case                                                        |
|------------|-----------------------------------|--------------------------------------------------------------------------------------|
| extraction | no learned classification history | the message and proposals created by that case                                       |
| memory     | no learned classification history | messages, proposal decisions, embeddings, and recalled examples created by that case |

State created by one case must not become recallable by another case. Case results must be independent of
execution order.

## Fixture Restoration

| Rule            | Requirement                                                                                              |
|-----------------|----------------------------------------------------------------------------------------------------------|
| Timing          | after every case repetition                                                                              |
| Scope           | remove messages, expenses, decisions, embeddings, recalled examples, and other state created by the case |
| Preserved state | default catalogue and `HUF` preference                                                                   |
| Result          | application state matches Fixture v1 before another case starts                                          |
| Verification    | both the application preconditions and internal memory state are checked                                 |

### Cleanup Method

| Concern      | Rule                                                                                                               |
|--------------|--------------------------------------------------------------------------------------------------------------------|
| Access       | runner uses PostgreSQL directly for cleanup in both Ledger and AI Connector databases                              |
| Permissions  | cleanup has delete permissions; the memory-readiness probe remains read-only                                       |
| Targets      | track case-owned user/message/expense IDs, including learning messages and spending queries                        |
| Ordering     | stop or finish case work and drain its change-stream processing before deleting memory state                       |
| Late writes  | if workers or queued events can recreate deleted state, restoration has not passed                                 |
| Verification | check both databases and the REST pending-expense precondition after cleanup                                       |
| Failure      | rebuild the isolated environment before another case starts, as defined in [Execution policy](execution-policy.md) |

Cleanup does not need a REST endpoint. SQL cleanup is teardown only; approve/discard actions still use the
application so their decisions reach memory through the normal change stream.

## Date-Sensitive Cases

1. Read the current UTC date before filing the message.
2. Calculate the expected dates from that value.
3. If the UTC date changes before the case finishes, discard the attempt and repeat the case against the new date.


