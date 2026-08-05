# Agent acting for a user — the ledger's tools (MCP over HTTP)

A language model acting for a user acts on their message here, one tool call per thing the message asks for.
This is the boundary an AI agent reaches the ledger through. Three things cross it: which categories one of the
caller's own groupings holds, a proposed expense, which a human reviews before it becomes one, and a period the
caller wants their spending totalled over.

- **Counterpart:** [the AI Connector Service](../../../../ai-connector-service/docs/contracts/out/ledger-mcp.md),
  acting for the user whose message it was handed
- **Transport:** MCP over Streamable HTTP, stateless, at `/mcp` on the service's own port
- **Schema:** none held in a file — the server publishes each tool's argument schema over the protocol itself,
  and a client reads it by listing the tools

## Operations

| Operation                 | Purpose                                                                                                   | Used by                                                                    |
|---------------------------|-----------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------|
| List the tools            | tells a client which tools exist and what each takes                                                      | the client, to put the tools and their arguments in front of its model     |
| `create_expense_proposal` | records one expense the model read from its caller's message                                              | [Create an expense proposal](../../usecases/create-an-expense-proposal.md) |
| `list_categories`         | answers which categories one of the caller's groupings holds                                              | [List a grouping's categories](../../usecases/list-categories.md)          |
| `summarize_spending`      | records the period the model read from a question about what its caller spent                             | [Summarize spending over a period](../../usecases/summarize-spending.md)   |
| Fetch the signing keys    | publishes the public half of the key tokens are signed with, so a client can verify and follow a rotation | any holder of a token                                                      |

### What `create_expense_proposal` takes

| Argument       | Meaning                                                                                | Required |
|----------------|------------------------------------------------------------------------------------------|----------|
| `category`     | the category's name — one filed under a grouping                                       | yes      |
| `grouping`     | the grouping it is filed under, as `list_categories` was asked for it                  | yes      |
| `description`  | what was bought                                                                        | yes      |
| `merchant`     | who it was bought from — null or blank is none                                         | no       |
| `amount`       | the amount as the message writes it, in the currency's main unit — 7200 for 7200 HUF   | yes      |
| `currencyCode` | ISO 4217, three letters                                                                | yes      |

**There is no identity argument, and no message argument.** Who the proposal is recorded against, and which
message it belongs to, both come off the token and nothing else
([ADR 0007](../../adr/0007-an-mcp-caller-is-identified-by-a-signed-token-not-a-tool-argument.md),
[ADR 0010](../../adr/0010-a-message-reference-rides-the-caller-token-not-the-extraction-request.md)).

### What `create_expense_proposal` answers with

The stored proposal: its id, the category name it was filed under, the description, the merchant, the amount in
the currency's main unit, the currency code, and the instant it was created. It carries no identity — the caller
already knows whose token it sent, and everything returned enters a model's context.

### What `list_categories` takes

| Argument   | Meaning                                        | Required |
|------------|------------------------------------------------|----------|
| `grouping` | the grouping's name, exactly as it was offered | yes      |

### What `list_categories` answers with

Under `grouping`, the grouping the call named; under `categories`, the names of the categories filed under it,
ordered by name. It carries no identity and no stored id.

### What `summarize_spending` takes

| Argument | Meaning                                                    | Required |
|----------|------------------------------------------------------------|----------|
| `from`   | the first day of the period, counted, as `YYYY-MM-DD`      | yes      |
| `to`     | the last day of the period, counted, as `YYYY-MM-DD`       | yes      |

Both days are ISO-8601 calendar dates. A relative phrase — "last week", "since Friday" — is never sent: the
caller works the period out against [the day the turn states](../out/ai-connector.md) and sends two days.

### What `summarize_spending` answers with

Under `from` and `to`, the period that was accepted, as the two days it was stored as. **No amount, no count and
no expense.** What the caller asked about is put in front of the user by
[the turn](../../usecases/handle-incoming-message.md), and never returned here, so a total no model has read is
a total no model can restate.

## Semantics

Every call carries its own token and the server keeps nothing between calls; two calls never share state.

Each proposal, and each period asked about, is stored under the message reference its token carries, which is
what lets the ledger tell the user which message produced what. A proposal call, or a summary call, whose token
carries no readable reference is refused and stores nothing. Listing categories never reads the reference, so a
token carrying none still lists.

A caller reaches only their own categories and their own spending: no tool takes an identity argument, and every
read is scoped to the token's subject.

A grouping and a category are both named, never identified. Which names resolve, and which are refused, is the
use case's rule, not the tool's — [for a proposal](../../usecases/create-an-expense-proposal.md#rules), and
[for a listing](../../usecases/list-categories.md#rules).

A proposal names both: the grouping is resolved first, and the category only under it. Nothing is filed under a
grouping itself.

The amount crosses as written, in the currency's main unit, and is scaled to minor units on this side
([ADR 0011](../../adr/0011-the-amount-is-scaled-to-minor-units-in-the-domain.md)).

- Accepted: digits, at most one dot, at most four decimals; surrounding whitespace is ignored.
- Nothing is rounded, regrouped or converted.
- Refused: any other written form — a comma decimal, grouped digits, a sign, an exponent, a currency symbol.
- Refused: an amount finer than its currency's decimal places.
- Refused: a currency with no minor unit at all.
- Refused: an amount too large to record.
- An absent `amount` is refused rather than read as zero. A deliberate zero is stored.
- The answer states the amount in the units the call spoke.

The proposal tool is not idempotent: the same call made twice stores two proposals, and nothing tells them apart
from two intended ones. A refused call stores nothing, so a corrected retry of it leaves one proposal.

Listing categories stores nothing: a duplicate, a redelivery, or a retry after a timeout whose first attempt
succeeded all answer the same list and leave no row behind.

The summary tool is idempotent in what the user reads, not in what is stored: the same call made twice leaves
two rows, and the turn reports the period once. Two *different* periods in one turn are two blocks, oldest
first.

A period is a period and nothing else. It cannot be narrowed to a category, a grouping or a merchant, and the
summary it produces is the whole ledger over those days.

How a caller authenticates:

- A short-lived RS256 JSON Web Token on the request, issued and validated by this service itself.
- The token names the user as its subject, `ledger-service` as its issuer, `mcp-adapter` as its audience, and
  carries the instant it was issued, the instant it expires, a unique id, and the
  [message reference](../../domain/message-reference.md) of the message being handled.
- Validation checks the signature and the algorithm, that the token is neither expired nor future-dated, the
  issuer, the audience, and that the token's own lifetime does not exceed the configured maximum.
- The signing key comes from a keystore read at startup; its public half is published, unauthenticated, at
  `/.well-known/jwks.json`. A rotation is a new key in the keystore and a restart — a client re-reads the keys
  and needs no change.
- The keystore, its password, the key, and the lifetime are all [configuration](../../configuration.md).
- A token is minted when this service [hands a user's turn to the connector](../out/ai-connector.md), which
  calls back with it while the turn runs. A token outlives the turn it was minted for by design, and nothing
  revokes one early.
- The caller sends its own token per call rather than establishing a session, so a turn making several proposals
  makes several independent calls.

Monitoring endpoints stay reachable without a token. Every other address on the service answers to nobody.

## Failures

| Condition                                                                               | Signal                                                               |
|-----------------------------------------------------------------------------------------|----------------------------------------------------------------------|
| No token, an expired one, a wrong issuer or audience, or one whose lifetime is too long | 401 on the transport, with no tool result and nothing describing why |
| An argument's value is not the type the published schema declares                       | refused against the schema, before the tool runs                     |
| An argument is missing, malformed, or an amount its currency cannot record              | a tool error naming the invalid request and the field at fault       |
| The grouping sent holds no category of the category name sent                           | a tool error naming both, so it can be corrected                     |
| The grouping name is unknown                                                            | a tool error repeating it, so it can be corrected                    |
| A listing's grouping name names a category rather than a grouping                       | a tool error saying so, so it can be corrected                       |
| A day of a period is blank, or is not an ISO-8601 calendar date                         | a tool error naming the day at fault and the value it could not read |
| A period's last day is before its first                                                 | a tool error saying the period ends before it starts                 |
| The token's subject names no stored user                                                | a tool error saying the user is unknown                              |
| The token carries no readable message reference, on a proposal call                     | a tool error saying the proposal could not be created                |
| The token carries no readable message reference, on a summary call                      | a tool error saying the spending could not be summarized             |
| The proposal or the period cannot be stored, or the categories cannot be read           | a tool error saying so, naming no table, constraint or stack frame   |
| Anything else                                                                           | a tool error saying the call could not be completed                  |

A failure inside the tool is a successful call carrying an error result, never an exception on the transport.
Authentication is the exception: it never reaches the tool at all, so a model never reads why it was refused.

Every rejection is logged with the kind of failure, and with neither the arguments nor the token. A call's
arguments reach the log only at debug level.

## Compatibility

The caller is a language model: it picks a tool out of the published list by its name and description, and fills
each argument from the description published beside it. Both are part of the contract — rewording one changes
what arrives, with no schema to compare against and nothing failing at build time.

A tool added here reaches a client when it next reads the published list, and a client that has already read one
goes on offering only what it read.

Adding an optional argument costs a client nothing. For a client outside this repository, renaming one, or
making an optional one required, is a new tool rather than an edit. Inside it, the tools and their one caller
ship together, so an argument is renamed or made required in place.

A rename in place is only safe while the two ship together. A client that read the list before the rename goes
on sending the old argument name, and every call of that tool fails until it restarts and reads the list again.
Deploying the two independently makes a rename a new tool instead.

What the ledger hands its own tool through the token costs a client nothing either: the caller forwards the
token untouched, so a claim added there is neither read nor rewritten on the way
([ADR 0010](../../adr/0010-a-message-reference-rides-the-caller-token-not-the-extraction-request.md)). A caller
that mints its own tokens instead would have to carry that claim, which the proposal and summary tools both
refuse a call without.

Moving to an identity provider outside this service means the tokens are minted and the keys published
elsewhere. Callers change where they get a token; the tools and their arguments do not change.

The endpoint can be switched off entirely, which makes every call to it fail rather than answer — see
[configuration](../../configuration.md).
