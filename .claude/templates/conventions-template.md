# Conventions — `<module-name>`

Copy this file to `<module>/docs/conventions.md` and fill in every placeholder with this module's real facts. This
is the single source of truth for the module's architecture, style, and tooling conventions — anyone working on
this module should be able to read it and know how things are done here. If a section doesn't apply to this
module, say so explicitly rather than deleting it — a missing answer should never have to be guessed.

## Table of Contents

- [Orientation](#orientation)
  - [Project Structure](#project-structure)
  - [Documentation References](#documentation-references)
  - [Tech Stack](#tech-stack)
- [Architecture & Layering](#architecture--layering)
  - [Architecture Enforcement](#architecture-enforcement)
  - [Layer → Test-Type Mapping](#layer--test-type-mapping)
- [Testing Conventions](#testing-conventions)
  - [Test Tooling](#test-tooling)
  - [Naming Conventions](#naming-conventions)
  - [Testing Style](#testing-style)
- [Code Style](#code-style)
  - [Production-Code Style](#production-code-style)
  - [Refactoring Conventions](#refactoring-conventions)
- [Process & Commands](#process--commands)
  - [Version Control](#version-control)
  - [Parallelism](#parallelism)
  - [Build & Test Commands](#build--test-commands)
  - [File Locations](#file-locations)

## Orientation

### Project Structure

Orient the reader in this module's physical layout — every package/folder path named elsewhere in this file is
relative to what's stated here.

- Module root: `<path from the repo root, e.g. widget-service/; or "repo root" for a single-module repo>`
- Build tool / module system: `<e.g. Gradle multi-module — this module is the :widget-service subproject; or "single Gradle project, no submodules">`
- Base package / namespace: `<e.g. com.example.widget — every package mentioned elsewhere in this file is relative to this>`
- Source set layout: `<e.g. src/main/java and src/test/java (standard Gradle layout); src/testFixtures/java for shared test builders/factories, if used>`
- Package layout: replace the tree below with this module's actual layer/package structure, one line per folder
  with a short comment on what it holds — so new classes land in the right package without guessing.

```
<e.g., replace with this module's real layer/package tree:>
com.example.widget
├── domain          # enterprise business rules
│   ├── model       # entities with identity, e.g. Order, Customer
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

### Documentation References

Point to existing documentation worth reading before making changes — architecture diagrams, ADRs, API references,
runbooks. This is background context, not instruction; it never overrides a rule stated elsewhere in this file.

- Architecture / diagrams: `<e.g. <module>/README.md — a C4 Component diagram of ports and adapters; or "none">`
- ADRs / design decisions: `<path or link, e.g. docs/adr/; or "none">`
- API reference: `<e.g. the API schema file itself (see File Locations below), or a hosted docs link; or "none">`
- Runbooks / operational docs: `<path or link, e.g. docs/runbooks/; or "none">`
- Other: `<anything else worth reading — a domain glossary, an external system's docs; or "none">`

### Tech Stack

State the module's production language, framework, the infrastructure it talks to, and how its API contract is
generated.

- Language / framework: `<e.g. Java 21 / Spring Boot, Python / FastAPI, TypeScript / NestJS>`
- Database: `<e.g. PostgreSQL; or "none — module is stateless">`
- Persistence framework / ORM: `<e.g. Spring Data JPA / Hibernate, a hand-rolled JDBC repository, Prisma; or "n/a">`
- Messaging / event broker: `<e.g. RabbitMQ via Spring AMQP, Kafka; or "none">`
- Caching: `<e.g. Redis; or "none">`
- External services consumed: `<other systems this module calls in production and how — e.g. a payments API over REST, a notification service over gRPC; or "none">`
- Contract-first codegen: `<what is generated from the API schema and what the generator owns — e.g. request/response models with validation annotations are generated, controllers are hand-written against generated interfaces; or "none — everything is hand-written">`

## Architecture & Layering

### Architecture Enforcement

Name the module's dependency-rule test — the check that keeps the clean/hexagonal layers (domain, application,
adapters) from depending on each other in the wrong direction. Its run command belongs in **Build & Test Commands**
below, alongside every other command; when to run it (CI, pre-commit, after every layer change) is a workflow
decision, not a fact about the module to restate here.

- Tool: `<e.g. ArchUnit, dependency-cruiser, import-linter, deptrac, or "none yet">`
- Test class / config file: `<e.g. com.example.architecture.CleanArchitectureTest, .dependency-cruiser.js>`

### Layer → Test-Type Mapping

List which packages/folders map to each test layer, so it's clear where unit, integration, and system tests
target.

- **Unit-test targets:** `<packages/folders containing pure logic — e.g. domain/, application/usecase/>`
- **Integration-test targets (outbound adapters):** `<packages/folders containing outbound adapters — e.g. adapter/persistence/, adapter/httpclient/>`
- **Integration-test targets (inbound adapters):** `<packages/folders containing inbound adapters tested as framework slices, and the kinds of entry point each covers — e.g. adapter/web/ (REST controllers), adapter/messaging/ (event listeners), adapter/scheduling/ (cron triggers)>`
- **System-test entry points:** `<by default, the same inbound adapters listed above, entered end-to-end instead of as a framework slice — list only exceptions here, e.g. an entry point with no system-level coverage; or "same as integration-test inbound adapters above">`

## Testing Conventions

### Test Tooling

State the tools used to exercise the module under test (e.g. a container-based database, an HTTP stubbing server,
an API-level test client).

- Test framework: `<e.g. JUnit 5, pytest, Jest>`
- Container-based dependencies for tests: `<e.g. Testcontainers running Postgres, a Docker-composed message broker>`
- HTTP stubbing for outbound calls: `<e.g. WireMock, nock, responses>`
- API-level test client: `<e.g. RestAssured, httpx test client, supertest>`
- Inbound-adapter slice testing: `<the framework mechanism for booting one inbound adapter with its inbound-port beans mocked — e.g. @WebMvcTest with MockMvc and @MockitoBean>`
- Firing non-HTTP entry points in system tests: `<how a system test makes the framework fire a scheduled/message-driven entry point as in production — e.g. a test-profile schedule override plus Awaitility, publishing to the containerized broker; or "none — module has no non-HTTP entry points">`

### Naming Conventions

Describe the naming patterns test code in this module follows.

- Test method naming pattern: `<e.g. when<Condition>_then<Result>()>`
- Test class naming: `<e.g. <ClassUnderTest>Test for unit/integration, <ScenarioName>Test for system tests>`
- Inbound-adapter test class naming: `<e.g. <ControllerClass>Test — or state that it follows the general integration pattern above>`
- Mapper/helper class conventions: `<e.g. static *Utils class per package, or a *Mapper interface with a generated impl>`
- Test base classes and what they provide: `<e.g. AbstractIntegrationTest boots the full app context, wires the test database and HTTP stub server, and resets stub state after each test>`
- Existing shared test builders/factories and what they provide: `<e.g. WidgetTestDataFactory creates valid Widget aggregates for reuse across integration and system tests — list every reusable builder/factory here so new tests reuse it instead of recreating it; or "none yet">`

### Testing Style

Style preferences test code in this module follows, beyond naming.

- Parameterized/table-driven tests: `<e.g. prefer @ParameterizedTest when the same behaviour is exercised across several values (enum cases, thresholds, null-handling matrices); never duplicate a case as both a parameterized entry and a one-off test>`
- Assertion library / style: `<e.g. AssertJ assertThat(...) only — never JUnit assertEquals/assertTrue>`
- Test description annotations: `<e.g. every test carries @DisplayName in the format "when [condition] - then [outcome]", or "none">`
- Imports / qualified names: `<e.g. import types directly; never use fully qualified class names inside code bodies>`
- Mocked-port verification depth: `<e.g. verify the call and its key arguments on the mocked inbound port; avoid full object-equality interaction assertions>`

## Code Style

### Production-Code Style

Style and idioms this module's production code follows.

- Dependency-injection style: `<e.g. constructor injection only — never field injection>`
- Null-handling policy: `<e.g. never return null — use Optional or throw a domain exception>`
- Error/exception conventions: `<e.g. domain exceptions per error case; a shared NotFound exception type; error-to-response mapping via a global handler>`
- Logging: `<e.g. structured logger per class, debug level for infrastructure operations; or "no logging conventions yet">`
- Imports / qualified names: `<e.g. import types directly; never use fully qualified class names inside code bodies>`
- Method decomposition: `<e.g. extract private helpers when a method exceeds one screen; one public method per port operation>`
- Domain ↔ entity mapping style (outbound adapters): `<e.g. toDomain()/fromDomain() on the entity class — never private helpers in the adapter; or a dedicated mapper class per adapter>`
- Persistence / outbound HTTP-client idioms: `<e.g. use the ORM repository methods where available; transaction annotations on write methods; declarative HTTP client interfaces>`
- Inbound binding/validation/error mapping: `<the framework mechanism the inbound adapter uses for request binding, validation, and error-to-response mapping — e.g. bean validation on generated request models plus a global exception handler>`

### Refactoring Conventions

Guidance for refactoring this module's code — what a cleanup pass should prioritize, where extracted code belongs,
and what must be left alone. A reasonable default checklist applies even without this section (cross-class
duplication, duplicated test fixtures, idiom inconsistency, leftover scaffolding, needless complexity, import
hygiene); this section prioritizes, extends, or overrides those defaults. If this module has no special
refactoring guidance, say so — the Production-Code Style and Testing Style sections above still apply.

- Priorities: `<what to tackle first — e.g. deduplicating mapping logic; import hygiene (no unused imports, no fully qualified names in bodies); collapsing needless conditionals>`
- Extraction targets: `<where extracted shared code goes — e.g. a *Mapper class per adapter package; shared test builders into a testFixtures/ source set>`
- Shared helpers that may be extended: `<existing helper/fixture classes that may be extended additively when consolidating duplicates — e.g. TestData builder class, AbstractIntegrationTest; or "none">`
- Leave-alone list: `<what must never be touched, even when it looks duplicated or inconsistent — e.g. generated code, DTOs mirroring the API schema, a package mid-migration; or "nothing">`
- Thresholds: `<when extraction is worth it — e.g. extract only when logic repeats in 2+ classes; keep methods under one screen; or "use your judgment">`

## Process & Commands

### Version Control

How and when changes to this module get committed — useful both for a contributor's own workflow and for any tool
that might commit on their behalf. If this section is silent, no commit policy is assumed and nothing should be
auto-committed.

- Commit incrementally: `<yes/no — e.g. "yes, after each logical unit of work", or "no — commit manually at the end">`
- Granularity: `<e.g. one commit per logical step (a class, a test file, a migration); one commit per feature; or "as small as reasonably possible">`
- Message format: `<e.g. Conventional Commits (feat:, fix:, refactor:); "[<ticket-id>] <one-line summary>"; whatever this repo's git history already uses>`
- Squash before merging: `<e.g. "no — keep the full commit history", "yes — squash into one commit before merging to main">`
- Branch policy: `<e.g. "commit directly on the current branch", "create a feature branch per unit of work">`

### Parallelism

How many test runs or build tasks can safely execute concurrently against this module. Concurrent processes share
one build daemon, one container runtime (for tests that boot Testcontainers or similar), and finite RAM — too many
running at once produces false "setup crash" failures that look like a broken test but are really resource
contention between siblings. A missing or silent section here means no known cap.

- Max concurrent test runs: `<e.g. "8", or "unlimited" if the module's build/test infra handles it>`
- Max concurrent build/implementation tasks per batch: `<e.g. "4">`
- Notes: `<e.g. "tests that boot Testcontainers count double toward the cap"; or "none">`

### Build & Test Commands

Exact commands, runnable from the module (or repo) root — state which. Anyone working on this module needs these
to compile, test, and verify changes.

- Compile / type-check: `<e.g. ./gradlew compileJava compileTestJava, npx tsc --noEmit>`
- Run a single test class: `<e.g. ./gradlew test --tests "<FullyQualifiedTestClassName>", npx jest <path>>`
- Run the module's full test suite: `<e.g. ./gradlew test>`
- Run the architecture-enforcement test: `<e.g. ./gradlew test --tests "com.example.architecture.*", npx depcruise src>`
- Run contract codegen (if any): `<e.g. ./gradlew openApiGenerate; or "n/a">`

### File Locations

Give exact paths (or the intended path with `TBD — confirm path` if it doesn't exist yet).

- API schema file: `<e.g. src/main/resources/schemas/api.yaml>`
- Migration folder + naming scheme: `<e.g. src/main/resources/db/migration/V<NNN>__<name>.sql>`
- Manual/`.http` request files (if any): `<path, or "none">`
