# Reading a Written Message

Every box is `ledger-service` except the browser and the AI connector. Nothing in the AI connector changes: it
receives a turn exactly as it does from Telegram.

## Filing and asking

```plantuml
@startuml
actor "Person" as User
participant "the page" as Page
participant "the ledger's browser API" as Api
database "the ledger's store" as Store
participant "the reader" as Reader

User -> Page : writes a text and files it
Page -> Api : POST /api/v1/messages
alt refused (AC02 to AC07)
  Api --> Page : 400, 401, 403, 404, 429 or 503
else stored
  Api -> Store : one written message, WAITING
  Api -> Reader : look for work now
  Api --> Page : 202, id and READING
  loop every 5 seconds while READING (DN01)
    Page -> Api : GET /api/v1/messages/{id}
    Api -> Store : that message, scoped to the caller
    Api --> Page : READING, or DONE with the outcome, the text and the days
  end
end
@enduml
```

A dropped "look for work now" costs nothing: the reader's next poll finds the message.

## Reading

The reader runs inside the ledger, apart from any request, on work of its own. It looks for work every
`<reader poll interval setting>`, and when filing asks it to.

```plantuml
@startuml
participant "the reader" as Reader
database "the ledger's store" as Store
participant "the turn" as Turn
participant "AI Connector" as AI

Reader -> Store : claim the oldest WAITING message, skipping any another reader holds
alt nothing waiting
  Store --> Reader : nothing
else one claimed
  Store --> Reader : the message, now READING
  Reader -> Turn : the text, as the stored person, under message id web:<id>
  alt the person's groupings cannot be read, or lack the catch-all
    Turn --> Reader : FAILED, the connector never called
  else prepared
    Turn -> Store : stamp the hand-over, only if still READING
    alt the sweep settled it meanwhile
      Store --> Turn : nothing stamped; the connector is never called
    end
    Turn -> AI : the same extraction request a Telegram turn sends
    AI -> Turn : stores pending spending through the ledger's tools
    AI --> Turn : done, refused, or unreachable
    Turn -> Store : count every entry under web:<id>, pending or accepted (DN20)
    Turn --> Reader : the outcome; no report is sent anywhere (DN06)
  end
  Reader -> Store : the message, DONE with that outcome, only if still READING
end
@enduml
```

The turn is the one a Telegram message runs, resolving the person by the stored id rather than finding or
creating them. The credential is minted only after the hand-over is stamped. A failure after the connector was
called leaves the message `READING` for the sweep.

A pass of the reader or the sweep that fails is logged, and the next interval runs as usual.

## The sweep

Every `<reader poll interval setting>`, beside the claim:

| `updated_at` older than …        | with … under `web:<id>`, pending or accepted | becomes           |
|----------------------------------|----------------------------------------------|-------------------|
| the turn's longest possible life | at least one entry                           | `DONE`, `PARTIAL` |
| the turn's longest possible life | nothing                                      | `DONE`, `FAILED`  |
| anything more recent             | anything                                     | left alone        |

The turn's longest possible life is the credential's lifetime, plus the leeway its validation allows past expiry,
plus a margin for a tool call accepted just before that. Each is read from the setting that already holds it,
never copied. No tool call of the turn is accepted after it, so what is stored by then is final (DN07). The
sweep, like settling, writes only where the message is still `READING`, and logs a warning per text it settles.

## The stored message

`written_message`, one row per text filed:

| Column        | Holds                                                                              |
|---------------|------------------------------------------------------------------------------------|
| `id`          | the message's own id, as the browser names it                                      |
| `user_id`     | who filed it; every read is scoped to it; removed with its user                   |
| `text`        | the text as filed, 1 to 1000 UTF-16 code units, never blank                       |
| `status`      | `WAITING`, `READING` or `DONE`, and nothing else                                   |
| `outcome`     | `RECORDED`, `PARTIAL`, `NOTHING_IDENTIFIED`, `ANSWERED` or `FAILED`               |
| `created_at`  | when it was filed                                                                  |
| `updated_at`  | when it last moved: the claim, the hand-over, and settling each set it            |

- `outcome` is present exactly when `status` is `DONE`, checked by the store.
- At most one row per `user_id` is `WAITING` or `READING`, enforced by a unique index over those rows. A second
  filing that violates that index, and only that one, answers 429 (DN15).
- The claim reads the oldest `WAITING` row through an index over those rows alone.

Pending spending stored by its turn carries `web:<id>` as its incoming message id, as a Telegram turn's carries
`<conversation>:<message>`. The days an answer names are the UTC days of every entry under `web:<id>`, pending or
since accepted.

```plantuml
@startuml
[*] --> WAITING : filed
WAITING --> READING : claimed by one reader
READING --> DONE : the turn ends, whatever its outcome
READING --> DONE : swept, PARTIAL or FAILED
DONE --> [*]
@enduml
```

The browser sees `WAITING` and `READING` alike, as `READING`.
