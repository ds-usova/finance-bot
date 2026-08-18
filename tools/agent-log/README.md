# The Agent Log Viewer

`tools/agent-log/agent-log.html` browses `build/agent-log.jsonl`, the file the `log-agent.ps1` hook appends to
every time a sub-agent is spawned or finishes.

## Why it exists

The log is one JSON object per line, three lines per agent, interleaved across concurrent sessions. Reading it
raw answers nothing about how long a plan's agents ran or which type dominates. The page pairs each agent's
start and stop, attaches the model and description from the spawning call, and lets a session or an agent be
removed from the file once it is no longer interesting.

## Usage

Open the file in Chrome or Edge (double-click it, or drag it into a tab) and press **Open agent-log.jsonl**,
then pick `build/agent-log.jsonl`.

| Control                     | Does                                                                        |
|-----------------------------|-----------------------------------------------------------------------------|
| Session / Type / Model      | Filters every table below the toolbar. Click a session row to filter to it. |
| Status                      | `running` is an agent with a start but no stop yet.                         |
| Search                      | Substring over id, type, model, description and session.                    |
| Elapsed by agent type       | Runs, total, average, max and min of finished agents; the bar is share.     |
| Export summary / `export`   | Markdown per agent type — count, min/max/avg tokens and time — to copy.     |
| `delete` on a row           | Removes that event, that agent's events, or that session's events.          |
| Save                        | Rewrites the opened file in place. Nothing is written until pressed.        |
| Reload                      | Re-reads the file — the hook keeps appending while the page is open.        |

## Where it stops

- In-place saving needs the File System Access API — Chrome and Edge. Elsewhere the page opens the file through
  a picker and **Save** downloads a new `agent-log.jsonl` to replace the old one by hand.
- Model and tokens come from the agent's transcript, read by the hook when the agent stops. A running agent
  shows the model the spawning call asked for, or *inherited* when it named none.
- Tokens are the sum over the agent's turns of input, output, cache-read and cache-create; the tooltip on the
  cell splits them. Nested agents' tokens are their own, not folded into the parent.
- A `PreToolUse` line is matched to the next `SubagentStart` of the same session and type. Two identical spawns
  in the same instant may swap descriptions.
- Duration is wall-clock between the start and stop hooks.
