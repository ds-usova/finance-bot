# Review: One Capture Context

**No open bugs, 1 manual check.** Every entry is `ledger-service`.

## Manual test

**[ ] The capture classes stay green whatever order they run in**

- **Given** the four `@CdcCaptureTest` classes now share one engine on one slot, and
  `RecoverSlotSystemTest` drives that slot to `lost` before repairing it
- **When** `tools/agent-test/agent-test.sh --module ledger-service --all` is run several times over
- **Then** every run reports 1164 passed

> The order was deliberately left unpinned, so which classes run before the repair is JUnit's choice rather than
> anyone's. The repair is complete — `RecoverSlotSystemTest`'s own last assertion is `STREAMING` on a rebuilt
> slot, and a change made after it reaches the stream. What no single run proves is the other direction: a
> recovered slot resumes from the current write-ahead position, so a change written before the recovery and not
> yet published is never captured. Inside one context that window belongs to the class that opened it, and a run
> on a slower machine is what would widen it far enough to reach another class.
