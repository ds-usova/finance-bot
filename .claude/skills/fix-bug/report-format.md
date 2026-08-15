# The closing report

What a finished fix tells the user, once phase 4 is done.

- **The symptom, and that it no longer reproduces** — the same command from `## How it reproduces`, and what it
  produces now. Where the bug was intermittent, the run counts both gates actually used.
- **The failure text of every `red` step's run**, quoted.
- **Each step, its kind, and the files it touched**, per module. A step struck out mid-run is listed with what
  replaced it.
- **How many attempts each module logged, and what they ruled out** — one line each, pointing at the log rather
  than repeating it. A module that logged none says so.
- **Every test the fix changed**, and under which step. A `red` step rewriting a test that pinned the old
  behaviour is the expected case, and the line says what that test used to assert. A `green` step that edited a
  test is a defect, not a footnote.
- **Anything a probe left changed that reverting the edit did not undo** — an applied migration, a consumed
  offset.
- **A probe that became a `stabilize` step**, so a reader knows why the diff carries a seam nothing asked for.
- **What is left where this fix came from**, in one line: `<that file> — 1 of 4 open (R1)`. It costs one read of
  that file.
- Steps re-classified or abandoned, and why. **A fix abandoned entirely says what was reverted, what would not
  revert, and what the log rules out.**
- Defects found and not fixed.
- **What the conventions' finished-work list did**, per entry — the pages rewritten from the `docs:` lines, and
  any page a step named that the pass left alone.
