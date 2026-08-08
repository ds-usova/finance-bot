# Browse recorded expenses

- **At:** `/`, behind the route guard
- **In:** a signed-in person · a filter they set (optional)
- **Out:** a page of their expenses, newest first, and the category tree the filter offers
- **Why:** everything the bot recorded, and everything still awaiting a decision, is visible in one place

*Implemented by the expenses page, the expense list, the filter controls and the expenses client.*

## Collaborators

| Direction | Collaborator                                             | Through                                                 | For                                                             |
|-----------|----------------------------------------------------------|---------------------------------------------------------|-----------------------------------------------------------------|
| in        | [A signed-in person's browser](sign-in-with-telegram.md) | the expenses page                                       | seeing what the ledger holds for them                           |
| out       | [Ledger Service](../contracts/out/ledger-browse-api.md)  | [the browse API](../contracts/out/ledger-browse-api.md) | the entries, and the categories and groupings behind the filter |
| out       | [Sign in with Telegram](sign-in-with-telegram.md)        | [the session state it owns](sign-in-with-telegram.md)   | dropping to anonymous on a refused read, and ending the session |

## Rules

- Opening the page reads the listing, the categories and the groupings. Changing a filter reads the listing
  again; the tree is read once.
- A filter field left unset is not sent, so the ledger applies its own default for it.
- The period narrows by when a row was recorded, not when the money was spent.
- A row is named from the category list, matched by id. A category the list does not answer leaves it blank.
- A grouping narrows the categories offered, from the tree already held. It is not a listing filter and never
  reaches the ledger; a category outside the chosen one is dropped.
- The listing is read with the ledger's own page size, and no control moves past the first page.
- A refusal for want of a session drops the session to anonymous, from any of the three reads. That is distinct
  from signing out, which ends a session still open and tells the ledger so. An expiry tells it nothing.

## Outcomes

| Outcome            | When                                                    | Result                                                           |
|--------------------|---------------------------------------------------------|------------------------------------------------------------------|
| The list is shown  | every read is answered                                  | the entries appear newest first, and the controls offer the tree |
| Nothing to show    | the listing answers no entries                          | the page says so in place of the list                            |
| Part of the list   | the total is larger than the entries answered           | the page says how many of the total are on screen                |
| The list narrows   | a filter is changed                                     | the listing is read again with it                                |
| Sent to sign in    | any of the three reads is refused for want of a session | the sign-in page is shown, with no reload                        |
| Failure reported   | a read fails for any other reason                       | the failure is shown, and whatever is on screen stays            |
| Categories unnamed | only the category read failed                           | the entries still list, each with its category blank             |
| Signed out         | the sign-out control is used                            | the sign-in page is shown, and the ledger clears the cookie      |

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
  Page --> User : the list and the filter controls
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
  Page -> Ledger : read the listing with the filter
  Ledger --> Page : the narrowed entries
  Page --> User : the narrowed list
end

group ending the session
  User -> Page : signs out
  Page -> Auth : end the session
  Auth -> Ledger : end the session
  Auth --> User : the sign-in page
end
@enduml
```

## Components

```plantuml
@startuml C3-BrowseRecordedExpenses-Components
!include <C4/C4_Component>

AddElementTag("page", $bgColor="#D6336C", $fontColor="#FFFFFF", $borderColor="#A61E4D")

Person(user, "Person", "Signed in, browsing their expenses")
System_Ext(ledger, "Ledger Service", "Answers the listing and the category tree")

Container_Boundary(webApp, "Web App") {
  Component(routeGuard, "Route guard", "React", "Keeps the page behind an open session")
  Component(expensesPage, "Expenses page", "React", "Reads what the ledger holds and composes it", $tags="page")
  Component(expenseList, "Expense list", "React", "Renders a row per entry")
  Component(expenseFilters, "Filter controls", "React", "Offers the grouping, the category, the status and the period")
  Component(authContext, "Session state", "React context", "Holds who is signed in")
  Component(expensesClient, "Expenses client", "TypeScript", "Calls the listing, the categories and the groupings")
}

Rel_R(user, routeGuard, "Opens")
Rel_R(routeGuard, expensesPage, "Admits")
Rel_D(routeGuard, authContext, "Asks whether a session is open")
Rel_D(expensesPage, expenseList, "Renders")
Rel_D(expensesPage, expenseFilters, "Renders")
Rel_D(expensesPage, authContext, "Reports an expired session to")
Rel_R(expensesPage, expensesClient, "Reads through")
Rel_R(expensesClient, ledger, "Browse requests", "HTTPS, same origin")
@enduml
```
