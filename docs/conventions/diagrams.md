# Conventions > Diagram Format

Applies to every service and to the repository's own documents — READMEs, design files, plans, use-case and
contract pages. It says what a diagram is written in; what a diagram must *show* is fixed by whoever asks for it.

Diagrams are **PlantUML** in fenced ` ```plantuml ` blocks, inline in the document — nothing is rendered to a
committed image. Structure is drawn with the **C4 model** via the bundled C4-PlantUML stdlib (`!include
<C4/C4_Context>`, `<C4/C4_Container>`, `<C4/C4_Component>` for C1, C2 and C3), and flows are drawn as plain
**sequence diagrams**.

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
