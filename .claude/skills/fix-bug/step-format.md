# The step format

The grammar of a fix's checklist, read by `fix.sh` and by the agent applying a step. When each kind is used,
and what proves it, is the skill's.

Every step carries an ID, its kind, and one line of what it does. IDs are `S01`, `R01`, `G01` upward, one
sequence per kind, assigned once and never renumbered.

**`fix.sh validate` checks the result** — a duplicate ID, an unrecognized kind, a line the kind does not take, a
line the kind owes, a placeholder value, a `files:` with no bullet under it, a `fixes:` or `needs:` pointing at
nothing, an attempt missing its evidence, and an Open Question with no answer. Run it before handing the file
over, and again after writing any answer into it. The script ships with the skill at `scripts/fix/fix.sh` — under
`${CLAUDE_PLUGIN_ROOT}` when installed as a plugin, under `.claude/` in a plain checkout.

```
- [ ] S01 · stabilize · <the signature, interface or contract that moves>
  - files:
    - `path/to/Port`
    - `path/to/OneCaller`
  - test-files:
    - `path/to/OneCallerTest`
  - disables: `SomeTest#aMethod` — cleared by R01
  - docs: `<module>/docs/contracts/out/<counterpart>.md`

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

| Line          | On which kinds     | Holds                                                                   |
|---------------|--------------------|-------------------------------------------------------------------------|
| `files:`      | `stabilize`, `green` | every production file the step may edit, one per bullet under the label |
| `test-files:` | `stabilize`, `red` | every test file the step may edit, one per bullet under the label       |
| `runs:`       | `red`, `green`     | the test that must fail, then pass                                      |
| `reproduces:` | `red`              | the symptom the test's failure must show                                |
| `fixes:`      | `green`            | the `red` step whose test this one turns green                          |
| `disables:`   | `stabilize`        | each test it turns off, and the step that clears it                     |
| `needs:`      | any                | what must already be true for this step's run to be green               |
| `docs:`       | any                | the pages this step invalidates                                         |

**`green` carries no `test-files:`.** A fix proven by a test the same run edited is proven by nothing. The test
was written by the `red` step and stays as it was written; a `green` step that cannot pass without changing it
goes back to the user, because the reproduction was wrong and the diagnosis rests on it.

**`reproduces:` names the symptom, not the assertion.** "The second call charges the account twice" is the
symptom. `assertEquals(1, charges.size())` is how a test says it, and how it says it is `runs:`. The symptom is
what the user reported, restated precisely enough that a passing test can be recognized as the wrong test.

**Every `red` step has a `green` step naming it, and every `green` step names one `red` step.** That pairing is
the whole verification model of this skill: nothing is fixed that was not first reproduced.

**`needs:` states a fact, not a schedule.** It says what must hold for the step's run to be green, never when
either step runs. The order the kinds run in is the skill's, and it is the same in every fix.

**`files:` and `test-files:` carry one path per bullet under the label.** A run of paths on the label line is
read by scanning for commas, and a step's boundary is the thing a reader has to see at a glance. The label line
itself stays empty, and `validate` refuses one with no bullet under it.

**Together `files:` and `test-files:` are the boundary.** Anything outside them is another step's, or another
fix's.

**A step lives in the file of the module it edits.** A bug crossing two services has a `stabilize` step in
`shared/fix.md` for the contract, and its own `red` and `green` steps in each module's file. No step names a step
in another file, and `validate` refuses an ID it cannot find.

**A step carries `docs:` where its change is visible outside the code** — a port, a contract, a stored shape, a
configuration knob, or an operation. A `green` step usually carries one: the page said the old behaviour was the
behaviour.
