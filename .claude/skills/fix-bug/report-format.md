# The closing report

What a finished fix tells the user, once phase 4 is done.

- **The symptom, and that it no longer reproduces** — the same command from `## How it reproduces`, and what it
  produces now.
- **The failure text of every `red` step's run**, quoted. That is the proof the bug was reproduced before it was
  fixed, and it is the one thing a reader cannot reconstruct from the diff.
- **Each step, its kind, and the files it touched**, per module.
- **How many attempts each module logged, and what they ruled out** — one line each, pointing at the log rather
  than repeating it. A module that logged none says so.
- **Every test the fix changed**, so an assertion loosened to make a step green is visible. A `green` step that
  edited a test is a defect, not a footnote.
- **Anything the whole-suite run turned red and why it was right to change it** — a test that depended on the old,
  wrong behaviour.
- **What is left where this fix came from**, in one line: `<that file> — 1 of 4 open (R1)`. It costs one read of
  that file.
- Steps re-classified or abandoned, and why.
- Defects found and not fixed.
- **What the conventions' finished-work list did**, per entry — the pages rewritten from the `docs:` lines, and
  any page a step named that the pass left alone.
