# Browse recorded expenses

- **At:** `/`, behind the route guard
- **In:** a signed-in person
- **In:** a filter they set (optional)
- **In:** another category they pick for a row (optional)
- **Out:** a page of their expenses, newest first, cut into UTC days, and the category tree the filter offers
- **Out:** a row filed under the category picked for it
- **Why:** everything the bot recorded, and everything still awaiting a decision, is visible in one place
- **Why:** a miscategorised entry is put right where it is read, without leaving the listing

*Implemented by the expenses page, the filter controls, the expense list with its day sections, the category
picker, the pager and the expenses client.*

What this listing shows awaiting a decision is acted on by
[Accept pending expenses](accept-pending-expenses.md).

## Collaborators

| Direction | Collaborator                                             | Through                                                 | For                                                                                 |
|-----------|----------------------------------------------------------|---------------------------------------------------------|-------------------------------------------------------------------------------------|
| in        | [A signed-in person's browser](sign-in-with-telegram.md) | the expenses page                                       | seeing what the ledger holds for them                                               |
| out       | [Ledger Service](../contracts/out/ledger-browse-api.md)  | [the browse API](../contracts/out/ledger-browse-api.md) | the entries, each day's figures, and the categories and groupings behind the filter |
| out       | [Ledger Service](../contracts/out/ledger-browse-api.md)  | [the browse API](../contracts/out/ledger-browse-api.md) | filing a row under the category picked for it                                       |
| out       | [Sign in with Telegram](sign-in-with-telegram.md)        | [the session state it owns](sign-in-with-telegram.md)   | dropping to anonymous on a refused call                                             |

## Outcomes

| Outcome                | When                                                                  | Result                                                                             |
|------------------------|-----------------------------------------------------------------------|------------------------------------------------------------------------------------|
| The list is shown      | every read is answered                                                | the entries appear newest first in day sections, and the controls offer the tree   |
| Nothing to show        | the listing answers no entries                                        | the page says so in place of the list                                              |
| Part of the list       | the total is larger than the entries answered                         | the page names which of them are on screen, and offers the way on                  |
| Another page           | the way on or back is used                                            | the listing is read again at the new offset                                        |
| The list narrows       | a filter is changed, the period complete or empty                     | the listing is read again with it, from the first page                             |
| Nothing is read        | one day of the period is set or cleared on its own                    | nothing is sent, and the list on screen stands                                     |
| Refiled                | a row's category is changed and the change is answered                | that row stands under its new category                                             |
| Refiled under a filter | the same, with the listing narrowed by category                       | the day it sits on is read again, and the row leaves a filter it no longer matches |
| No change is sent      | a change is out, or the category picked is the one the row has        | nothing, and one change stands                                                     |
| Refile refused         | a change is refused for any reason but a session or the row moving on | the refusal is shown under that row, and its category stands                       |
| The row moved on       | the ledger answers that the row no longer matches                     | the refusal is shown under that row, and the day it sat on is read again           |
| Sent to sign in        | a read or a change is refused for want of a session                   | the sign-in page is shown, with no reload                                          |
| Failure reported       | a read fails for any other reason                                     | the failure is shown, and whatever is on screen stays                              |
| Categories unnamed     | only the category read failed                                         | the entries still list, each with its category blank                               |

## Flow

```plantuml
@startuml BrowseRecordedExpenses-Sequence
actor "Person" as User
participant "Expenses page" as Page
participant "Session state" as Auth
participant "Ledger" as Ledger

User -> Page : opens the app
Page -> Ledger : read the listing, the categories and the groupings

alt every read is answered
  Ledger --> Page : the entries, the day figures and the tree
  Page -> Page : cut the answered page into UTC days
  Page --> User : the day sections and the filter controls
else a read is refused for want of a session
  Ledger --> Page : no valid session
  Page -> Auth : the session has expired
  Auth --> User : the sign-in page
else a read fails otherwise
  Ledger --> Page : the failure
  Page --> User : what went wrong
end

group narrowing the list
  User -> Page : sets a filter
  alt the filter is complete
    Page -> Ledger : read the listing with the filter, from the first page
    Ledger --> Page : the narrowed entries
    Page --> User : the narrowed list
  else one day of the period is set alone
    Page --> User : the list on screen stands
  end
end

group refiling a row
  User -> Page : picks another category for a row
  Page -> Ledger : file that row under it
  alt the change is answered
    Ledger --> Page : the entry as it now stands
    Page --> User : the row under its new category
  else the row has moved on
    Ledger --> Page : no such row
    Page -> Ledger : read that day again
    Page --> User : the refusal under the row, and the day as it now stands
  else refused for want of a session
    Ledger --> Page : no valid session
    Page -> Auth : the session has expired
    Auth --> User : the sign-in page
  else refused otherwise
    Ledger --> Page : the failure
    Page --> User : the refusal under the row, its category unchanged
  end
end
@enduml
```

## Components

```plantuml
@startuml C3-BrowseRecordedExpenses-Components
!include <C4/C4_Component>

AddElementTag("page", $bgColor="#A85C74", $fontColor="#FFFFFF", $borderColor="#7E4457")

Person(user, "Person", "Signed in, browsing their expenses")
System_Ext(ledger, "Ledger Service", "Answers the listing and the category tree, and refiles a row")

Container_Boundary(webApp, "Web App") {
  Component(appShell, "App shell", "React", "The frame every route renders inside", $tags="page")
  Component(routeGuard, "Route guard", "React", "Keeps the page behind an open session")
  Component(expensesPage, "Expenses page", "React", "Reads what the ledger holds and composes it", $tags="page")
  Component(expenseList, "Expense list", "React", "Cuts the answered page into UTC days")
  Component(daySection, "Day section", "React", "Heads a day with its count and its spend, and lists it")
  Component(categoryPicker, "Category picker", "React", "Offers the tree for one row's category")
  Component(expenseFilters, "Filter controls", "React", "Offers the category, the status and the period")
  Component(pager, "Pager", "React", "Steps by the page size the ledger applied")
  Component(authContext, "Session state", "React context", "Holds who is signed in")
  Component(expensesClient, "Expenses client", "TypeScript", "Calls the listing, the categories, the groupings and the refile")
}

Rel_R(user, appShell, "Opens")
Rel_R(appShell, routeGuard, "Renders the matched route through")
Rel_R(routeGuard, expensesPage, "Admits")
Rel_D(routeGuard, authContext, "Asks whether a session is open")
Rel_D(expensesPage, expenseList, "Renders")
Rel_D(expensesPage, expenseFilters, "Renders")
Rel_D(expensesPage, pager, "Renders")
Rel_D(expenseList, daySection, "Renders one per day")
Rel_D(daySection, categoryPicker, "Renders one per row")
Rel_D(expensesPage, authContext, "Reports an expired session to")
Rel_R(expensesPage, expensesClient, "Reads and refiles through")
Rel_R(expensesClient, ledger, "Browse and refile requests", "HTTPS, same origin")
@enduml
```

## References

- [ADR 0014: The web app and the ledger are served from one origin](../../../docs/adr/0014-the-web-app-and-the-ledger-are-served-from-one-origin.md) —
  why every read carries the session cookie without a CORS step
- [Accept pending expenses](accept-pending-expenses.md) — what a person does with the entries this listing shows
  awaiting a decision
