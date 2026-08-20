# Clear the emptied reports

- **In**
  - the person, by the id the ledger stores them under
  - the [messages](../domain/incoming-message-id.md) an acceptance recorded entries under
- **Out**
  - reports in the chat with their buttons taken off
- **Why:** a report whose spending is already accepted stops offering two buttons that decide nothing

*Implemented by `ClearEmptiedReportsUseCase`.*

## Prerequisites

- An acceptance already committed, and the person was already answered.
- The person's id is the stored one, resolved by whoever hands the work over.

## How it is entered

- It runs on a thread of its own, out of a pool whose bounds are [configuration](../configuration.md).
- Nobody waits for it. Nothing it does or fails to do reaches the browser, and nothing is retried.

## Collaborators

| Direction | Collaborator                                                      | Through                                                                           | For                                                                                 |
|-----------|-------------------------------------------------------------------|-----------------------------------------------------------------------------------|-------------------------------------------------------------------------------------|
| in        | [Accept the proposals a person chose](accept-chosen-proposals.md) | [Accept the proposals a person chose](accept-chosen-proposals.md)                 | handing over the messages an acceptance may have emptied                            |
| out       | [Database](../contracts/out/database.md)                          | [Users, categories and expenses](../contracts/out/database.md)                    | reading what is still pending under each message, and where its reports were posted |
| out       | [Telegram](../contracts/out/telegram-replies.md)                  | [Outgoing replies](../contracts/out/telegram-replies.md)                          | taking the buttons off a report the acceptance emptied                              |

## Outcomes

| Outcome             | When                                                                                 | Result                                                             |
|---------------------|--------------------------------------------------------------------------------------|--------------------------------------------------------------------|
| Report cleared      | nothing of theirs is left pending under the message, and a report is recorded for it | every report recorded for that message loses its buttons           |
| Message left alone  | one of their entries is still pending under the message                              | nothing is sent for it, and the remaining messages are still taken |
| No report recorded  | nothing says where that message's report was posted                                  | nothing is sent, and the remaining messages are still taken        |
| Clearing refused    | Telegram refuses the edit, or cannot be reached                                      | it is logged, never retried, and the next report is still taken    |
| Counts unreadable   | reading what is still pending fails                                                  | it is logged, and no report is cleared                             |
| Reports unreadable  | reading where a message's reports were posted fails                                  | the run ends there, and the messages after it are left alone       |
| Nothing handed over | the work names no message                                                            | nothing is read and nothing is sent                                |
| Clearing dropped    | the pool has no room for the work                                                    | it never runs, and nothing is queued or run on the request thread  |

## Components

```plantuml
@startuml C3-Component-ClearEmptiedReports
!include <C4/C4_Component>

AddElementTag("telegramExternal", $bgColor="#1c94e0", $fontColor="#ffffff", $borderColor="#125d8c")
AddElementTag("dbExternal", $bgColor="#d68910", $fontColor="#ffffff", $borderColor="#8f5c0a")
AddElementTag("portIn", $bgColor="#16a085", $fontColor="#ffffff", $borderColor="#0e6655", $legendText="inbound port (interface)")
AddElementTag("portOut", $bgColor="#7f8c8d", $fontColor="#ffffff", $borderColor="#566573", $legendText="outbound port (interface)")
AddElementTag("core", $bgColor="#2c3e50", $fontColor="#ffffff", $borderColor="#1b2631", $legendText="application core")
AddRelTag("implements", $lineStyle="dashed")

System_Ext(telegram, "Telegram", "Messaging platform; hosts the bot and the report", $tags="telegramExternal")
ContainerDb(db, "Database", "PostgreSQL", "Stores expenses and where each report was posted", $tags="dbExternal")

Container_Boundary(ledger, "Ledger Service (Java, Spring Boot)") {
  Component(acceptService, "Accept the Chosen Proposals Use Case", "Plain Java", "Hands over the messages an acceptance recorded entries under", $tags="core")
  Component(dispatchPort, "Report Clearing Dispatch Port", "Interface", "Outbound port", $tags="portOut")
  Component(dispatcher, "Report Clearing Dispatcher", "Bounded thread pool", "Runs the clearing off the request thread, and drops it when the pool is full", $tags="core")
  Component(clearPort, "Clear Emptied Reports Port", "Interface", "Inbound port", $tags="portIn")
  Component(clearService, "Clear the Emptied Reports Use Case", "Plain Java", "Takes each emptied message and clears every report recorded for it", $tags="core")
  Component(expenseRepositoryPort, "Expense Repository Port", "Interface", "Outbound port", $tags="portOut")
  Component(reportRepositoryPort, "Proposal Report Repository Port", "Interface", "Outbound port", $tags="portOut")
  Component(messageDeliveryPort, "Message Delivery Port", "Interface", "Outbound port", $tags="portOut")
  Component(expenseRepositoryAdapter, "Expense Repository Adapter", "Spring Data Relational", "Says which messages still hold a pending entry", $tags="dbExternal")
  Component(reportRepositoryAdapter, "Proposal Report Repository Adapter", "Spring Data Relational", "Reads where a message's reports were posted", $tags="dbExternal")
  Component(deliveryAdapter, "Telegram Message Delivery Adapter", "Spring Component", "Takes the buttons off a report, leaving its text", $tags="telegramExternal")
}

Rel_D(acceptService, dispatchPort, "Hands the emptied messages to")
Rel_U(dispatcher, dispatchPort, "Implements", $tags="implements")
Rel_D(dispatcher, clearPort, "Invokes, on its own thread")
Rel_L(clearService, clearPort, "Implements", $tags="implements")
Rel_D(clearService, expenseRepositoryPort, "Reads what is still pending through")
Rel_D(clearService, reportRepositoryPort, "Looks the reports up through")
Rel_D(clearService, messageDeliveryPort, "Clears the buttons through")
Rel_U(expenseRepositoryAdapter, expenseRepositoryPort, "Implements", $tags="implements")
Rel_U(reportRepositoryAdapter, reportRepositoryPort, "Implements", $tags="implements")
Rel_U(deliveryAdapter, messageDeliveryPort, "Implements", $tags="implements")
Rel_D(expenseRepositoryAdapter, db, "SQL", "JDBC")
Rel_D(reportRepositoryAdapter, db, "SQL", "JDBC")
Rel_R(deliveryAdapter, telegram, "The report, with its keyboard removed", "Telegram Bot API")

Lay_D(dispatchPort, dispatcher)
Lay_D(clearPort, expenseRepositoryPort)
Lay_D(expenseRepositoryPort, expenseRepositoryAdapter)

SHOW_LEGEND()
@enduml
```

## Flow

```plantuml
@startuml ClearEmptiedReports-Activity
start
:the messages an acceptance recorded entries under, off the request thread;
if (any message handed over?) then (no)
  stop
endif
:ask the database which of them still hold a pending entry;
if (the read fails?) then (yes)
  :log it, and stop;
  stop
endif
repeat :take the next message;
  if (anything still pending under it?) then (yes)
    :leave its report alone;
  else (no)
    :ask the database where that message's reports were posted;
    if (no report recorded?) then (yes)
      :nothing to clear;
    else (no)
      repeat :take the next report, oldest first;
        :ask Telegram to take its buttons off, leaving the text;
        if (Telegram refuses, or cannot be reached?) then (yes)
          :log it, and do not retry;
        endif
      repeat while (another report?) is (yes)
    endif
  endif
repeat while (another message?) is (yes)
->no;
stop
@enduml
```

## References

- [Resolve a reported proposal](resolve-a-reported-proposal.md) — what a report that keeps its buttons answers
  when it is tapped
- [Configuration](../configuration.md) — the pool's bounds, and what happens to work it has no room for
