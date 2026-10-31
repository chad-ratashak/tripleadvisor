package org.triplea.generalstaff.llm;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.triplea.generalstaff.observe.GameObservation;
import org.triplea.generalstaff.observe.GameObservation.TerritorySummary;
import org.triplea.generalstaff.observe.UnitCounts;
import org.triplea.generalstaff.odds.BattleEstimate;
import org.triplea.generalstaff.odds.BattleEstimate.UnitMean;
import org.triplea.generalstaff.plan.OperationParisReport;
import org.triplea.generalstaff.plan.PlanReport;

/**
 * Asks the language model for a briefing over labelled facts and fills the figures in itself. The
 * model may only cite a figure by its label; any digits it writes on its own are reported as a
 * warning and never mistaken for the engine's numbers.
 */
public final class Explainer {
  private static final Pattern PLACEHOLDER = Pattern.compile("\\{(F\\d+)\\}");
  private static final Pattern DIGITS = Pattern.compile("\\d[\\d.,%]*");
  private static final Set<String> AIRCRAFT = Set.of("fighter", "tactical_bomber", "bomber");
  private static final Gson GSON = new Gson();

  /** One labelled figure the briefing may cite, with the text the label expands to. */
  public record Fact(String label, String description, String value) {}

  private final OllamaClient client;
  private final String model;

  public Explainer(final OllamaClient client, final String model) {
    this.client = client;
    this.model = model;
  }

  public String model() {
    return model;
  }

  /** Briefs the commander on {@code facts} in the light of {@code intent}. */
  public Briefing brief(final String intent, final List<Fact> facts) {
    final String factLines =
        facts.stream()
            .map(
                fact ->
                    String.format(
                        Locale.ROOT,
                        LlmText.FACT_LINE,
                        fact.label(),
                        fact.description(),
                        fact.value()))
            .collect(Collectors.joining("\n"));
    final OllamaClient.ChatResult result =
        client.chatJson(
            model,
            LlmText.EXPLAIN_SYSTEM,
            String.format(Locale.ROOT, LlmText.EXPLAIN_USER, intent, factLines),
            schema(),
            0.2);
    return fill(parseJson(result.content()), facts, model, result.latencyMillis());
  }

  /** The figures of an Operation Paris report, labelled F1, F2, ... */
  public static List<Fact> facts(final OperationParisReport report) {
    final List<Fact> facts = new ArrayList<>();
    facts.add(
        fact(
            facts,
            "German full attack destroys every defender in France",
            report.germanFullAttack().attackerWins().format()));
    facts.add(
        fact(
            facts,
            "German air-only strike destroys every defender (France stays French, open to an Italian walk-in)",
            report.germanAirOnlyStrike().attackerWins().format()));
    facts.add(
        fact(
            facts,
            "German aircraft expected to be lost in the air-only strike",
            String.format(
                Locale.ROOT,
                "%.1f of %d",
                aircraftLost(report.germanAirOnlyStrike()),
                aircraft(report.germanAirOnlyStrike()))));
    facts.add(
        fact(
            facts,
            "Italy owns France after the German air-only strike and its own attack (no Allied move in between)",
            report.italianFollowUp().italyOwnsFrance().format()));
    facts.add(
        fact(
            facts,
            "France already empty when Italy arrives (walk-in)",
            report.italianFollowUp().walkIn().format()));
    for (final BattleEstimate strafe : report.germanStrafes()) {
      facts.add(
          fact(
              facts,
              "Whole German force retreating after round "
                  + strafe.options().retreatAfterRound()
                  + " still destroys every defender, i.e. conquers France by accident",
              strafe.attackerWins().format()));
    }
    facts.add(
        fact(
            facts,
            "Italy alone against the full French garrison destroys every defender",
            report.italyAlone().attackerWins().format()));
    facts.add(
        fact(
            facts,
            "Average TUV swing of the German full attack (positive favours Germany)",
            String.format(Locale.ROOT, "%+.1f", report.germanFullAttack().averageTuvSwing())));
    return facts;
  }

  /** The figures of a ranked plan report: the top three plans and the rejected count. */
  public static List<Fact> facts(final PlanReport report) {
    final List<Fact> facts = new ArrayList<>();
    final long feasible =
        report.plans().stream().filter(PlanReport.OperationalPlan::feasible).count();
    facts.add(
        fact(
            facts,
            "Candidate plans evaluated / plans that keep the constraint",
            report.plans().size() + " / " + feasible));
    for (final PlanReport.OperationalPlan plan : report.plans().stream().limit(3).toList()) {
      final String name =
          "Plan '" + plan.id() + "' (rank " + plan.rank() + ", " + plan.description() + ")";
      facts.add(
          fact(
              facts,
              name + ": " + report.ally() + " own " + report.target() + " at the end of their turn",
              plan.metrics().italyOwnsFrance().format()));
      facts.add(
          fact(
              facts,
              name + ": " + report.attacker() + " conquer " + report.target() + " (forbidden)",
              plan.metrics().germanyConquersFrance().format()));
      facts.add(
          fact(
              facts,
              name + ": aircraft of the " + report.attacker() + " lost on average",
              String.format(Locale.ROOT, "%.1f", plan.metrics().meanGermanAircraftLost())));
      facts.add(
          fact(
              facts,
              name + ": TUV of the " + report.attacker() + " lost on average",
              String.format(Locale.ROOT, "%.1f", plan.metrics().meanGermanTuvLost())));
    }
    return facts;
  }

  /** The figures describing one territory in an observation. */
  public static List<Fact> facts(final GameObservation observation, final String territoryName) {
    final TerritorySummary territory =
        observation.territories().stream()
            .filter(candidate -> candidate.name().equals(territoryName))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown territory: " + territoryName));
    final List<Fact> facts = new ArrayList<>();
    facts.add(
        fact(
            facts,
            "Owner of " + territory.name(),
            territory.owner() == null ? "nobody" : territory.owner()));
    facts.add(
        fact(
            facts,
            "Production value of " + territory.name(),
            Integer.toString(territory.production())));
    facts.add(fact(facts, "Units in " + territory.name(), UnitCounts.describe(territory.units())));
    facts.add(
        fact(facts, "Neighbours of " + territory.name(), String.join(", ", territory.neighbors())));
    for (final GameObservation.PlayerSummary player : observation.players()) {
      facts.add(fact(facts, "Treasury of " + player.name(), player.pus() + " PUs"));
    }
    return facts;
  }

  /** Puts the engine's figures into the model's text and flags anything the model invented. */
  static Briefing fill(
      final RawBriefing raw, final List<Fact> facts, final String model, final long latencyMillis) {
    final Map<String, String> values =
        facts.stream().collect(Collectors.toMap(Fact::label, Fact::value, (a, b) -> a));
    final Set<String> cited = new LinkedHashSet<>();
    final Set<String> invented = new LinkedHashSet<>();
    final Function<String, String> expand =
        text -> {
          final String withoutPlaceholders = PLACEHOLDER.matcher(text).replaceAll(" ");
          final Matcher digits = DIGITS.matcher(withoutPlaceholders);
          while (digits.find()) {
            invented.add(digits.group());
          }
          final Matcher placeholders = PLACEHOLDER.matcher(text);
          final StringBuilder out = new StringBuilder();
          while (placeholders.find()) {
            final String label = placeholders.group(1);
            cited.add(label);
            placeholders.appendReplacement(
                out, Matcher.quoteReplacement(values.getOrDefault(label, placeholders.group())));
          }
          placeholders.appendTail(out);
          return out.toString();
        };
    final String headline = expand.apply(nonNull(raw.headline()));
    final List<String> assessment = nonNull(raw.assessment()).stream().map(expand).toList();
    final String recommendation = expand.apply(nonNull(raw.recommendation()));
    final List<String> risks = nonNull(raw.risks()).stream().map(expand).toList();
    final List<String> warnings = new ArrayList<>();
    if (!invented.isEmpty()) {
      warnings.add(
          String.format(Locale.ROOT, LlmText.INVENTED_NUMBERS, String.join(", ", invented)));
    }
    if (cited.isEmpty() && !facts.isEmpty()) {
      warnings.add(LlmText.MISSING_FIGURE);
    }
    return new Briefing(
        headline, assessment, recommendation, risks, warnings, model, latencyMillis);
  }

  static RawBriefing parseJson(final String json) {
    try {
      final RawBriefing raw = GSON.fromJson(json, RawBriefing.class);
      if (raw == null) {
        throw new OllamaException("The model returned an empty briefing");
      }
      return raw;
    } catch (final JsonSyntaxException e) {
      throw new OllamaException("The model's briefing is not the expected JSON: " + json, e);
    }
  }

  /** The model's answer before the figures are filled in. */
  record RawBriefing(
      String headline, List<String> assessment, String recommendation, List<String> risks) {}

  static JsonObject schema() {
    final JsonObject string = new JsonObject();
    string.addProperty("type", "string");
    final JsonObject strings = new JsonObject();
    strings.addProperty("type", "array");
    strings.add("items", string);
    final JsonObject properties = new JsonObject();
    properties.add("headline", string);
    properties.add("assessment", strings);
    properties.add("recommendation", string);
    properties.add("risks", strings);
    final JsonObject schema = new JsonObject();
    schema.addProperty("type", "object");
    schema.add("properties", properties);
    final JsonArray required = new JsonArray();
    properties.keySet().forEach(required::add);
    schema.add("required", required);
    return schema;
  }

  private static Fact fact(final List<Fact> soFar, final String description, final String value) {
    return new Fact("F" + (soFar.size() + 1), description, value);
  }

  private static double aircraftLost(final BattleEstimate estimate) {
    return estimate.expectedAttackerLosses().stream()
        .filter(loss -> AIRCRAFT.contains(loss.type()))
        .mapToDouble(UnitMean::mean)
        .sum();
  }

  private static int aircraft(final BattleEstimate estimate) {
    return estimate.attackingUnits().stream()
        .filter(stack -> AIRCRAFT.contains(stack.type()))
        .mapToInt(stack -> stack.count())
        .sum();
  }

  private static String nonNull(final String value) {
    return value == null ? "" : value;
  }

  private static List<String> nonNull(final List<String> values) {
    return values == null ? List.of() : values;
  }
}
