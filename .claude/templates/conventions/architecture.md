# [Conventions](../conventions.md) > Architecture & Layering

Where code goes, which layer may depend on which, and what a document draws.

## Package Structure

Orient the reader in the module's physical layout — every path named elsewhere in these files is relative to what
is stated here.

- Module root: `<path from the repo root, e.g. widget-service/; or "repo root" for a single-module repository>`
- Build tool / module system: `<e.g. Gradle multi-module — this module is the :widget-service subproject>`
- Base package / namespace: `<e.g. com.example.widget>`
- Source set layout: `<e.g. src/main/java, src/test/java, src/testFixtures/java for shared test builders>`

Replace the tree below with the module's real layer/package structure, one line per folder with a short note on
what it holds — so a new class lands in the right place without guessing.

```
<e.g., replace with this module's real tree:>
com.example.widget
├── domain          # enterprise business rules
│   ├── model       # entities with identity
│   ├── value       # value objects
│   └── exception
├── application     # application business rules
│   ├── usecase
│   ├── port        # inbound/outbound port interfaces
│   └── dto
└── adapter         # interface adapters
    ├── web
    └── persistence
```

## Naming Across the Layer Boundary

`<how the same concept is named on each side of a boundary — e.g. a domain Expense, its persistence ExpenseRecord,
its wire ExpenseResponse; or "no boundary-specific naming rules">`

## File Locations

Exact paths, or the intended path with `TBD — confirm path` where it does not exist yet.

- API schema file: `<e.g. src/main/resources/schemas/api.yaml; or "none">`
- Migration folder + naming scheme: `<e.g. src/main/resources/db/migration/V<NNN>__<name>.sql; or "none">`
- Generated sources: `<e.g. build/generated/sources/…; or "none">`
- Manual / `.http` request files: `<path; or "none">`

## Architecture Enforcement

The check that keeps the layers from depending on each other in the wrong direction. Its run command belongs in
[Build](build.md), with every other command.

- Dependency rule: `<which layer may depend on which — e.g. domain depends on nothing; application depends on
  domain only; adapters depend on application ports>`
- Tool: `<e.g. ArchUnit, dependency-cruiser, import-linter, deptrac; or "none yet — the rule is reviewed by hand">`
- Test class / config file: `<e.g. com.example.architecture.CleanArchitectureTest, .dependency-cruiser.js>`

## Diagram Format

What diagrams in this module's documents are written in, so a document renders wherever the docs are read. What a
diagram must *show* is fixed by whoever asks for it; this section only says what to write it in.

- Diagram language: `<e.g. PlantUML, Mermaid, Structurizr DSL, D2>`
- Fenced-block language tag: `<the tag a fenced block carries so the renderer picks it up — e.g. plantuml, mermaid>`
- Preamble / includes: `<lines a diagram needs before its own content — e.g. !include <C4/C4_Component>; or "none">`
- Renderer constraints: `<anything limiting what the toolchain can draw — e.g. "GitHub renders Mermaid natively but
  not PlantUML"; or "none">`
- Rendered output: `<where generated images live, if any are committed; or "none — diagrams live inline">`
