# A bug that crosses modules

Read this only where the diagnosis reaches more than one module. A single-module fix reads none of it.

## Which module the fix is cut in

**Which modules the fix reaches is a decision, not a reading of the diagnosis.** A chain of causes crossing two
services can usually be cut in either of them, and the two fixes are not equivalent:

| Cut it where                       | When that is right                                                    |
|------------------------------------|-----------------------------------------------------------------------|
| the module whose promise is broken | its own contract or documentation already says what it should do      |
| the module the cause is in         | the promise it breaks is one it makes to everybody, not to one caller |

**Prefer the smallest scope that makes the symptom impossible without contradicting a contract.** A module
behaving exactly as its contract says is not the bug, however clearly the cause passes through it — changing it
is a design change, and it goes to `design-task`. Say which cut was chosen in `## Why it happens`, and name the
one rejected.

**Where every module matches its own contract and the outcome is still wrong, the contracts disagree with each
other.** That is a real bug, and the promise it breaks is the one a page states about the system rather than
about a module: a use case's **Outcomes**, or what a caller was told would happen. Name that page as the promise
broken. **Where no page states it either, stop here.** Nothing was promised, so this is design work. `bug.md`
already exists by now and keeps everything the diagnosis established: close it with its `**Closed:**` line
naming `design-task`, and say so in the report.

## `shared/fix.md`

**Written only where the bug spans something two modules must agree on** — a schema they build from, or a shape
only a protocol carries. **A schema edit no module's steps depend on agreeing about belongs in `files:` of the
step that needs it**, since a shared file serializes the whole run before anything else starts.

It holds `stabilize` steps and nothing else — the schema or the page that states the shape, each consuming module's wiring to it, and every
call site the change breaks, carried back to compiling. A seam carried by a protocol breaks no call site and
still gets the file, for the shape alone. It is a `fix.md` in every other respect, and its header names every
module on the seam:

```
**Affected Modules:** `module-a`, `module-b`
**Bug:** [<the bug>](../bug.md)
**In flight:** <as in any other fix file>
```

**The contract is shared; the behaviour behind it is not.** A schema both modules build from goes here. The
handler that schema declares does not — the module serving it fixes it in its own file, and the consumer's own
`red` step drives its side at its own test boundary.

**A seam with no file still needs this one, and that is the case it matters most in.** Where the shape crossing
between two modules is published over a protocol rather than held in a schema, no call site breaks, both
modules compile throughout, and each side's tests pass against its own idea of the shape. The consumer's
stand-in is taught the new shape by a `stabilize` step; the producer's `green` step then emits whatever it
emits. **Both modules go green while disagreeing, and nothing in this skill would notice.**

**So the agreed shape is written down before either module starts.** That is `shared/fix.md`'s step: a
`stabilize` step whose `files:` names the contract page or the schema, stating exactly what the producer will
send and the consumer will expect. Each module's steps are then held to a written shape rather than to each
other.

**A written shape narrows the disagreement; it cannot catch one.** Nothing runs a page. Where a module's
conventions give a test type that drives the real counterpart rather than a stand-in, the seam gets one, in the
module that owns the entry point. Where none does, the fix says so and `review/findings.md` carries the
**Manual test** that stands in for it: two services, started together, exchanging the shape. **Say plainly in
the report which of the two this fix got.**

## Where the reproduction lives

**A reproduction runs inside one module.** Where the bug only shows with two services really running, it belongs
to the module that owns the entry point, with the counterpart at whatever boundary that module's conventions give
its integration tests: a stub server, a test broker. **Where no module can host it, the fix stops here**, on
the same terms as a bug that cannot be reproduced at all. A `green` step with no reproduction behind it is a
change nothing verified, and the file holding it will not validate.

**Where the counterpart's stand-in is itself wrong, correcting it is a `stabilize` step in the module that owns
it.** A bug in what two services believe about each other is usually a bug the stand-in shares, so the
reproduction is unwritable until the stand-in tells the truth. That correction is named in the diagnosis, it
lands before the `red` step, and it changes no production behaviour.

**No module's `red` step may depend on another module's `green` step.** Where the diagnosis says one does, the
fix is written the other way round: the module whose `green` step comes first owns the reproduction, and the
second module's part is a `stabilize` step or nothing. `validate` enforces the mechanical half of this — a
`fixes:` never crosses a file — and the diagnosis owes the rest. A fix that cannot be written this way is one
bug reported as two, and it is put to the user in Phase 2 rather than started.

## Applying it

**`shared/fix.md` is applied alone, before anything else**, by its own `fix-bug-module` agent, given every
module on the seam. Its exit condition is that every one of those modules compiles, passes whatever its
conventions name as the check on its layering rule, and its suite stands where phase 0 left it apart from
exactly the tests this file's `disables:` lines turned off. **A module whose conventions name no such check owes
the other two.** **It is handed every listed module's phase-0
figures**, since it is the one agent measuring more than one. A blocked shared fix stops the run there, with no
module agent started.

**Each module agent is then told what the shared fix disabled in its module**, as its `disables:` lines say it.
An agent measuring its module against a baseline taken before the shared fix landed would otherwise find
skipped tests it cannot account for and cannot look up.
