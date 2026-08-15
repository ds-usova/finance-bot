# A bug that crosses modules

Read this only where the diagnosis reaches more than one module.

## Which module the fix is cut in

| Cut it where                       | When that is right                                                    |
|------------------------------------|-----------------------------------------------------------------------|
| the module whose promise is broken | its own contract or documentation already says what it should do      |
| the module the cause is in         | the promise it breaks is one it makes to everybody, not to one caller |

**Prefer the smallest scope that makes the symptom impossible without contradicting a contract.** A module
behaving exactly as its contract says is not the bug; changing it is `design-task`. Say which cut was chosen in
`## Why it happens`, and name the one rejected.

**Where every module matches its own contract and the outcome is still wrong, the contracts disagree.** The
promise broken is then the one a use case's **Outcomes** or a caller's contract page states about the system.
Where no page states it either, nothing was promised: close `bug.md` with `**Closed:** design-task` and say so.

## `shared/fix.md`

**Written where the bug spans something two modules must agree on** — a schema they build from, or a shape a
protocol carries. It holds `stabilize` steps only: the schema or contract page stating the shape, each module's
wiring to it, every call site carried back to compiling, and whatever it had to disable. Its header names every
module on the seam:

```
**Affected Modules:** `module-a`, `module-b`
**Bug:** [<the bug>](../bug.md)
**In flight:** <as in any other fix file>
```

**The contract is shared; the behaviour behind it is not.** The handler a schema declares is fixed in its own
module's file. A shape published over a protocol breaks no call site and still gets this file: written down, it
holds both modules to one shape rather than to each other. Where a module's conventions give a test type that
drives the real counterpart, the seam gets one, in the module owning the entry point; where none does, the report
says the seam is proven only by the written shape.

**A schema edit no two modules must agree about** goes in the `files:` of the step that needs it, since a shared
file serializes the run.

## Where the reproduction lives

**A reproduction runs inside one module** — the one owning the entry point, with the counterpart at whatever
boundary its conventions give its integration tests. Where the counterpart's stand-in is itself wrong, correcting
it is a `stabilize` step in the module that owns it. Where no module can host the reproduction, the fix stops as
a bug that cannot be reproduced.

**No module's `red` step may depend on another module's `green` step.** `fixes:` never crosses a file. A fix that
cannot be written this way is two bugs, and goes to the user as two.

## Applying it

**`shared/fix.md` is applied first, alone**, by its own `fix-bug-module` agent, handed every listed module's
phase-0 figures. Its exit condition: every module on the seam compiles, passes its layering check, and its suite
stands where phase 0 left it apart from exactly the tests this file's `disables:` turned off. A blocked shared
fix stops the run there.

**Each module agent is then told what the shared fix disabled in its module**, so its skipped count reconciles
against a baseline taken before the shared fix landed.
