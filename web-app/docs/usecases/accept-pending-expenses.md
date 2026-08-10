# Accept pending expenses

- **At:** `/`, behind the route guard
- **In:** a signed-in person
- **In:** the entries awaiting a decision that they tick on the listing
- **Out:** those entries recorded in the ledger
- **Out:** the days they sat on, shown again as they now stand
- **Why:** everything the bot proposed is confirmed in one action, without answering it entry by entry

*Implemented by the expenses page, the expense list with its day sections, the action bar and the expenses
client.*

## Collaborators

| Direction | Collaborator                                                                                | Through                                                 | For                                      |
|-----------|---------------------------------------------------------------------------------------------|---------------------------------------------------------|------------------------------------------|
| in        | [A signed-in person's browser](sign-in-with-telegram.md)                                    | the expenses page                                       | confirming what the bot proposed         |
| out       | [Accept chosen proposals](../../../ledger-service/docs/usecases/accept-chosen-proposals.md) | [the browse API](../contracts/out/ledger-browse-api.md) | moving the ticked entries to recorded    |
| out       | [Browse expenses](../../../ledger-service/docs/usecases/browse-expenses.md)                 | [the browse API](../contracts/out/ledger-browse-api.md) | reading the touched days back            |
| out       | [Sign in with Telegram](sign-in-with-telegram.md)                                           | [the session state it owns](sign-in-with-telegram.md)   | dropping to anonymous on a refused write |

The listing this acts on is [Browse recorded expenses](browse-recorded-expenses.md).

## Prerequisites

- A session is open.
- A listing is on screen, since every id a tick carries is read from it.

## Outcomes

| Outcome                | When                                              | Result                                                                         |
|------------------------|---------------------------------------------------|--------------------------------------------------------------------------------|
| Nothing is offered     | nothing is ticked                                 | the action bar's row stands with no action in it                               |
| Accepted               | the acceptance is answered                        | the ticks clear, and the touched days are read again and replaced in place     |
| Some had moved on      | the answer names ids that matched nothing         | the person is told in words how many, and the touched days are still read back |
| Nothing more is ticked | the bound on one request is reached               | every unticked tick is disabled, and the ticked ones stay live                 |
| Nothing is sent        | the action is pressed again while the call is out | nothing, and one call stands                                                   |
| Failure reported       | the acceptance is refused for any other reason    | the wording is shown, the ticks stand, and the listing on screen is unchanged  |
| Sent to sign in        | the acceptance is refused for want of a session   | the sign-in page is shown, and nothing is read back                            |
| The days stand         | the read back fails                               | the failure is shown, and the days on screen stay as the acceptance found them |

## Flow

```plantuml
@startuml AcceptPendingExpenses-Sequence
actor "Person" as User
participant "Expenses page" as Page
participant "Session state" as Auth
participant "Ledger" as Ledger

User -> Page : ticks entries, a day at a time or one by one
User -> Page : accepts what is ticked
Page -> Ledger : accept the ticked ids

alt the acceptance is answered
  Ledger --> Page : how many moved, and how many matched nothing
  Page -> Page : clear the ticks
  Page --> User : how many had already moved on, where any had
  Page -> Ledger : read the touched days again
  alt the days are answered
    Ledger --> Page : those days as they now stand
    Page --> User : only those day sections, replaced
  else the read back fails
    Ledger --> Page : the failure
    Page --> User : what went wrong, the days left as they were
  end
else refused for want of a session
  Ledger --> Page : no valid session
  Page -> Auth : the session has expired
  Auth --> User : the sign-in page
else refused otherwise
  Ledger --> Page : the failure
  Page --> User : what went wrong, the ticks left standing
end
@enduml
```

## Components

```plantuml
@startuml C3-AcceptPendingExpenses-Components
!include <C4/C4_Component>

AddElementTag("page", $bgColor="#A85C74", $fontColor="#FFFFFF", $borderColor="#7E4457")

Person(user, "Person", "Signed in, confirming what the bot proposed")
System_Ext(ledger, "Ledger Service", "Records the proposals it is given and answers the listing")

Container_Boundary(webApp, "Web App") {
  Component(appShell, "App shell", "React", "The frame every route renders inside", $tags="page")
  Component(routeGuard, "Route guard", "React", "Keeps the page behind an open session")
  Component(expensesPage, "Expenses page", "React", "Owns what is ticked and offers the acceptance", $tags="page")
  Component(actionBar, "Action bar", "React", "Names how many are ticked and carries the action")
  Component(expenseList, "Expense list", "React", "Cuts the answered page into UTC days")
  Component(daySection, "Day section", "React", "Ticks one entry or a whole day")
  Component(expenseDays, "Day helpers", "TypeScript", "Reads back the touched days and merges a fresh one in")
  Component(authContext, "Session state", "React context", "Holds who is signed in")
  Component(expensesClient, "Expenses client", "TypeScript", "Calls the acceptance and the listing")
}

Rel_R(user, appShell, "Opens")
Rel_R(appShell, routeGuard, "Renders the matched route through")
Rel_R(routeGuard, expensesPage, "Admits")
Rel_D(routeGuard, authContext, "Asks whether a session is open")
Rel_D(expensesPage, actionBar, "Renders")
Rel_D(expensesPage, expenseList, "Renders")
Rel_R(expensesPage, expenseDays, "Works out what to read back with")
Rel_D(expensesPage, authContext, "Reports an expired session to")
Rel_D(expenseList, daySection, "Renders one per day")
Rel_R(expensesPage, expensesClient, "Accepts and re-reads through")
Rel_R(expensesClient, ledger, "Acceptance and browse requests", "HTTPS, same origin")
@enduml
```

## References

- [ADR 0014: The web app and the ledger are served from one origin](../../../docs/adr/0014-the-web-app-and-the-ledger-are-served-from-one-origin.md) —
  why the acceptance carries a CSRF token read from a cookie
- [Clear the emptied reports](../../../ledger-service/docs/usecases/clear-emptied-reports.md) — what happens in
  Telegram to a report this acceptance emptied
