# TripleA AI General Staff — Status and Results

Date: 2026-10-30. Milestones **M0 through M5 are complete and M6 has its first slice**, all on the
`general-staff` branch: the module builds, its 104 tests are green and the repo-wide `./verify`
passes. Every phase of the brief now has working code: read-only bridge (2), conversational advisor
(3), tactical evaluation (4), the Operation Paris planner (5), approved execution (6) and the start
of the full General Staff (7). The plan is
[02-architecture-and-implementation-plan.md](02-architecture-and-implementation-plan.md); the
verified engine findings are [01-reconnaissance-report.md](01-reconnaissance-report.md); the
language-model measurements are [llm-eval.md](llm-eval.md).

Everything under "Engine facts learned" was verified by running code. The results tables are the
engine's output under the stated assumptions, not plan recommendations.

## What exists

The Gradle subproject `:general-staff` at [game-app/general-staff](../../game-app/general-staff/)
(registered in `settings.gradle.kts`; conventions in its
[AGENTS.md](../../game-app/general-staff/AGENTS.md)). No file under `game-core` was changed.

| Package (`org.triplea.generalstaff`) | Does |
|---|---|
| `engine` | Opens a map XML or `.tsvg`; gives the engine in-memory client settings; **hosts a whole game in this JVM** (`HeadlessGameHost` over the engine's `ServerGame` with a headless `StaffLaunchAction`) |
| `observe` | Read-only JSON: position (players with treasuries, production, capitals, objectives **evaluated by the engine's own condition code**, relationships, territories with unit stacks) and the history tree |
| `sim` | **The engine on copies**: `Sandbox` (a deserialized copy with seeded dice; branch by `fork`), `TurnSimulator` (starts the real delegate, performs orders through the real move delegate, fights with the real battle code, ends the delegate), `StaffDelegateBridge`, `SimulationPlayer` (retreat-after-round policy, finding the battle by the id the engine passes, and no "AI combat step pause": the engine's AI base class sleeps a second per casualty step for an audience a simulation does not have), `OpponentPolicy` (passive, reinforce a territory), `LandingPolicy` (flies aircraft that cannot stay where they are to the nearest own or allied territory the engine accepts) |
| `rules` | `LegalityChecker` (engine validator or real delegate on a positioned copy; every refusal carries a `reason`), `ReachabilityIndex` (legal destinations per unit group; a group with none carries the engine's refusal), `Routes` (candidate routes, friendliest first, because the map's one shortest route may run through enemy ground when an equally short friendly one exists), `MoveJudge` |
| `odds` | `BattleOddsService` over `ConcurrentBattleCalculator`; `StrafeEvaluator` over force subsets and retreat rounds; every rate a `Proportion` with `n` and a 95% Wilson interval |
| `plan` | `OperationParisBaseline` (calculator PoC) and **`OperationParisPlanner`**: eleven plan families (Italy alone; air strikes retreating after rounds 1–3; strafes with 3, 6 or 9 cheapest land units retreating after rounds 1–2; the full attack), each sampled by N trajectories on forked sandboxes (German combat move and battle, an intervening Allied policy, the Italian combat move and battle), ranked lexicographically by `PlanEvaluator` under the hard constraint; each verdict carries an evidence flag (feasible on the observed rate, confirmed only when the 95% interval settles it: upper bound within tolerance for a kept plan, lower bound above it for a rejected one), neighbouring ranks whose intervals overlap are named, families that would order the same force are collapsed, and every description, reason and assumption is worded for the attacker, ally, defender and target given |
| `staff` | **Approved execution**: `StaffPlayer` is an `AbstractAi` that proposes a step's orders to the `ApprovalGate` (a decision names the step and the proposal's time, so a stale click answers nothing), blocks the engine thread until the operator decides, saves the game, performs the approved orders through the real delegate and records every answer (at a non-combat move it also proposes landing orders for every aircraft the engine says cannot stay); `ExecutionSession` hosts a game with the staff as one or more powers and idle stand-ins or the engine's Fast AI as the others; `AfterActionReport`; hybrid mode hands a step's remainder to Fast AI |
| `doctrine` | `DoctrineStore` (standing directives in a JSON file, with the war footing among named powers when added) and `DoctrineReconciler` (unknown names, achieved targets, new wars, peace) |
| `llm` | `OllamaClient`, `IntentParser` (directive → checked JSON, unknown names become questions with the capitals of the powers named), `Explainer` (briefings around labelled engine figures, invented digits flagged), `DirectiveEval` |
| `api` | `StaffService`, the `Cli`, `LocalHttpApi` (loopback JSON; **the command page at `/ui`** with wording, wiring and layout in separate files) |

**Tests: 104** (one more, `LiveMapFactsTest`, runs only when the Global 1940 map is installed). Beyond the earlier ones: `PlanEvaluatorTest` (the evidence flag on kept and rejected plans, unseparated ranks), `SimulationPlayerTest` (no AI pause), `ForceSubsetsTest` (stable tie-break, proven with a reversed key), `CliTest` (`legal` output contract), `ApprovalGateTest` (a decision for an older proposal of the same step is refused), `LandingPolicyTest` (fighters left over an empty sea zone are removed by the engine unless flown home), `SandboxTest` (independence of forks and of the
live game; same seed, same battle), `TurnSimulatorTest` (refused orders reported, real battle
outcomes recorded, retreat policy honoured), `OpponentPolicyTest`, `OperationParisPlannerTest`
(the full attack is rejected, the air strike never conquers, feasible plans rank first, live game
untouched), `HeadlessGameHostTest` (a whole Minimap game to round 2, saved and reloaded),
`ApprovalGateTest`, `StaffPlayerTest` (approved orders execute through the real delegate after a
save; a manual gate blocks the engine and a rejection moves nothing), `HybridDelegationTest` (Fast AI
takes the rest of a step), `DoctrineStoreTest`, and `GeneralStaffEndToEndTest` (directive → doctrine
→ plan → approved execution → reconciliation, on the fixture, offline).

**Command line**: `observe`, `history`, `legal`, `reach`, `odds paris`, `odds strafe`, `plan paris`,
`advise`, `explain`, `eval`, `host`, `doctrine`, `serve`. **Local API** (loopback only; `GET /` lists
it): position, rules, odds and plan endpoints, `/parse-directive`, `/explain`, the `/execute/*` gate
(`start`, `orders`, `status`, `approve`, `reject`, `report`, `stop`), `/doctrine/*`, and `/ui`.
**Launcher**: `/launch` and `Launch TripleAdvisor.command` start `serve` on the port in `.port`.

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
| Operation Paris baseline, 1,000 runs per scenario (about 9,200 battles incl. the calculator's per-worker copies of the game data) | 4.7 s wall warm, 7.9 s on the first run of a JVM |
| Copy of Global 1940 with delegates for a legality check | about 1 s |
| Reachability of every German unit group at `germansCombatMove` (37 groups, 391 legal destinations, each validated by the engine) | 0.57 s |
| Serialized Global 1940 game data (calculator options, no history) | 751 KB |
| `:general-staff:check` warm (format check, PMD, 55 tests) | about 30 s |
| One short directive through Ollama structured output (model load included) | llama3.2:3b 11 s, phi4-mini 10 s, gemma4:e2b 25 s |
| Planner: 11 families × 6 trajectories (66 forked copies of Global 1940, each running five engine steps with real battles), from the CLI including JVM start | 43 s wall before the AI-pause fix |
| Planner on the live 2nd Edition file: 11 families × 24 trajectories (264 forked copies, each with up to two real battles and two landing moves), from the CLI including JVM start | 110 s wall before the AI-pause fix (17.8 s CPU); 3.4 s wall after (15.1 s CPU) |
| Hosted game in this JVM: Germany staffed, nine idle powers, round 1 with two approved orders, saves before each movement step | about 6 s |
| End-to-end test (directive → doctrine → 2 families × 4 trajectories → hosted execution → reconciliation) | 22 s |
| `:general-staff:check` warm (format check, PMD, 73 tests, two forks) | about 80 s |
| `:general-staff:check` warm (format check, PMD, 104 tests, two forks) after the AI-pause fix | about 15 s |

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

- **Force sizing is the lever, and M2 now provides the tools to search it.** The whole German
  force conquers France even when told to retreat after one round a quarter of the time, and
  almost always after two. `odds strafe` sweeps smaller forces; `reach` and `legal` say which
  units may actually get there. Searching plans over those is M4.
- **An uncontrolled air strike is ruinous.** With the planes told to fight on, the Luftwaffe loses
  about 8.5 of 9 aircraft to empty France one time in five. The planner needs round-limited air
  strikes and must price aircraft losses.
- **The follow-up estimate is an upper bound.** It ignores Britain's move between the German and
  Italian turns and re-creates survivors at full strength. The trajectory simulator (M4) replaces
  this shortcut with real moves on a cloned game.

## Planner results: sampled trajectories (seed 5, 6 trajectories per plan, intervening power passive)

Six trajectories give very wide intervals (a 0-of-6 is 0% [0–39%]); the point is the method and the
ordering, not the digits. Figures are the engine's: real moves, real battles with seeded dice.

**Caveat (added 2026-10-30 evening):** this table is the first planner's run on the Balanced fixture
and its figures do not reproduce. The live-map fleet ran 24 trajectories per plan on the 2nd Edition
file and found, for example, the six-land strafe conquering France in some trajectories, so the
"force-size cliff" below was an artefact of six samples. Two defects also shaped these numbers: the
retreat round was never applied to a lone battle (the engine fights a single pending battle through
the tracker, bypassing the delegate's "current battle"), and surviving aircraft were removed at the end
of the German non-combat move for want of a landing order. Both are fixed; the current figures are in
[04-live-map-scenarios.md](04-live-map-scenarios.md), with the evidence flag the planner now attaches to
every verdict.

| rank | plan | Italy owns France | Germany conquers France | German TUV lost | Italian PU gain |
|---|---|---|---|---|---|
| 1 | strafe-6land-r1 (aircraft + 6 cheapest land units, retreat after round 1) | 100% [61–100] | 0% [0–39] | 116 | 19.0 |
| 2 | strafe-6land-r2 | 100% [61–100] | 0% [0–39] | 116 | 19.0 |
| 3 | air-strike-r1 | 83% [44–97] | 0% [0–39] | 98 | 15.8 |
| 7 | air-strike-r3 | 50% [19–81] | 0% [0–39] | 98 | 9.5 |
| 8 | italy-alone | 0% [0–39] | 0% [0–39] | 0 | 0 |
| 9 | strafe-9land-r2 | 33% [10–70] | **67% [30–90]** | 120 | 6.3 |
| 11 | full-attack | 0% [0–39] | **100% [61–100]** | 114 | 0 |

Three things the planner established that the calculator alone could not:

- **The constraint has a force-size cliff.** Six cheapest land units with the aircraft never
  conquered France in these samples; nine did so two times in three. The planner found the cliff by
  running the real battle with the real retreat rule, which is exactly the kind of fact a human
  staff would otherwise estimate.
- **Every German aircraft died in every strike** in this run. Two causes, both found by the
  live-map fleet and since fixed: the retreat policy read the delegate's "current battle", which
  stays empty when the engine fights a lone battle through the tracker, so the aircraft never
  retreated; and the planner never ran the German non-combat move, so every survivor was removed by
  the engine for want of a landing place. An all-air attacker that retreats stays over the battle
  site (the engine's rule), which is exactly why the landing step matters.
- **The Italian PU gain is the French bank** (19 PUs when Italy walks in or wins), visible as the
  treasury change at capture, confirming the reconnaissance finding by running it.

Not evaluated, and said so in the report: the delayed-conquest family (strike on the German turn,
attack on Italy's next turn), which needs a full round of every power's turn.

## Approved execution: what a hosted turn looks like

`host --game <fixture> --staff Germans --orders orders.json --rounds 1` ran the first German turn
in this JVM with idle stand-ins for the other powers. The staff recorded: no purchase (left to the
engine's defaults), a save before the combat move, two orders performed through the real move
delegate (2 infantry Holland Belgium → France, 2 fighters Western Germany → France), and a save
before the non-combat move; the battle was fought by the engine with the planned retreat round. With
a manual gate the engine thread blocks at the combat move until `/execute/approve` or
`/execute/reject` arrives; `StaffPlayerTest` proves both paths.

## The language layer, and how it is kept honest

- `IntentParser` sends the whole-map observation's player names, capitals and territory names with
  the directive and asks for a fixed JSON shape (Ollama's structured output). Every player and
  territory name in the answer is checked against the observation; an unknown name becomes a
  question back to the commander with the closest real names and the relevant capitals ("Paris" →
  "France (French)"). `valid` is false until there is nothing left to ask.
- `Explainer` gives the model labelled facts (`{F1} = description = value`) and lets it cite them
  only by label; code fills the figures in. Any digits the model wrote itself are reported in the
  briefing's `warnings`, as is a briefing that cites no figure at all.
- Models are local and U.S.-origin (Ollama 0.40.9: `gemma4:e2b` default, `phi4-mini`, `llama3.2`).
  Their measured parsing quality is in [llm-eval.md](llm-eval.md).

## Engine facts learned (verified by running)

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
7. **`IDelegateBridge.sendMessage` names `WebSocketMessage`** from `:lobby-client-data`, so any
   module that implements a bridge needs that project as a dependency.
8. **`MoveValidator` reports too little movement as a disallowed unit, not an error.** A move is
   legal exactly when the result has no error, no disallowed unit and no unresolved unit, which
   is `MoveDelegate.performMove`'s own acceptance test; the dry run agreed with the real delegate
   on all nine moves in `LegalityCheckerTest`.
9. **The validator reads the current step**, so a copy must be positioned with
   `GameSequence.setRoundAndStep` (display name and player) before validating; Pro AI does the
   same, and does not call the move delegate's `start()` in its simulation.
10. **`HistoryWriter` only insists on the Swing thread when the game data says changes are
    Swing-only**, so history can be built and exported headless; a `.tsvg` written by
    `GameDataManager.saveGame` round-trips it exactly.
11. **Anti-aircraft guns can move but not during combat movement**: the reachability index lists
    the group with zero legal destinations, so a planner sees both the unit and the refusal.
12. **Objectives can be evaluated outside the end-turn step** with the engine's own
    `testAllConditionsRecursive` through a read-only bridge: at the start, Germany's
    "trade with Russia" objective holds and "control the Caucasus" does not.

13. **`BattleDelegate.start()` fights a lone battle by itself** (`fightBattleIfOnlyOne`) and the
    defenceless ones, so anything that wants battle outcomes must take the pending list before
    starting the delegate. The simulator does; its first version did not and saw no battles.
14. **A sandbox made from a fixture must initialize client settings too**: the first battle in it
    hits the same `ClientSetting` reads as the calculator. `Sandbox.fromBytes` now does it.
15. **Branching by rollback is not enough across steps.** Inverting the journal restores the game
    data but not delegate state (pending battles, moves made), so trajectories fork from a
    serialized snapshot (about a second per fork for Global 1940) and run in a thread pool.
16. **A whole `ServerGame` runs in this JVM from public engine API**: `ServerGame` with
    `LocalNoOpMessenger`, `ClientNetworkBridge.NO_OP_SENDER`, a `LaunchAction` of our own that
    installs `HeadlessDisplay` and `HeadlessSoundChannel`, `setDelegateAutosavesEnabled(false)`,
    `ClientSetting.saveGamesFolderPath` pointed at a work folder (the engine still saves at each new
    round), the AI pause settings at zero, and a code-source folder with `.triplea-root` and
    `assets`. No Mockito, no `game-headless` dependency.
17. **The engine blocks on a player exactly as designed.** `Player.start(stepName)` runs on the
    engine thread; blocking inside it (the approval gate) halts the game until the operator answers,
    and `ServerGame.saveGame` can be called from there because the delegate lock is free while the
    player has the turn. `stopGame()` must release the gate or shutdown hangs.
18. **Fast AI will play the rest of a step for another `Player`**: constructed with the staff's
    name and label, initialized with the staff's own `PlayerBridge`, its `start(stepName)` moves
    through the same delegate; its odds estimator needs no battle-calculator copies.
19. **`PlayerTypes.Type` can be instantiated anonymously** to put a prepared `Player` instance into
    the game loader, so the staff player never needs a registration edit in `game-core`.

## Open items carried forward

- The map is installed (2026-10-30; see [04-live-map-scenarios.md](04-live-map-scenarios.md)).
  Still the owner's: target ruleset (2nd Edition assumed) and whether any upstream hook is ever
  proposed (fork-only until extensive play testing; see `CLAUDE.md`).
- Planner: evaluate the delayed-conquest family (needs full-round simulation with policies for
  every power); a tolerance of zero can never be confirmed by a finite sample, so a directive that
  means "never" needs a stated tolerance or a very large run; a cheaper branch than
  re-deserializing a snapshot; a retreat policy that also pulls aircraft out once the land units
  are gone; runs on the same file and seed still differ slightly (losses by a few hundredths of a
  TUV; once, one plan of eleven flipped one trajectory of 24) because the engine draws unit ids at
  random on every load and hashes units by id (caching the parsed game bytes between runs would
  remove it); the JSON metric names keep the Operation Paris words (`italyOwnsFrance` is "the ally
  owns the target").
- Execution: purchases and placement are still the engine's defaults for a staffed power;
  per-theatre delegation maps a directive's `delegated_theaters` to units by hand today.
- Engine quirk, not this module's: the national-objective history line written by
  `TriggerAttachment` prints `Optional[PUs]` because it concatenates an `Optional<String>`; seen in
  a hosted run on the live file (04, finding 41). A candidate upstream bug report, to be filed only
  when the owner says so; nothing under `game-core` changes here.
- M6 remainder: Fast AI opponents run only in hosted games, not in sandboxes; the command page is
  functional but plain; doctrine is reconciled on request, not automatically each turn.
- Language models: grow the directive set beyond 20; try a few-shot prompt with the map's own
  vocabulary; the shape failures of gemma4 need a look at the raw answers.
