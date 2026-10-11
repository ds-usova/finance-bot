# File expenses as text

- **At:** `/`, behind the route guard
- **In:** a signed-in person
- **In:** a text naming what they spent, written in the write-in line
- **Out:** the text filed with the ledger and read there
- **Out:** the days its entries landed on, read back and opened, nothing ticked
- **Out:** the text returned to the line, where nothing was filed from it
- **Why:** spending reaches the ledger from the page, without writing to the bot

*Implemented by the expenses page, its filing loop, the write-in line, the messages client and the day helpers.*

## Collaborators

| Direction | Collaborator                                                                                          | Through                                                 | For                                     |
|-----------|-------------------------------------------------------------------------------------------------------|---------------------------------------------------------|-----------------------------------------|
| in        | [A signed-in person's browser](sign-in-with-telegram.md)                                              | the expenses page                                       | filing spending without Telegram        |
| out       | [List a person's texts being read](../../../ledger-service/docs/usecases/browse-written-messages.md)  | [the browse API](../contracts/out/ledger-browse-api.md) | finding the text being read             |
| out       | [File a text for reading](../../../ledger-service/docs/usecases/file-written-message.md)              | [the browse API](../contracts/out/ledger-browse-api.md) | handing the text over                   |
| out       | [Read what became of a filed text](../../../ledger-service/docs/usecases/read-written-message.md)     | [the browse API](../contracts/out/ledger-browse-api.md) | learning what became of it              |
| out       | [Browse expenses](../../../ledger-service/docs/usecases/browse-expenses.md)                           | [the browse API](../contracts/out/ledger-browse-api.md) | reading the filed days back             |
| out       | [Sign in with Telegram](sign-in-with-telegram.md)                                                     | [the session state it owns](sign-in-with-telegram.md)   | dropping to anonymous on a refused call |

## Prerequisites

- A session is open.

## Outcomes

| Outcome                  | When                                                                   | Result                                                                          |
|--------------------------|------------------------------------------------------------------------|---------------------------------------------------------------------------------|
| Held                     | the page is still asking which text is being read                      | the line is read-only                                                           |
| Resumed                  | the page loads while a text of theirs is being read                    | the reading line holds it                                                       |
| Ready                    | none is being read, or asking fails for any reason but a session       | the line is editable, nothing beneath it                                        |
| Filed                    | the filing is answered                                                 | the line clears and is held, and the reading line holds the text                |
| Another was being read   | the filing is refused as one already being read, and that one is found | the new text stays in the held line, and the reading line holds the one found   |
| None found behind it     | the same, and asking finds none or fails                               | the line is editable, holding the new text, nothing beneath it                  |
| Filing refused           | the filing is refused for any other reason but a session               | the text stays in the editable line, with the refusal beneath it                |
| Still reading            | asking after the text answers it still being read, or fails but a 404  | the reading line stands                                                         |
| Recorded                 | the outcome is `RECORDED`, naming a day the listing can show           | those days are merged and opened, and the filing is announced                   |
| Recorded out of view     | the outcome is `RECORDED`, naming no day the listing can show          | nothing is read back, and a pencil note says the list does not show those days  |
| Partly recorded          | the outcome is `PARTIAL`                                               | as Recorded, with a pencil note that the list may be incomplete                 |
| Nothing found            | the outcome is `NOTHING_IDENTIFIED`                                    | the text returns to the line, with a pencil note saying so                      |
| Only a question          | the outcome is `ANSWERED`                                              | the text returns to the line, with a pencil note that only spending is filed    |
| Could not be read        | the outcome is `FAILED` or any other, or asking answers 404            | the text returns to the line, with a refusal                                    |
| The newer text stays     | a text returns while the line holds another                            | the line keeps the newer one, and only the note or refusal appears              |
| The days stand           | the read back fails                                                    | the listing's failure is shown, and the days on screen stay                     |
| Sent to sign in          | any call here is refused for want of a session                         | the sign-in page is shown                                                       |

The listing can show a day already on the page. On the first page, it can also show any day the filter's status
and period include.

## Flow

```plantuml
@startuml FileExpensesAsText-Activity
start
note right: any 401 drops the session
:the line held;
:ask the Ledger for the text being read;
if (one is being read?) then (yes)
  :the reading line holds it;
else (none, or the ask failed)
  :the line editable;
  :the Person files a text;
  :the line held;
  :file it with the Ledger;
  if (answered?) then (filed)
    :the line clears;
    :the reading line holds the text;
  elseif (one already being read) then
    :ask the Ledger for the text being read;
    if (found?) then (yes)
      :the reading line holds that one;
    else (none, or the ask failed)
      :the line editable;
      stop
    endif
  else (refused)
    :the refusal beneath the line;
    stop
  endif
endif
repeat
  :wait 5 seconds;
  :ask the Ledger what became of it;
repeat while (still reading, or the ask failed?) is (yes)
->an outcome;
:the reading line goes, the line editable;
if (spending recorded?) then (yes)
  :read the showable days back from the Ledger;
  :open them in the listing;
else (no)
  :the text returns to the line;
endif
:the outcome beneath the line;
stop
@enduml
```

## Components

```plantuml
@startuml C3-FileExpensesAsText-Components
!include <C4/C4_Component>

AddElementTag("page", $bgColor="#A85C74", $fontColor="#FFFFFF", $borderColor="#7E4457")

Person(user, "Person", "Signed in, writing what they spent")
System_Ext(ledger, "Ledger Service", "Files and reads the text, and answers the listing")

Container_Boundary(webApp, "Web App") {
  Component(appShell, "App shell", "React", "The frame every route renders inside", $tags="page")
  Component(routeGuard, "Route guard", "React", "Keeps the page behind an open session")
  Component(expensesPage, "Expenses page", "React", "Holds the listing and reads the filed days back", $tags="page")
  Component(filingLoop, "Filing loop", "React hook", "Files the text and asks after it")
  Component(writeInLine, "Write-in line", "React", "The field, the reading line and the note")
  Component(expenseList, "Expense list", "React", "Cuts the answered page into UTC days")
  Component(daySection, "Day section", "React", "Opens when a filing lands on it")
  Component(expenseDays, "Day helpers", "TypeScript", "Decides which days the listing can show, and merges them in")
  Component(authContext, "Session state", "React context", "Holds who is signed in")
  Component(messagesClient, "Messages client", "TypeScript", "Files, lists and reads texts")
  Component(expensesClient, "Expenses client", "TypeScript", "Calls the listing")
}

Rel_R(user, appShell, "Opens")
Rel_R(appShell, routeGuard, "Renders the matched route through")
Rel_R(routeGuard, expensesPage, "Admits")
Rel_D(routeGuard, authContext, "Asks whether a session is open")
Rel_D(expensesPage, writeInLine, "Renders")
Rel_R(expensesPage, filingLoop, "Runs the filing through")
Rel_D(filingLoop, messagesClient, "Files and asks through")
Rel_D(filingLoop, authContext, "Reports an expired session to")
Rel_D(expensesPage, expenseList, "Renders, naming the days to open")
Rel_D(expenseList, daySection, "Renders one per day")
Rel_R(expensesPage, expenseDays, "Works out what to read back with")
Rel_D(expensesPage, authContext, "Reports an expired session to")
Rel_R(expensesPage, expensesClient, "Reads the filed days back through")
Rel_R(messagesClient, ledger, "Message requests", "HTTPS, same origin")
Rel_R(expensesClient, ledger, "Browse requests", "HTTPS, same origin")
@enduml
```

## References

- [ADR 0014: The web app and the ledger are served from one origin](../../../docs/adr/0014-the-web-app-and-the-ledger-are-served-from-one-origin.md) —
  why the filing carries a CSRF token read from a cookie
- [Accept pending expenses](accept-pending-expenses.md) — what a person does with the entries a filed text
  produced
