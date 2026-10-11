# Backlog

**Format:** 2
**Last ids:** BB05 · BR00 · BT03 · BP00

Work still open from reviews: one row per bug not yet fixed, one row per `R` row whose owner still says
`open`. The owning `review/findings.md` holds the finding; this file only points at it. The `#` column is the
id to name when planning a bug fix or a rework — `B<n>` for a bug, `C<n>` for a refactoring candidate — and it
never changes once given.

## Bugs

| # | Raised by | Module | What | Where |
|---|-----------|--------|------|-------|

## Refactoring candidates

| #  | Raised by | Module                 | What                                                                                                  | Where                                                                                       |
|----|-----------|------------------------|-------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| C2 | task 32   | `ai-connector-service` | keep a hand-entered expense as a row of its own, with its own retention                               | [findings](implemented/32-the-connector-learns-what-became-of-a-message/review/findings.md) |
| C6 | task 40   | `ledger-service`       | loosen the log assertion pinning an SLF4J overload, and restore the class's own three-argument idiom  | [findings](implemented/40-set-a-default-currency/review/findings.md)                        |

## Open

### BB01 · `web-app` — asking after a filed text never stops on a 400, a 429 or a malformed answer

- **Kind** bug · **Priority** normal · **Size** S
- **Raised by** user, 2026-10-11
- **What** after a text is filed, the page asks after it every 5 seconds and keeps asking through a 400, a 429
  or an answer it cannot parse. A broken answer means it asks forever. The spec keeps asking only on a 503 or a
  network failure · `web-app/src/pages/useWrittenMessage.ts`
- **Why** found while archiving task 47, by reading the code; unverified by a test

### BB02 · `web-app` — a filed text is announced as filed while a category filter hides its entries

- **Kind** bug · **Priority** normal · **Size** S
- **Raised by** user, 2026-10-11
- **What** the rule for which days the listing can show checks the status and the period, never the category.
  Under a category filter a filed day is read back, holds no matching entry, and the filing is still announced
  as filed rather than out of view · `listingCanShow` in `web-app/src/components/expenseDays.ts`
- **Why** found while archiving task 47, by reading the code; unverified by a test

### BB03 · `web-app` — a filing answered with an empty or non-JSON body shows the browser's own error text

- **Kind** bug · **Priority** normal · **Size** S
- **Raised by** user, 2026-10-11
- **What** a filing answered with an empty body, or one that is not JSON, puts the browser's own error text
  beneath the write-in line instead of the line's fixed wording, unlike every other surface's malformed answer ·
  `fileMessage` in `web-app/src/api/messages.ts`
- **Why** found while archiving task 47, by reading the code; unverified by a test

### BB04 · `openapi` — the written-message 429 and 404 examples do not match the messages served

- **Kind** bug · **Priority** low · **Size** S
- **Raised by** user, 2026-10-11
- **What** the 429 example says "a text of yours is already waiting to be read", the ledger answers "user <id>
  already has a written message waiting or being read". The 404 example says "no message of yours carries that
  id", the ledger answers "no written message <id> for the caller" · `openapi/paths/messages.yaml`,
  `openapi/paths/message.yaml`
- **Why** found while archiving task 47, by comparing the schema with the code

### BB05 · `ledger-service` — the web turn's log lines print the message id as a record, not `web:<id>`

- **Kind** bug · **Priority** low · **Size** S
- **Raised by** user, 2026-10-11
- **What** the claim and settle lines print `IncomingMessageId[value=web:42]` where the plan asked for `web:42`.
  The sweep's warning already prints `web:42`. The Telegram turn logs the same record form ·
  `HandleWrittenMessageUseCase`, `ExtractionTurn`
- **Why** found while archiving task 47, by reading the code
