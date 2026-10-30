package org.triplea.generalstaff.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.triplea.delegate.Matches;
import games.strategy.triplea.util.TuvCostsCalculator;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.triplea.generalstaff.engine.GameLoading;
import org.triplea.generalstaff.observe.HistoryLogExporter;
import org.triplea.generalstaff.observe.ObservationExporter;
import org.triplea.generalstaff.odds.BattleOddsService;
import org.triplea.generalstaff.odds.ForceSubsets;
import org.triplea.generalstaff.odds.StrafeEvaluator;
import org.triplea.generalstaff.odds.StrafeSweep;
import org.triplea.generalstaff.odds.StrafeSweepFormatter;
import org.triplea.generalstaff.plan.ForceSelection;
import org.triplea.generalstaff.plan.OperationParisBaseline;
import org.triplea.generalstaff.plan.OperationParisReport;
import org.triplea.generalstaff.plan.ReportFormatter;
import org.triplea.generalstaff.rules.LegalityChecker;
import org.triplea.generalstaff.rules.MoveVerdict;
import org.triplea.generalstaff.rules.ReachabilityIndex;

/** Command line entry point. See {@link CliText#USAGE} for the commands. */
public final class Cli {
  private static final Gson GSON =
      new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();
  private static final int DEFAULT_PARIS_RUNS = 2000;
  private static final int DEFAULT_SWEEP_RUNS = 1000;
  private static final int DEFAULT_SWEEP_ROUNDS = 3;
  private static final int DEFAULT_LAND_STEP = 3;
  private static final long DEFAULT_SEED = 1L;

  private Cli() {}

  public static void main(final String[] args) {
    System.exit(run(List.of(args), System.out, System.err));
  }

  /** Runs one command; returns the process exit code. */
  static int run(final List<String> args, final PrintStream out, final PrintStream err) {
    if (args.isEmpty()) {
      err.print(CliText.USAGE);
      return 2;
    }
    try {
      final Arguments arguments = Arguments.parse(args.subList(1, args.size()));
      return switch (args.get(0)) {
        case "observe" -> observe(arguments, out);
        case "history" -> history(arguments, out);
        case "legal" -> legal(arguments, out);
        case "reach" -> reach(arguments, out);
        case "odds" -> odds(arguments, out, err);
        default -> {
          err.print(CliText.USAGE);
          yield 2;
        }
      };
    } catch (final IllegalArgumentException | IllegalStateException e) {
      err.println(CliText.ERROR_PREFIX + e.getMessage());
      return 1;
    }
  }

  private static int observe(final Arguments arguments, final PrintStream out) {
    final GameData data = GameLoading.load(arguments.requiredPath("--game"));
    final ObservationExporter exporter = new ObservationExporter();
    out.println(exporter.toJson(exporter.export(data, arguments.values("--focus"))));
    return 0;
  }

  private static int history(final Arguments arguments, final PrintStream out) {
    final GameData data = GameLoading.load(arguments.requiredPath("--game"));
    final HistoryLogExporter exporter = new HistoryLogExporter();
    out.println(exporter.toJson(exporter.export(data)));
    return 0;
  }

  private static int legal(final Arguments arguments, final PrintStream out) {
    final GameData data = GameLoading.load(arguments.requiredPath("--game"));
    final LegalityChecker checker =
        LegalityChecker.forStep(data, arguments.required("--step"), DEFAULT_SEED);
    final Territory from = territory(data, arguments.required("--from"));
    final Territory to = territory(data, arguments.required("--to"));
    final String type = arguments.required("--type");
    final int count = arguments.intValue("--count", 1);
    final GamePlayer player = player(data, checker.player().getName());
    final List<Unit> units =
        from
            .getMatches(
                Matches.unitIsOwnedBy(player).and(unit -> unit.getType().getName().equals(type)))
            .stream()
            .limit(count)
            .toList();
    if (units.size() < count) {
      throw new IllegalArgumentException(
          String.format(Locale.ROOT, CliText.NO_SUCH_UNITS, count, type, from.getName()));
    }
    final MoveVerdict verdict = checker.check(units, from, to);
    out.println(verdict.legal() ? CliText.VERDICT_LEGAL : CliText.VERDICT_ILLEGAL);
    out.println(GSON.toJson(verdict));
    return verdict.legal() ? 0 : 3;
  }

  private static int reach(final Arguments arguments, final PrintStream out) {
    final GameData data = GameLoading.load(arguments.requiredPath("--game"));
    final LegalityChecker checker =
        LegalityChecker.forStep(data, arguments.required("--step"), DEFAULT_SEED);
    final Optional<String> from = arguments.values("--from").stream().findFirst();
    from.ifPresent(name -> territory(data, name));
    out.println(GSON.toJson(ReachabilityIndex.compute(checker, from)));
    return 0;
  }

  private static int odds(final Arguments arguments, final PrintStream out, final PrintStream err) {
    final List<String> positional = arguments.positional();
    if (positional.equals(List.of("paris"))) {
      return paris(arguments, out);
    }
    if (positional.equals(List.of("strafe"))) {
      return strafe(arguments, out);
    }
    err.println(CliText.ERROR_PREFIX + CliText.UNKNOWN_ODDS_TARGET);
    return 2;
  }

  private static int paris(final Arguments arguments, final PrintStream out) {
    final GameData data = GameLoading.load(arguments.requiredPath("--game"));
    final OperationParisReport report =
        new OperationParisBaseline(
                data,
                "Germans",
                "Italians",
                "French",
                "France",
                arguments.longValue("--seed", DEFAULT_SEED),
                arguments.intValue("--max-pools", OperationParisBaseline.DEFAULT_MAX_POOLS))
            .run(arguments.intValue("--runs", DEFAULT_PARIS_RUNS));
    if (arguments.flag("--json")) {
      out.println(GSON.toJson(report));
    } else {
      out.print(ReportFormatter.toText(report));
    }
    return 0;
  }

  private static int strafe(final Arguments arguments, final PrintStream out) {
    final GameData data = GameLoading.load(arguments.requiredPath("--game"));
    final GamePlayer attacker = player(data, arguments.required("--attacker"));
    final GamePlayer defender = player(data, arguments.required("--defender"));
    final Territory territory = territory(data, arguments.required("--territory"));
    final List<Unit> force = ForceSelection.strikeForce(data, attacker, territory);
    final List<ForceSubsets.Force> forces =
        ForceSubsets.byLandCount(
            force,
            arguments.intValue("--land-step", DEFAULT_LAND_STEP),
            new TuvCostsCalculator().getCostsForTuv(attacker));
    final StrafeSweep sweep;
    try (BattleOddsService odds = new BattleOddsService(data)) {
      sweep =
          new StrafeEvaluator(odds)
              .sweep(
                  attacker,
                  defender,
                  territory,
                  forces,
                  List.copyOf(territory.getUnits()),
                  arguments.intValue("--rounds", DEFAULT_SWEEP_ROUNDS),
                  arguments.intValue("--runs", DEFAULT_SWEEP_RUNS));
    }
    if (arguments.flag("--json")) {
      out.println(GSON.toJson(sweep));
    } else {
      out.print(StrafeSweepFormatter.toText(sweep));
    }
    return 0;
  }

  private static Territory territory(final GameData data, final String name) {
    final Territory territory = data.getMap().getTerritoryOrNull(name);
    if (territory == null) {
      throw new IllegalArgumentException(
          String.format(Locale.ROOT, CliText.UNKNOWN_TERRITORY, name));
    }
    return territory;
  }

  private static GamePlayer player(final GameData data, final String name) {
    final GamePlayer player = data.getPlayerList().getPlayerId(name);
    if (player == null) {
      throw new IllegalArgumentException(String.format(Locale.ROOT, CliText.UNKNOWN_PLAYER, name));
    }
    return player;
  }

  /** {@code --key value} options (repeatable), bare {@code --flag}s and positional words. */
  record Arguments(Map<String, List<String>> options, List<String> positional) {
    static Arguments parse(final List<String> args) {
      final Map<String, List<String>> options = new LinkedHashMap<>();
      final List<String> positional = new ArrayList<>();
      String key = null;
      for (final String arg : args) {
        if (arg.startsWith("--")) {
          key = arg;
          options.computeIfAbsent(key, k -> new ArrayList<>());
        } else if (key != null) {
          options.get(key).add(arg);
          key = null;
        } else {
          positional.add(arg);
        }
      }
      return new Arguments(options, positional);
    }

    List<String> values(final String key) {
      return options.getOrDefault(key, List.of());
    }

    boolean flag(final String key) {
      return options.containsKey(key);
    }

    String required(final String key) {
      final List<String> values = values(key);
      if (values.isEmpty()) {
        throw new IllegalArgumentException(String.format(Locale.ROOT, CliText.MISSING_OPTION, key));
      }
      return values.get(0);
    }

    Path requiredPath(final String key) {
      return Path.of(required(key));
    }

    int intValue(final String key, final int defaultValue) {
      return (int) longValue(key, defaultValue);
    }

    long longValue(final String key, final long defaultValue) {
      final List<String> values = values(key);
      if (values.isEmpty()) {
        return defaultValue;
      }
      try {
        return Long.parseLong(values.get(0));
      } catch (final NumberFormatException e) {
        throw new IllegalArgumentException(
            String.format(Locale.ROOT, CliText.NOT_A_NUMBER, key, values.get(0)), e);
      }
    }
  }
}
