# REST API Update

Three operations join the shared browser API under a new `messages` tag. Each joins the browser's filter chain
as an operation that needs a session.

| Operation                            | Request                    | Response                                             |
|--------------------------------------|----------------------------|------------------------------------------------------|
| `POST /api/v1/messages`              | `text`                     | 202 with a `WrittenMessage`, `state: READING`        |
| `GET /api/v1/messages/{id}`          | the id the filing answered | 200 with a `WrittenMessage`                          |
| `GET /api/v1/messages?state=READING` | `state`, `READING` only    | 200 with a list of `WrittenMessage`, empty or of one |

| `WrittenMessage` field | Meaning                                                                                             |
|------------------------|-----------------------------------------------------------------------------------------------------|
| `id`                   | the filed message, unique across every person's                                                     |
| `state`                | `READING` or `DONE`                                                                                 |
| `text`                 | the text as filed                                                                                   |
| `outcome`              | `RECORDED`, `PARTIAL`, `NOTHING_IDENTIFIED`, `ANSWERED` or `FAILED`; `null` until `DONE` (DF36)     |
| `days`                 | the UTC dates its entries were stored on, oldest first; empty until `DONE`, and when none (DN12)    |

| Request field | Bound                                                    |
|---------------|----------------------------------------------------------|
| `text`        | 1 to 1000 UTF-16 code units, not blank or only whitespace |

## `POST /api/v1/messages`

| Status | When                                                         |
|--------|--------------------------------------------------------------|
| 202    | the text is stored, waiting to be read                       |
| 400    | `text` is absent, blank or too long, or the body is not JSON |
| 401    | no session, refused before the operation is reached          |
| 403    | no CSRF token                                                |
| 404    | the session outlives its user row                            |
| 429    | the person already has a text waiting or being read          |
| 503    | the store refused the write                                  |

## `GET /api/v1/messages/{id}`

| Status | When                                                         |
|--------|--------------------------------------------------------------|
| 200    | the caller filed that message                                |
| 400    | `id` is not a positive whole number                          |
| 401    | no session                                                   |
| 404    | no message of the caller's has that id, whoever else's it is |
| 503    | the store refused the read                                   |

## `GET /api/v1/messages?state=READING`

| Status | When                                     |
|--------|------------------------------------------|
| 200    | the caller's texts waiting or being read |
| 400    | `state` is absent or anything else       |
| 401    | no session                               |
| 404    | the session outlives its user row (DF35) |
| 503    | the store refused the read               |
