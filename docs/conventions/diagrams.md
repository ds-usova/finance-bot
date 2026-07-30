# Conventions > Diagram Format

Applies to every service and to the repository's own documents — READMEs, design files, plans, use-case and
contract pages. It says what a diagram is written in; what a diagram must *show* is fixed by whoever asks for it.

Diagrams are **PlantUML** in fenced ` ```plantuml ` blocks, inline in the document — nothing is rendered to a
committed image. Structure is drawn with the **C4 model** via the bundled C4-PlantUML stdlib (`!include
<C4/C4_Context>`, `<C4/C4_Container>`, `<C4/C4_Component>` for C1, C2 and C3), and flows are drawn as plain
**sequence diagrams**.
