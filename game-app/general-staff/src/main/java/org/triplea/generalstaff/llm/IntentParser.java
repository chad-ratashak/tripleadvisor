package org.triplea.generalstaff.llm;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.triplea.generalstaff.observe.GameObservation;
import org.triplea.generalstaff.observe.GameObservation.PlayerSummary;
import org.triplea.generalstaff.observe.GameObservation.TerritorySummary;

/**
 * Turns a commander's plain-language directive into a {@link StrategicDirective} with the language
 * model, then checks every name in it against the game. The model handles language; this class
 * decides what is trusted. Pass a whole-map observation so the model sees every name.
 */
public final class IntentParser {
  public static final String DEFAULT_MODEL = "gemma4:e2b";
  private static final Gson GSON =
      new GsonBuilder()
          .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
          .create();
  private static final int SUGGESTIONS = 3;

  private final OllamaClient client;
  private final String model;

  public IntentParser(final OllamaClient client, final String model) {
    this.client = client;
    this.model = model;
  }

  public String model() {
    return model;
  }

  /** Parses and validates one directive; the result says whether it may be acted on. */
  public ParsedDirective parse(final String directiveText, final GameObservation observation) {
    final OllamaClient.ChatResult result =
        client.chatJson(
            model,
            systemPrompt(observation),
            String.format(Locale.ROOT, LlmText.INTENT_USER, directiveText),
            schema(),
            0.0);
    return validate(
        parseJson(result.content()), observation, model, result.latencyMillis(), result.content());
  }

  /** The model's JSON as a directive; the shape is enforced by the schema, nulls are normalized. */
  public static StrategicDirective parseJson(final String json) {
    try {
      final StrategicDirective directive = GSON.fromJson(json, StrategicDirective.class);
      if (directive == null) {
        throw new OllamaException("The model returned an empty answer");
      }
      return directive.normalized();
    } catch (final JsonSyntaxException e) {
      throw new OllamaException("The model's answer is not the expected JSON: " + json, e);
    }
  }

  /** Checks every player and territory name against the observation; unknown names ask back. */
  public static ParsedDirective validate(
      final StrategicDirective directive,
      final GameObservation observation,
      final String model,
      final long latencyMillis,
      final String rawJson) {
    final Set<String> players =
        observation.players().stream().map(PlayerSummary::name).collect(Collectors.toSet());
    final Set<String> territories =
        observation.territories().stream().map(TerritorySummary::name).collect(Collectors.toSet());

    final Set<String> namedPlayers = new LinkedHashSet<>();
    if (!directive.actingPlayer().isBlank()) {
      namedPlayers.add(directive.actingPlayer());
    }
    namedPlayers.addAll(directive.mentionedPlayers());
    final Set<String> namedTerritories = new LinkedHashSet<>(directive.targetTerritories());
    namedTerritories.addAll(directive.mentionedTerritories());

    final List<String> unknownPlayers =
        namedPlayers.stream().filter(name -> !players.contains(name)).toList();
    final List<String> unknownTerritories =
        namedTerritories.stream().filter(name -> !territories.contains(name)).toList();

    final List<String> clarifications = new ArrayList<>(directive.clarificationQuestions());
    for (final String unknown : unknownPlayers) {
      clarifications.add(
          String.format(
                  Locale.ROOT, LlmText.UNKNOWN_PLAYER, unknown, String.join(", ", sorted(players)))
              + suggestion(unknown, players));
    }
    for (final String unknown : unknownTerritories) {
      clarifications.add(
          String.format(Locale.ROOT, LlmText.UNKNOWN_TERRITORY, unknown)
              + capitalHint(observation, namedPlayers)
              + suggestion(unknown, territories));
    }
    if (directive.actingPlayer().isBlank()) {
      clarifications.add(LlmText.MISSING_ACTING_PLAYER);
    }
    final boolean valid =
        unknownPlayers.isEmpty()
            && unknownTerritories.isEmpty()
            && directive.clarificationQuestions().isEmpty()
            && !directive.objective().isBlank()
            && !directive.actingPlayer().isBlank();
    return new ParsedDirective(
        directive,
        valid,
        unknownPlayers,
        unknownTerritories,
        clarifications,
        model,
        latencyMillis,
        rawJson);
  }

  static String systemPrompt(final GameObservation observation) {
    final String players =
        observation.players().stream()
            .map(player -> player.name() + " (" + player.pus() + " PUs)")
            .collect(Collectors.joining(", "));
    final String capitals =
        observation.territories().stream()
            .filter(territory -> territory.capitalOf() != null)
            .map(territory -> territory.name() + " (" + territory.capitalOf() + ")")
            .sorted()
            .collect(Collectors.joining(", "));
    final String territories =
        observation.territories().stream()
            .map(TerritorySummary::name)
            .sorted()
            .collect(Collectors.joining(", "));
    return String.format(
        Locale.ROOT,
        LlmText.INTENT_SYSTEM,
        observation.gameName(),
        observation.round(),
        observation.step(),
        players,
        capitals,
        territories);
  }

  /** The JSON schema the model's answer must follow (Ollama structured output). */
  static JsonObject schema() {
    final JsonObject properties = new JsonObject();
    properties.add("objective", string());
    properties.add("acting_player", string());
    properties.add("target_territories", stringArray());
    properties.add("priority", enumeration("critical", "high", "normal", "low"));
    properties.add("hard_constraints", stringArray());
    properties.add("soft_constraints", stringArray());
    properties.add(
        "risk_tolerance", enumeration("minimal", "cautious", "balanced", "aggressive", "reckless"));
    properties.add("planning_horizon_rounds", integer());
    properties.add("delegated_theaters", stringArray());
    properties.add(
        "approval_requirements", enumeration("every_action", "irreversible_only", "none"));
    properties.add("mentioned_players", stringArray());
    properties.add("mentioned_territories", stringArray());
    properties.add("clarification_questions", stringArray());
    final JsonObject schema = new JsonObject();
    schema.addProperty("type", "object");
    schema.add("properties", properties);
    final JsonArray required = new JsonArray();
    properties.keySet().forEach(required::add);
    schema.add("required", required);
    return schema;
  }

  private static String suggestion(final String unknown, final Set<String> candidates) {
    final List<String> closest = NameMatcher.closest(unknown, candidates, SUGGESTIONS);
    return closest.isEmpty()
        ? ""
        : String.format(
            Locale.ROOT,
            LlmText.SUGGESTION,
            closest.stream().map(name -> "'" + name + "'").collect(Collectors.joining(" or ")));
  }

  private static String capitalHint(
      final GameObservation observation, final Set<String> namedPlayers) {
    final List<String> capitals =
        observation.territories().stream()
            .filter(territory -> territory.capitalOf() != null)
            .filter(
                territory -> namedPlayers.isEmpty() || namedPlayers.contains(territory.capitalOf()))
            .map(territory -> territory.name() + " (" + territory.capitalOf() + ")")
            .sorted()
            .toList();
    if (capitals.isEmpty()) {
      return "";
    }
    return String.format(
        Locale.ROOT,
        namedPlayers.isEmpty() ? LlmText.CAPITAL_HINT : LlmText.CAPITAL_HINT_NAMED,
        String.join(", ", capitals));
  }

  private static List<String> sorted(final Set<String> names) {
    return names.stream().sorted().toList();
  }

  private static JsonObject string() {
    final JsonObject type = new JsonObject();
    type.addProperty("type", "string");
    return type;
  }

  private static JsonObject integer() {
    final JsonObject type = new JsonObject();
    type.addProperty("type", "integer");
    return type;
  }

  private static JsonObject stringArray() {
    final JsonObject type = new JsonObject();
    type.addProperty("type", "array");
    type.add("items", string());
    return type;
  }

  private static JsonObject enumeration(final String... values) {
    final JsonObject type = string();
    final JsonArray allowed = new JsonArray();
    for (final String value : values) {
      allowed.add(value);
    }
    type.add("enum", allowed);
    return type;
  }
}
