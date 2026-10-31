package org.triplea.generalstaff.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import games.strategy.triplea.xml.TestMapGameData;
import org.junit.jupiter.api.Test;
import org.triplea.generalstaff.observe.GameObservation;
import org.triplea.generalstaff.observe.ObservationExporter;

/**
 * Talks to the real local Ollama server. Runs only when {@code GENERAL_STAFF_OLLAMA} is set, so the
 * suite stays offline by default; the model can be chosen with {@code GENERAL_STAFF_MODEL}.
 */
class OllamaSmokeTest {
  @Test
  void parsesTheOperationParisDirectiveWithTheRealModel() {
    assumeTrue(System.getenv("GENERAL_STAFF_OLLAMA") != null, "set GENERAL_STAFF_OLLAMA to run");
    final OllamaClient client = OllamaClient.fromEnvironment();
    assumeTrue(client.isAvailable(), "Ollama is not running");
    final String model =
        System.getenv().getOrDefault("GENERAL_STAFF_MODEL", IntentParser.DEFAULT_MODEL);
    final GameObservation observation =
        new ObservationExporter().export(TestMapGameData.GLOBAL1940.getGameData());

    final ParsedDirective parsed =
        new IntentParser(client, model)
            .parse(
                "Italy, not Germany, must capture Paris this round. Germany may soften the French"
                    + " garrison but must not take the capital itself.",
                observation);

    System.out.println(parsed);
    assertThat(parsed.directive().objective()).isNotBlank();
    assertThat(parsed.valid() || !parsed.clarifications().isEmpty()).isTrue();
  }
}
