# Agent acting for a user — the expense proposal tool (MCP over HTTP)

A language model acting for a user records the spending in their message here, one tool call per expense. This
is the boundary an AI agent reaches the ledger through; the only thing it can do across it is propose an
expense, which a human reviews before it becomes one.

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
| Fetch the signing keys    | publishes the public half of the key tokens are signed with, so a client can verify and follow a rotation | any holder of a token                                                      |

### What the tool takes

| Argument           | Meaning                                                                  | Required |
|--------------------|--------------------------------------------------------------------------|----------|
| `category`         | the category's name — one filed under a grouping, never a grouping       | yes      |
| `parentCategory`   | the grouping's name — only to break a tie between categories sharing one | no       |
| `description`      | what was bought                                                          | yes      |
| `merchant`         | who it was bought from — null or blank is none                           | no       |
| `amountMinorUnits` | the amount in the currency's minor units — 12.50 EUR is 1250             | yes      |
| `currencyCode`     | ISO 4217, three letters                                                  | yes      |

**There is no identity argument, and no message argument.** Who the proposal is recorded against, and which
message it belongs to, both come off the token and nothing else
([ADR 0007](../../adr/0007-an-mcp-caller-is-identified-by-a-signed-token-not-a-tool-argument.md),
[ADR 0010](../../adr/0010-a-message-reference-rides-the-caller-token-not-the-extraction-request.md)).

### What the tool answers with

The stored proposal: its id, the category name it was filed under, the description, the merchant, the amount in
minor units, the currency code, and the instant it was created. It carries no identity — the caller already
knows whose token it sent, and everything returned enters a model's context.

## Semantics

Every call carries its own token and the server keeps nothing between calls; two calls never share state.

Each proposal is stored under the message reference its token carries, which is what lets the ledger tell the
user which message produced what. A call whose token carries no readable reference is refused, and stores
nothing.

A category is named, not identified. Which names resolve, and which are refused, is
[the use case's rule](../../usecases/create-an-expense-proposal.md#rules), not the tool's.

An absent `amountMinorUnits` is refused rather than read as zero. A deliberate zero is stored.

The tool is not idempotent: the same call made twice stores two proposals, and nothing tells them apart from two
intended ones. A refused call stores nothing, so a corrected retry of it leaves one proposal.

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
| An argument's value cannot be read as the type the schema declares                      | the protocol's own binding failure, before the tool runs             |
| An argument is missing or unusable                                                      | a tool error naming the invalid request and the field at fault       |
| The category name is unknown, names a grouping, or matches several                      | a tool error carrying what to retry with                             |
| The token's subject names no stored user                                                | a tool error saying the user is unknown                              |
| The token carries no message reference, or one that cannot be read                      | a tool error saying the proposal could not be created                |
| The proposal cannot be stored                                                           | a tool error saying so, naming no table, constraint or stack frame   |
| Anything else                                                                           | a tool error saying the proposal could not be created                |

A failure inside the tool is a successful call carrying an error result, never an exception on the transport.
Authentication is the exception: it never reaches the tool at all, so a model never reads why it was refused.

Every rejection is logged with the kind of failure, and with neither the arguments nor the token. A call's
arguments reach the log only at debug level.

## Compatibility

The caller is a language model: it picks this tool out of the published list by its name and description, and
fills each argument from the description published beside it. Both are part of the contract — rewording one
changes what arrives, with no schema to compare against and nothing failing at build time.

Adding an optional argument costs a client nothing. Renaming one, or making an optional one required, is a new
tool rather than an edit.

What the ledger hands its own tool through the token costs a client nothing either: the caller forwards the
token untouched, so a claim added there is neither read nor rewritten on the way
([ADR 0010](../../adr/0010-a-message-reference-rides-the-caller-token-not-the-extraction-request.md)). A caller
that mints its own tokens instead would have to carry that claim, which the tool refuses a call without.

Moving to an identity provider outside this service means the tokens are minted and the keys published
elsewhere. Callers change where they get a token; the tool and its arguments do not change.

The endpoint can be switched off entirely, which makes every call to it fail rather than answer — see
[configuration](../../configuration.md).
