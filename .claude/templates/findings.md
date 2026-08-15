# A findings file

What a finished piece of work leaves open, written into its own `review/findings.md`. A person reading it learns
what they are inheriting without opening a plan or a rework file. The skill that writes one says which sections
its own kind of work may fill.

```
# Review: <name>

**<the counts by section, or that nothing is open>**

## Critical
```

**The first line is the whole file when the work is clean**: `**Nothing open.** <one clause>`. A reader who sees
it stops there. A missing file and clean work must never look the same, which is why the file is written either
way.

Sections are these four, in this order, and a section with nothing in it is left out:

| Section                   | Holds                                       | Shape                                        |
|---------------------------|---------------------------------------------|----------------------------------------------|
| **Critical**              | fix before the next task starts             | one block per defect, in the form below      |
| **Bug**                   | real, and it can wait                       | one block per defect, in the form below      |
| **Refactoring candidate** | nothing behaves wrong                       | table — # · Status · module · what · why     |
| **Manual test**           | what no test can see, so a person must look | one block per check, in the form below       |

**A candidate is numbered and carries a status**, because it outlives the work that raised it. `#` is `R1`
upward, assigned once and never reused. `Status` is `open`, or `done · <the rework that closed it>` — or
`done · directly` where it was taken without one. The opening count line says how many are still open, so the
first line of the file answers what is left without reading the table.

**A refactoring candidate is a defect nobody sees yet, or the author's own deferral — never something the run
merely noticed.** An asymmetry, a naming quibble, a test that could exist, a case the current schema or
configuration makes unreachable: none of them is a row. Before writing one, ask what breaks and who notices.
**A file holding no candidates is the normal result**, and a run that files four has usually confused a list of
observations with a list of work.

**A defect is reported as a case, not as a description.** Whoever picks it up reproduces it before fixing it,
and a paragraph about a class does not tell them how:

```
**`<module>` — <the symptom, in one line>**

- **Given** <the state the system is in>
- **When** <what happens>
- **Then** <what should follow>
- **Actual** <what follows instead>
- **Fix** <the proposal> · `<class or file>`
```

**Four of those lines are observations; `Fix` is not.** `Given`, `When`, `Then` and `Actual` are what the run
saw. The fix is a proposal, and whoever picks it up implements it as written — so where this run did not
exercise the mechanism behind it, the line says `unverified`. A design records the same distinction as
`Basis:`; a findings file without it reads a guess and a tested conclusion in one voice.

**A manual check is the same block, minus what has not happened yet.** No `Actual`, since nobody has looked, and
no `Fix`, since nothing is claimed to be wrong:

```
**[ ] `<module>` — <what this check decides, in one line>**

- **Given** <the state to arrange, and where on screen>
- **When** <what the person does; "it renders" where they only look>
- **Then** <the one thing that must hold>
```

**`Then` states one observable.** A check that needs three is three blocks — bundled into one sentence, a person
who sees two of them hold has no way to record the third failing, which is the whole reason the list exists. The
tick rides on the heading, so a half-worked list still says where it stopped.

**The module comes first** — in a defect's heading, in a check's heading, and in the first cell of a table row,
unless every entry in the section shares one module, which the section's opening line then names. Nothing is
*grouped* by module: a reader triages by what an entry costs them, and this tells them where to go once they
have.

**Write what a person hits, not the mechanism.** The class is the last thing on the line, never the sentence.
