package org.triplea.generalstaff.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import games.strategy.triplea.xml.TestMapGameData;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.triplea.generalstaff.observe.GameObservation;
import org.triplea.generalstaff.observe.ObservationExporter;

class IntentParserTest {
  private static GameObservation observation;

  private static final String PARIS_ANSWER =
      """
      {"objective": "Italy captures France while Germany only weakens the garrison",
       "acting_player": "Italians", "target_territories": ["France"], "priority": "critical",
       "hard_constraints": ["Germany must not conquer France"],
       "soft_constraints": ["Preserve German land units for the east"],
       "risk_tolerance": "balanced", "planning_horizon_rounds": 1, "delegated_theaters": [],
       "approval_requirements": "irreversible_only",
       "mentioned_players": ["Italians", "Germans", "French"],
       "mentioned_territories": ["France"], "clarification_questions": []}
      """;

  @BeforeAll
  static void loadFixture() {
    observation = new ObservationExporter().export(TestMapGameData.GLOBAL1940.getGameData());
  }

  @Test
  void aWellFormedAnswerWithRealNamesIsValid() throws IOException {
    try (FakeOllama ollama = new FakeOllama()) {
      ollama.answerWith(PARIS_ANSWER);
      final IntentParser parser = new IntentParser(ollama.client(), "fake:1b");

      final ParsedDirective parsed =
          parser.parse("Italy, not Germany, must take Paris.", observation);

      assertThat(parsed.valid()).isTrue();
      assertThat(parsed.directive().actingPlayer()).isEqualTo("Italians");
      assertThat(parsed.directive().targetTerritories()).containsExactly("France");
      assertThat(parsed.directive().hardConstraints()).hasSize(1);
      assertThat(parsed.clarifications()).isEmpty();
      assertThat(parsed.model()).isEqualTo("fake:1b");
      final String systemPrompt =
          ollama
              .requests()
              .get(0)
              .getAsJsonArray("messages")
              .get(0)
              .getAsJsonObject()
              .get("content")
              .getAsString();
      assertThat(systemPrompt)
          .contains("Germans (30 PUs)")
          .contains("France (French)")
          .contains("Northern Italy");
      final String userPrompt =
          ollama
              .requests()
              .get(0)
              .getAsJsonArray("messages")
              .get(1)
              .getAsJsonObject()
              .get("content")
              .getAsString();
      assertThat(userPrompt).contains("Italy, not Germany, must take Paris.");
    }
  }

  @Test
  void unknownNamesAreQuestionedWithSuggestionsNotGuessed() {
    final StrategicDirective directive =
        new StrategicDirective(
            "Italy captures Paris",
            "Italy",
            List.of("Paris"),
            "high",
            List.of(),
            List.of(),
            "balanced",
            1,
            List.of(),
            "none",
            List.of("Italy", "French"),
            List.of("Paris"),
            List.of());

    final ParsedDirective parsed = IntentParser.validate(directive, observation, "test", 0L, "{}");

    assertThat(parsed.valid()).isFalse();
    assertThat(parsed.unknownPlayers()).containsExactly("Italy");
    assertThat(parsed.unknownTerritories()).containsExactly("Paris");
    assertThat(parsed.clarifications()).hasSize(2);
    assertThat(parsed.clarifications().get(0))
        .contains("'Italy'")
        .contains("Did you mean 'Italians'");
    // The hint lists the capitals of the powers the directive names, and says so.
    assertThat(parsed.clarifications().get(1))
        .contains("'Paris'")
        .contains("Capitals of the powers the directive names")
        .contains("France (French)")
        .doesNotContain("Capitals on this map")
        .doesNotContain("Germany (Germans)");
  }

  @Test
  void theModelsOwnQuestionsAndABlankObjectiveBlockAction() {
    final StrategicDirective directive =
        new StrategicDirective(
            "",
            "Germans",
            List.of(),
            "normal",
            List.of(),
            List.of(),
            "balanced",
            1,
            List.of(),
            "none",
            List.of(),
            List.of(),
            List.of("Which front do you mean?"));

    final ParsedDirective parsed = IntentParser.validate(directive, observation, "test", 0L, "{}");

    assertThat(parsed.valid()).isFalse();
    assertThat(parsed.unknownPlayers()).isEmpty();
    assertThat(parsed.clarifications()).containsExactly("Which front do you mean?");
  }

  @Test
  void answersThatAreNotTheExpectedJsonAreRejected() {
    assertThatThrownBy(() -> IntentParser.parseJson("not json at all"))
        .isInstanceOf(OllamaException.class);
    assertThat(IntentParser.parseJson("{}").targetTerritories()).isEmpty();
    assertThat(IntentParser.parseJson("{}").objective()).isEmpty();
  }

  @Test
  void schemaRequiresEveryField() {
    final var schema = IntentParser.schema();

    assertThat(schema.getAsJsonArray("required")).hasSize(13);
    assertThat(schema.getAsJsonObject("properties").has("hard_constraints")).isTrue();
    assertThat(
            schema
                .getAsJsonObject("properties")
                .getAsJsonObject("risk_tolerance")
                .getAsJsonArray("enum"))
        .hasSize(5);
  }

  @Test
  void aDirectiveWithoutAnActingPowerAsksWhoActs() {
    final StrategicDirective directive =
        new StrategicDirective(
            "Take France",
            "",
            List.of("France"),
            "high",
            List.of(),
            List.of(),
            "balanced",
            1,
            List.of(),
            "none",
            List.of(),
            List.of("France"),
            List.of());

    final ParsedDirective parsed = IntentParser.validate(directive, observation, "test", 0L, "{}");

    assertThat(parsed.valid()).isFalse();
    assertThat(parsed.unknownPlayers()).isEmpty();
    assertThat(parsed.clarifications()).singleElement().asString().contains("which power acts");
  }

  @Test
  void withNoPowerNamedTheHintListsEveryCapitalOnTheMap() {
    final StrategicDirective directive =
        new StrategicDirective(
            "Take Paris",
            "",
            List.of("Paris"),
            "high",
            List.of(),
            List.of(),
            "balanced",
            1,
            List.of(),
            "none",
            List.of(),
            List.of("Paris"),
            List.of());

    final ParsedDirective parsed = IntentParser.validate(directive, observation, "test", 0L, "{}");

    assertThat(parsed.valid()).isFalse();
    assertThat(parsed.clarifications()).hasSize(2);
    assertThat(parsed.clarifications().get(0))
        .contains("Capitals on this map:")
        .contains("France (French)")
        .contains("Germany (Germans)")
        .contains("Japan (Japanese)")
        .doesNotContain("powers the directive names");
    assertThat(parsed.clarifications().get(1)).contains("which power acts");
  }
}
