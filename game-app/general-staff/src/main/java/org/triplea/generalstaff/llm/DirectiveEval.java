package org.triplea.generalstaff.llm;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.triplea.generalstaff.observe.GameObservation;
import org.triplea.generalstaff.odds.Proportion;

/**
 * Measures how well a model parses directives: did it return the right JSON shape, and did every
 * name in it exist in the game? Rates carry their sample size like every other figure here.
 */
public final class DirectiveEval {
  public static final String BUILT_IN_DIRECTIVES =
      "org/triplea/generalstaff/llm/eval-directives.txt";

  /** One directive's outcome. */
  public record Case(
      String directive,
      boolean schemaOk,
      boolean namesValid,
      long latencyMillis,
      String actingPlayer,
      List<String> targetTerritories,
      List<String> unknownNames,
      String failure) {}

  /** The whole run. */
  public record Summary(
      String model,
      int n,
      Proportion schemaCompliance,
      Proportion namesValid,
      double meanLatencyMillis,
      List<Case> cases) {}

  private DirectiveEval() {}

  /** The directives shipped with the module, one per line; blank lines and # comments skipped. */
  public static List<String> builtInDirectives() {
    try (InputStream in =
        DirectiveEval.class.getClassLoader().getResourceAsStream(BUILT_IN_DIRECTIVES)) {
      if (in == null) {
        throw new IllegalStateException("Missing resource " + BUILT_IN_DIRECTIVES);
      }
      return parseDirectives(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    } catch (final IOException e) {
      throw new IllegalStateException("Could not read " + BUILT_IN_DIRECTIVES, e);
    }
  }

  static List<String> parseDirectives(final String text) {
    return text.lines()
        .map(String::strip)
        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
        .toList();
  }

  /** Runs every directive through the parser against the observation. */
  public static Summary run(
      final IntentParser parser, final GameObservation observation, final List<String> directives) {
    final List<Case> cases = new ArrayList<>();
    for (final String directive : directives) {
      cases.add(evaluate(parser, observation, directive));
    }
    final int n = cases.size();
    final int schemaOk = (int) cases.stream().filter(Case::schemaOk).count();
    final int namesValid = (int) cases.stream().filter(Case::namesValid).count();
    final double meanLatency = cases.stream().mapToLong(Case::latencyMillis).average().orElse(0.0);
    return new Summary(
        parser.model(),
        n,
        Proportion.of(schemaOk, n),
        Proportion.of(namesValid, n),
        meanLatency,
        cases);
  }

  private static Case evaluate(
      final IntentParser parser, final GameObservation observation, final String directive) {
    final long started = System.nanoTime();
    try {
      final ParsedDirective parsed = parser.parse(directive, observation);
      final List<String> unknown = new ArrayList<>(parsed.unknownPlayers());
      unknown.addAll(parsed.unknownTerritories());
      return new Case(
          directive,
          true,
          parsed.unknownPlayers().isEmpty() && parsed.unknownTerritories().isEmpty(),
          parsed.latencyMillis(),
          parsed.directive().actingPlayer(),
          parsed.directive().targetTerritories(),
          unknown,
          "");
    } catch (final OllamaException e) {
      return new Case(
          directive,
          false,
          false,
          (System.nanoTime() - started) / 1_000_000,
          "",
          List.of(),
          List.of(),
          e.getMessage());
    }
  }

  /** A one-page text table of the run. Layout only; labels are in {@link LlmText}. */
  public static String toText(final Summary summary) {
    final StringBuilder out = new StringBuilder();
    out.append(String.format(Locale.ROOT, LlmText.EVAL_TITLE, summary.model(), summary.n()))
        .append('\n');
    out.append(LlmText.EVAL_SCHEMA)
        .append(": ")
        .append(summary.schemaCompliance().format())
        .append('\n');
    out.append(LlmText.EVAL_NAMES).append(": ").append(summary.namesValid().format()).append('\n');
    out.append(LlmText.EVAL_LATENCY)
        .append(": ")
        .append(String.format(Locale.ROOT, "%.1f s", summary.meanLatencyMillis() / 1000.0))
        .append('\n')
        .append('\n');
    out.append(LlmText.EVAL_COLUMNS).append('\n');
    for (final Case c : summary.cases()) {
      out.append(
          String.format(
              Locale.ROOT,
              "%-3s %-6s %7.1f  %s -> %s%s | %s%n",
              c.schemaOk() ? "yes" : "no",
              c.namesValid() ? "yes" : "no",
              c.latencyMillis() / 1000.0,
              c.actingPlayer().isBlank() ? "?" : c.actingPlayer(),
              c.targetTerritories().isEmpty() ? "?" : String.join("/", c.targetTerritories()),
              c.unknownNames().isEmpty()
                  ? ""
                  : " (unknown: " + String.join(", ", c.unknownNames()) + ")",
              c.directive()));
    }
    out.append(LlmText.EVAL_NOTE).append('\n');
    return out.toString();
  }
}
