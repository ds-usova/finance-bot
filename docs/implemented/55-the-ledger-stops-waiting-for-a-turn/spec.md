# Spec: The Ledger Stops Waiting for a Turn

**Format:** 2
**Approved:** ds-usova, 2026-10-14, 6e9c7f6bafa9
**Affected Modules:** `ledger-service`, `ai-connector-service`

## Objective

BT10 asks for three things, and three tasks deliver them, in this order:

- **Task 55, this one:** the ledger keeps every turn before handing it over, and hands it over apart from the
  message that started it. The connector accepts a turn and answers at once. It tells the ledger how the turn
  ended through an operation the ledger serves, and the ledger reports then. A sweep settles a turn whose end
  never arrives.
- **Task 56:** filing an expense becomes idempotent by the message and the expense's position in it.
- **Task 57:** the sweep retries a turn that ended unavailable. It closes BT10.

Today the ledger holds the extraction call open for the whole turn, near five seconds on average (task 52, DN05).
A Telegram turn runs on the thread that collects updates, so every other person's message and every button tap
waits behind it. The web reader reads one text at a time for the same reason. A hand-over that fails is never
tried again.

## Requirements

- **RQ01:** Taking a message in costs the ledger keeping its turn. The hand-over runs apart, and never waits for
  the turn.
- **RQ02:** When a turn ends, the connector tells the ledger how it ended, and the ledger reports it then.
- **RQ03:** Whatever a turn ends as, the person reads the report, or the page shows the outcome, they read today
  for the same turn.
- **RQ04:** A turn whose end never arrives is settled by a sweep, a Telegram message as well as a text filed from
  the web.
- **RQ05:** A turn is reported once and run once. A second end, a second hand-over, or one arriving after the
  turn was settled, changes nothing.
- **RQ06:** Both extraction modes run this way.
- **RQ07:** Only the turn's own credential ends a turn, and only the turn of the person and the message it names.
- **RQ08:** A turn the connector cannot take yet, or whose hand-over went unanswered, is handed over again later
  rather than failed.
- **RQ09:** An operator can see turns the sweep settles, reports that were never delivered, and a queue that is
  not draining.

## Acceptance Scenarios

Four entry points: taking a message in and handing it over, the connector's run of a turn, the turn's end, and
the sweep. The flow is [The turn in two halves](the-turn-in-two-halves.md). What the ledger keeps meanwhile is
[A turn awaiting its end](a-turn-awaiting-its-end.md).

### Taking a message in and handing it over

- **AC01:** a Telegram message is taken in
  - Given: a person sends the bot a message naming an expense
  - When: the ledger collects it
  - Then: the ledger keeps the turn, queued, and the batch is acknowledged without the connector being called.
    The hand-over follows apart. No report is sent until the turn's end.
  - Proves: RQ01

- **AC02:** a queued turn is handed over
  - Given: a queued Telegram turn, or a text filed from the web and waiting
  - When: the ledger hands it to the connector
  - Then: the call answers once the request is accepted, before the model is called. The turn is stamped as
    handed over and awaits its end. A text moves from `WAITING` to `READING`. The ledger goes on to the next
    queued turn.
  - Proves: RQ01

- **AC03:** the connector refuses the request itself
  - Given: the connector refuses the request as unauthenticated or as an invalid argument
  - When: the ledger hands a turn over
  - Then: the turn is settled at once, as a turn that failed with nothing filed: a Telegram message gets the
    Failed report, and a text is settled `FAILED`
  - Proves: RQ03

- **AC04:** the turn cannot be kept
  - Given: the ledger's store refuses the write
  - When: the ledger collects a Telegram message
  - Then: the connector is never called, no report is sent, and the failure is logged, as a store failure is
    today
  - Proves: RQ03

- **AC17:** a Telegram message arrives again
  - Given: a batch delivered again after a stop, holding a message whose turn is still kept
  - When: the ledger collects that message
  - Then: no second turn is kept. One info line names the message as already taken in.
  - Proves: RQ05

- **AC18:** the hand-over's outcome is unknown
  - Given: the connector cannot be reached, cannot read the ledger's key set, or does not answer within the
    hand-over's deadline
  - When: the ledger hands a turn over
  - Then: the turn stays queued, and a later look hands it over again with a fresh credential
  - Proves: RQ08

- **AC25:** the connector is running as many turns as it allows
  - Given: the connector runs the number of turns its setting caps it at
  - When: the ledger hands one more over
  - Then: it is refused as busy before it is accepted. The turn stays queued, and a later look hands it over
    again.
  - Proves: RQ08

- **AC26:** a turn is handed over while its first hand-over runs
  - Given: a hand-over the connector accepted, whose answer never reached the ledger, and whose run goes on
  - When: the ledger hands the same turn over again
  - Then: the connector answers it as accepted and does not run the turn a second time
  - Proves: RQ05

- **AC19:** a settled turn is not handed over
  - Given: a queued turn that its end, the refusal or the sweep has already settled
  - When: the ledger looks for turns to hand over
  - Then: it is never handed over again
  - Proves: RQ05

- **AC27:** a turn is never accepted
  - Given: a turn still queued past the wait DN09 sets, and past the turn's longest life since its latest
    hand-over attempt
  - When: the sweep runs
  - Then: the turn is settled by what is under its message: Partial if anything, else Failed. It is never handed
    over again.
  - Proves: RQ04, RQ08

- **AC28:** the person cannot be prepared at hand-over
  - Given: a queued turn whose person's catalogue lacks the designated catch-all
  - When: the ledger is about to hand it over
  - Then: the connector is never called. A Telegram turn is removed with no report and counted unreported; a
    text is settled `FAILED`. Both as today.
  - Proves: RQ03

- **AC29:** the store fails while preparing a hand-over
  - Given: a queued turn
  - When: reading its person's catalogue or currency fails
  - Then: the turn stays queued, and the look stops until its next run
  - Proves: RQ08

- **AC30:** a hand-over is not answered
  - Given: several queued turns
  - When: one is refused as busy, refused as unavailable, or not answered
  - Then: the look stops there, and the rest wait for its next run
  - Proves: RQ01, RQ08

- **AC31:** a turn queued across midnight
  - Given: a turn kept on one day and handed over on the next
  - When: the connector reads a relative period out of it
  - Then: it is anchored on the day the turn was kept
  - Proves: RQ03

- **AC32:** a person files a second text while the first is queued
  - Given: a text of theirs queued or handed over
  - When: they file another from the page
  - Then: it is refused as today, until the first is settled. A Telegram person has no such limit.
  - Proves: RQ03

### The connector's run

- **AC05:** a turn is accepted
  - Given: a request that verifies and is usable, in either mode
  - When: the connector receives it
  - Then: it answers at once with an empty answer. Keeping the message, recalling the examples and the turn
    itself run afterwards, as the caller's identity. The run carries the credential with it, and neither the
    hand-over's end nor its deadline cancels it.
  - Proves: RQ01, RQ06

- **AC06:** a turn ends acted on
  - Given: an accepted turn, in either mode
  - When: the turn finishes
  - Then: the connector tells the ledger the turn was acted on, with the turn's own credential
  - Proves: RQ02, RQ06

- **AC07:** a turn ends unavailable
  - Given: an accepted turn
  - When: the provider, the classifier's ledger calls or the ledger's tools fail the turn as unavailable today
  - Then: the connector tells the ledger the turn ended unavailable. What was filed before stands.
  - Proves: RQ02, RQ06

- **AC08:** a turn ends on an unexpected failure
  - Given: an accepted turn
  - When: anything else fails inside the connector
  - Then: the connector tells the ledger the turn failed, naming nothing internal
  - Proves: RQ02

- **AC20:** a turn outlives its bound
  - Given: an accepted turn still running at the connector's run bound, a setting kept below the credential's
    lifetime
  - When: the bound passes
  - Then: the run stops. Once any ledger call in flight has answered or passed its own deadline, the connector
    tells the ledger the turn ended unavailable. Nothing is filed after that.
  - Proves: RQ02

- **AC21:** the end cannot reach the ledger
  - Given: a turn that has ended
  - When: telling the ledger fails as unavailable, or gets no answer in time
  - Then: the connector tells it again, until the credential expires. A refusal as unauthenticated is never
    retried. Giving up is logged at error, and the sweep settles the turn.
  - Proves: RQ02, RQ04

### The turn's end

- **AC09:** a Telegram turn's end arrives
  - Given: a Telegram message awaiting its end
  - When: its end arrives, acted on, unavailable or failed
  - Then: the person gets the report they get today for that outcome and what was filed: Recorded, Answered,
    Nothing identified, Partial or Failed, totals first, buttons when it lists a proposal. The turn's row is
    deleted.
  - Proves: RQ02, RQ03

- **AC10:** a web text's end arrives
  - Given: a text `READING`, or still `WAITING` because the end arrived before the acceptance was recorded
  - When: its end arrives
  - Then: the text is settled `DONE` with the outcome it is settled with today: `RECORDED`, `ANSWERED`,
    `NOTHING_IDENTIFIED`, `PARTIAL` or `FAILED`
  - Proves: RQ02, RQ03

- **AC11:** an end arrives for a turn already settled
  - Given: a turn the sweep settled, a refused hand-over, or an end that already arrived
  - When: an end arrives for it
  - Then: the call answers as taken, and nothing changes: no second report, no second outcome
  - Proves: RQ05

- **AC12:** two ends arrive at once
  - Given: a turn awaiting its end
  - When: two ends for it arrive together
  - Then: exactly one is reported. The other answers as taken and reports nothing.
  - Proves: RQ05

- **AC13:** the end carries no usable credential
  - Given: an end with no credential, an expired one, or one naming no message
  - When: it reaches the ledger
  - Then: it is refused as unauthenticated, and nothing changes
  - Proves: RQ07

- **AC22:** the end names another person's message
  - Given: a turn awaiting its end under person A's message
  - When: an end arrives whose credential names person B and that message
  - Then: A's turn is untouched, and the call answers as taken
  - Proves: RQ07

- **AC14:** the ledger cannot read or settle the turn
  - Given: a turn awaiting its end
  - When: its end arrives and the store refuses the read or the settle
  - Then: the end is refused as unavailable, nothing is reported, and the turn still awaits its end, for a later
    end or the sweep
  - Proves: RQ05

### The sweep

- **AC15:** a Telegram turn's end never arrives
  - Given: a Telegram message handed over and awaiting its end past the turn's longest life, its connector
    restarted mid-turn or its end lost
  - When: the sweep runs
  - Then: the person gets the Partial report when anything was filed or asked about under the message, else the
    Failed one. A warning names the message and the person. The row is deleted.
  - Proves: RQ04

- **AC16:** a web text's end never arrives
  - Given: a text handed over and `READING` past the turn's longest life, for the same causes
  - When: the sweep runs
  - Then: it is settled as today's sweep settles it
  - Proves: RQ04

- **AC23:** the sweep cannot reach the store
  - Given: turns past their longest life, or queued past their wait
  - When: finding them, reading under one, or settling one fails
  - Then: the failure is logged, and the rest wait for the next run
  - Proves: RQ04

- **AC24:** a swept turn shows in the meters
  - Given: the sweep settles a turn of either channel
  - When: the meters are read
  - Then: a counter of swept turns, by channel, has grown by one. A swept Telegram turn also counts under its
    report's outcome, as every delivered report does. A swept text still counts there nowhere.
  - Proves: RQ09

- **AC33:** a report cannot be delivered
  - Given: a Telegram turn settled by its end, a refusal or the sweep
  - When: its report cannot be delivered
  - Then: the turn counts as unreported, as a failed delivery does today
  - Proves: RQ09

- **AC34:** the queue stalls
  - Given: hand-overs refused as busy or unavailable, or unanswered
  - When: the meters are read
  - Then: a gauge of the oldest queued turn's age, by channel, keeps rising until a hand-over is accepted
  - Proves: RQ09

## Decisions

- **DN01:** How many turns may the connector run at once, and what happens to one beyond that?
  - Answer: a connector setting caps it. One beyond the cap is refused as busy before it is accepted. The ledger
    keeps it queued and hands it over again on a later look, never failing it for that.
  - Basis: decided (user, 2026-10-14)

- **DN02:** What does the connector do when it cannot tell the ledger a turn has ended?
  - Answer: it tells it again while the end is unavailable or unanswered, until the credential expires. It never
    retries a refusal as unauthenticated. Then it logs at error, and the sweep settles the turn.
  - Basis: decided (user, 2026-10-14)

- **DN03:** What bounds how long a turn runs once no caller is waiting for it?
  - Answer: a connector setting, kept below the credential's lifetime. At it the run stops and ends unavailable,
    once any ledger call in flight has answered or passed its own deadline.
  - Basis: decided (user, 2026-10-14)

- **DN05:** Does the turn's end say whether it failed as unavailable or otherwise, though nothing in this task
  treats them differently?
  - Answer: yes, three values: acted on, unavailable, failed. A value the ledger does not know is read as failed.
  - Basis: decided (user, 2026-10-14)

- **DN06:** A hand-over that gets no answer, or cannot tell whether it arrived: is the turn settled at once, or
  left for its end or the sweep?
  - Answer: neither. The ledger keeps every turn first, as an outbox, and hands it over apart. A hand-over that
    is unanswered, unreachable or refused as unavailable leaves the turn queued, and a later look hands it over
    again. Only a refusal of the request itself settles it at once.
  - Basis: decided (user, 2026-10-14)

- **DN07:** What becomes of a Telegram turn's row once it is settled?
  - Answer: it is deleted, and the delete is the settle-once guard. The table holds only turns in flight.
  - Basis: decided (user, 2026-10-14)

- **DN08:** How does a turn the sweep settled show in the meters?
  - Answer: a new counter of swept turns, by channel. Every existing meter keeps its meaning.
  - Basis: decided (user, 2026-10-14)

- **DN09:** How long may a turn stay queued, never accepted, before the sweep settles it?
  - Answer: a new ledger setting, counted from when the turn was kept, and applied only once the turn's longest
    life has also passed since its latest hand-over attempt
  - Basis: decided (user, 2026-10-14)

- **DN10:** How does a web text show it is queued rather than handed over?
  - Answer: a text stays `WAITING` until the connector accepts it, and moves to `READING` then. Its end settles it
    from either.
  - Basis: decided (user, 2026-10-14)

- **DN11:** With memory off the connector never reads who a token names. Does it read the person and the message
  anyway, only to keep one run per turn?
  - Answer: yes. With memory off the connector reads the two claims unverified, only to keep one run per turn.
    The ledger still verifies the credential on every effect.
  - Basis: decided (user, 2026-10-14)

- **DN12:** What shows a queue that is not draining?
  - Answer: a gauge of the oldest queued turn's age, by channel
  - Basis: decided (user, 2026-10-14)

## Design Artifacts

- [The turn in two halves](the-turn-in-two-halves.md) — taking in, the hand-over, the run, the end, and what
  crosses between the two services.
- [A turn awaiting its end](a-turn-awaiting-its-end.md) — what the ledger keeps for a turn, how it moves from
  queued to settled once, and the sweep.
