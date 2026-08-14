# The step format

The grammar of a rework's checklist, read by `rework.sh` and by the agent applying a step. When each kind is
used, and what proves it, is the skill's.

Every step carries an ID, its kind, and one line of what it does. IDs are `R01` upward, assigned once, never
renumbered.

**`rework.sh validate` checks the result** — a duplicate ID, an unrecognized kind, a line the kind does not
take, a line the kind owes, a placeholder value, a `files:` with no bullet under it, a `survives:` naming no
tier, a `needs:` pointing at nothing, and an Open Question with no answer. Run it before handing the file over,
and again after writing any answer into it. The script ships with the skill at `scripts/rework/rework.sh` —
under `${CLAUDE_PLUGIN_ROOT}` when installed as a plugin, under `.claude/` in a plain checkout.

```
- [ ] R01 · extract · <what moves, and where to>
  - files:
    - `path/to/A`
    - `path/to/NewB`
  - test-files:
    - `path/to/NewBTest`
  - frozen: `ATest`
  - cover: `NewBTest`

- [ ] R02 · tests · <what is restructured>
  - test-files:
    - `path/to/OneTest`
    - `path/to/TwoTest`
  - survives: <one scenario per line> · <what it runs against>
  - measures: <the number this step claims to move> <before> -> <after>

- [ ] R03 · pin · <what is now enforced, set, or dropped>
  - test-files:
    - `path/to/TheCheck`
  - needs: <what must already be true for this step's run to be green>
  - proves: <the mutation and the failure it produced, or why there is none>

- [ ] R04 · stabilize · <the signature that moves>
  - files:
    - `path/to/Port`
    - `path/to/OneCaller`
  - test-files:
    - `path/to/OneCallerTest`
    - `path/to/SomeTest`
  - disables: `SomeTest#aMethod` — cleared by R05

- [ ] R05 · inline · <what is reshaped>
  - files:
    - `path/to/D`
  - runs: `DTest`

- [ ] R06 · behaviour · <what the code starts doing instead>
  - files:
    - `path/to/C`
  - test-files:
    - `path/to/CTest`
  - runs: `CTest#theMethodThatChanges`
  - now: <what the code does today>
  - then: <what it does after this step>
  - docs: `<module>/docs/contracts/out/<counterpart>.md`
```

| Line             | On which kinds        | Holds                                                                     |
|------------------|-----------------------|---------------------------------------------------------------------------|
| `files:`         | all but `tests`       | every production file the step may edit, one per bullet under the label   |
| `test-files:`    | all                   | every test file the step may edit, one per bullet under the label         |
| `runs:`          | `inline`, `behaviour` | what runs after it                                                        |
| `frozen:`        | `extract`             | what must stay green **and unedited** — the behaviour-preserved claim     |
| `cover:`         | `extract`             | the tests written for the moved code, each of which gets a mutation check |
| `survives:`      | `tests`               | the scenarios that must still run, each with what it runs against         |
| `measures:`      | `tests`               | the number the step's claim is about, before and after                    |
| `needs:`         | any                   | what must already be true for this step's run to be green                 |
| `proves:`        | `pin`                 | how the step was shown to hold                                            |
| `disables:`      | `stabilize`           | each test it turns off, and the step that clears it                       |
| `now:` / `then:` | `behaviour`           | the behaviour before and after                                            |
| `docs:`          | all                   | the pages this step invalidates — none on most `inline` and `tests` steps |

**A step whose claim is a number carries `measures:`**, taken before and after. Narrowing what a test boots is
the case: every scenario still runs and the suite is green whether it happened or not. This is the step's own
claim, not a count of tests.

**`needs:` states a fact, not a schedule.** It says what must hold for the step's run to be green, never when
either step runs.

**`files:` and `test-files:` carry one path per bullet under the label.** A run of paths on the label line is
read by scanning for commas, and a step's boundary is the thing a reader has to see at a glance. The label line
itself stays empty, and `validate` refuses one with no bullet under it.

**Together `files:` and `test-files:` are the boundary**, plus whatever a mutation temporarily breaks and then
restores. Anything outside all of that is another step's.

**`cover:` is mutated one test method at a time**, not once per class, and only the methods this step wrote.
Extracting into a class that already has tests owes nothing for the ones that were already there.

**`survives:` names behaviour, never a method.** "A proposal is accepted" survives being moved into a different
class under a different name; `whenAccepted_thenRecorded` does not.

**A scenario keeps what it was proven against.** Swapping the real thing for a mock changes what the test
proves, so it is a decision, asked under **Open Questions**. An answered `yes` is written into the line as
`<before> -> <after>`, and the step is then held to what the line now says, not to what it said before.

**`frozen:` is a claim about the moment its step ran**, so a later step may restructure the same class.

**A step carries `docs:` where its change is visible outside the code** — a port, a contract, a stored shape, a
configuration knob, an operation, or a conventions page whose rule the step invalidates.
