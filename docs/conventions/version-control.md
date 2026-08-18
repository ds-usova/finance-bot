# Conventions > Version Control

How work reaches the history. One answer for every module: a change spanning two of them is still one history,
and two policies would contradict each other inside a single commit.

- **Commit incrementally.** Intermediate changes land as the work goes, on whichever branch is checked out.
- **One commit per passed stage guardrail.** A plan's stages are stabilization, red, green, refactor and wrap-up;
  a step or a wave inside one earns no commit of its own. Work that has no stages gates each edit on its own test
  run, and that run is the guardrail the commit follows.
- **Every commit names the paths it takes** — those of the module being worked, plus any shared path that work
  owns. The working tree and its index are shared whether or not another plan is visibly running, so a commit
  naming nothing takes whatever else is staged in it.
- **Name them with `git commit -o <paths>`.** Without `-o` the commit takes the *index* for those paths, and
  another session can stage a file under one of them between the `add` and the `commit` — which is how 437 lines
  of a design nobody here was writing reached the history. `-o` commits the working tree's version of the named
  paths and ignores the rest of the index. A new file still needs `git add` first, since `-o` commits only what is
  tracked.
- **Nothing a stage produced is left outside its commit.** A scope worked out from what version control already
  tracks covers what the change edited and misses what it created, since a new file sits outside the index until
  it is put there.
- **The branch already exists and is already checked out.** Commits go to the current branch; the work creates,
  switches and deletes none. `main` is a normal choice of current branch — development happens there, so work
  that starts on it commits to it.
- **`<Prefix>: <description>`**, one subject line stating what the change does:

  | Prefix          | For                                                                 |
  |-----------------|---------------------------------------------------------------------|
  | `Feature`       | new or extended functionality                                       |
  | `Bug`           | a fix for incorrect behaviour                                       |
  | `Configuration` | build, infrastructure or application configuration                  |
  | `Test`          | test code only, such as tests written ahead of their implementation |
  | `Refactor`      | behaviour-preserving cleanup and restructuring                      |
  | `Documentation` | updates to documentation                                            |

  Add new prefixes as new kinds of change show up. The format applies from this point in the history onward;
  earlier commits predate it.

- **A body is usually none.** The subject carries the change and the diff carries the detail. Add a few lines only
  for what the diff cannot show: a constraint that forced the approach, or a consequence a later reader would
  otherwise miss. Never a list of touched files or a per-file summary — that is `git show --stat` — never a test
  count or suite status, and never a restatement of the subject.
- **Do not squash, and do not merge into the main branch.**

## Committing While Another Module Is Being Worked

Several plans can be implemented at the same time, in one working tree, each committing its own module.

- **Another module's files may be half-written beside yours**, which is what the path-naming rule above is for: a
  commit naming its paths takes only those, whatever else is staged.
- **A commit refused because the repository index is locked is retried once**, after a moment. Another commit was
  landing at that instant; nothing is wrong.
- **Only that failure is retried.** A rejected hook, an empty commit, or a bad path is reported, never repeated. A
  retry that also fails is left alone: the files stay in the tree and the next commit takes them.
