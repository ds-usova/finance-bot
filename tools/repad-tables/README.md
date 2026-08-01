# The Table Re-padder

`tools/repad-tables/repad-tables.sh` realigns every markdown table in the files it is given, so each column is
exactly as wide as its widest cell.

## Why it exists

Editing one cell of a table leaves every other row short or long, and the convention is to re-emit the whole
table aligned rather than leave it ragged. Doing that by hand means re-typing rows that did not change; doing it
with a throwaway script means a new script each time, written in whatever language is nearest, and an approval
prompt for running it.

## Usage

Run it with bash, from the **repository root**:

```
tools/repad-tables/repad-tables.sh docs/conventions/adr.md
tools/repad-tables/repad-tables.sh --check ledger-service/docs/configuration.md
```

| Mode      | Does                                                                        |
|-----------|-----------------------------------------------------------------------------|
| (default) | Rewrites each file in place, and names the ones it changed.                 |
| `--check` | Names the files that would change and exits 1 if any would. Writes nothing. |

A file whose tables are already aligned is left byte-for-byte alone, so running it over a directory is safe.

## What it does and does not touch

- A table is a run of lines starting with `|` whose **second** line is a rule row of dashes and colons. Anything
  else beginning with `|` is passed through — box art in prose stays as written.
- Fenced code blocks are passed through whole, so a table inside ``` is never rewritten.
- Alignment colons are preserved: `:---`, `---:` and `:---:` survive with their dashes stretched.
- The rule row is emitted without padding spaces (`|-----|`), which is how the tables already in this
  repository are written.

## Where it stops

It aligns; it does not reflow. A cell longer than the line budget stays long, and the table gets wider — that is
a signal the cell wants shortening, not something the tool should hide.
