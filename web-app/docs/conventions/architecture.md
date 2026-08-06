# [Conventions](../conventions.md) > Architecture & Layering

## Directory Structure

```
src/
  api/          the only place fetch is called
  auth/         session state, its types and the route guard
  components/   presentational components, reusable across pages
  pages/        one component per route, composing the rest
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
- **A page composes.** Reading context, calling `api/`, and deciding what to render belong to `pages/`.
- **Session state has one owner**, the auth context. Nothing else stores who is signed in.

## Naming

- A file is named for the thing it exports, in the case that thing uses: `TelegramLoginButton.tsx`,
  `useAuth.ts`, `client.ts`.
- A test sits beside the file it covers, as `<file>.test.ts` or `<file>.test.tsx`.

## Diagrams

Component and flow diagrams follow the [repository-wide diagram conventions](../../../docs/conventions/diagrams.md).
