# Conventions > Follow-Up Work

What runs once a change is complete — every item of work done, the guardrail green, nothing left open. A change
with anything still open gets none of it.

One answer for every service, because every service is measured by the same tool and documented the same way. A
module that differs says so in its own section file.

## What Runs

1. `tools/plan-evidence/plan-evidence.sh --plan <the finished work>` — measures every module and writes
   `evidence.md` and `evidence.json` into the task's `review/` folder; see
   [Evidence for a Finished Plan](java-build.md#evidence-for-a-finished-plan). It runs **once for the task**,
   whichever of its plans is named, and **first**, so the commit it records is the one that closed the work.
   Commit its output as `Documentation: <name> implementation evidence`. A non-zero exit means the work is not
   finished: report the verdict rather than continuing down this list.
2. `archive-knowledge`, given the finished work — a document per use case with its collaborators on both sides,
   the contracts with the systems around the service, and the ADRs the work was authorized to record. Commits its
   own output.

## What Gets Written

- **An ADR, placed** — one per technical decision approved for recording. What earns one, where it goes, how it
  is numbered, what it looks like, and what separates a placed record from a written one are all
  [Architecture Decision Records](adr.md).

**An ADR exists only because it was approved.** Each candidate is raised as a question — the decision as a fact,
and the page that holds it if no ADR is written — and only an answered `yes` produces one. A candidate rejected,
or never raised, means no ADR.

The question is asked while the change is being worked out, not reconstructed from finished code afterwards. A
decision discovered too late is proposed in the archiving report rather than written as a fact nobody agreed to.

**Screen candidates before asking**, so the list is one or none rather than every decision the change made: a
rule statable without naming a technology, a file layout, or a type is product behaviour, and the use-case,
contract, or domain page that owns it is the whole answer.
