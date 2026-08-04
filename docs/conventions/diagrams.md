# Conventions > Diagram Format

Applies to every service and to the repository's own documents — READMEs, design files, plans, use-case and
contract pages. It says what a diagram is written in; what a diagram must *show* is fixed by whoever asks for it.

Diagrams are **PlantUML** in fenced ` ```plantuml ` blocks, inline in the document — nothing is rendered to a
committed image. Structure is drawn with the **C4 model** via the bundled C4-PlantUML stdlib (`!include
<C4/C4_Context>`, `<C4/C4_Container>`, `<C4/C4_Component>` for C1, C2 and C3). Flows are drawn as **sequence** or
**activity** diagrams — see below.

## Choosing a Flow Diagram

**First: does the flow earn a diagram?** An activity diagram earns its place when the *shape* carries something
a list cannot — arms that rejoin, a loop, a fork, a guard that changes what comes after it. A straight line of
guards that each end the flow is a table of condition and outcome; drawing it spends a screen to say what five
rows say, and the use-case pages already carry that table under **Outcomes**.

- **Sequence** — when the participants are the content: who calls whom, in what order, and what crosses each
  boundary. Needs no include.
- **Activity** — when the decisions are the content: the branches, guards and loops one flow runs through. Uses
  `start` / `stop`, `if (…) then (…)` / `else` / `endif`, `repeat`, `fork`. Needs no include.

**The test: read the branches.** If every arm of an `alt` names the same one or two participants, the diagram is
about logic rather than interaction, and the lifelines are repeated scenery — an activity diagram states the same
thing once. If the arms differ in *who* takes part, it is a sequence diagram.

A flow with both — several participants *and* real branching — is two diagrams, not one overloaded diagram: a
sequence diagram for the exchange, an activity diagram for the decision it turns on. Neither restates the other.

## Component Boundaries

A component diagram carries one boundary per layer — `domain`, `application` — and then **one boundary per partner
system and direction**, not a single pair of inbound and outbound boxes.

- **Label**: `adapter (inbound) — Telegram`, `adapter (outbound) — Postgres`, `adapter (outbound) — AI Connector`.
  Direction, then the system, named as a reader knows it. No package paths.
- **A system that is talked to both ways gets a box each way.** Telegram delivers updates and receives replies, so
  an inbound Telegram box and an outbound Telegram box stand side by side.
- **Every class fronting that system sits in its box**, mappers and renderers included, because all of them break
  when that one API changes.
- **Adapters fronting no external system** — use-case wiring, logging — stay out of the diagram unless the change
  is about them.

A reader then sees what a change to one partner system reaches, which is the question a component diagram is read
to answer.

## Layout

A structure diagram reads left to right along the call chain: **whoever initiates on the left**, the service's own
elements in the middle, **the things it depends on — ports, adapters, stores, other services — on the right**.

Direction is stated, never left to the renderer. Relations carry `Rel_R` / `Rel_L` / `Rel_D`, and elements that
must line up are pinned with `Lay_*`; a diagram whose elements move when one is added was drawn without them.
