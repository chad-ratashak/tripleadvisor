# general-staff

The **AI General Staff**: an engine-validated planner and adviser that sits next to the TripleA
engine and exposes its services as JSON. Design and status live in
[docs/general-staff](../../docs/general-staff/): the brief (`00-brief.md`), the verified
reconnaissance report (`01-reconnaissance-report.md`) and the architecture and milestone plan
(`02-architecture-and-implementation-plan.md`). Read those before changing this module.

## Packages (`org.triplea.generalstaff`)

| Package   | Role                                                                                     |
|-----------|------------------------------------------------------------------------------------------|
| `engine`  | Opening games (`GameLoading`: map XML or `.tsvg`); later the headless host               |
| `observe` | Read-only JSON snapshot of a position (`ObservationExporter` -> `GameObservation`)       |
| `odds`    | Battle estimates from the engine's `ConcurrentBattleCalculator` (`BattleOddsService`)    |
| `plan`    | Plan evaluation; today the Operation Paris baseline (`OperationParisBaseline`)           |
| `api`     | The `Cli` entry point (`./gradlew :general-staff:run --args="..."`); later a local HTTP API |

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
- Language-model output, when it arrives (milestone M3), is data until validated against an
  observation; it never names a unit position, route or probability.

## Building and testing

No JDK is on this Mac's `PATH`; use the Homebrew one:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
```

```bash
./gradlew :general-staff:test
```

```bash
./gradlew :general-staff:run --args="observe --game <game.xml|save.tsvg> --focus France"
```

Gradle's `run` task works from the module folder, so give `--game` an absolute path.

```bash
./gradlew :general-staff:run --args="odds paris --game <game.xml|save.tsvg> --runs 2000"
```

Tests use the checked-in Global 1940 Balanced fixture (`TestMapGameData.GLOBAL1940`); no map
download is needed. `OperationParisBaselineTest` runs the battle calculator and is the slow one;
`-Dgeneralstaff.runs=<n>` changes its sample size (default 1000). The test task uses a 2 GB heap
because the calculator holds one copy of the game data per worker thread.
