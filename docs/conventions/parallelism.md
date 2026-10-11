# Conventions > Parallelism

Every module builds and tests on one machine. A few limits therefore cannot be stated per module, because each
module file describes only itself. Those limits are here. What is contended *inside* a module stays in that
module's [Agent Configuration](../../ledger-service/docs/conventions/agent.md).

## The machine

- Concurrent build or test runs, counting every module at once: **2**.
- Both may start containers. `ledger-service` and `ai-connector-service` each start Postgres, Redis and Toxiproxy
  per run. `web-app`'s suite is in-process.
- Implementation agents: **no machine-wide limit**. Each module caps its own plan.

Memory is the scarce resource. An agent waiting on a model call costs almost nothing. A running suite costs a
JVM, its containers, or a bundler, and it is those that fill the machine.

## How many modules can be worked at once

Every module queues its own runs down to one at a time. The number of runs is therefore the number of modules
being worked.

- **Two at a time is the limit.** A third is one run too many.
- **Any two modules may pair**, the two container-starting suites included.

An unexplained container-startup failure is memory pressure until proven otherwise. Rerun it before debugging it
as a real failure.
