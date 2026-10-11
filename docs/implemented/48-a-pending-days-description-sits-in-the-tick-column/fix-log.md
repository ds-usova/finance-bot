# Fix Log: the entry line's cells end on a line instead of spanning from `sm` up

**In flight:**

## Attempts

## Run Log

- **RL01 (FR01):** the finish's build failed `tsc` on the rule's literal scan: `match[2]` is `string | undefined`
  under the module's strict indexing. Falling back to `''` fixed it, with no assertion changed; the rule still
  passes, 2 of 2.
