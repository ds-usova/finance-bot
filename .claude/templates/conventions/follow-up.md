# [Conventions](../conventions.md) > Follow-Up Work

What happens once a change is complete — every item of work done, the suite green, nothing left open. A change
with anything still open is not complete, and none of this applies to it yet.

## What Runs

The commands and tools this module puts over finished work, in order. Order matters where one reads what another
wrote — number them. An empty list is an answer: write "none".

Each entry says what it is given, what it produces, and whether it commits its own output. A generic reference
like `<module>/docs/conventions.md` is what belongs here.

1. `<e.g. the measurement script, given the finished work — measures the module and writes its evidence
   beside it. Commit the output as "<prefix>: <name> implementation evidence". A non-zero exit means the work is
   not finished: report the verdict rather than continuing down this list.>`
2. `<e.g. the documentation pass, given the finished work — writes a page per behaviour it added and the contracts
   with the systems around it. Commits its own output.>`

## What Gets Written

The kinds of document a finished change earns, and what each one is for. This is the list of kinds, not of
documents: which ones a given change actually produces depends on what it did.

- `<kind>` — `<what earns one, and where it lands — e.g. "a decision record — one per technical decision the
  developer approved for recording, stated as a fact, numbered when it is written">`
- `<e.g. "a manual request file — one per endpoint this module keeps examples for">`

**An artifact under the developer's approval is asked for, never assumed.** Where an entry above needs consent,
say so here. A candidate rejected, or never raised, means the artifact is not written — and the question is
asked while the change is being planned, when the reasoning is still at hand, rather than reconstructed from
finished code afterwards.

**Screen candidates before asking**, so the list is one or none rather than every decision the change made: a
rule statable without naming a technology, a file layout or a type is product behaviour, and the page that owns
that behaviour is the whole answer.
