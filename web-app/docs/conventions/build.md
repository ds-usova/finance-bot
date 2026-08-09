# [Conventions](../conventions.md) > Build

The commands themselves, and what each answers, are in
[Building a Node Module](../../../docs/conventions/node-build.md). This page names what is specific to this
module.

| Fact              | Value                                      |
|-------------------|--------------------------------------------|
| Run commands from | the repository root, with `--prefix web-app` |
| Source root       | `src/`                                     |
| Coverage minimum  | 80% lines, statements, functions, branches |
| Coverage config   | `vite.config.ts`, `test.coverage`          |
| Dev server port   | 1004                                       |
| Container port    | 1003, mapped to nginx's 80                 |

The npm scripts take no `--module` flag: this module is its own npm project, and its directory is the target.

The repository's [test runner](../../../tools/agent-test/README.md) does take one — `--module web-app` — and
drives those same scripts, so a run is reported the same way it is for a Gradle module and two runs cannot
overwrite each other's results.

## Module Tasks

| Task             | Command                                                                  |
|------------------|--------------------------------------------------------------------------|
| Gate             | `npm --prefix web-app run verify`                                        |
| Lint             | `npm --prefix web-app run lint`                                          |
| Format           | `npm --prefix web-app run format`                                        |
| Check formatting | `npm --prefix web-app run format:check`                                  |
| Type check       | `npm --prefix web-app run typecheck`                                     |
| Coverage         | `npm --prefix web-app run verify:coverage`                               |
| Contract codegen | `npm --prefix web-app run generate:api`, reading `openapi/ledger-api.yaml` |

`--prefix` is what makes these runnable: every script's working directory is this module, and the repository root
holds no `package.json`. From inside `web-app/` the flag is redundant and `npm run <task>` is the same command.

`typecheck` is `tsc --noEmit` on its own. `build` runs the same check and then bundles, so a type check asked of
`build` pays for a bundle nobody wanted.

## Docker

The image builds from the **repository root**, as every module's does:

```
docker compose -f infrastructure/docker-compose.yaml build web-app
```

The build stage bakes `VITE_TELEGRAM_BOT_USERNAME` into the bundle, so the image is specific to one bot. The
serve stage is nginx, which also proxies `/api` to the ledger so the browser sees one origin.
