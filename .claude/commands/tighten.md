---
description: Tighten one prose file — cut what carries no fact, sharpen what remains, and change nothing it means. For a README, a rules file, a skill, an agent, a design or a plan. Prose only, never code.
argument-hint: [ path to the file to tighten ]
---

# Tighten

Make one file shorter to read and cheaper to load, without changing a single thing it says.

**Cutting a rule is not compression, it is damage.** The file is read by people who need what it constrains and
by agents that obey it. This skill removes words that carry no constraint; it never removes a constraint because
the sentence around it was long.

## 1. Scope

One file, the one named as the argument.

- **Source, tests, scripts** — refused. Shortening code is a different judgment, and a comment that reads as
  fluff is often the only place a constraint is written down.
- **A project that states its own writing rules** — find them and read them first. They win wherever they differ
  from this file, including where they are stricter. A project that states none is governed by section 2 alone.

## 2. What Shorter Means Here

For any prose file:

- One idea per sentence. A sentence joining two clauses with a dash, a semicolon or a second "and" is two.
- Where a sentence and a table say the same thing, the table stays.
- A rule with conditions and outcomes is a table. A flow whose shape carries meaning is a diagram.
- State a fact once. Where another file owns it, link instead of repeating.
- Say what is, not what isn't.

A file an **agent** obeys — a skill, an agent definition, a rules file — is written for a tired reader who gets
one pass, and takes two more:

- No clause hanging off a clause, no aside between dashes.
- The sequence, the guardrails and the handoffs stay. Everything else is a candidate.

## 3. Classify Every Block Before Cutting

Read the file whole, then label each paragraph, bullet and table cell:

| Label           | What it is                                                        | What happens to it                           |
|-----------------|---------------------------------------------------------------------|----------------------------------------------|
| **rule**        | something must, must not, or happens in a stated order            | kept, tightened only                         |
| **contract**    | a format a parser reads, a path, a command, a name, a threshold   | kept verbatim                                |
| **rationale**   | why the rule is right                                             | cut, unless it changes what someone would do |
| **example**     | an illustration of a rule                                         | cut, unless it *is* the format specification |
| **restatement** | a fact this file, or another file, already states                 | cut, and link if the owner is elsewhere      |

**The biggest saving is never word-level.** A fact stated in two files costs every session that loads both. Look
for restatement across files before polishing sentences inside one.

## 4. What Fluff Looks Like

Concretely, so it can be found rather than sensed:

- A second sentence that says the first again in other words.
- A tail explaining why a rule exists, hanging off the rule.
- The "why" clause appended to a table cell whose other cells have none.
- Hedges and connectors: *generally*, *usually*, *simply*, *it is worth noting*, *in order to*, *that being said*.
- A section opening that announces itself: *This section describes…*, *Below is…*.
- Naming a sibling file, skill or agent where naming it changes nothing the reader will do.
- An enumeration of what the thing is not, where saying what it is would do.
- A parenthetical that repeats the sentence it sits in.

## 5. Rewrite, Then Prove Nothing Was Lost

Rewrite the file in place. Then, before reporting, do the check that makes this safe:

1. List every **rule** and **contract** in the original.
2. List every one in the rewrite.
3. Any entry in the first list with no match in the second is restored, whatever it cost in lines.

A rule that survived only as an implication is a rule that was cut. State it.

## 6. Report

- Lines before and after.
- What was cut, by label from the table above, one line each.
- Anything that reads as fluff and was **kept**, with what rule it carries — this is the list that stops the next
  pass from cutting it.
- Anything the file states that another file owns, named, so the duplication can be settled separately.

Show the diff. Commit nothing: the file's owner decides whether shorter is better here.
