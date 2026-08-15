# The files

What `upgrade.md` and each `<module>/steps.md` carry. Every file follows the repository's documentation
conventions. The directory carries the number and the name; no file repeats them.

## `upgrade.md`

```
# Upgrade: <the module, or the modules, and what moves>

**Affected Modules:** `module-a`
**Source:** <one line — the request, or the conventions entry that scheduled this run>
**Baseline:** <the commit the suite was green at>
**Surveyed with:** <what listed versions and what listed vulnerabilities, or `manifest + registry` / `none`>
**Policy:** <the conventions' line on which versions are routine, or `open — chosen in Phase 2`>

## Survey

| Dependency | Current | Newest | Target | Vulnerabilities | Guide | Status |
|------------|---------|--------|--------|-----------------|-------|--------|

## What changes

**<the first move, in a clause — "the test stack moves one minor", "the HTTP client crosses a major">**

| #   | Kind | What changes | Touches |
|-----|------|--------------|---------|

**Not changed:** <one line — a version a reader might expect to move and it does not, and why>

## Kept back

| Guide asked | Kept because | Would unblock |
|-------------|--------------|---------------|

## Steps

<the checklist — step-format.md>

## Attempts

<see templates/attempts.md — phase is a step ID>

## Open Questions

- **Q1:** …
  - A:
```

### Survey

- **One row per dependency that is behind or vulnerable.** A row per BOM, with the libraries it pins listed
  under it in `Dependency` as `└ name`.
- **`Target`** is what this run moves to: the newest routine version under the policy, or the newest of all
  where the policy is open. A row the user leaves unselected keeps its `Target` and its `Status` says why.
- **`Vulnerabilities`** is the identifiers the scanner reported and the version that fixes each, or `—`.
- **`Guide`** is a link, or `none found`.
- **`Status`** is one of `proposed`, `deferred`, `not offered` (a major the policy keeps for its own story),
  `done`, `kept back`, `blocked`. Written `proposed` at Phase 1, settled at Phase 2, finalized at Phase 4.

### What changes

The upgrade to a person: a reader who agrees with the survey and these tables can stop. Rows grouped by the move
they make. `What changes` starts with a verb — `bump`, `rename`, `replace call`, `move setting`. `Touches` is
the manifest, or the one package a `migrate` change reaches.

### Kept back

**The section that keeps a half-done migration honest.** One row per change a guide asked for that stays undone
at the target version: what the guide said, why it stays — the attempt IDs, in one clause — and what would let
the next run finish it: a fixed release, a dependency of the module's own that has to move first, a decision.

Empty on most upgrades. Never removed.

## `<module>/steps.md`

Carries `**Affected Module:**` and `**Upgrade:** [<the upgrade>](../upgrade.md)` above its `## Steps`, its own
`## Kept back` and `## Attempts`, and nothing else. `## Survey` and `## What changes` stay in `upgrade.md` and
cover every steps file. `shared/steps.md` names every module the catalog serves.
