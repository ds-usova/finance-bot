# Spec: File Expenses as Text from the Web

**Format:** 2
**Approved:** ds-usova, 2026-10-02, 66b49c17db67
**Affected Modules:** `ledger-service`, `web-app`

## Objective

Today spending reaches the ledger only as a Telegram message. A person already on the expenses page has to leave
it, write to the bot, and come back to accept what it proposed.

This change lets them write the same text on the page. The ledger stores it and answers at once. It then reads the
text the way it reads a Telegram message, off the request. The page asks every few seconds what became of it.
Spending found in it appears as pending entries, accepted as any other is. Text in which nothing was found comes
back to the person.

Discarding a pending entry from the page is a later task (DF25).

## Requirements

- **RQ01:** A signed-in person can write what they spent on the expenses page and file it, without Telegram.
- **RQ02:** Filing is answered at once. The text is read afterwards, and the person never waits on the reading.
- **RQ03:** Until its reading ends, the page shows the filed text as being read, across a reload too.
- **RQ04:** Spending the text names appears on the page as pending entries, accepted the way any other is.
- **RQ05:** Text in which nothing to file was found comes back to the person, saying so.
- **RQ06:** A filed text always ends with an outcome, a failed or interrupted reading included.
- **RQ07:** A person files, and learns the outcome of, only their own texts.
- **RQ08:** A person has at most one text being read at a time, and each filed text is read exactly once.

## Acceptance Scenarios

### Filing a text

- **AC01:** a text is filed
  - Given: the person is signed in, with no text being read
  - When: they file `coffee 3.40, taxi home 14`
  - Then: the response is 202 with the message's id and `READING`, the text is stored waiting, and a reader is
    asked to look for work at once
  - Proves: RQ01, RQ02

- **AC02:** the text is unusable
  - Given: the person is signed in
  - When: they file a blank text, or one longer than 1000 characters
  - Then: the response is 400 naming `text`, and nothing is stored
  - Proves: RQ01

- **AC03:** nobody is signed in
  - Given: the request carries no session cookie, or one this service did not sign
  - When: it files a text
  - Then: the response is 401, and nothing is stored
  - Proves: RQ07

- **AC04:** the write carries no CSRF token
  - Given: a valid session and no CSRF header
  - When: it files a text
  - Then: the response is 403, and nothing is stored
  - Proves: RQ07

- **AC05:** the session outlives its user row
  - Given: a valid session whose user row no longer exists
  - When: it files a text
  - Then: the response is 404, and nothing is stored
  - Proves: RQ01

- **AC06:** the store is unavailable
  - Given: the person is signed in and the store refuses writes
  - When: they file a text
  - Then: the response is 503, naming no table or statement, and nothing is stored
  - Proves: RQ06

- **AC07:** a text is already being read
  - Given: the person has a text waiting or being read
  - When: they file another, or two filings of theirs arrive at the same moment
  - Then: exactly one is stored, and every other answers 429
  - Proves: RQ08

### Asking what became of it

- **AC08:** the text is still being read
  - Given: a text the person filed, not yet read to the end
  - When: the page asks for it by id
  - Then: the response is 200 with `READING`
  - Proves: RQ03

- **AC09:** the reading has ended with spending
  - Given: a text the person filed, read to the end, whose entries were stored on one UTC day
  - When: the page asks for it by id
  - Then: the response is 200 with `DONE`, the outcome, the text as filed, and that one day
  - Proves: RQ04

- **AC10:** the reading has ended with nothing
  - Given: a text the person filed, read to the end, with no entry stored under it
  - When: the page asks for it by id
  - Then: the response is 200 with `DONE`, the outcome, the text, and no days
  - Proves: RQ05, RQ06

- **AC11:** the id is not the caller's
  - Given: an id naming another person's text, or nothing at all
  - When: the page asks for it
  - Then: the response is 404 either way, and nothing tells the two apart
  - Proves: RQ07

- **AC12:** the page asks which text is being read
  - Given: the person has one text waiting or being read, and another person has one too
  - When: the page asks for the person's texts being read
  - Then: the response is 200 listing only the person's one text, and an empty list when they have none
  - Proves: RQ03, RQ07

### Reading a filed text

- **AC13:** the text names spending
  - Given: a waiting text `coffee 3.40, taxi home 14`
  - When: it is read
  - Then: two pending entries are stored under that message, the outcome is `RECORDED`, and nothing is sent to
    Telegram
  - Proves: RQ02, RQ04

- **AC14:** nothing to file is found
  - Given: a waiting text `paid back anna for the concert`
  - When: it is read
  - Then: no pending entry is stored under that message, and the outcome is `NOTHING_IDENTIFIED`
  - Proves: RQ05

- **AC15:** the text only asks a question
  - Given: a waiting text `how much did I spend this week`
  - When: it is read
  - Then: no pending entry is stored under that message, the period it asked about is not kept, and the outcome
    is `ANSWERED`
  - Proves: RQ05

- **AC16:** the reading fails before anything is stored
  - Given: a waiting text, and a connector that refuses the turn or cannot be reached
  - When: it is read
  - Then: the outcome is `FAILED`, and the text is never read again
  - Proves: RQ06

- **AC17:** the reading fails after some spending is stored
  - Given: a waiting text naming three expenses, and a connector that fails after storing one
  - When: it is read
  - Then: the stored entry stays pending, and the outcome is `PARTIAL`
  - Proves: RQ04, RQ06

- **AC18:** the person cannot be prepared for the turn
  - Given: a waiting text whose person's groupings cannot be read, or lack the catch-all
  - When: it is read
  - Then: the connector is never called, and the outcome is `FAILED`
  - Proves: RQ06

- **AC19:** one text is read once
  - Given: one waiting text, and two readers looking for work at the same moment
  - When: both look
  - Then: exactly one of them reads it, and the other finds nothing to read
  - Proves: RQ08

- **AC20:** a reading is interrupted after storing spending
  - Given: a text left `READING` past the turn's longest possible life, with one entry under it, pending or
    already accepted
  - When: the reader's sweep finds it
  - Then: the outcome is `PARTIAL`, and the text is not read again
  - Proves: RQ06, RQ08

- **AC21:** a reading is interrupted before storing anything
  - Given: a text left `READING` past the turn's longest possible life, with nothing under it
  - When: the reader's sweep finds it
  - Then: the outcome is `FAILED`, and the text is not read again
  - Proves: RQ06, RQ08

- **AC22:** the service stops before a text is read
  - Given: a text stored waiting when the service stopped
  - When: the service starts again
  - Then: the text is read, and its outcome is reached as AC13 to AC18 say
  - Proves: RQ02, RQ06

- **AC33:** entries accepted before the outcome still count
  - Given: a text whose two entries were accepted from another tab before its turn settled
  - When: the turn settles, or the sweep settles it
  - Then: the outcome is `RECORDED` or `PARTIAL`, never `NOTHING_IDENTIFIED` or `FAILED`
  - Proves: RQ04, RQ06

- **AC34:** a recent reading is left alone
  - Given: a text handed to the connector less recently than the turn's longest possible life
  - When: the sweep runs
  - Then: the text stays `READING`
  - Proves: RQ08

- **AC35:** a turn ends after its text was swept
  - Given: a text the sweep already settled
  - When: its turn ends and tries to settle it
  - Then: the outcome the sweep stored stands
  - Proves: RQ08

- **AC36:** one failed pass does not stop the reader
  - Given: a pass of the reader or the sweep in which the store fails
  - When: the next interval comes
  - Then: the reader looks for work again, and a waiting text is still read
  - Proves: RQ06

### The page

- **AC23:** a text is filed from the page
  - Given: the person has written a text in the write-in line
  - When: they file it, by the control or by Enter outside composition
  - Then: the line clears and turns read-only, keeping focus, and a reading line holding the text stands beneath
    it
  - Proves: RQ01, RQ03, RQ08

- **AC24:** the page is opened while a text is being read
  - Given: the person has a text being read, and reloads the page or returns to it
  - When: the page loads
  - Then: the line is read-only until the page learns of that text, the reading line then holds it, and the page
    asks after it as AC23's does
  - Proves: RQ03, RQ08

- **AC37:** the page cannot learn what is being read
  - Given: the page loading
  - When: asking for the person's text being read answers 503, or the network fails
  - Then: the line becomes editable, and no reading line appears
  - Proves: RQ03

- **AC38:** a filing meets a text the page did not know of
  - Given: a text being read that was filed from another tab, and a new text in this page's line
  - When: filing answers 429
  - Then: the new text stays in the line, the page asks for the text being read, and a reading line holding that
    one appears and is asked after
  - Proves: RQ03, RQ08

- **AC25:** the reading ends with spending
  - Given: a reading line on the page, and a listing on its first page whose filter includes today
  - When: the next answer is `RECORDED`
  - Then: the reading line goes, the line is editable, focus stays in it, and each day the answer names is read
    back and opened, showing its new entries pending and unticked
  - Proves: RQ04

- **AC39:** the entries land outside the listing on screen
  - Given: a reading line, and a listing whose filter or page shows none of the days the answer will name
  - When: the next answer is `RECORDED`
  - Then: no day is merged in, and a pencil note beneath the line says the list on screen does not show them
  - Proves: RQ04

- **AC26:** the reading ends with part of the spending
  - Given: a reading line on the page
  - When: the next answer is `PARTIAL`
  - Then: the days are read back as AC25 says, and the person is told the list may be incomplete
  - Proves: RQ04, RQ06

- **AC27:** nothing to file was found
  - Given: a reading line on the page
  - When: the next answer is `NOTHING_IDENTIFIED`
  - Then: the reading line goes, the text returns to the editable line, and a pencil note beneath it says nothing
    to file was found
  - Proves: RQ05

- **AC28:** the text only asked a question
  - Given: a reading line on the page
  - When: the next answer is `ANSWERED`
  - Then: the text returns as AC27 says, and the note says only spending is filed here
  - Proves: RQ05

- **AC29:** the reading failed
  - Given: a reading line on the page
  - When: the next answer is `FAILED`, or asking answers 404
  - Then: the reading line goes, the text returns to the editable line, and a refusal says it could not be read
  - Proves: RQ06

- **AC30:** asking fails for a while
  - Given: a reading line on the page
  - When: asking answers 503, or the network fails
  - Then: the reading line stands, and the page asks again 5 seconds later
  - Proves: RQ03

- **AC31:** filing is refused
  - Given: a text in the write-in line
  - When: filing answers 400, 403, 404 or 503
  - Then: the text stays in the line, the refusal is shown, and no reading line appears
  - Proves: RQ06

- **AC40:** the field keeps to its rules
  - Given: the write-in line, editable
  - When: the person presses Shift+Enter, types past 1000 characters, or leaves only spaces in it
  - Then: Shift+Enter breaks a line, the field takes nothing past 1000, and the file control is disabled while
    the text is only spaces
  - Proves: RQ01

- **AC32:** the session expires
  - Given: a text being filed, a reading line on the page, or the page loading
  - When: filing, asking by id, or asking for the text being read answers 401
  - Then: the page drops to anonymous and sends the person to `/login`
  - Proves: RQ07

## Decisions

- **DN01:** How often does the page ask what became of a filed text?
  - Answer: Every 5 seconds, from the moment filing is answered until an outcome other than `READING` arrives.
  - Basis: decided (user, 2026-10-01)

- **DN02:** Is the text read while the person waits, or after they are answered?
  - Answer: After. Filing stores the text and answers 202 at once. Reading happens off the request.
  - Basis: decided (user, 2026-10-01)

- **DN03:** Where does the person write the text?
  - Answer: An inline write-in line on the expenses page, between the filters and the days. The reading line and
    every note sit directly beneath it.
  - Basis: decided (user, 2026-10-01)

- **DN04:** How does a text in which nothing was found come back to the person?
  - Answer: It returns to the write-in line, with a pencil note beneath it saying nothing to file was found. It
    is not printed as a refusal.
  - Basis: decided (user, 2026-10-01)

- **DN05:** What does the page do with a text that only asked a question?
  - Answer: The same as DN04, with a note that only spending is filed here. Showing totals on the page is a later
    change (DF26).
  - Basis: decided (user, 2026-10-01)

- **DN06:** Does a text filed from the page also produce a report in Telegram?
  - Answer: No. Nothing is sent to Telegram for it.
  - Basis: decided (user, 2026-10-01)

- **DN07:** What happens to a text left `READING` — the service stopped mid-reading, or settling it failed?
  - Answer: A periodic sweep settles it by what was stored, once the turn's longest possible life has passed:
    any pending entry under it means `PARTIAL`, none means `FAILED`. It is never read again.
  - Basis: decided (user, 2026-10-01)

- **DN08:** Does a text still being read survive a reload of the page, or leaving it?
  - Answer: Yes. On load the page asks for the person's text being read, and resumes asking after it. An outcome
    reached while the page was closed is not shown.
  - Basis: decided (user, 2026-10-01)

- **DN09:** How long may a filed text be?
  - Answer: 1000 characters. The write-in line takes no more.
  - Basis: decided (user, 2026-10-01)

- **DN10:** How does a person get rid of a wrong pending entry a web text produced?
  - Answer: Not in this change. Discarding ticked pending entries from the page is filed as a later task (DF25).
  - Basis: decided (user, 2026-10-01)

- **DN11:** Is filing the same text again after a lost answer guarded against?
  - Answer: No. A second filing is a second text. An idempotency key is filed as a later change (DF24).
  - Basis: decided (user, 2026-10-01)

- **DN12:** How does the page learn which day to read back?
  - Answer: The answer to asking carries the UTC days the message's entries were stored on.
  - Basis: decided (user, 2026-10-01)

- **DN13:** How do the new pending entries appear?
  - Answer: Each day they landed on is opened. Nothing is ticked for the person.
  - Basis: decided (user, 2026-10-01)

- **DN14:** Can a second text be filed while the first is being read?
  - Answer: No. One text at a time.
  - Basis: decided (user, 2026-10-01)

- **DN15:** How many texts may a person have waiting or being read in the ledger?
  - Answer: One. Another filing answers 429 until it is read.
  - Basis: decided (user, 2026-10-01)

- **DN16:** What happens to a returning text if the line already holds a new one?
  - Answer: It cannot: the write-in line is disabled, not just its control, while a text is being read.
  - Basis: decided (user, 2026-10-01)

- **DN17:** How tall are the write-in line and the reading line?
  - Answer: The write-in line grows from 3 to 8 lines, then scrolls. The reading line shows at most 3 lines of the
    text.
  - Basis: decided (user, 2026-10-01)

- **DN18:** Is the keyboard hint shown on a touch device?
  - Answer: No. It shows only where the pointer is fine.
  - Basis: decided (user, 2026-10-01)

- **DN19:** What do the page's new strings say?
  - Answer: The wording in [the write-in line](the-write-in-line.md#wording).
  - Basis: decided (user, 2026-10-01)

- **DN20:** Does an entry already accepted count as stored under its text? (widens DN07's count)
  - Answer: Yes. The outcome and the sweep count every entry under the text, pending or accepted. The Telegram
    turn's count is unchanged.
  - Basis: decided (user, 2026-10-01)

- **DN21:** What is the line while the page asks, on load, for the text being read?
  - Answer: Read-only until the answer arrives. A failed ask leaves it editable, and DN15's 429 catches a text
    that was being read after all.
  - Basis: decided (user, 2026-10-01)

- **DN22:** What does the page do when filing answers 429 with no reading line on screen?
  - Answer: It asks for the text being read and shows it as a reading line, asking after it. The new text stays
    in the line.
  - Basis: decided (user, 2026-10-01)

- **DN23:** How is the line held while a text is filed or read, and where is focus? (refines DN16)
  - Answer: Read-only rather than disabled, from the moment it is filed, so focus stays in it. The text returns
    to the same place.
  - Basis: decided (user, 2026-10-01)

- **DN24:** What is the person told when the entries land outside the listing on screen?
  - Answer: A pencil note beneath the line. The filter and the page are left as they are.
  - Basis: decided (user, 2026-10-01)

- **DN25:** After a 429, what does the page do when asking for the text being read finds none, or fails?
  - Answer: The line becomes editable again, holding the new text, with nothing beneath it. The "already
    reading" wording is dropped.
  - Basis: decided (user, 2026-10-02)

- **DN26:** Which note does a `PARTIAL` outcome get when none of its days can be shown?
  - Answer: The note that the list may be incomplete.
  - Basis: decided (user, 2026-10-02)

- **DN27:** A text resumed behind a 429 comes back with its text returned. What happens to the new text already
  in the line?
  - Answer: The new text stays in the line. Only the outcome's note or refusal appears beneath it.
  - Basis: decided (user, 2026-10-02)

## Design Artifacts

- [Reading a written message](reading-a-written-message.md) — who stores, claims, reads, sweeps and answers a
  filed text, and the states it passes through.
- [REST API update](rest-api-update.md) — the three operations, their fields and statuses.
- [The write-in line](the-write-in-line.md) — what the page decides from loading to outcome, and its wording.
