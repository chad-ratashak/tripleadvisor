package org.triplea.generalstaff.api;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import games.strategy.engine.data.GameData;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import org.triplea.generalstaff.doctrine.DoctrineReconciler;
import org.triplea.generalstaff.doctrine.DoctrineStore;
import org.triplea.generalstaff.json.Json;
import org.triplea.generalstaff.llm.Briefing;
import org.triplea.generalstaff.llm.BriefingFormatter;
import org.triplea.generalstaff.llm.DirectiveEval;
import org.triplea.generalstaff.llm.IntentParser;
import org.triplea.generalstaff.llm.OllamaClient;
import org.triplea.generalstaff.llm.ParsedDirective;
import org.triplea.generalstaff.odds.StrafeSweepFormatter;
import org.triplea.generalstaff.plan.OperationParisBaseline;
import org.triplea.generalstaff.plan.OperationParisReport;
import org.triplea.generalstaff.plan.PlanFormatter;
import org.triplea.generalstaff.plan.PlanReport;
import org.triplea.generalstaff.plan.ReportFormatter;
import org.triplea.generalstaff.rules.MoveVerdict;
import org.triplea.generalstaff.staff.AfterActionReport;
import org.triplea.generalstaff.staff.ApprovalGate;
import org.triplea.generalstaff.staff.ExecutionRecord;
import org.triplea.generalstaff.staff.ExecutionSession;
import org.triplea.generalstaff.staff.PlannedStep;

/** Command line entry point. See {@link CliText#USAGE} for the commands. */
public final class Cli {
  /** The commander's intent the Operation Paris briefing is written against when none is given. */
  public static final String DEFAULT_INTENT =
      "Italy, not Germany, is to capture France (Paris) this round; Germany may weaken the"
          + " garrison but must not conquer the territory itself and must preserve its army for"
          + " the east.";

  private static final Gson GSON = Json.GSON;
  private static final int DEFAULT_PARIS_RUNS = 2000;
  private static final int DEFAULT_TRAJECTORIES = 40;
  private static final int DEFAULT_SWEEP_RUNS = 1000;
  private static final int DEFAULT_SWEEP_ROUNDS = 3;
  private static final int DEFAULT_LAND_STEP = 3;
  private static final int DEFAULT_PORT = 4040;
  private static final int EXIT_USAGE = 2;
  private static final int EXIT_NEGATIVE = 3;

  private Cli() {}

  public static void main(final String[] args) {
    System.exit(run(List.of(args), System.out, System.err));
  }

  /** Runs one command; returns the process exit code. */
  static int run(final List<String> args, final PrintStream out, final PrintStream err) {
    if (args.isEmpty()) {
      err.print(usage());
      return EXIT_USAGE;
    }
    try {
      final Arguments arguments = Arguments.parse(args.subList(1, args.size()));
      final StaffService service =
          new StaffService(
              OllamaClient.fromEnvironment(),
              arguments.values("--model").stream().findFirst().orElse(IntentParser.DEFAULT_MODEL));
      return switch (args.get(0)) {
        case "observe" -> observe(service, arguments, out);
        case "history" -> history(service, arguments, out);
        case "legal" -> legal(service, arguments, out);
        case "reach" -> reach(service, arguments, out);
        case "odds" -> odds(service, arguments, out, err);
        case "plan" -> plan(service, arguments, out, err);
        case "advise" -> advise(service, arguments, out);
        case "validate" -> validate(service, arguments, out);
        case "explain" -> explain(service, arguments, out);
        case "eval" -> eval(service, arguments, out);
        case "serve" -> serve(service, arguments, out);
        case "host" -> host(service, arguments, out);
        case "doctrine" -> doctrine(service, arguments, out, err);
        default -> {
          err.print(usage());
          yield EXIT_USAGE;
        }
      };
    } catch (final IllegalArgumentException | IllegalStateException | IOException e) {
      err.println(CliText.ERROR_PREFIX + e.getMessage());
      return 1;
    }
  }

  static String usage() {
    return String.format(Locale.ROOT, CliText.USAGE, IntentParser.DEFAULT_MODEL);
  }

  private static int observe(
      final StaffService service, final Arguments arguments, final PrintStream out) {
    final GameData data = service.load(arguments.requiredPath("--game"));
    out.println(service.toJson(service.observe(data, arguments.values("--focus"))));
    return 0;
  }

  private static int history(
      final StaffService service, final Arguments arguments, final PrintStream out) {
    final GameData data = service.load(arguments.requiredPath("--game"));
    out.println(service.toJson(service.history(data)));
    return 0;
  }

  private static int legal(
      final StaffService service, final Arguments arguments, final PrintStream out) {
    final MoveVerdict verdict =
        service.legal(
            service.load(arguments.requiredPath("--game")),
            arguments.required("--step"),
            arguments.required("--from"),
            arguments.required("--to"),
            arguments.required("--type"),
            arguments.intValue("--count", 1));
    if (!arguments.flag("--json")) {
      out.println(verdict.legal() ? CliText.VERDICT_LEGAL : CliText.VERDICT_ILLEGAL);
    }
    out.println(GSON.toJson(verdict));
    return verdict.legal() ? 0 : EXIT_NEGATIVE;
  }

  private static int reach(
      final StaffService service, final Arguments arguments, final PrintStream out) {
    out.println(
        GSON.toJson(
            service.reach(
                service.load(arguments.requiredPath("--game")),
                arguments.required("--step"),
                arguments.values("--from").stream().findFirst())));
    return 0;
  }

  private static int odds(
      final StaffService service,
      final Arguments arguments,
      final PrintStream out,
      final PrintStream err) {
    final List<String> positional = arguments.positional();
    if (positional.equals(List.of("paris"))) {
      final OperationParisReport report = paris(service, arguments);
      out.print(
          arguments.flag("--json") ? GSON.toJson(report) + "\n" : ReportFormatter.toText(report));
      return 0;
    }
    if (positional.equals(List.of("strafe"))) {
      final var sweep =
          service.strafe(
              service.load(arguments.requiredPath("--game")),
              arguments.required("--attacker"),
              arguments.required("--defender"),
              arguments.required("--territory"),
              arguments.intValue("--rounds", DEFAULT_SWEEP_ROUNDS),
              arguments.intValue("--land-step", DEFAULT_LAND_STEP),
              arguments.intValue("--runs", DEFAULT_SWEEP_RUNS));
      out.print(
          arguments.flag("--json")
              ? GSON.toJson(sweep) + "\n"
              : StrafeSweepFormatter.toText(sweep));
      return 0;
    }
    err.println(CliText.ERROR_PREFIX + CliText.UNKNOWN_ODDS_TARGET);
    return EXIT_USAGE;
  }

  private static int plan(
      final StaffService service,
      final Arguments arguments,
      final PrintStream out,
      final PrintStream err) {
    if (!arguments.positional().equals(List.of("paris"))) {
      err.println(CliText.ERROR_PREFIX + CliText.UNKNOWN_PLAN_TARGET);
      return EXIT_USAGE;
    }
    final PlanReport report =
        service.plan(
            service.load(arguments.requiredPath("--game")),
            arguments.intValue("--trajectories", DEFAULT_TRAJECTORIES),
            arguments.longValue("--seed", StaffService.DEFAULT_SEED),
            arguments.doubleValue("--tolerance", 0.0),
            arguments.values("--intervening").stream().findFirst().orElse("passive"),
            arguments.values("--attacker").stream().findFirst().orElse("Germans"),
            arguments.values("--ally").stream().findFirst().orElse("Italians"),
            arguments.values("--defender").stream().findFirst().orElse("French"),
            arguments.values("--target").stream().findFirst().orElse("France"));
    final Optional<Briefing> briefing =
        arguments.flag("--explain")
            ? Optional.of(
                service.explainPlan(
                    report,
                    arguments.values("--intent").stream().findFirst().orElse(DEFAULT_INTENT),
                    Optional.empty()))
            : Optional.empty();
    if (arguments.flag("--json")) {
      out.println(
          briefing.isPresent()
              ? GSON.toJson(Map.of("report", report, "briefing", briefing.get()))
              : GSON.toJson(report));
    } else {
      out.print(PlanFormatter.toText(report));
      briefing.ifPresent(text -> out.print("\n" + BriefingFormatter.toText(text)));
    }
    return 0;
  }

  private static OperationParisReport paris(final StaffService service, final Arguments arguments) {
    return service.paris(
        service.load(arguments.requiredPath("--game")),
        arguments.intValue("--runs", DEFAULT_PARIS_RUNS),
        arguments.longValue("--seed", StaffService.DEFAULT_SEED),
        arguments.intValue("--max-pools", OperationParisBaseline.DEFAULT_MAX_POOLS));
  }

  private static int advise(
      final StaffService service, final Arguments arguments, final PrintStream out) {
    final ParsedDirective parsed =
        service.parseDirective(
            service.load(arguments.requiredPath("--game")),
            arguments.required("--directive"),
            Optional.empty());
    out.println(parsed.valid() ? CliText.DIRECTIVE_VALID : CliText.DIRECTIVE_QUESTIONS);
    for (final String question : parsed.clarifications()) {
      out.println("  - " + question);
    }
    out.println(GSON.toJson(parsed));
    return parsed.valid() ? 0 : EXIT_NEGATIVE;
  }

  private static int validate(
      final StaffService service, final Arguments arguments, final PrintStream out)
      throws IOException {
    final String json =
        arguments.flag("--parsed-file")
            ? Files.readString(Path.of(arguments.required("--parsed-file")), StandardCharsets.UTF_8)
            : arguments.required("--parsed");
    final ParsedDirective parsed =
        service.validateDirective(
            service.load(arguments.requiredPath("--game")),
            json,
            arguments.values("--source").stream().findFirst().orElse("external"));
    out.println(parsed.valid() ? CliText.DIRECTIVE_VALID : CliText.DIRECTIVE_QUESTIONS);
    for (final String question : parsed.clarifications()) {
      out.println("  - " + question);
    }
    out.println(GSON.toJson(parsed));
    return parsed.valid() ? 0 : EXIT_NEGATIVE;
  }

  private static int explain(
      final StaffService service, final Arguments arguments, final PrintStream out) {
    final OperationParisReport report = paris(service, arguments);
    final Briefing briefing =
        service.explain(
            report,
            arguments.values("--intent").stream().findFirst().orElse(DEFAULT_INTENT),
            Optional.empty());
    if (arguments.flag("--json")) {
      out.println(GSON.toJson(Map.of("report", report, "briefing", briefing)));
    } else {
      out.print(ReportFormatter.toText(report));
      out.println();
      out.print(BriefingFormatter.toText(briefing));
    }
    return 0;
  }

  private static int eval(
      final StaffService service, final Arguments arguments, final PrintStream out)
      throws IOException {
    final GameData data = service.load(arguments.requiredPath("--game"));
    final List<String> directives =
        arguments.values("--directives").stream().findFirst().isPresent()
            ? Files.readString(Path.of(arguments.required("--directives")), StandardCharsets.UTF_8)
                .lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .toList()
            : DirectiveEval.builtInDirectives();
    final DirectiveEval.Summary summary =
        DirectiveEval.run(
            new IntentParser(service.ollama(), service.model()),
            service.observe(data, List.of()),
            directives);
    out.print(
        arguments.flag("--json") ? GSON.toJson(summary) + "\n" : DirectiveEval.toText(summary));
    return 0;
  }

  private static int host(
      final StaffService service, final Arguments arguments, final PrintStream out)
      throws IOException {
    final GameData data = service.load(arguments.requiredPath("--game"));
    final Set<String> staff =
        new LinkedHashSet<>(List.of(arguments.required("--staff").split(",")));
    final String othersName = arguments.values("--others").stream().findFirst().orElse("idle");
    final ExecutionSession.Others others =
        switch (othersName) {
          case "idle" -> ExecutionSession.Others.IDLE;
          case "fast" -> ExecutionSession.Others.FAST_AI;
          default -> throw new IllegalArgumentException(CliText.UNKNOWN_OTHERS);
        };
    final String mode = arguments.values("--mode").stream().findFirst().orElse("auto-approve");
    final ApprovalGate gate =
        switch (mode) {
          case "auto-approve" -> ApprovalGate.autoApprove();
          case "auto-reject" -> ApprovalGate.autoReject();
          default -> throw new IllegalArgumentException(CliText.UNKNOWN_MODE);
        };
    final Path work =
        arguments.values("--work").stream()
            .findFirst()
            .map(Path::of)
            .orElseGet(
                () ->
                    Path.of(
                        System.getProperty("java.io.tmpdir"),
                        "general-staff-host-" + System.currentTimeMillis()));
    try (ExecutionSession session = service.startExecution(data, staff, others, gate, work)) {
      if (arguments.flag("--orders")) {
        final List<PlannedStep> steps =
            GSON.fromJson(
                Files.readString(Path.of(arguments.required("--orders")), StandardCharsets.UTF_8),
                new TypeToken<List<PlannedStep>>() {}.getType());
        session.setOrders(steps);
      }
      final int lastRound = arguments.intValue("--rounds", 1);
      session.runUntilRound(lastRound, 10_000);
      final AfterActionReport report = session.report();
      if (arguments.flag("--json")) {
        out.println(GSON.toJson(report));
      } else {
        out.println(
            String.format(
                Locale.ROOT,
                CliText.HOST_DONE,
                report.round(),
                report.currentStep(),
                report.records().size()));
        for (final ExecutionRecord record : report.records()) {
          out.println(
              "  "
                  + record.step()
                  + ": "
                  + (record.order() == null ? "" : record.order() + " ")
                  + record.note()
                  + (record.error() == null ? "" : " (" + record.error() + ")"));
        }
      }
    }
    return 0;
  }

  private static int doctrine(
      final StaffService service,
      final Arguments arguments,
      final PrintStream out,
      final PrintStream err) {
    final List<String> positional = arguments.positional();
    final DoctrineStore store = DoctrineStore.open(arguments.requiredPath("--file"));
    if (positional.equals(List.of("list"))) {
      out.println(GSON.toJson(store.entries()));
      return 0;
    }
    if (positional.equals(List.of("reconcile"))) {
      final GameData data = service.load(arguments.requiredPath("--game"));
      out.println(
          GSON.toJson(DoctrineReconciler.reconcile(store, service.observe(data, List.of()))));
      return 0;
    }
    if (positional.equals(List.of("add"))) {
      final GameData data = service.load(arguments.requiredPath("--game"));
      final String text = arguments.required("--directive");
      final ParsedDirective parsed = service.parseDirective(data, text, Optional.empty());
      out.println(
          GSON.toJson(
              store.add(
                  arguments.values("--title").stream().findFirst().orElse(text),
                  text,
                  parsed.directive(),
                  true,
                  arguments.values("--notes").stream().findFirst().orElse(""),
                  service.observe(data, List.of()))));
      return parsed.valid() ? 0 : EXIT_NEGATIVE;
    }
    if (positional.equals(List.of("remove"))) {
      out.println(GSON.toJson(Map.of("removed", store.remove(arguments.required("--id")))));
      return 0;
    }
    err.println(CliText.ERROR_PREFIX + CliText.UNKNOWN_DOCTRINE_TARGET);
    return EXIT_USAGE;
  }

  private static int serve(
      final StaffService service, final Arguments arguments, final PrintStream out)
      throws IOException {
    final LocalHttpApi api = new LocalHttpApi(arguments.intValue("--port", DEFAULT_PORT), service);
    api.start();
    out.println(String.format(Locale.ROOT, ApiText.SERVING, api.port(), service.model()));
    out.println(CliText.SERVE_STOP);
    final CountDownLatch forever = new CountDownLatch(1);
    Runtime.getRuntime().addShutdownHook(new Thread(api::close));
    try {
      forever.await();
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
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

    double doubleValue(final String key, final double defaultValue) {
      final List<String> values = values(key);
      if (values.isEmpty()) {
        return defaultValue;
      }
      try {
        return Double.parseDouble(values.get(0));
      } catch (final NumberFormatException e) {
        throw new IllegalArgumentException(
            String.format(Locale.ROOT, CliText.NOT_A_NUMBER, key, values.get(0)), e);
      }
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
