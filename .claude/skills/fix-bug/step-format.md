# The step format

The grammar of a fix's checklist, read by `fix.sh` and by the agent applying a step. When each kind is used,
and what proves it, is the skill's.

Every step carries an ID, its kind, and one line of what it does. IDs are `S01`, `R01`, `G01` upward, one
sequence per kind, assigned once and never renumbered. **The letter is the kind**, and `validate` refuses a step
whose prefix says something its kind does not.

**`fix.sh validate` checks the result**, and what it catches is listed in the script's own README. Run it before
handing the file over, and again after writing any answer into it. Given the bug's directory rather than one
file, it checks `bug.md` and every `fix.md` at once. The script ships with the skill at `scripts/fix/fix.sh` —
under `${CLAUDE_PLUGIN_ROOT}` when installed as a plugin, under `.claude/` in a plain checkout.

```
- [ ] S01 · stabilize · <the signature, interface or contract that moves>
  - files:
    - `path/to/Port`
    - `path/to/OneCaller`
  - test-files:
    - `path/to/OneCallerTest`
  - disables: `SomeTest#aMethod` — cleared by R01
  - docs: `<module>/docs/contracts/out/<counterpart>.md`
  # in shared/fix.md the same line names the file: cleared by module-a/fix.md · R01

- [ ] R01 · red · <the test that reproduces the bug>
  - test-files:
    - `path/to/TheBugTest`
  - reproduces: <the symptom the test must fail with>
  - runs: `TheBugTest#theScenario`
  - needs: S01

- [ ] G01 · green · <what starts happening instead>
  - files:
    - `path/to/TheClass`
  - fixes: R01
  - runs: `TheBugTest#theScenario`
```

| Line          | On which kinds       | Holds                                                                   |
|---------------|----------------------|-------------------------------------------------------------------------|
| `files:`      | `stabilize`, `green` | every production file the step may edit, one per bullet under the label |
| `test-files:` | `stabilize`, `red`   | every test file the step may edit, one per bullet under the label       |
| `runs:`       | `red`, `green`       | the test that must fail, then pass                                      |
| `reproduces:` | `red`                | the symptom the test's failure must show                                |
| `fixes:`      | `green`              | the `red` step whose test this one turns green                          |
| `disables:`   | `stabilize`          | each test it turns off, and the step that clears it                     |
| `needs:`      | any                  | what must already be true for this step's run to be green               |
| `docs:`       | any                  | the pages this step invalidates                                         |

**`green` carries no `test-files:`.** A fix proven by a test the same step edited is proven by nothing. The test
was written by the `red` step and stays as it was written. A `green` step that cannot pass without changing it
goes back to the user: the reproduction was wrong, and the diagnosis rests on it.

**`reproduces:` names the symptom, not the assertion.** "The second call charges the account twice" is the
symptom. `assertEquals(1, charges.size())` is how a test says it, and how it says it is `runs:`. The symptom is
what the user reported, restated precisely enough that a passing test can be recognized as the wrong test.

**Where the bug is intermittent, `reproduces:` carries the rate** the diagnosis measured, as `<failures> in
<runs>`, and `SKILL.md`'s Phase 0 gives the step's run count.

**Every `red` step has a `green` step naming it, and every `green` step names one `red` step.** `validate`
refuses a reproduction nothing fixes.

**`needs:` states a fact, not a schedule.** It says what must hold for the step's run to be green, never when
either step runs. The order the kinds run in is the skill's, and it is the same in every fix.

**`files:` and `test-files:` carry one path per bullet under the label.** The label line itself stays empty, and
`validate` refuses one with no bullet under it.

**Together `files:` and `test-files:` are the boundary.** Anything outside them is another step's, or another
fix's.

**A path under `files:` or `test-files:` is written from the repository root**, so a step in one module's file
and a step in another's read the same way. A reference to another fix file names it as it sits beside this one:
`shared/fix.md · S01`.

**A `stabilize` step may carry no `files:` at all.** Preparing a stub, a fixture or a builder so the `red` step
can be written is what the kind is for, and that work is all `test-files:`.

**A step lives in the file of the module it edits.** A bug crossing two services has a `stabilize` step in
`shared/fix.md` for the contract, and its own `red` and `green` steps in each module's file.

**A step in another file is named with that file** — `needs: shared/fix.md · S01`, or
`disables: `SomeTest#aMethod` — cleared by module-a/fix.md · R01`. Only `needs:` and `disables:` may cross, and
`validate` does not resolve what it cannot see. A bare ID always means this file, and `validate` refuses one no
step here defines.

**`fixes:` never crosses.** A reproduction and the code that fixes it are the same module's, and `validate`
refuses a `fixes:` naming anything but a `red` step in the same file.

**A step carries `docs:` where its change makes a page wrong** — a port, a contract, a stored shape, a
configuration knob, or an operation. Often a `green` step does, because the page described the old behaviour as
the behaviour. **A page that already says what the fix makes true carries no line**: the code was wrong, not the
page, and naming it sends the archiving pass looking for a change nobody made.

**A schema, a migration, or any other file the build reads is a production file**, and goes in `files:`. Where
it also states a promise a reader relies on, its page goes in `docs:` as well.
