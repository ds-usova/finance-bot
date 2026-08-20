# Archiving a Plan

What `archive-knowledge` reads when the finished work is a `plan.md`. Everything else about the run is in the
skill itself.

## The Input

**The plan** — the path given, else the one implemented in this conversation. Read it whole: an answered
question under **Open Questions / Blockers** is often the clearest statement of a decision.

**The design file it links**, for the `D<n>` entries the decision-record section below reads.

**The diff is what the plan's steps targeted**, taken against the state before the task's first commit.

## The Gate

The plan is in its task directory under `docs/implemented/`, has no `- [ ]`, and every blocker has a resolution.

## Where the Survey's Inventory Comes From

| Artifact      | The inventory                                                                                                             |
|---------------|---------------------------------------------------------------------------------------------------------------------------|
| Use case      | the module's usecase package, filtered to what this plan added or changed — its unit green-phase steps name them directly  |
| Domain        | the module's domain layer, filtered to what this plan added or changed                                                    |
| Contract      | every edge the service has, not only the ones this plan added                                                             |
| Configuration | updated where the plan added, removed, or changed a knob                                                                  |
| Conventions   | a page whose rule this plan's **Post-Implementation Steps** or an answered blocker contradicts                            |

## What Authorizes a Decision Record

An item under the plan's **Post-Implementation Steps**, in whatever shape the module conventions define for it,
and nothing else. A plan carrying no such item yields no record.

**Where the candidates come from.** An entry in the design marked `Basis: decided` is a decision the user made
between defensible options, already written down with its rationale. Entries marked `assumed` are not: the
repository already determined them, so the code and the conventions own the fact. Name the `D` number in the
**report**, with the rest of what a writer will need, and in any proposal made there — not in the record itself,
whose sections belong to whoever the conventions say writes them.
