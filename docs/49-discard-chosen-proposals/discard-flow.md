# Discarding the Chosen Proposals

Each refusal ends the request with one answer and changes nothing.

| Checked, in order                                  | Refused with |
|----------------------------------------------------|--------------|
| a session cookie this service signed               | 401          |
| a CSRF token                                       | 403          |
| a list within the bounds, in a body that parses    | 400          |
| a user stored under the session's identity         | 404          |
| the store accepts the write                        | 503          |

Once the write runs, the order matters: the response never waits for Telegram.

```plantuml
@startuml
participant "caller" as Caller
participant "discard chosen proposals" as Discarding
database "the expense store" as Store
queue "the change stream" as Stream
participant "clear the emptied reports" as Clearing

Caller -> Discarding : the ids, and the caller
Discarding -> Store : remove the caller's PENDING rows carrying those ids, and record a ProposalDiscarded fact per row, together
Store --> Discarding : the message each removed row was reported on
opt anything was removed
  Store -> Stream : the facts, once committed
  Discarding -> Clearing : those messages, handed over without waiting
end
Discarding --> Caller : 200, discarded and missing

note over Clearing
  A message with nothing left pending loses its report's buttons;
  one with entries still pending keeps them (AC11, AC12).
  A clearing that is dropped or refused changes nothing for the caller.
end note
@enduml
```
