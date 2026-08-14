# Review: One Context for Every System Test

**No open bugs, 1 manual check.** Every entry is `ledger-service`.

## Manual test

**[ ] The suite stays green over repeated runs**

- **Given** the fifteen system test classes now share one booted application, one poll loop and one database
  whose rows are never cleared between classes
- **When** `tools/agent-test/agent-test.sh --module ledger-service --all` is run several times over
- **Then** every run reports 1164 passed

> Class order is what a shared context exposes: JUnit's order is deterministic for a given set of classes but
> not one anybody chose, and a single green run says the current order works rather than that every order does.
> The rework removed the two known couplings — a scenario's update id and Telegram user are now its own, and
> replies are read back filtered to the scenario's conversation. What no test can see is a timing-dependent one:
> a turn from the previous test still in flight when the next starts. The filter is what would catch it, and a
> run on a slower machine is what would provoke it.
