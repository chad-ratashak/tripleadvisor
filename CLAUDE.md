@AGENTS.md

Nested `AGENTS.md` files exist throughout the source tree (under `lib/`,
`game-app/`, `http-clients/`, `gradle/`, `docs/`, etc.). When working in or
beneath a directory that contains an `AGENTS.md`, read that file as well.
Guidance from a nested `AGENTS.md` applies to its directory and descendants,
and takes precedence over higher-level `AGENTS.md` files on conflict.

## Rules for interacting with the main project (triplea-game/triplea)

This repository is Chad's fork (`chad-ratashak/tripleadvisor`, remote `origin`). The main project
is `triplea-game/triplea` (remote `upstream`). The General Staff work lives here, not there.

- **Never push to, open a pull request against, comment on, or file an issue in the main project
  from this work** unless Chad says so in words for that specific action. Pull requests go from a
  fork branch into the fork's own `main`, with `--repo chad-ratashak/tripleadvisor --head <branch>
  --base main` passed explicitly to `gh pr create`; never rely on `gh` guessing the parent repo.
- The `upstream` remote is fetch-only on purpose (its push URL is `no_push`) and the GitHub CLI's
  default repository for this checkout is the fork. Leave both that way. Syncing with the main
  project is read-only: `git fetch upstream`, then merge into the fork.
- The main project does accept community modifications, and parts of this work may be offered
  some day. That happens only after the work is very well tested, which means lots of hands-on
  play testing in the real desktop client over many games, not just green unit tests, and only when
  Chad decides to and says so. Until then, treat anything here as fork-only.
- Keep the engine untouched so nothing here can affect the main project's behaviour: the
  `:general-staff` module is additive, with no edits under `game-core` and no new serialized types
  or remote interfaces. If an upstream-friendly hook ever seems worth proposing, write it up as a
  proposal in `docs/general-staff/` first; do not open anything upstream.
