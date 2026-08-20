# Authenticated user id

A caller the service has already established the identity of, named by the id the ledger itself stores them
under. It is carried inward, so a use case never has to trust a value the request supplied.

The identity the delivering platform knows a person by is read once, at first recognition. An authenticated
request never carries it afterwards.

## Invariants

| Part     | Bound                             |
|----------|-----------------------------------|
| `userId` | a stored user's id, `1` or higher |

- Built from a token's subject, read as a decimal number.
- A subject that is absent, blank, not a number, or a number too large to hold is refused, never rounded and
  never passed on.
- Only the part of the service that checks who the caller is may build one — everywhere else can receive one but
  cannot create one out of an arbitrary value. An architecture rule enforces this
  ([Architecture Enforcement](../conventions/architecture.md#architecture-enforcement)).

## Made of / held by

A single stored user id.

- [The change stream](../contracts/out/change-stream.md) — the id every published fact names a person by.
- [User](user.md) — the stored person it names, and where the platform's own identity lives instead.
- [Read the current session](../usecases/read-the-current-session.md) — resolves it back to that person.
- [Create an expense proposal](../usecases/create-an-expense-proposal.md) — records spending against the caller
  it names.
- [MCP — the create expense proposal tool](../contracts/in/mcp.md) — where the identity is read from, and why it
  is not a request field ([ADR 0007](../adr/0007-an-mcp-caller-is-identified-by-a-signed-token-not-a-tool-argument.md)).
