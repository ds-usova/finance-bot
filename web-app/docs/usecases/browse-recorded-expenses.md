# Browse recorded expenses

- **At:** `/`, behind the route guard
- **In:** a signed-in person · a filter they set (optional)
- **Out:** a page of their expenses, newest first, cut into UTC days, and the category tree the filter offers
- **Why:** everything the bot recorded, and everything still awaiting a decision, is visible in one place

*Implemented by the expenses page, the filter controls, the expense list with its day sections, the pager and
the expenses client.*

## Collaborators

| Direction | Collaborator                                             | Through                                                 | For                                                             |
|-----------|----------------------------------------------------------|---------------------------------------------------------|-----------------------------------------------------------------|
| in        | [A signed-in person's browser](sign-in-with-telegram.md) | the expenses page                                       | seeing what the ledger holds for them                           |
| out       | [Ledger Service](../contracts/out/ledger-browse-api.md)  | [the browse API](../contracts/out/ledger-browse-api.md) | the entries, and the categories and groupings behind the filter |
| out       | [Sign in with Telegram](sign-in-with-telegram.md)        | [the session state it owns](sign-in-with-telegram.md)   | dropping to anonymous on a refused read                         |

## Rules

- Opening the page reads the listing, the categories and the groupings. Changing a filter reads the listing
  again; the tree is read once.
- A filter field left unset is not sent, so the ledger applies its own default for it.
- The period is sent as a complete pair or as neither day. Setting or clearing one day on its own sends nothing.
- The period narrows by when a row was recorded, not when the money was spent.
- A row is named from the category list, matched by id. A category the list does not answer leaves it blank.
- A grouping narrows the categories offered, from the tree already held. It is not a listing filter and never
  reaches the ledger; a category outside the chosen one is dropped.
- The answered page is cut into UTC days here. The ledger is never asked to group or total by day.
- A day's figure sums that day's recorded entries only, one figure per currency, nothing converted.
- A day split by the page boundary appears on both pages. Each part counts and totals only its own entries.
- The listing is read with the ledger's own page size. Paging steps by the size it answered with.
- Changing a filter returns to the first page.
- Only the newest listing read reaches the screen. A slower one answering after its filter was left is dropped,
  and so is its refusal.
- A refusal for want of a session drops the session to anonymous, from any of the three reads. The ledger is
  told nothing.

## Outcomes

| Outcome            | When                                                    | Result                                                                           |
|--------------------|---------------------------------------------------------|------------------------------------------------------------------------------------|
| The list is shown  | every read is answered                                  | the entries appear newest first in day sections, and the controls offer the tree |
| Nothing to show    | the listing answers no entries                          | the page says so in place of the list                                            |
| Part of the list   | the total is larger than the entries answered           | the page names which of them are on screen, and offers the way on                |
| Another page       | the way on or back is used                              | the listing is read again at the new offset                                      |
| The list narrows   | a filter is changed, the period complete or empty       | the listing is read again with it, from the first page                           |
| Nothing is read    | one day of the period is set or cleared on its own      | nothing is sent, and the list on screen stands                                   |
| Sent to sign in    | any of the three reads is refused for want of a session | the sign-in page is shown, with no reload                                        |
| Failure reported   | a read fails for any other reason                       | the failure is shown, and whatever is on screen stays                            |
| Categories unnamed | only the category read failed                           | the entries still list, each with its category blank                             |

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
  Ledger --> Page : the entries and the tree
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
@enduml
```

## Components

```plantuml
@startuml C3-BrowseRecordedExpenses-Components
!include <C4/C4_Component>

AddElementTag("page", $bgColor="#A85C74", $fontColor="#FFFFFF", $borderColor="#7E4457")

Person(user, "Person", "Signed in, browsing their expenses")
System_Ext(ledger, "Ledger Service", "Answers the listing and the category tree")

Container_Boundary(webApp, "Web App") {
  Component(appShell, "App shell", "React", "The frame every route renders inside", $tags="page")
  Component(routeGuard, "Route guard", "React", "Keeps the page behind an open session")
  Component(expensesPage, "Expenses page", "React", "Reads what the ledger holds and composes it", $tags="page")
  Component(expenseList, "Expense list", "React", "Cuts the answered page into UTC days")
  Component(daySection, "Day section", "React", "Heads a day with its count and its spend, and lists it")
  Component(expenseFilters, "Filter controls", "React", "Offers the grouping, the category, the status and the period")
  Component(pager, "Pager", "React", "Steps by the page size the ledger applied")
  Component(authContext, "Session state", "React context", "Holds who is signed in")
  Component(expensesClient, "Expenses client", "TypeScript", "Calls the listing, the categories and the groupings")
}

Rel_R(user, appShell, "Opens")
Rel_R(appShell, routeGuard, "Renders the matched route through")
Rel_R(routeGuard, expensesPage, "Admits")
Rel_D(routeGuard, authContext, "Asks whether a session is open")
Rel_D(expensesPage, expenseList, "Renders")
Rel_D(expensesPage, expenseFilters, "Renders")
Rel_D(expensesPage, pager, "Renders")
Rel_D(expenseList, daySection, "Renders one per day")
Rel_D(expensesPage, authContext, "Reports an expired session to")
Rel_R(expensesPage, expensesClient, "Reads through")
Rel_R(expensesClient, ledger, "Browse requests", "HTTPS, same origin")
@enduml
```
