# Waves

How a pipeline turns a set of eligible steps into parallel sub-agents, and who verifies the result. Read by
`implement-plan-module` for its red and green stages; the same rules apply to any stage that spawns more than one
step agent into one module.

## Bundling

**Bundle by grouping and layer, not by class.** A grouping is the source grouping the module conventions name —
a package, a directory, a feature folder. Steps whose target classes share a grouping *and* a layer go to one
sub-agent: the classes of one grouping are each other's context. The layer half of the key keeps the agent types
intact where a grouping holds both (a pure mapper beside its adapter's slice test).

A bundle starts only when every step in it is eligible; leave a step out rather than hold the bundle for it. The
bundled agent **reports per step ID**, and each is ticked separately.

**Never hand one production class to two parallel agents.** Two classes in one grouping routinely pull on a
third; the bundle gives that third one owner.

## The cap

The module conventions' **Parallelism** section sets how many step agents run at once. Spawn up to the cap and
queue the rest, launching a queued bundle as a running one finishes. A missing or silent section means no cap.

**When the cap forces a choice, keep one grouping in one wave.** Two eligible bundles from the same grouping and
different layers write beside each other; take bundles from different groupings first, and put the second of a
pair in the next wave. Among the rest, take them in the order the scheduler gives — it ranks by longest remaining
dependency chain, which is what sets the phase's wall time.

## Launching a wave

A wave is launched as [`sub-agents.md`](sub-agents.md) says for running several agents at once — and which shape
that is depends on whether the pipeline runs as a session or as a sub-agent, since only the first can block on a
spawn. Bundles spawned and awaited one at a time are not a wave; they are the phase run serially at the wave's
cost.

**A pipeline running as a sub-agent takes the parallel shape of the two that file offers**, and hands the wave
back.

## Who verifies a wave

**Who runs the guardrail depends on how many agents share a source set.**

- **One bundle in a module this wave** — the sub-agent verifies itself and reports the result.
- **More than one** — the sub-agents **do not run tests at all**. Say so in the prompt: write the files, report,
  verify nothing. The orchestrator runs the module's suite **once** when the wave is done, maps each failure back
  to a step by its test class name, and re-delegates only what actually failed.

Never let an agent wait out or work around a compile error in a file it does not own. That is the other agent's
work in progress, and the wave's single verification is where it resolves.

## Ticking

Tick each item as its sub-agent reports success and the wave's verification confirms it. If one reports a
blocker, leave the item unchecked, record the blocker, and let the rest of the wave continue: one failed step does
not stop the stage, but the stage is complete only when every item is ticked or recorded as blocked. A blocked
step's dependents are not spawned; they are recorded as blocked by that dependency.
