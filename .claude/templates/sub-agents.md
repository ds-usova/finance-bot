# Sub-agents

How an agent is spawned, waited for, resumed and answered, in this harness. Written once here and linked from
every skill and agent that spawns or is spawned; a change to how the harness runs agents is an edit to this file
and to nothing else. What a spawned agent is *told* — its step, its scenarios, its conventions — is the
spawning skill's; only the mechanics are here.

## Spawning and waiting

**A turn that ends does not resume.** Nothing re-invokes an orchestrator when a run it started finishes or an
agent it spawned reports; the work stands still until the level above notices.

**Which shapes are available depends on who is waiting.** `TaskOutput` and `SendMessage` reach an orchestrator
running as a session and do not reach one running as a sub-agent, whose tool surface carries neither.

### A session waits inside the call

| To                                              | Do                                                                                                                                                                                                                       |
|-------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| run one agent and use its result                | `Agent` with `run_in_background: false`; the call returns its report                                                                                                                                                      |
| run several at once — a wave                    | every `Agent` call with `run_in_background: true`, then one `TaskOutput(<its id>, block: true, timeout)` per agent, in the same turn, before anything else. Several blocking calls in one message would also run concurrently, but nothing makes them share a message — a wave issued that way ran four bundles one after another — so the background-then-block shape is the wave's shape |
| continue an agent with the context it built     | `SendMessage` to its id — the harness resumes it **in the background**, whatever it says — and, as the very next call, `TaskOutput(<its id>, block: true, timeout)` with a timeout generous enough for the work; that call's result is the resumed turn's report |
| run a suite or a script                         | the foreground shell with a timeout for the whole thing; a background run with a watch on its file is not waiting                                                                                                          |

As a session, a background spawn or a resume not followed by its `TaskOutput`, or a run backgrounded and
watched, all end the turn with the work mid-flight. `TaskOutput` is the wait; the notification the harness sends
when a background agent finishes re-invokes nobody.

### A sub-agent orchestrator waits inline for one agent and hands a wave back

With no `TaskOutput`, these are the only two shapes available, and they answer different situations rather than
being alternatives for the same one:

| Spawning                                                             | Do                                                                                                       | Costs                                                   |
|----------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------|---------------------------------------------------------|
| one agent — a stabilization step, a refactor pass, one re-delegation | `run_in_background: false`; the call returns its report                                                  | nothing — there is no wave to lose                      |
| more than one at once — a wave                                       | spawn every bundle with `run_in_background: true`, then end the turn naming the agents it is waiting for | the level above must resume it, one round trip per wave |

**A wave spawned one bundle at a time with `run_in_background: false` is the shape to avoid.** It keeps the turn
alive and buys nothing: the phase runs serially at the wave's cost.

**Handing a wave back is not returning mid-decision.** A turn that ends with its children running is finished
work handed on — "spawned A, B and C, waiting" — while a turn that ends because a question came up is a blocker
and says so. A turn that says only "waiting" gives the level above nothing to resume against.

A suite or a script is still waited for in the foreground, whichever shape is chosen.

Where the level above sees an agent return with children in flight, it resumes that agent with one message once
they finish; the harness's task-notification is the signal, and it arrives at that level. A grandchild's report
arrives there too, so the level above relays it rather than expecting the middle agent to have seen it.

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
