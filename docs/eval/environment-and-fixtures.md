# Environment and Fixtures

## Environment Lifecycle

| Concern    | Rule                                              |
|------------|---------------------------------------------------|
| Scope      | one ephemeral environment per evaluation run      |
| Creation   | newly created and isolated before the run         |
| Disposal   | discarded after success, failure, or cancellation |
| Reuse      | no application state survives into a later run    |
| Production | production services and data are never used       |
| Traffic    | only traffic caused by the evaluation is allowed  |

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

## Date-Sensitive Cases

1. Read the current UTC date before filing the message.
2. Calculate the expected dates from that value.
3. If the UTC date changes before the case finishes, discard the attempt and repeat the case against the new date.


