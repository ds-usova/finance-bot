# The step format

The grammar of a rework's checklist, read by `rework.sh` and by the agent applying a step. When each kind is
used, and what proves it, is [`applying-a-step.md`](applying-a-step.md).

Every step carries an ID, its kind, and one clause of what it does. IDs are `R01` upward across the whole rework,
assigned once, never renumbered.

**`rework.sh validate` checks the result** — a duplicate ID, an unrecognized kind, a line the kind does not
take, a line the kind owes, a placeholder value, a `files:` with no bullet under it, a `survives:` naming no
tier, a `needs:` or `disables:` naming a step nothing defines, and an Open Question with no answer. Run it on
every steps file before handing the rework over, and again after writing any answer. The script ships with the
skill at `scripts/rework/rework.sh` — under `${CLAUDE_PLUGIN_ROOT}` when installed as a plugin, under `.claude/`
in a plain checkout.

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
  - survives: <a scenario> · <what it runs against>
  - survives: <another scenario> · <what it runs against>
  - measures: <the number this step claims to move> <before> -> <after>

- [ ] R03 · pin · <what is now enforced, set, or dropped>
  - test-files:
    - `path/to/TheCheck`
  - needs: R02
  - proves: <the mutation and the failure it produced, or why there is none>

- [ ] R04 · stabilize · <the signature that moves>
  - files:
    - `path/to/Port`
    - `path/to/OneCaller`
  - test-files:
    - `path/to/OneCallerTest`
    - `path/to/SomeTest`
  - disables: `SomeTest#aMethod` — cleared by R05

- [ ] R05 · inline · <what is reshaped or relocated>
  - files:
    - `path/to/D`
  - runs: `DTest`
  - docs: `<module>/README.md`
```

| Line          | On which kinds  | Holds                                                                         |
|---------------|-----------------|-------------------------------------------------------------------------------|
| `files:`      | all but `tests` | every production file the step may edit, one per bullet under the label       |
| `test-files:` | all             | every test file the step may edit, one per bullet under the label             |
| `runs:`       | `inline`        | what runs after it — its tests, and the layering check where a file relocates |
| `frozen:`     | `extract`       | what must stay green **and unedited** — the behaviour-preserved claim         |
| `cover:`      | `extract`       | the tests written for the moved code, each mutated; `none` where it owes none |
| `survives:`   | `tests`         | one scenario and what it runs against; one line per scenario                  |
| `measures:`   | `tests`         | the number the step's claim is about, before and after                        |
| `needs:`      | any             | the step IDs whose work this step's run needs to be green                     |
| `proves:`     | `pin`           | how the step was shown to hold                                                |
| `disables:`   | `stabilize`     | each test it turns off, and the step that clears it                           |
| `docs:`       | any             | the pages this step invalidates — none on most `inline` and `tests` steps     |

**A step whose claim is a number carries `measures:`.** Narrowing what a test boots is the case: every scenario
still runs and the suite is green whether it happened or not.

**`files:` and `test-files:` are the boundary**, plus whatever a mutation temporarily breaks and restores.
Anything outside is another step's. One path per bullet; the label line stays empty.

**`cover:` is mutated one test method at a time**, and only the methods this step wrote. Extracting into a class
whose existing tests already reach the moved body writes `cover: none`.

**`survives:` names behaviour, never a method.** "A proposal is accepted" survives being moved into a different
class under a different name; `whenAccepted_thenRecorded` does not.

**A scenario keeps what it was proven against.** Swapping the real thing for a mock changes what the test
proves, so it is asked under **Open Questions**. An answered `yes` is written into the line as
`<before> -> <after>`, and the step is then held to what the line now says.

**`frozen:` is a claim about the moment its step ran**, so a later step may restructure the same class.

**A step carries `docs:` where its change is visible outside the code** — a port, a contract, a stored shape, a
configuration knob, an operation, or a conventions page whose rule the step invalidates.
