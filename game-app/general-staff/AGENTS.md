# general-staff

The **AI General Staff**: an engine-validated planner and adviser that sits next to the TripleA
engine and exposes its services as JSON. Design and status live in
[docs/general-staff](../../docs/general-staff/): the brief (`00-brief.md`), the verified
reconnaissance report (`01-reconnaissance-report.md`) and the architecture and milestone plan
(`02-architecture-and-implementation-plan.md`). Read those before changing this module.

## Packages (`org.triplea.generalstaff`)

| Package    | Role                                                                                              |
|------------|---------------------------------------------------------------------------------------------------|
| `engine`   | Opening games (`GameLoading`); in-memory engine settings (`EngineSettings`); hosting a whole game in this JVM (`HeadlessGameHost`, `StaffLaunchAction`) |
| `observe`  | Read-only JSON snapshots: position (`ObservationExporter`) and history (`HistoryLogExporter`)     |
| `sim`      | Running the engine on copies: `Sandbox` (fork by snapshot), `TurnSimulator` (real delegates and battles), `StaffDelegateBridge`, `SimulationPlayer`, `OpponentPolicy` |
| `rules`    | Engine-backed legality: `LegalityChecker` / `MoveJudge`, `ReachabilityIndex`                      |
| `odds`     | Battle estimates from the engine's calculator (`BattleOddsService`), `StrafeEvaluator`, `Proportion` |
| `plan`     | `OperationParisBaseline` (calculator PoC), `OperationParisPlanner` (sampled trajectories, ranked under the hard constraint), `PlanEvaluator`, `ForceSelection` |
| `staff`    | Approved execution: `StaffPlayer` (an `AbstractAi` that blocks on the `ApprovalGate`), `PlannedStep`/`PlannedOrder`, `ExecutionSession`, `AfterActionReport` |
| `doctrine` | The commander's standing directives on disk (`DoctrineStore`) and what changed since (`DoctrineReconciler`) |
| `llm`      | The language side: `OllamaClient`, `IntentParser` (directive -> checked JSON), `Explainer` (briefings around engine figures), `DirectiveEval` |
| `api`      | `StaffService` (one front door), the `Cli`, `LocalHttpApi` (JSON over loopback HTTP, plus the command page at `/ui`) |
| `json`     | The one Gson configuration (`Json.GSON`)                                                           |

## Rules for this module

- **The engine is authoritative.** Odds come from the battle calculator, legality from the real
  validators and delegates. Never estimate either by hand or with a language model.
- **Never mutate the live `GameData`** outside a registered `Player` callback. Simulations run on
  clones; units created for a calculation use `UnitType.createTemp`, never `create`, so they are
  not registered in the live unit list. Tests prove the live data is unchanged after a run.
- **Every probability carries `n` and a 95% interval** (`odds.Proportion`, Wilson score). Never
  print a bare percentage. Oversample so the smallest subset you draw a conclusion from has
  hundreds of samples.
- **No edits to `game-core`** are needed for this module, and none are made: no
  `GameDataComponent` subclasses, no `@RemoteActionCode` interfaces, no new serialized types.
- **Wording is separate from code.** Text a reader sees lives in `*Text.java` files
  (`plan/ReportText`, `api/CliText`); renderers hold layout only. Change a sentence without
  reading code.
- **Nothing moves in a hosted game without the gate.** `StaffPlayer` proposes a step's orders to the
  `ApprovalGate` and the engine thread blocks until the operator approves, edits or rejects; the
  game is saved before approved orders run; every engine answer is recorded.
- **Simulations fork, they do not share.** A `Sandbox` is a deserialized copy with its own seeded
  dice; trajectories never touch each other or the live game.
- **Language-model output is data until code has validated it.** `IntentParser` checks every
  player and territory name against the observation and asks back instead of guessing;
  `Explainer` lets the model cite figures only by label and fills the numbers in itself, flagging
  any digits the model wrote. The model never decides legality or odds.
- **Models stay local and U.S.-origin** (Ollama; default `gemma4:e2b`; `llama3.2`, `phi4-mini`
  installed). Measure a model with `eval` before trusting it; results live in
  `docs/general-staff/llm-eval.md`.

## Building, testing and running

No JDK is on this Mac's `PATH`; use the Homebrew one:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

```bash
./gradlew :general-staff:check
```

The CLI (`./gradlew :general-staff:run --args="..."`; Gradle's `run` task works from the module
folder, so give `--game` an absolute path; no arguments prints the full usage):

| Command | Does |
|---|---|
| `observe --game F [--focus T]...` | position JSON |
| `history --game F` | history JSON (rounds, steps, events) |
| `legal --game F --step S --from A --to B --type U [--count N]` | the engine's verdict on one move |
| `reach --game F --step S [--from A]` | legal destinations per unit group |
| `odds paris --game F [--runs N] [--seed S] [--json]` | Operation Paris calculator baseline |
| `odds strafe --game F --attacker P --defender Q --territory T [...]` | force and retreat sweep |
| `plan paris --game F [--trajectories N] [--tolerance T] [--intervening passive or reinforce] [--explain]` | ranked plans from sampled trajectories |
| `advise --game F --directive "..." [--model M]` | directive -> checked JSON (needs Ollama) |
| `explain --game F [--intent "..."] [--runs N] [--model M]` | baseline plus a staff briefing (needs Ollama) |
| `eval --game F [--model M] [--directives FILE]` | directive-parsing evaluation (needs Ollama) |
| `host --game F --staff P[,P] [--others idle or fast] [--orders FILE] [--mode auto-approve or auto-reject] [--rounds R]` | host the game here and execute the orders file under the gate |
| `doctrine list, add, remove or reconcile --file D [...]` | the commander's standing directives |
| `serve [--port P] [--model M]` | the local JSON API; `GET /` lists endpoints, `/ui` is the command page |

`/launch` (or the clickable `Launch TripleAdvisor.command`, generated locally and ignored by git)
starts `serve` on the port in `.port`. `OLLAMA_HOST` overrides the Ollama address.

Tests use the checked-in Global 1940 Balanced fixture (`TestMapGameData.GLOBAL1940`) and the
Minimap fixture; no map download is needed and no network: the language layer is tested against a
fake Ollama (`FakeOllama`). `OllamaSmokeTest` talks to the real server only when
`GENERAL_STAFF_OLLAMA` is set. `OperationParisBaselineTest` is the slow one;
`-Dgeneralstaff.runs=<n>` changes its sample size (default 1000). The test task uses a 2 GB heap
because the calculator holds one copy of the game data per worker thread.
