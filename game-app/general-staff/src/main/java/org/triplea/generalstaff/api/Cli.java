package org.triplea.generalstaff.api;

import com.google.gson.GsonBuilder;
import games.strategy.engine.data.GameData;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.triplea.generalstaff.engine.GameLoading;
import org.triplea.generalstaff.observe.ObservationExporter;
import org.triplea.generalstaff.plan.OperationParisBaseline;
import org.triplea.generalstaff.plan.OperationParisReport;
import org.triplea.generalstaff.plan.ReportFormatter;

/**
 * Command line entry point: {@code observe} prints a position as JSON, {@code odds paris} runs the
 * Operation Paris baseline. See {@link CliText#USAGE}.
 */
public final class Cli {
  private static final int DEFAULT_RUNS = 2000;
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

  private static int odds(final Arguments arguments, final PrintStream out, final PrintStream err) {
    if (!arguments.positional().equals(List.of("paris"))) {
      err.println(CliText.ERROR_PREFIX + CliText.UNKNOWN_ODDS_TARGET);
      return 2;
    }
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
            .run(arguments.intValue("--runs", DEFAULT_RUNS));
    if (arguments.flag("--json")) {
      out.println(
          new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(report));
    } else {
      out.print(ReportFormatter.toText(report));
    }
    return 0;
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

    Path requiredPath(final String key) {
      final List<String> values = values(key);
      if (values.isEmpty()) {
        throw new IllegalArgumentException(CliText.MISSING_GAME);
      }
      return Path.of(values.get(0));
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
