# [Conventions](../conventions.md) > Architecture & Layering

## Directory Structure

```
src/
  api/          the only place fetch is called
  auth/         session state, its types and the route guard
  components/   presentational components, reusable across pages
    ui/         the shadcn/ui components, copied in as source
  i18n/         the i18next instance, the English catalogue and the key type derived from it
  lib/          the class-name helper every components/ui/ component imports
  pages/        one component per route, composing the rest
  testing/      fixtures the tests share, built by hand and never imported by production code
  theme/        the light or dark choice: resolved, applied to the document, stored
  routes.tsx    the route table
  App.tsx       the provider and router shell
  main.tsx      the mount point
```

## Dependency Rules

- **`fetch` appears nowhere outside `api/`.** A component that needs data calls a named function in `api/`, so
  the transport, the credentials mode and the CSRF header have one home.
- **`api/` never imports from `pages/` or `components/`.** It depends on the types in `auth/types.ts` and
  nothing else in the tree.
- **A component in `components/` is presentational.** It takes what it renders as props and calls back through
  props. It does not read the auth context.
- **A pure helper sits in `components/` too**, beside the components that render through it. `lib/` holds one
  thing: the class-name helper every `components/ui/` component imports.
- **A page composes.** Reading context, calling `api/`, and deciding what to render belong to `pages/`.
- **A pure helper shared by more than one page sits in `pages/` too**, beside the pages that call it — the way a
  shared rendering helper sits in `components/`.
- **The shell is a page.** `pages/AppShell.tsx` is the layout element every route renders inside. It reads the
  session to decide whether the sign-out control is shown.
- **Session state has one owner**, the auth context. Nothing else stores who is signed in.

## A Copied `components/ui/` Component

The registry's source is a starting point, not a finished component. Adapting it to this module's
[Code Style](code-style.md) is the first half; the second is what the copy must carry before it is used:

- **A popup that can hold a list is bounded and scrolls.** A list the data can grow is cut off at the popup's
  edge otherwise, and the entries past the cut cannot be reached at all.
- **A block sits on `--card`.** `--surface` is the page behind it, so a block wearing it reads as a gap.
- **A control that stands beside another shares its shape** — height, padding, radius, surface, focus ring. A
  button used as a field takes the field size rather than its own.
- **A variant with no consumer is deleted.** Symmetry with the registry is not a reason to keep one.

## Naming

- A file is named for the thing it exports, in the case that thing uses: `TelegramLoginButton.tsx`,
  `useAuth.ts`, `client.ts`.
- A file under `components/ui/` keeps the lower-case name shadcn/ui gives it: `button.tsx`, `select.tsx`.
- A test sits beside the file it covers, as `<file>.test.ts` or `<file>.test.tsx`.

## Diagrams

Component and flow diagrams follow the [repository-wide diagram conventions](../../../docs/conventions/diagrams.md).
This module colours its route components; see [Writing Documentation](documentation.md#diagram-colour).
