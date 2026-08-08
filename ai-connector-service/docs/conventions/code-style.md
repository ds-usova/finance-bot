# [Conventions](../conventions.md) > Code Style

Idioms for production code and guidance for refactoring.

## Production-Code Style

### General

Applies across all layers.

- **Dependency injection**: constructor injection only — never field injection. Constructors are plain and
  hand-written in every layer, assigning `final` fields.
- **Null handling**: never return `null`. Return `Optional<T>` for a possibly-absent value, or throw a domain
  exception. The exception is a record carrying an external system's unvalidated answer, where an absent field
  is a legitimate outcome; it is normalized at the first boundary that reads it and never propagates further.
- **Logging**: through the core's own `Logger`/`LoggerFactory` ports, never a logging library directly. A class
  that logs takes the factory as a constructor parameter and derives its logger from it. `debug` for
  infrastructure operations, `info` for business events; never `System.out`. Messages use `{}` placeholders,
  never string concatenation. Domain objects normally do not log at all. **User content — the text sent in, a
  prompt, a model's raw answer — is never logged above `debug`.**
- **Imports**: import types directly. A fully qualified name appears in a body only to disambiguate two types
  with the same simple name, which here means a generated protobuf type against its domain counterpart.
- **Long string literals** (prompts, JSON, multi-line text): text blocks — never concatenation, never one
  cramped line. Text long enough to be edited as prose belongs in a resource file instead.
- **Method decomposition**: extract private helpers once a method exceeds roughly one screen. One public method
  per port operation on a use case or an adapter.
- **A method body separates its phases with a blank line** — the guards, the work, the result. A method whose
  lines run together reads as one step, and a reader has to re-derive where one phase ends and the next begins.
- **A stateless helper class is named for its role**, never `*Utils`. One that converts between two
  representations is a `*Mapper`; one that writes a core type as text for a transport is a `*Renderer`. A class
  that goes both ways, or that answers a question about a thing rather than transforming it, takes that thing's
  own name instead — `CallerTokenContext`. `Utils` names a bucket, and a bucket collects whatever is convenient
  to put down.
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

- Exceptions are few and shared, in `domain/exception`. Add a type only when a caller must **branch** on it;
  otherwise the message carries what distinguishes one failure from another.
- Behaviour lives on the object that owns the data. A method that mainly reads a domain object's own fields
  belongs on that class — not as a free function taking it as a parameter, and not on a mapper.
- Value objects validate themselves in their compact constructor, so an invalid instance cannot exist anywhere
  in the system. Parsing that can fail is exposed as a named static factory that throws.
- A value parsed from free text is matched with a factory returning `Optional<T>`, not a throwing `valueOf`:
  input that matches nothing is an expected outcome, and the caller turns it into a result, not an error.

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
- **Outbound-port results do not validate themselves.** A record carrying an external system's answer holds
  nullable fields and accepts anything, because a bad answer is a normal outcome and a throwing constructor
  would turn it into an error. Deciding what such an answer means is the use case's job — and is why the use
  case is more than a pass-through.

### Adapter — gRPC

- The service implementation extends the generated base class and does three things: map the request into a
  command, call the inbound port, map the result onto the response. Business decisions never live here.
- Domain ↔ proto mapping lives in one static mapper class — never private helpers scattered through the service
  implementation.
- Exception-to-status mapping lives in a single `GrpcExceptionHandler` bean declared from configuration; the
  service implementation never catches domain exceptions itself. A handler returns `null` for a throwable it
  does not recognize, so the next one gets a turn and an unmapped failure does not leak its message.
- Every call terminates its `StreamObserver` — `onNext` then `onCompleted`, or `onError`. A caller waits until
  its deadline for one.

### Adapter — AI

- The chat client is a bean, built once from its injected builder with the prompt and default options applied
  there — never rebuilt per call, never constructed inside the adapter.
- A tool provider is attached per call, on `chatClient.prompt()...tools(...)`, never as a default on the bean.
- Model, options and prompt location come from configuration, never hard-coded.
- A provider or tool failure — transport error, non-2xx, unparseable body — is translated into a module
  exception at this boundary and stops. A framework or HTTP-client exception never escapes into the core.

## Refactoring Conventions

The default cleanup checklist (cross-class duplication, duplicated test fixtures, idiom inconsistency, leftover
scaffolding, needless complexity, import hygiene) applies; the points below prioritize and extend it.

- **Priorities**: (1) deduplicate mapping logic and test fixtures written independently in different places;
  (2) align idioms with this file; (3) import hygiene — no unused imports, no fully qualified names in bodies;
  (4) collapse needless conditionals and scaffolding left over from getting tests to pass.
- **Extraction targets**: shared test helpers go to the shared test package, and get listed there (see
  [Testing Conventions](testing.md#naming-conventions)); shared production mapping goes into the proto mapper
  or onto the value object that owns the data, never into a new helper beside the service implementation.
- **Misplaced behaviour is moved, not counted.** A private static method taking a domain object and reading only
  its own fields belongs on that type ([Domain](#domain)). Moving it is a relocation, not an extraction, so the
  threshold below does not apply: one occurrence is enough.
- **Leave alone**: generated sources — a change there means a change to the schema that produced them; the
  field numbers of an already-released message, never renumbered or reused; `package-info.java` files.
- **Thresholds**: extract only when logic repeats in 2+ classes; keep methods under one screen; otherwise use
  your judgment.
