# Conventions > Version Control

How work reaches the history. One answer for every module: a change spanning two of them is still one history,
and two policies would contradict each other inside a single commit.

- Commit incrementally: yes — the agent commits intermediate changes as it goes, on whichever branch is
  currently checked out.
- Granularity: one commit per passed stage guardrail (stabilization, red, green, refactor, wrap-up) — not per
  step or per wave.
- Branch policy: the developer creates and checks out the branch manually before work starts; the agent never
  creates, switches, or deletes branches — it commits to the current branch only. `main` is a normal choice of
  current branch: development happens there, so a run that starts on it commits to it and asks nothing.
- Message format: `<Prefix>: <description>` — one subject line stating what the change does, and a prefix naming
  the kind of change:
  - `Feature` — new or extended functionality;
  - `Bug` — a fix for incorrect behavior;
  - `Configuration` — build, infrastructure, or application configuration;
  - `Test` — changes to test code only, such as tests written ahead of their implementation;
  - `Refactor` — behavior-preserving cleanup and restructuring.
  - `Documentation` — updates to documentation.

  Add new prefixes as new kinds of change show up. The format applies from this point in the history onward;
  earlier commits predate it.
- Message body: usually none — the subject carries the change and the diff carries the detail. Add a few lines
  only for what the diff cannot show: a constraint that forced the approach, or a consequence a later reader
  would otherwise miss. Never a list of touched files or a per-file summary (that is `git show --stat`), never
  a test count or suite status (not a property of the commit), never a restatement of the subject.
- Squash before merging: do not squash, do not merge the changes into the main branch.

## Committing While Another Module Is Being Worked

Several plans can be implemented at the same time, in one working tree, each committing its own module.

- Scope: commit the paths of the module being worked, plus any shared path that work owns. Another module's
  files may be half-written beside them, and a commit naming paths takes only those, whatever else is staged.
- A commit refused because the repository index is locked is retried once, after a moment. Another commit was
  landing at that instant; nothing is wrong. **Only that failure is retried** — a rejected hook, an empty
  commit, or a bad path is reported, never repeated. A retry that also fails is left alone: the files stay in
  the tree and the next commit takes them.
