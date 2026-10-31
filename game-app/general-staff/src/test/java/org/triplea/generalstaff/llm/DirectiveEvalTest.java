package org.triplea.generalstaff.llm;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.triplea.xml.TestMapGameData;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.triplea.generalstaff.observe.GameObservation;
import org.triplea.generalstaff.observe.ObservationExporter;

class DirectiveEvalTest {
  @Test
  void theBuiltInSetHasTwentyDirectivesAndSkipsComments() {
    assertThat(DirectiveEval.builtInDirectives()).hasSize(20);
    assertThat(
            DirectiveEval.parseDirectives(
"""
# comment

  one
two
"""))
        .containsExactly("one", "two");
  }

  @Test
  void countsShapeAndNameFailuresSeparately() throws IOException {
    final GameObservation observation =
        new ObservationExporter().export(TestMapGameData.GLOBAL1940.getGameData());
    try (FakeOllama ollama = new FakeOllama()) {
      ollama.answerWith(
          "{\"objective\":\"take France\",\"acting_player\":\"Italians\",\"target_territories\":[\"France\"],"
              + "\"priority\":\"high\",\"hard_constraints\":[],\"soft_constraints\":[],\"risk_tolerance\":\"balanced\","
              + "\"planning_horizon_rounds\":1,\"delegated_theaters\":[],\"approval_requirements\":\"none\","
              + "\"mentioned_players\":[],\"mentioned_territories\":[],\"clarification_questions\":[]}");
      ollama.answerWith(
          "{\"objective\":\"take Paris\",\"acting_player\":\"Italy\",\"target_territories\":[\"Paris\"]}");
      ollama.answerWith("this is not json");
      final IntentParser parser = new IntentParser(ollama.client(), "fake:1b");

      final DirectiveEval.Summary summary =
          DirectiveEval.run(parser, observation, List.of("one", "two", "three"));

      assertThat(summary.n()).isEqualTo(3);
      assertThat(summary.schemaCompliance().successes()).isEqualTo(2);
      assertThat(summary.namesValid().successes()).isEqualTo(1);
      assertThat(summary.cases().get(1).unknownNames()).containsExactly("Italy", "Paris");
      assertThat(summary.cases().get(2).failure()).isNotBlank();
      assertThat(DirectiveEval.toText(summary)).contains("fake:1b").contains("3 directives");
    }
  }
}
