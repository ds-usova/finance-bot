# Authenticated user id

A caller the service has already established the identity of, named by the external identity the delivering
platform knows them by. It is the answer to *who is this request acting as*, carried inward so a use case never
has to trust a value the request supplied.

## Invariants

- An identity is present and not blank.
- An identity is opaque text, carried on unchanged and never interpreted.
- Only the part of the service that checks who the caller is may build one — everywhere else can receive one but
  cannot create one out of an arbitrary string. An architecture rule enforces this
  ([Architecture Enforcement](../conventions/architecture.md#architecture-enforcement)).

## Made of / held by

A single external identity.

- [User](user.md) — the stored person that identity resolves to.
- [Create an expense proposal](../usecases/create-an-expense-proposal.md) — records spending against the caller
  this names.
- [MCP — the create expense proposal tool](../contracts/in/mcp.md) — where the identity is read from, and why it
  is not a request field ([ADR 0007](../adr/0007-an-mcp-caller-is-identified-by-a-signed-token-not-a-tool-argument.md)).
