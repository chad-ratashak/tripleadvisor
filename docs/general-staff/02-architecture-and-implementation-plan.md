# TripleA AI General Staff — Architecture and Implementation Plan

Date: 2026-10-29. Status: **M0–M5 complete and M6's first slice delivered** (2026-10-30; see
[03-status-and-results.md](03-status-and-results.md)). What remains of M6 is listed there. Every engine fact the plan relies on is a verified
finding in [01-reconnaissance-report.md](01-reconnaissance-report.md) (cited as "Recon §n"); the
brief is [00-brief.md](00-brief.md).

## Context

The brief asks for a conversational General Staff that turns natural-language directives into
engine-validated, simulated, approved and executed TripleA turns, starting with "Operation Paris"
on the 1940 Global map. Reconnaissance shows TripleA already contains most of the deterministic
machinery (rules-faithful odds calculator, move validator, clone-and-simulate pattern, in-process
headless game loop) but exposes none of it outside the JVM, has no legal-move enumerator, no JSON
state export, and no seedable dice. The plan therefore builds a **thin Java module next to the
engine** that wraps those verified pieces behind structured JSON, and keeps the LLM strictly on
the language side of that boundary.

## Decisions (with the evidence behind them)

1. **Java-native planning core, as a new Gradle subproject `:general-staff`** at
   `game-app/general-staff`. Reason: `GameData` is a Java object graph reachable only in-process
   (Recon §12.5); cloning is a whole-graph serialization (Recon §5), so a planner that lives in
   another process would pay that cost on every query. A subproject is also the least invasive
   extension point: zero edits to `game-core` are required for an in-process host (Recon §6).
2. **A JSON seam, not a Python bridge, as the integration boundary.** The module exposes its
   services as JSON records over (a) a CLI and (b) a local HTTP API using the JDK's built-in
   `com.sun.net.httpserver` (no new dependency; Gson is already in the build, Recon §1). This is
   what the LLM layer, tests, a future web command UI, and any Python experiment all talk to.
   Verdict on the brief's question 5: **Java-native with an optional Python client**, not a
   Java/Python bridge inside the planning loop.
3. **LLM via Ollama's REST API from Java first.** Java's `HttpClient` + Gson is enough for
   `/api/chat` with JSON-schema `format`. A Python client over the same JSON seam stays available
   for prompt experiments, so iteration speed is not sacrificed. All installed models are
   U.S.-origin (Recon §1); start with the strongest local model (`gemma4:e2b`) and measure
   schema compliance before choosing.
4. **Simulation is sampled, never averaged.** The engine's own lookahead applies the closest-to-
   average battle result (Recon §5). The planner instead draws N trajectories, each resolved by the
   real battle code, and reports rates with 95% confidence intervals and raw n (house rule:
   oversample so the smallest reported subset has hundreds of samples).
5. **Scoring is lexicographic.** Hard constraints (probability of violation, e.g. "Germany
   conquers France") are evaluated first and must be 0 or below an explicit tolerance; then the
   primary objective rate; then secondary objectives (TUV preserved, casualties, economy).
6. **The engine remains authoritative.** Every action the LLM or the planner proposes is checked
   by `MoveValidator` / `PurchaseDelegate` / `AbstractPlaceDelegate` on a clone positioned at
   the right step, and the live game is only ever changed through the real delegates from inside a
   registered `Player` (Recon §7, §8). The only rollback mechanism is `ServerGame.saveGame`
   before execution (Recon §4).
7. **No ChromaDB, no web UI, no new frameworks until a phase needs them.** Doctrine is a JSON file
   until retrieval is demonstrably needed.

## Architecture (brief §4 mapped onto verified engine APIs)

| Brief component | Module class (proposed) | Engine API it wraps (verified) |
|---|---|---|
| TripleA Game State Reader | `observe.ObservationExporter`, `observe.HistoryLogExporter` | `GameData` accessors under `acquireReadLock()`; `GameDataManager.loadGame`, `GameParser.parse`; history tree walk; `TuvCostsCalculator`, `TerritoryAttachment.getProduction`, `RulesAttachment` objectives, `RelationshipTracker` |
| Legal Action Generator | `rules.LegalityChecker`, `rules.ReachabilityIndex`, `rules.PurchaseOptions`, `rules.PlacementOptions` | `MoveValidator.validateMove` dry runs on a clone; `GameMap.getNeighborsByMovementCost` + `getRouteForUnits` to generate candidates; `ProductionFrontier`; `AbstractPlaceDelegate.getPlaceableUnits` |
| Battle Probability Calculator | `odds.BattleOddsService`, `odds.StrafeEvaluator` | `ConcurrentBattleCalculator` (`setRetreatAfterRound`, order of losses, `AggregateResults.getResults()` for per-run distributions) |
| Scenario / Continuation Simulator | `sim.Sandbox`, `sim.TurnSimulator`, `sim.OpponentPolicy`, `sim.StaffDelegateBridge` | `GameDataUtils.cloneGameData(..withDelegates(true))`; real `MoveDelegate.performMove` and `BattleDelegate` on the clone via a custom `IDelegateBridge` (modelled on `ProDummyDelegateBridge`) with a **seedable** `IRandomSource`; `CompositeChange.invert()` for cheap branching; FastAi or scripted policies for opponents |
| Plan Scoring and Ranking | `plan.PlanEvaluator`, `plan.Directive`, `plan.OperationalPlan` | pure Java over `SimulationResult`s |
| Conversational LLM General Staff / Intent Parser / Explanation | `llm.OllamaClient`, `llm.IntentParser`, `llm.Explainer` | Ollama REST; outputs validated against the observation (territory, player and unit-type names must exist) |
| TripleA Action Executor | `staff.StaffPlayer extends AbstractAi`, `staff.ApprovalGate`, `engine.HeadlessGameHost`, `engine.StaffLaunchAction implements LaunchAction` | `ServerGame` loop from the `GameTestUtils` pattern; `IMoveDelegate.performMove`, `IPurchaseDelegate.purchase`, `IAbstractPlaceDelegate.placeUnits`, `IBattleDelegate.fightBattle`; `Player` callbacks for casualties/retreats |
| Human approval / command interface | `api.LocalHttpApi`, `api.Cli` | JDK `HttpServer`, Gson |

Proposed layout:

```
game-app/general-staff/
  build.gradle.kts        id("triplea-java-library"); deps: :game-core, :java-extras, libs.gson;
                          testImplementation(testFixtures(project(":game-core")))
  AGENTS.md               module rules (below)
  src/main/java/org/triplea/generalstaff/{engine,observe,rules,odds,sim,plan,llm,staff,api}/
  src/test/java/org/triplea/generalstaff/...
  src/test/resources/      golden JSON snapshots, recorded LLM responses
```

Module rules to write into its `AGENTS.md`: no edits to `GameDataComponent` classes or
`@RemoteActionCode` interfaces; the live `GameData` is never mutated outside a `Player` callback;
every simulation runs on a clone; every reported probability carries `n` and a CI; LLM output is
data until validated.

## Operation Paris: what the planner must reason about (verified facts → plan space)

Facts (Recon §10): the capital is the territory `France`, defended at start by 10 French/British
land units, 1 aaGun and 1 fighter; Northern Italy is adjacent and holds 2 inf, 2 art, 1 arm,
1 bomber; Italy has 2 fighters within range in Southern Italy; Germany has 9 aircraft within
return range (3 fighters, 4 tactical bombers, 2 bombers) and a large land force adjacent; France holds 19 PUs; Russia, the USA, China and Britain move between
Germany and Italy; France moves after Italy; there is no Italy-specific bonus — the prize is the
19-PU bank plus 4 PUs/turn plus the factory and airfield.

Engine rules that shape the plan (Recon §8): a battle fought to the end by land units conquers;
**air units alone cannot conquer**; an emptied enemy territory is captured by any land unit that
walks in during combat move; capturing the capital transfers the bank to whoever captures.

Candidate plan families the planner must generate and compare:

- **Air-only strike (G1):** German aircraft attack France alone. Hard constraint satisfied with
  probability 1 (air cannot conquer). Success = all 12 defenders die → Italy walks in on I1 with
  any land unit from Northern Italy, provided no Allied unit re-enters France first. Cost: AA fire
  and fighter losses; reported as expected aircraft lost.
- **Land strafe (G1):** attack with land + air and retreat after round k. Carries a real
  probability of an unintended German conquest (all defenders die before the retreat), which the
  planner must compute and treat as a hard-constraint violation rate, not a cost.
- **Delayed conquest (G1 → I2):** weaken on G1, reinforce Northern Italy on I1 non-combat
  (6 infantry from Southern Italy), attack on I2; must model French purchases/placement on F1 and
  Allied reinforcement.
- **No German action; Italy attacks I1 anyway** (baseline, expected to be hopeless).
- **Opponent response between G1 and I1:** Britain can fly fighters into France or move units;
  Russia and the USA cannot (neutral/not at war). Modelled first as a small scripted policy set
  (do nothing / fly fighters / maximum reinforcement) and later with FastAi on the sandbox.

Outputs per plan: P(Italy owns France at end of I1 or I2) with CI, P(Germany conquers), expected
German and Italian losses by unit type, economic delta (Italian vs German PU change), and the
exact `MoveDescription`s that implement it. If every family fails the constraint or has a low
rate, the report says so and recommends the closest feasible alternative (the brief's rule).

## Minimal proof of concept (brief §14.7)

A JUnit test plus a CLI command in `:general-staff`, no network required:

1. Load `TestMapGameData.GLOBAL1940` (the checked-in Balanced fixture) — and, once the map is
   installed, the live `ww2global40_2nd_edition.xml`.
2. Export a JSON observation focused on France and its neighbours (owner, production, capital,
   units by owner/type, treasuries, relevant objectives, at-war matrix).
3. Run `ConcurrentBattleCalculator` for: (a) full German attack on France; (b) German air-only
   strike, reporting the fraction of runs with zero defenders left; (c) the Italian I1 attack
   against the French survivors sampled from (b); (d) strafe variants with
   `setRetreatAfterRound(k)` for k = 1..3, reporting the survivor distribution and the German
   conquest rate. Each with n ≥ 1,000 and 95% CIs.
4. Print a one-page comparison. The test asserts schema, n, and that live `GameData` is unchanged
   (serialize-and-compare), not specific odds.

## Milestones

Phase numbers follow the brief. Effort is a rough guide for one engineer plus agents.

### M0 — Toolchain and module scaffold (0.5 day) — **done 2026-10-30**
- Document `JAVA_HOME` for Homebrew `openjdk@25` in `docs/general-staff/` and the module `AGENTS.md`; add a one-line pointer to the General Staff docs in the root `AGENTS.md`.
- Register `:general-staff` in `settings.gradle.kts`; `build.gradle.kts` as above; empty `GeneralStaffSmokeTest` that loads `TestMapGameData.GLOBAL1940`.
- Exit: `./gradlew :general-staff:test` green; `./verify` still green.
- Delivered beyond the scaffold: the read-only observation export (M1's first slice), the odds service
  with Wilson intervals and the Operation Paris proof of concept with a CLI (M2's first slice). Status
  and measurements: [03-status-and-results.md](03-status-and-results.md).

### M1 — Phase 2 read-only bridge (2–3 days) — **done 2026-10-30** (no `--maps-folder` override yet; not needed to load a file by path)
- `engine.GameLoading`: open a game XML (`GameParser.parse`) or a `.tsvg` (`GameDataManager.loadGame`) with `ClientSetting` initialised; a `--maps-folder` override via `ClientSetting.mapFolderOverride`.
- `observe.ObservationExporter` → `GameObservation` JSON (brief §6 fields: current player and step from `GameSequence`, territories with owner/production/capital/VC/units, per-player PUs, income estimate, objectives and whether satisfied, relationships, key rule flags). Unit stacks as counts per owner and type; TUV via `TuvCostsCalculator`.
- `observe.HistoryLogExporter`: headless re-implementation of `HistoryLog`'s tree walk → move log JSON.
- `api.Cli observe --game <xml|tsvg> [--focus <territory>...]`.
- Tests: `G40FixtureFactsTest` pins the verified map facts (capital = France, adjacency, 12 defenders, 19 PUs) so fixture drift is caught; golden JSON snapshots on `GLOBAL1940` and `MINIMAP`.
- Exit: the France observation printed from both the fixture and (if installed) the live map.

### M2 — Phase 4 first slice: tactical evaluation and the PoC (3–4 days; independent of M3) — **done 2026-10-30**
- `odds.BattleOddsService`: wraps `ConcurrentBattleCalculator`; returns a `BattleEstimate` record with rates, CIs, n, average survivors, TUV swing, and the per-run outcome histogram from `getResults()`.
- `odds.StrafeEvaluator`: sweeps `setRetreatAfterRound` and reports survivor distributions and the unintended-conquest rate.
- `rules.LegalityChecker`: `MoveValidator` dry run on a clone positioned at a named step (clone via `cloneGameData(withDelegates(true))`, then `GameSequence.setRoundAndStep` as `AbstractProAi` does); `rules.ReachabilityIndex`: per-unit candidate destinations = `getNeighborsByMovementCost` filtered by `getRouteForUnits` + `validateMove`.
- The PoC above as `OperationParisBaselineTest` + `Cli odds ...`.
- Measure and record: clone time, calculator runs/second, heap, for G40 on this Mac (Recon §13).
- Exit: PoC report produced; legality checker agrees with `MoveDelegate.performMove` on a table of known legal/illegal moves (`LegalityCheckerTest`).

### M3 — Phase 3 conversational advisor (3–4 days; can run in parallel with M2) — **done 2026-10-30** (eval results in [llm-eval.md](llm-eval.md))
- `llm.OllamaClient` (`/api/chat`, JSON-schema `format`, temperature 0 for parsing).
- `llm.IntentParser`: directive text + compact observation → `StrategicDirective` JSON (brief §6); every named territory/player/unit type is validated against the observation; unknown names are returned as clarification questions, never guessed.
- `llm.Explainer`: turns `GameObservation` and later `PlanEvaluation` into staff-style briefings; prohibited from inventing numbers — all figures are interpolated from the JSON by code, not generated.
- `api.LocalHttpApi` (`/observe`, `/parse-directive`, `/explain`) and `Cli advise`.
- Tests: `IntentParserTest` on recorded Ollama responses (offline); a schema-compliance eval over ~20 fixed directives, run per model, recorded in `docs/general-staff/llm-eval.md`; smoke test behind an env flag when Ollama is running.
- When the HTTP API exists, add the project launcher per the house conventions (`/launch`, `Launch TripleAdvisor.command`, `.port` in the backend range, stale-port kill, iCloud preflight).

### M4 — Phase 5 Operation Paris planner (5–7 days) — **done 2026-10-30** (trajectories by fork, not by rollback; see the status doc)
- `sim.Sandbox`: clone with delegates + `StaffDelegateBridge` (seedable `IRandomSource`, `DelegateHistoryWriter.createNoOpImplementation()`, headless display, a `Player` that answers casualty/retreat callbacks by policy). Branching via `CompositeChange.invert()` where possible, re-clone otherwise.
- `sim.TurnSimulator`: apply an ordered action list through the real delegates on the clone; fight pending battles with real `MustFightBattle`s under the seeded source (sampled, not averaged); advance steps with `GameSequence`; run `OpponentPolicy` for intervening players.
- `plan.OperationParisPlanner`: enumerate the plan families above with `ReachabilityIndex`-generated unit subsets; evaluate each with N trajectories; `plan.PlanEvaluator` ranks lexicographically; emit `OperationalPlan` JSON with executable `MoveDescription`s and the explanation inputs.
- Tests: `SandboxIsolationTest` (live data byte-identical after simulation); `TurnSimulatorTest` on `MINIMAP`/`DELEGATE_TEST` with scripted dice; `OperationParisPlannerTest` asserting constraint handling (the full-attack family is rejected; the air-only family reports violation rate 0) and output schema.
- Exit: a ranked Operation Paris report with CIs from the live 2nd Edition map.

### M5 — Phase 6 approved execution (4–6 days) — **done 2026-10-30** (movement orders; purchases and placement stay with the engine's defaults)
- `engine.HeadlessGameHost` + `engine.StaffLaunchAction` (own `LaunchAction`, no Mockito/game-headless dependency): start a `ServerGame` from XML or `.tsvg` with a chosen player map (`PlayerTypes.Type` instances built in-process; FastAi/ProAi for other powers), autosave folder redirected to a scratch directory.
- `staff.StaffPlayer extends AbstractAi`: implements `purchase/tech/move/place`, overrides `battle` and the casualty/retreat callbacks; inside `start(stepName)` it blocks on `staff.ApprovalGate` (the engine thread waits exactly as it does for a human) until the operator approves, edits, or rejects the step's orders through the HTTP API; saves via `ServerGame.saveGame` before executing; logs every delegate return value; stops on any `Optional<String>` error and reports the deviation.
- After-action report from the history delta since the step began.
- Tests: `StaffPlayerTest` drives a `ServerGame` on `MINIMAP` with an auto-approving gate and asserts progress; a blocking-gate test; a save-before-execute test.
- Optional two-line registration in `HeadedPlayerTypes.getPlayerTypes()` to play inside the desktop client (the only `game-core`/`game-headed` edit in the whole plan, and still optional).

### M6 — Phase 7 full General Staff (ongoing) — **first slice done 2026-10-30**: doctrine store and reconciler, hybrid delegation of a step's remainder to Fast AI, Fast AI opponents in hosted games, the command page at `/ui`, and the end-to-end test from directive to approved execution
- Doctrine store (JSON per campaign; reconciled against each new observation; surfaced as conflicts, never silently applied), multi-theatre directives with per-theatre delegation (hybrid mode), opponent modelling with FastAi/ProAi on sandboxes (never the live ProAi's static calculator), a web command UI over the HTTP API, and the "Caucasus + Paris + Luftwaffe + favourable naval ops" success-criterion scenario as an end-to-end test.

## Anticipated obstacles and mitigations

1. **No JDK on PATH** — set `JAVA_HOME` to Homebrew `openjdk@25` (verified working); consider `brew link` or `mise` later.
2. **Maps not installed** — the live map is a ≈20 MB zip from GitHub (`triplea-maps/world_war_ii_global`); installing it under `~/triplea/downloadedMaps` (or a project folder via `mapFolderOverride`) needs the owner's go-ahead. Until then, tests use the checked-in Balanced fixture and the Original XML.
3. **Ruleset ambiguity** — 2nd Edition, Original and Balanced Mod differ in turn order, objectives and Vichy/`destroysPUs`. The planner must read these from the loaded `GameData`, never hard-code them; the owner should confirm which variant is the target (default: 2nd Edition).
4. **No legal-move enumerator** — generate-and-test with `MoveValidator` is correct but potentially slow for many units; cache per (unit type, origin) within a step and prune with ProAi's `ProMatches` predicates.
5. **Validator reads the current step** — always position the clone's `GameSequence` at the intended step before validating or simulating.
6. **Unseeded calculator dice** — accept Monte Carlo noise for odds (report CIs) and use the module's own seedable bridge for trajectory simulation; a small upstream PR could add an `IRandomSource` parameter to `BattleCalculator` later.
7. **Cloning cost and memory** — measure in M2; reuse one clone per plan family and roll back with `invert()`; run heavy jobs with a 2 GB+ heap; never run two heavy simulations concurrently on this machine.
8. **ProAi's static calculator** — never use ProAi inside a sandbox while a live ProAi plays; use FastAi or a dedicated `ConcurrentBattleCalculator`.
9. **`AbstractAi.start` is final and dispatches by step-name suffix** — fine for standard maps; `UserActions` steps would need a separate `Player` implementation if ever required.
10. **Engine-thread blocking** — the approval gate must block inside `Player.start`; delegate calls happen from that thread. Any UI thread only enqueues decisions.
11. **Autosaves** — redirect `ClientSetting.saveGamesFolderPath` for hosts and sandboxes.
12. **Save/network compatibility** — the module defines no `GameDataComponent` subclasses and no remote interfaces; doctrine and plans are stored outside `GameData`.
13. **LLM hallucination** — the LLM never emits unit positions, routes or odds; it emits a directive schema that code validates, and it explains numbers that code computed.

## Tests (summary)

| Test | Proves |
|---|---|
| `G40FixtureFactsTest` | map facts the planner assumes are still true in the fixture |
| `ObservationExporterTest` (golden JSON) | export is complete and stable |
| `LegalityCheckerTest` | dry-run verdicts match the real delegate on known cases |
| `BattleOddsServiceTest` | CI arithmetic, n, result schema; calculator smoke on G40 |
| `OperationParisBaselineTest` | the PoC runs end to end on the fixture and leaves live data untouched |
| `SandboxIsolationTest`, `TurnSimulatorTest` | simulation never leaks into the live game; scripted dice reproduce trajectories |
| `OperationParisPlannerTest` | constraint handling and ranking order |
| `StaffPlayerTest` | execution through real delegates with approval gating and save-before-execute |
| `IntentParserTest` + model eval | directive parsing is validated and measured per model |

## Verification (how to run, once implemented)

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```
```bash
./gradlew :general-staff:test
```
```bash
./gradlew :general-staff:run --args="observe --game <path-to-xml-or-tsvg> --focus France"
```
```bash
./gradlew :general-staff:run --args="odds paris --game <path> --runs 2000"
```
`./verify` must stay green (Spotless, PMD, custom checks).

## Decisions needed from the owner before M1/M2 complete

1. Permission to download and install the World War II Global map (≈20 MB zip from GitHub) into `~/triplea/downloadedMaps` (or a project-local maps folder).
2. Target ruleset: Global 1940 **2nd Edition** (assumed) vs Balanced Mod vs Original.
3. Whether the owner-facing surface should start as CLI + HTTP API (assumed) or embed in the desktop client sooner (M5 optional registration).
4. Whether small upstream-friendly hooks (seedable calculator dice; player-type registration) may be proposed to `triplea-game/triplea`, or kept fork-local.
