# Stabilizing

What a stabilize step may do and may not, wherever one runs: a plan's **Stabilization** group, a fix file's
`stabilize` step, a rework's `stabilize` step. The workflow that owns the step says which items it holds and
what proves it; this file says how each item is done. Read it before writing one and before applying one.

## What it is for

A stabilize step exists so the step after it can be written — a red test against a stub, a call site against a
changed signature, a schema no code path reads yet. It carries the tree back to compiling and **nothing more**.
A stabilize that changes what anything already does is a green step in disguise, and is refused as one.

## The edits

| The change             | What is done                                                                                                                                       |
|------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------|
| a method that must exist | a stub: the signature, and a body returning the minimum — `null`, `0`, `false`, an empty value — with a short inline **intent comment** saying what the method is supposed to do; a documentation comment alone does not carry implementation intent |
| a signature that changes | keep every line of existing logic; add a `TODO` at the insertion point naming the work; add the minimal return or argument that compiles. Existing behaviour is never replaced or stubbed out |
| a call site the change broke | the immediate fix that compiles, under the row above; a call site the step did not name is fixed the same way, and the widening is reported |
| a contract artifact — a schema, a migration, a message shape | written verbatim from what names it. Never edited by a red or green step: a test that fails on a missing table or property, rather than on an assertion, was written against an unstabilized tree |
| configuration the change needs — a property, a default, a client's address, a schedule | added here, so no later step invents one on the fly |
| shared test infrastructure — a fixture, a builder, a composed annotation, a container more than one later step needs | written once, here; a red or green step adds nothing shared. Infrastructure that only proves itself at runtime ships with a throwaway test that boots it, where the module's testing conventions ask for one |

**A stub's intent comment and a `TODO` name the work, never the step that owes it.** Nothing finds its work by
searching the tree for a step id, and a module whose conventions ban citing a plan step in a comment fails the
build on one. The one place a step id belongs is a disabled test's reason.

## Tests that stop compiling or would now fail

**A test method never disappears from the run.** Whatever is done to it, the runner still reports it —
**disabled**, by the mechanism the module's testing conventions give, so it counts as *skipped* rather than
vanishing:

- **it compiles but would now fail** — disable it where it stands, body intact;
- **it cannot compile** — keep the method, disable it, and comment out only the lines inside it. The husk stays
  *within* the method; the method is never commented out whole.

A test whose assertions survive the change is a broken call site, fixed under the edits table above; only a test
whose assertions the change invalidates is disabled.

The reason names the step whose own test class this is. No step, or a step that reworks another class, is the
same defect: a test nothing will ever re-enable. The skip list is then the
list of what is owed, and the total and skipped counts stay readable against the baseline: the total falls only
where an item names a file to delete, and the skipped count is exactly what was disabled here.

**Stabilizing disables; the step that owns the rework deletes.** A test that is obsolete rather than owed a rework
is removed by the step whose text names it — an `update: … — delete` bullet, a `disables:` line — never here on
the stabilizer's own judgement.

## Done means

- the module compiles, test sources included;
- its architecture-enforcement test, where the conventions name one, passes;
- the pre-existing suite is where the baseline left it: green, the total unchanged except for named deletions,
  the skipped count the baseline plus exactly what this step disabled;
- every stub a later step covers carries an intent comment consistent with that step's scenarios.

Whoever applies the step verifies these before it is ticked. Whoever spawns an agent to apply it verifies them
again before the next stage starts.
