# [Conventions](../conventions.md) > Code Style

Idioms for production code and guidance for refactoring.

## Production-Code Style

### General

Applies across all layers.

- **Dependency injection**: constructor injection only — never field injection. Constructors are plain and
  hand-written in every layer, assigning `final` fields.
- **Null handling**: never return `null`. Return `Optional<T>` for a possibly-absent value, or throw a domain
  exception.
- **Logging**: through the core's own `Logger`/`LoggerFactory` ports, never a logging library directly. A class
  that logs takes the factory as a constructor parameter and derives its logger from it. `debug` for
  infrastructure operations, `info` for business events; never `System.out`. Messages use `{}` placeholders,
  never string concatenation. Domain objects normally do not log at all.
- **Long string literals** (SQL, JSON, multi-line text): text blocks — never concatenation, never one cramped
  line.
- **Method decomposition**: extract private helpers once a method exceeds roughly one screen. One public method
  per port operation on a use case or an adapter.
- **A method body separates its phases with a blank line** — the guards, the work, the result. A method whose
  lines run together reads as one step, and a reader has to re-derive where one phase ends and the next begins.
- **A stateless helper class is named for its role**, never `*Utils`. One that writes a core type as text for a
  transport is a `*Renderer`; one that converts between two representations is a `*Mapper`. A class that goes both
  ways, or that answers a question about a thing rather than transforming it, takes that thing's own name instead
  — `ProposalCallbackData`, `ColumnLimits`, `ForeignKeyViolations`. `Utils` names a bucket, and a bucket collects
  whatever is convenient to put down.
- **Member order**: fields, then constructors, then methods by descending visibility — `public`,
  package-private, `protected`, `private`. A reader meets the type's API before its internals.
- **Comments and javadoc**: the fewer the better. Write one only for what the code cannot show — third-party
  behaviour a reader would otherwise have to go and look up, an ordering that is load-bearing, or a workaround
  together with the constraint forcing it. Never restate a name, a signature, or an annotation, and never
  justify a design decision: conventions belong in this directory, and a second copy in a javadoc is one more
  thing to keep in sync. The same goes for `@param`/`@return` tags that only spell the parameter name out again.
  **Never cite a plan step or a design decision by number** — `D7`, `RI03`. Those live in an archived task
  directory that a reader of this file has no reason to open, and they name nothing once the plan is finished.
  Write the reason itself, or leave it out.

### Domain

- One exception type per error case, in `domain/exception`.
- Behaviour lives on the object that owns the data. A method that mainly reads a domain object's own fields
  belongs on that class — not as a free function taking it as a parameter, and not on a persistence entity.
- Value objects validate themselves in their compact constructor, so an invalid instance cannot exist anywhere
  in the system. Parsing that can fail is exposed as a named static factory that throws.
- **Entities validate themselves too**, in the factory or constructor that builds them, throwing their domain
  exception: every field the entity cannot exist without is asserted there. A command's checks cover the shape of
  incoming input; an entity's cover the entity being a thing that can exist, and a port taking an entity is
  reachable without any command. Column widths are the exception, and stay with the adapter that knows the schema
  ([ADR 0004](../adr/0004-column-widths-are-checked-in-the-persistence-adapter.md)).
- **An entity's identity is its stored id.** Entities extend `domain/model/Entity`, which holds the id and
  implements `equals`/`hashCode` as `final`: same concrete class and same id means the same entity, and an entity
  whose id is absent equals only itself. A business key that is not the id is a named method, never `equals`.
- **A reference to something that is not stored raises a not-found domain exception**, in the use case that looks
  it up and in the adapter whose constraint rejects it alike. The split is by what the caller supplied: a
  not-found exception is raised for an **id**, while a **name the caller chose** that resolves to nothing raises
  that concept's own invalid-argument exception instead. The storage-failure exception means the store failed,
  and nothing else — a caller acts differently on the two.
- `domain/model` holds classes; `domain/value` and `application/dto` hold records. An entity cannot use a record's
  generated `equals`, which covers every component rather than the identity.

### Application

- Use cases are plain classes with no framework annotations, wired as beans from configuration in the adapter
  layer.
- **An inbound-port command is named `<UseCase>Command`**, so a reader tells it from the domain type it carries
  data toward. The input of an *outbound* port is not a command and keeps its own name.
- **Inbound-port commands validate themselves** in their compact constructor, throwing a domain exception. A
  use case therefore trusts its command's fields and checks only that the command itself is present. Validating
  in both places is the failure mode this rule exists to prevent: the two checks drift apart, and neither
  reader can tell which one is authoritative.
- An adapter mapping external input into a command checks its own preconditions **before** constructing it, so
  that unusable input takes that adapter's normal rejection path instead of surfacing as an exception from the
  record.
- A command's optional text field normalizes a present-but-blank value to `Optional.empty()` rather than
  rejecting it, so absence has one representation by the time anything downstream reads it.
- A port interface documents the runtime exceptions its operations throw, as `@throws` javadoc — the one
  exception to the no-`@param`/`@return` rule, since an unchecked exception appears in no signature.

### Adapter — Web

- Error-to-response mapping lives in a single global `@RestControllerAdvice`. Controllers never catch domain
  exceptions themselves.

### Adapter — Persistence

- Transactions: Spring's transaction machinery lives here only — the framework-agnostic core cannot carry it
  (enforced).
- Domain ↔ entity mapping lives on the persistence entity: a `toDomain()` instance method and a static
  `fromDomain(...)` factory — never private mapping helpers scattered through the adapter.
- **A read-model projection owns its mapping the same way**: a `to<ReadModel>()` instance method on the projection
  record. An adapter's query method reads as
  `repository.find….stream().map(Projection::toSummary).toList()` and holds no mapping of its own.
- Persistence entities are types of their own, distinct from domain types. Repositories are Spring Data JDBC
  interfaces with derived or `@Query` methods.
- An outbound adapter translates every runtime exception its infrastructure raises into a domain exception; no
  framework type crosses an outbound port.

## Refactoring Conventions

The default cleanup checklist (cross-class duplication, duplicated test fixtures, idiom inconsistency, leftover
scaffolding, needless complexity, import hygiene) applies; the points below prioritize and extend it.

- **Priorities**: (1) deduplicate mapping logic and test fixtures written independently in different places;
  (2) align idioms with this file; (3) no fully qualified names in bodies; (4) collapse needless conditionals
  and scaffolding left over from getting tests to pass.
- **Extraction targets**: shared test helpers go to the shared test package, and get listed there (see
  [Testing Conventions](testing.md#naming-conventions)); shared production mapping goes onto the type that owns
  the data, not into a new helper class.
- **Misplaced behaviour is moved, not counted.** A private static method taking a domain object and reading only
  its own fields belongs on that type ([Domain](#domain)). Moving it is a relocation, not an extraction, so the
  threshold below does not apply: one occurrence is enough.
- **Leave alone**: an applied migration — never edit one, always add a new one; `package-info.java` files;
  anything generated.
- **Thresholds**: extract only when logic repeats in 2+ classes; keep methods under one screen; otherwise use
  your judgment.
