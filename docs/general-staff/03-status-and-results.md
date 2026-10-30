# TripleA AI General Staff — Status and First Results

Date: 2026-10-30. Milestone **M0 is complete** and the brief's minimal proof of concept (brief
§14.7) runs on the checked-in Global 1940 Balanced fixture. The plan is
[02-architecture-and-implementation-plan.md](02-architecture-and-implementation-plan.md); the
verified engine findings are [01-reconnaissance-report.md](01-reconnaissance-report.md).

Everything in "Engine facts learned" below was verified by running code; the results table is the
engine's battle calculator output under the stated assumptions, not a plan recommendation.

## What exists

The Gradle subproject `:general-staff` at [game-app/general-staff](../../game-app/general-staff/)
(registered in `settings.gradle.kts`; conventions in its
[AGENTS.md](../../game-app/general-staff/AGENTS.md)). No file under `game-core` was changed.

| Package (`org.triplea.generalstaff`) | Classes | Does |
|---|---|---|
| `engine` | `GameLoading`, `EngineSettings`, `InMemoryPreferences` | Opens a map XML or `.tsvg`; gives the engine in-memory client settings when no host has initialized them |
| `observe` | `ObservationExporter`, `GameObservation`, `UnitCounts` | Read-only JSON snapshot: step, players (PUs, production, capitals, objective definitions), relationships, territories (owner, production, capital, VC, neighbours, unit stacks); whole map or a focus set plus neighbours |
| `odds` | `BattleOddsService`, `BattleScenario`, `BattleOptions`, `BattleEstimate`, `Proportion` | Wraps `ConcurrentBattleCalculator`; every rate is a `Proportion` with `n` and a 95% Wilson interval; per-run survivor distribution and expected losses by type |
| `plan` | `OperationParisBaseline`, `OperationParisReport`, `ReportFormatter`, `ReportText` | The proof of concept: forces chosen by position and range, seven calculator scenarios, a sampled Italian follow-up, a one-page text report and a JSON report |
| `api` | `Cli`, `CliText` | `observe --game <file> [--focus T]...` and `odds paris --game <file> [--runs N] [--seed S] [--max-pools K] [--json]` |

Tests (17, all green): `GeneralStaffSmokeTest` (M0 exit), `ObservationExporterTest` (pins the map
facts the planner relies on: capital `France`, six neighbours, 12 combat units + 2 infrastructure,
treasuries 19/30/10, no Axis objective naming France, war/neutrality matrix, opening step),
`ProportionTest` (Wilson arithmetic), `BattleOddsServiceTest` (every run counted once; temporary
units never enter the live unit list), `OperationParisBaselineTest` (end to end; live game data
structurally identical to the fresh parse and byte-identical between runs).

Run it (no JDK is on this Mac's PATH):

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```
```bash
./gradlew :general-staff:check
```
```bash
./gradlew :general-staff:run --args="odds paris --game $PWD/game-app/game-core/src/testFixtures/resources/ww2_g40_balanced.xml --runs 2000"
```

(Gradle's `run` task works from the module folder, so `--game` needs an absolute path.)

## Measured on this Mac (8 cores, 8 GB RAM, 2 GB test heap)

| Measurement | Value |
|---|---|
| Baseline, 1,000 runs per scenario (7 scenarios + 11 follow-up pools of 200, about 9,200 battles, including the calculator's per-worker copies of the game data) | 4.7 s wall warm, 7.9 s on the first run of a JVM |
| `:general-staff:check` warm (format check, PMD, 17 tests) | about 30 s |
| Serialized Global 1940 game data (calculator options, no history) | 751 KB |

## First results: Operation Paris on the Balanced fixture (seed 7, n = 1,000 per scenario)

Forces the baseline selected from the data: German strike force 7 infantry, 3 artillery, 3 armour,
4 mech infantry, 3 fighters, 4 tactical bombers, 2 bombers (the aircraft are the 9 within return
range of France); Italian strike force 2 infantry, 2 artillery, 1 armour, 1 bomber, 2 fighters;
French garrison 6 infantry, 1 artillery, 1 armour, 1 aaGun, 1 fighter + British 1 artillery,
1 armour, plus the factory and airfield.

| Scenario | Attacker destroys all defenders | Rounds | Defenders left | TUV swing |
|---|---|---|---|---|
| German full attack, fight to the end | 100.0% [99.6–100.0] | 1.7 | 0.00 | +76.5 |
| German air-only strike, planes fight on | 19.0% [16.7–21.5] | 2.8 | 3.65 | −60.9 |
| German strafe, whole force, retreat after round 1 | 25.8% [23.2–28.6] | 1.0 | 2.18 | +31.4 |
| German strafe, whole force, retreat after round 2 | 99.8% [99.3–99.9] | 1.8 | 0.00 | +76.1 |
| German strafe, whole force, retreat after round 3 | 100.0% [99.6–100.0] | 1.8 | 0.00 | +76.1 |
| Italy attacks the full garrison alone | 0.9% [0.5–1.7] | 1.6 | 7.39 | −20.8 |

Italian follow-up after the air-only strike, no Allied move in between, survivors re-created at
full strength: Italy owns France at the end of its turn **87.2% [85.0–89.1]** (n = 1,000), of which
walk-ins into an emptied France 20.9% [18.5–23.5]; no survivor set was left unsimulated.

What the numbers say to the planner (observations about the method, not conclusions about play):

- **Force sizing is the lever, and the baseline does not search it.** The whole German force
  conquers France even when told to retreat after one round a quarter of the time, and almost
  always after two. A strafe that respects "Germany must not take France" needs a smaller force,
  which means searching unit subsets; that needs the legality checker and reachability index
  (M2), not more calculator runs.
- **An uncontrolled air strike is ruinous.** With the planes told to fight on, the Luftwaffe loses
  about 8.5 of 9 aircraft to empty France one time in five. The planner needs a round-limited air
  strike (retreat after k rounds) and must price aircraft losses, which the engine's Attacker
  Retreat Planes rule makes legal.
- **The follow-up estimate is an upper bound.** It ignores Britain's move between the German and
  Italian turns and re-creates survivors at full strength. The trajectory simulator (M4) replaces
  this shortcut with real moves on a cloned game.

## Engine facts learned in M0 (verified by running)

1. **Battles need `ClientSetting` initialized.** `GameObjectInputStream.resolveUnit` reads
   `showSerializeFeatures` while translating units into the calculator's copies, and
   `SelectCasualties.execute` reads `useWebsocketNetwork` every firing step. The desktop and bot
   hosts call `ClientSetting.initialize()`, which uses the user's real preference store; the
   module instead installs an in-memory `Preferences` tree (`engine.EngineSettings`) when nothing
   has initialized the settings, so defaults apply and nothing is written to disk.
2. **`UnitType.create` registers the new units in the live `UnitsList`**; `createTemp` does not.
   Calculation-only units must use `createTemp`, or the live game grows with every estimate.
3. **The engine's serialized form contains lazy caches.** `UnitType.unitAttachment` fills on the
   first `getUnitAttachment()`, `ProductionFrontier.cachedRules` on the first rules lookup (TUV
   costs). A serialized snapshot of a freshly parsed game therefore differs from one taken after
   any use; after that it is byte-stable. "Nothing changed" is proved structurally against the
   fresh state and byte for byte between a first and a second run.
4. **The fixture opens on `gameInitDelegate`**, a one-time step with no player; the first player
   step is `germanBid`. Any "current player" logic must tolerate a null player.
5. **The Balanced fixture's aaGun is not infrastructure** (the option is commented out), so it is a
   real defending unit that must be destroyed before France counts as emptied, and the engine
   fights it as a 0-defence casualty. The 2nd Edition file should be checked on this point once
   the map is installed.
6. **Nine, not ten, German aircraft** start within return range of France (3 fighters, 4 tactical
   bombers, 2 bombers); the plan document's "10 aircraft" was a miscount and has been corrected.

## Open items carried forward

- Owner decisions from the plan: map download permission, target ruleset (2nd Edition assumed),
  CLI + HTTP first, upstream hooks.
- M1 remainder: history/move-log export, a `.tsvg` loading test, golden JSON snapshots on
  `MINIMAP` and `GLOBAL1940`.
- M2: `LegalityChecker` and `ReachabilityIndex` (force subsets), `StrafeEvaluator` with
  round-limited air strikes, clone-time measurement separate from battle time.
