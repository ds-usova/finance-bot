# [Conventions](../conventions.md) > Code Style

Idioms for production code and guidance for refactoring.

## Production-Code Style

### General

Applies across all layers.

- Dependency-injection style: constructor injection only — never field injection. Lombok
  `@RequiredArgsConstructor` with `final` fields is the idiom in the adapter layer; `domain`/`application` use
  plain hand-written constructors instead, since those layers stay annotation-light and Spring-free (see
  [Package Structure](architecture.md#package-structure)).
- Null-handling policy: never return `null` — return `Optional<T>` for possibly-absent values or throw a domain
  exception.
- Logging: SLF4J via Lombok `@Slf4j` (Logback config in `src/main/resources/logback.xml`); `debug` level for
  infrastructure operations, `info` for business events. Never `System.out`.
- Imports / qualified names: import types directly; never fully qualified class names inside code bodies unless
  there are no other options (e.g. classes with the same name in different packages).
- Long string literals (SQL, JSON, multi-line text): Java text blocks (`"""…"""`) — never concatenation or one
  cramped line.
- Method decomposition: extract private helpers when a method exceeds roughly one screen; one public method per
  port operation on use cases and adapters.

### Domain

- One domain exception per error case, in `bot.finance.domain.exception`.
- Keep behavior on the object it belongs to: if a method mainly introspects the internal fields of a domain
  object, move it onto that class instead of extracting it as a free function or placing it on the persistence
  entity — e.g. `BankAccount.evaluateFinancialHealth()`, never
  `evaluateFinancialHealth(BankAccount bankAccount)` living on the persistence entity.

### Application

- Use cases are plain classes with no Spring annotations, wired as beans from `@Configuration` classes in
  `adapter/config` (see [Package Structure](architecture.md#package-structure)).

### Adapter — Web

- Error-to-response mapping happens in a single global `@RestControllerAdvice` in `adapter/web` — controllers
  never catch domain exceptions themselves.

### Adapter — Persistence

- Transactions: `@Transactional` (and any other Spring transaction machinery) lives here only — the
  framework-agnostic core cannot carry it (ArchUnit-enforced).
- Domain ↔ entity mapping: mapping methods on the persistence entity class itself — `toDomain()` instance method
  and a static `fromDomain(<Domain>)` factory — never private mapping helpers scattered in the adapter.
- Persistence idioms: Spring Data JDBC repositories (`CrudRepository`/`ListCrudRepository`) with derived or
  `@Query` methods; entities are simple records/classes in `adapter/persistence`, distinct from domain types.

## Refactoring Conventions

The default cleanup checklist (cross-class duplication, duplicated test fixtures, idiom inconsistency, leftover
scaffolding, needless complexity, import hygiene) applies; the points below prioritize and extend it.

- Priorities: (1) deduplicate mapping logic and test fixtures written independently in different places;
  (2) align idioms with this file (injection style, exception mapping, text blocks); (3) import hygiene — no
  unused imports, no fully qualified names in bodies; (4) collapse needless conditionals and scaffolding left
  over from getting tests to pass.
- Extraction targets: shared test helpers go to `bot.finance.common` (stub registration into `WireMockStubs`,
  JSON handling into `JsonUtils`, builders/factories as new classes there, which then get listed in
  [Naming Conventions](testing.md#naming-conventions)); shared production mapping stays on the entity classes.
- Shared helpers that may be extended additively: `WireMockStubs`, `JsonUtils`, `AbstractSystemTest`
  (hook methods are explicitly reserved for later needs), `PersistenceAdapterTest`.
- Leave-alone list: **applied Flyway migrations are immutable** — never edit an existing migration file, always
  add a new one; `package-info.java` files; anything generated (none yet).
- Thresholds: extract only when logic repeats in 2+ classes; keep methods under one screen; otherwise use your
  judgment.
