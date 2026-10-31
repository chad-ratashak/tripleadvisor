package org.triplea.generalstaff.observe;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.triplea.xml.TestMapGameData;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * The observation JSON for the small Minimap fixture is pinned to a golden file, so any change to
 * the schema or to the values is a deliberate, reviewed change. Regenerate with the CLI: {@code
 * observe --game <minimap.xml>} and copy the output over the golden file.
 */
class MinimapGoldenTest {
  @Test
  void observationMatchesTheGoldenFile() throws IOException {
    final ObservationExporter exporter = new ObservationExporter();
    final String actual = exporter.toJson(exporter.export(TestMapGameData.MINIMAP.getGameData()));

    try (InputStream in =
        getClass().getClassLoader().getResourceAsStream("golden/minimap-observation.json")) {
      assertThat(in).as("golden file present").isNotNull();
      final String expected = new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
      assertThat(actual.strip()).isEqualTo(expected);
    }
  }
}
