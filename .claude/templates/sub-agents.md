# Sub-agents

How an agent is spawned, waited for, resumed and answered, in this harness. Written once here and linked from
every skill and agent that spawns or is spawned; a change to how the harness runs agents is an edit to this file
and to nothing else. What a spawned agent is *told* — its step, its scenarios, its conventions — is the
spawning skill's; only the mechanics are here.

## Spawning and waiting

**A turn that ends does not resume.** Nothing re-invokes an orchestrator when a run it started finishes or an
agent it spawned reports; the work stands still until the level above notices. So an orchestrator waits inside
the call, always, in one of these shapes:

| To                                              | Do                                                                                                                                                                                                                       |
|-------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| run one agent and use its result                | `Agent` with `run_in_background: false`; the call returns its report                                                                                                                                                      |
| run several at once                             | several such `Agent` calls **in one message**; they run concurrently and the message returns when the last has                                                                                                             |
| continue an agent with the context it built     | `SendMessage` to its id — the harness resumes it **in the background**, whatever it says — and, as the very next call, `TaskOutput(<its id>, block: true, timeout)` with a timeout generous enough for the work; that call's result is the resumed turn's report |
| run a suite or a script                         | the foreground shell with a timeout for the whole thing; a background run with a watch on its file is not waiting                                                                                                          |

A spawn in the background, a resume not followed by `TaskOutput`, or a run backgrounded and watched, all end the
turn with the work mid-flight. Where the level above sees such an agent return with children in flight, it
resumes that agent with one message once they finish; the harness's task-notification is the signal, and it
arrives at that level.

**Every spawn passes `model`**, from the module conventions' **Sub-Agent Models** section — the executing model
for step work, the deciding model for planning, review and the refactor pass. Only a module with no such section
falls back to the default.

**Point a sub-agent at the rule; do not restate it.** A rule the repository writes down is passed as the file that
owns it, named so the agent reads it there — never as a remembered version, which is a second copy that can drift
in the one place no review looks. The same for counts and inventories drawn from the tree: read, never recall. A
prompt carries the agent's own context — its target, its scenarios, what it may not touch — and pointers for
everything else.

**A message is a resume, not a conversation.** An orchestrator sends one when it wants the agent's own context
kept — a fix to work it just reported. It never sends one to ask a question the agent should have put in its
report, and it never leaves the message unanswered.

## Reporting back

**The report is the only channel back.** End the turn with a short, structured report the orchestrator can act
on. The orchestrator is not addressable by name — never send it a message; anything you would have asked goes in
the report as a blocker. A message arriving from the orchestrator mid-task resumes you: answer it the same way,
with a report at the end of that turn, never a message back — the orchestrator is blocked on exactly that report.
