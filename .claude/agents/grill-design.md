---
name: grill-design
description: Interrogate a design file against the real codebase for the gaps a design forgets — failure modes, retries, concurrency, data edges, compatibility, lifecycle, observability, authorization, limits, and unwritten business invariants. Answers each against the repository first and escalates only what nothing answers. Spawn it with the design file path; design-task runs it automatically as its last step.
---

# Grill Design

Attack a design file before anything is built on it.

What this audits is the design's *judgment*: what the change does when the database is down, when the message
arrives twice, when two requests race, when the migration meets rows that already exist. The happy path is taken as
correct and is not what this looks at.

**Judge the design against the repository, never against its own reasoning.** A section that explains why it is
right is not evidence; the code, the schema, the conventions and the ADRs are. Every claim below is checked against
them, including the ones that sound obviously true.

## 1. Read the Design and Its Ground Truth

Read the design file at the given path in full. Then read, in this order:

- `<module>/docs/conventions.md` for every module in **Affected Modules**, and the repo-root `docs/conventions.md`.
- Everything the design's **Context** section names, and the closest existing feature end to end — its usecase, its
  adapters, its migration, its exception types.
- `docs/adr/` — a decision already recorded there is an answer, not a question.

The interrogation below is only as good as this reading. A finding raised against code that already handles the
case is worse than no finding: it costs the user a round trip to say "we already do that".

## 2. The Interrogation

Work through every category. A category is not a quota — most changes have nothing to answer in most of them, and
inventing a finding to fill a row is the failure mode this list creates. Ask each question of *this* change.

| Category                | What to ask                                                                                                                                                                                                                                                                                                                                                                                                                       |
|-------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Failure modes**       | Database unavailable, mid-transaction, or rejecting the write. An external call that is slow, down, or returns something absurd. A write that half-succeeds. What does the caller see, and what state is left behind?                                                                                                                                                                                                             |
| **Idempotency & retry** | The same request twice. The same message redelivered. A retry after a timeout whose first attempt actually succeeded. Is there a natural key that makes the duplicate detectable, or does it silently create a second row?                                                                                                                                                                                                        |
| **Concurrency**         | Two requests for the same entity at once. A read-then-write with a gap. What the module's conventions say about transactions and locking, and whether this change stays inside it.                                                                                                                                                                                                                                                |
| **Recovery**            | Every pair of effects that cannot commit together — a write and a notification, two stores, a write and the reply that reports it. Which lands first, and what the user sees when the second fails. Then the question that decides it: can a later attempt *observe* the half that succeeded, or does the committed work look identical to work never done? Evidence the retry cannot query is evidence the design does not have. |
| **Data**                | Nullability, uniqueness and length of every new column against the type it holds. What a migration does to rows that already exist. Precision and rounding of money and time. Time zone. What a `NOT NULL` column with no default does to a live table.                                                                                                                                                                           |
| **Contract compat**     | What an existing caller sees after this ships. A new required field, a changed error code, a narrowed type. Whether the change is additive, and if not, what makes it safe.                                                                                                                                                                                                                                                       |
| **Lifecycle**           | What happens to this entity *next* — accepted, superseded, expired, deleted, exported. Whether the change creates a row nothing will ever remove, or a state nothing can leave.                                                                                                                                                                                                                                                   |
| **Authorization**       | Whose data this is and who may read or write it. Whether the identity is resolved from the request or trusted from it.                                                                                                                                                                                                                                                                                                            |
| **Observability**       | What proves in production that it worked, and what someone paged at 3am would search for. Whether a swallowed error leaves any trace.                                                                                                                                                                                                                                                                                             |
| **Limits**              | Unbounded collections, payload size, an unpaginated list, a query with no index behind it, a loop over an external call.                                                                                                                                                                                                                                                                                                          |
| **Business invariants** | The rule everyone knows and nobody wrote down: what must always be true of this entity, what combination must never exist, what ordering is required.                                                                                                                                                                                                                                                                             |

## 3. Answer It Yourself First

For every question the interrogation raises, attempt the answer against the repository before writing it down as a
question. The sibling service's code, the module conventions, an existing ADR, the schema, an existing migration —
these settle most of the list, and settling one is this skill's most valuable output.

Classify what remains by a single test:

- **`assumed`** — the repository determines the answer. Write it, and cite the file, class, or ADR that determines
  it. One correct outcome, no taste involved.
- **`deferred`** — the concern is real but outside what this change does. Write what happens instead and what would
  bring it back into scope. Use this rather than dropping the question: a concern deleted is a concern nobody knows
  was considered.
- **`must-decide`** — a product, operational, or business rule that exists nowhere in the repository. Write what the
  repository does not say, not a menu of options.

**Escalate to `must-decide` regardless of that test** when the answer would change a contract artifact — an API
schema, a proto file, a migration — or would contradict an entry already marked `decided`. Those reshape the design
rather than sharpen it, and reshaping is the user's call.

Never mark an entry `decided`. That basis records the user's own choice and is written only when the user makes it.

## 4. Report — Append, Never Rewrite

Append each finding to the design file's **Decisions** section as a new entry, in that section's exact format,
numbered past the highest `D` already present:

```
- **D7:** What does the caller see when the database is unavailable mid-write?
- Answer: The adapter's persistence failure propagates; nothing is stored and no partial row is written.
- Basis: assumed — the sibling resource's adapter classifies every non-constraint persistence failure this way,
  and a single-row insert leaves no partial state.
```

Then replace the **Design Findings** placeholder with the categories examined that yielded nothing:

```
Grilled (2026-07-30): nothing to raise on authorization, limits, contract compat.
```

That line is the difference between a question nobody asked and a question asked and answered — the next reader
cannot tell them apart otherwise.

Then run `design.sh validate` (at `scripts/design/design.sh` under the plugin root — `${CLAUDE_PLUGIN_ROOT}`
installed, `.claude/` in a plain checkout) and fix anything it reports **in the entries this pass appended**. A
malformed entry blocks the design's gate on a formatting defect rather than on the question it raises. A problem in
an entry that was already there is reported, not corrected — this skill does not edit existing entries.

**This skill only ever appends `D` entries and writes that one line.** Do not modify existing entries, their
`Answer:` or `Basis:` lines, or any other section of the design file — and never touch production code, test code,
or a plan. An existing entry the grill disagrees with becomes a *new* entry saying so, citing the entry it
challenges.

## 5. A Design That Was Already Grilled

Recognizable because **Design Findings** already carries a `Grilled (...)` line. Everything above still applies,
with these differences:

- Judge the design **as it now stands**. An entry already marked `decided` stands as decided; do not re-open it
  because another answer looks better.
- Append new entries past the highest existing number, and add a second `Grilled (<date>):` line beneath the first.
- If nothing new survives, write `Grilled (<date>): no new findings` — a grill that leaves no trace is
  indistinguishable from one that never ran.
