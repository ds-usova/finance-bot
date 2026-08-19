# Conventions > Version Control

How work reaches the history. One answer for every module: a change spanning two of them is still one history,
and two policies would contradict each other inside a single commit.

- **A commit follows a passed check.** Whatever gates the change — a green suite, a clean build — the commit
  comes after it, and half-finished work does not land.
- **Every commit names the paths it takes**: the module being worked, plus any shared path that work owns. More
  than one change can sit in the working tree at once, so a commit naming nothing takes whatever else is staged
  in it.
- **Name them with `git commit -o <paths>`.** Without `-o` the commit takes the *index* for those paths, so
  anything staged under one of them between the `add` and the `commit` goes along — which is how 437 lines of an
  unrelated design once reached the history. `-o` commits the working tree's version of the named paths and
  ignores the rest of the index. A new file still needs `git add` first, since `-o` commits only what is tracked.
- **Nothing the change produced is left outside its commit.** A scope worked out from what version control
  already tracks covers what was edited and misses what was created, since a new file sits outside the index
  until it is put there.
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
- **A message says what the commit does**, in the repository's own words. An identifier that belongs to a working
  document rather than to the code is not one of them.
- **Do not squash, and do not merge into the main branch.**
