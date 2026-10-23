# [Conventions](../conventions.md) > Architecture

Packages and the rule between them.

## Package Structure

Code is grouped by concern under `bot.finance.eval`, not by layer:

```
src/main/java/bot/finance/eval
├── (root)      # the entry point, the configuration, the wiring, the run and its failure type
├── corpus      # loading and selecting cases and their steps
├── execution   # running one case: the target turn, fixtures, polling, learning steps
├── ledger      # the ledger's REST API, as one browser session uses it
├── observe     # what is read beside the API: metrics, the call log, the memory probe
├── oracle      # judging an attempt against a case's expectations
└── report      # the run file, summaries and the comparison of two runs
```

- `Wiring` is the one place collaborators are constructed. Everything else takes them through its constructor.
- No dependency-injection framework is used.
- No layer rule is enforced, and no architecture test exists.
- A type used across packages is `public`; a helper used inside one package is package-private.

## File Locations

| What                       | Where                                                         |
|----------------------------|---------------------------------------------------------------|
| The case corpus            | repo-root `docs/eval/corpus/` — `cases/` and `steps/`         |
| The stack a run starts     | `eval-runner/compose.eval.yaml`, started by `eval-runner/eval.sh` |
| Run output                 | repo-root `build/eval/`                                       |
| The run viewer             | `eval-runner/run-viewer.html`                                 |

Paths given to the runner resolve from the repository root.

## Diagram Format

Repo-root [`docs/conventions/diagrams.md`](../../../docs/conventions/diagrams.md), unchanged for this module.
