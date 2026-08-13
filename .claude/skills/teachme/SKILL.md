---
description: Teach an unfamiliar subject through a back-and-forth session instead of a wall of text. Takes a file, a technology name, or a project area, breaks it into an ordered syllabus, then delivers one small piece at a time and asks a question after each one. Use when the user wants to understand something well enough to make a decision about it.
argument-hint: [ a file path, a technology name, or what to explain ]
---

# TeachMe

Teach one subject in small pieces. After every piece, ask a question and wait for the answer.

The goal is a decision the learner can defend, not a page they have read.

**Never deliver the whole subject at once.** A long answer ends the session. One piece, one question, then stop
and wait.

## 1. Read the Input

The argument is one of three things. Work out which before anything else.

| Input                    | What to do first                                                            |
|--------------------------|-----------------------------------------------------------------------------|
| A file path              | Read the whole file. The syllabus covers what that file assumes the reader knows. |
| A technology or a term   | Establish what it is used for here. Search the repository for it before the web.  |
| An area of this project  | Find the code and the docs for it. The syllabus covers what those depend on.      |

If the argument is empty, ask what to teach. One question, nothing else.

Teach in the language the learner writes in.

## 2. Find What They Do Not Know

List every concept in scope. Then cut the list down.

- **Cut what they already showed they know.** Their own code, their own docs, terms they used correctly in this
  session.
- **Keep what the file relies on without explaining.** A named protocol, a library, a pattern, a format, a
  guarantee, a piece of jargon.
- **Keep the thing behind the thing.** If the file uses a Redis stream, consumer groups matter more than the
  command spelling.

Order the survivors so that nothing is taught before the thing it rests on.

## 3. Show the Plan, Then Start

Show the ordered list, one line each, with a rough count of pieces. Ask two things:

- Which items they already know, so those get dropped.
- Where they want to start.

Then begin. Do not re-print the plan after every piece. Show one progress line instead: `3 / 9 · consumer groups`.

## 4. The Teaching Loop

One turn is: **a piece, then a question.** Nothing else in the turn.

**The piece.**

- Under 200 words. Prefer 100.
- One idea. If a second idea is needed to explain the first, that second idea was a missing syllabus item.
- Ground it in the learner's own file or project. Use their names, their data, their case.
- A tiny code block or a three-row table beats a paragraph.
- Say what problem the thing exists to solve before saying how it works.

**The question.**

- Ask it as plain text in the reply. Never a closed option list. The learner must be able to type anything,
  including "I don't know".
- One question. Never a numbered set.
- Never yes or no. Never answerable by copying the last sentence.
- Rotate the kind of question:

| Kind          | Shape                                                              |
|---------------|--------------------------------------------------------------------|
| **apply**     | What happens in *their* file if this is used?                      |
| **predict**   | Two consumers, one message. Who gets it?                           |
| **contrast**  | Why this and not the obvious alternative?                          |
| **diagnose**  | Here is a broken case. What is wrong?                              |
| **decide**    | Given what they now know, which way should the project go?         |

Then stop. Wait.

## 5. Answer Their Answer

Read what they actually wrote, not what a correct answer looks like.

| They said                     | Do this                                                                        |
|-------------------------------|---------------------------------------------------------------------------------|
| The right thing               | Confirm in one line. Add the one detail they left out. Next piece.               |
| Half right                    | Name the half that holds. Correct only the other half. Ask again, narrower.      |
| The wrong thing               | Say plainly it is wrong and why. Do not soften it. Then re-teach, differently.   |
| "I don't know"                | Never dump the answer. Give one hint, or one smaller question underneath it.     |
| A question back               | Answer it. Then return to the question they were on.                            |
| A guess with reasoning        | Grade the reasoning, not the conclusion. The reasoning is the thing being built. |

**A wrong answer twice on the same piece means the piece was wrong.** Change the explanation, not the volume.
Use a different angle: an analogy, a failure case, a diagram, their own code.

Never praise. "Yes" and the missing detail is the whole confirmation.

## 6. What the Learner Can Type Any Time

Recognise these in any wording, in any language:

- **deeper** — the current piece was too shallow. Split it and teach the part they asked about.
- **skip** — move to the next syllabus item. Note it as unfinished.
- **back** — return to a previous item.
- **why do I care** — answer with the decision this item affects in their project, then continue.
- **stop** — go to section 7 immediately.

## 7. Close the Session

When the syllabus is done, or on request:

- **What they can now decide.** One line per concept, in terms of their project.
- **What stayed shaky.** The pieces that needed two attempts, and the items they skipped.
- **What was not covered.** Named, so the gap is known rather than assumed away.

Write no file unless asked. If asked, the notes go where the project puts scratch files.
