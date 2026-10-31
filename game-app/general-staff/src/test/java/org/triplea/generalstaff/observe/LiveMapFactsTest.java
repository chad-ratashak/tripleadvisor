package org.triplea.generalstaff.observe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import games.strategy.triplea.xml.TestMapGameData;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.triplea.generalstaff.engine.GameLoading;
import org.triplea.generalstaff.observe.GameObservation.PlayerSummary;
import org.triplea.generalstaff.observe.GameObservation.TerritorySummary;

/**
 * When the World War II Global map is installed in the engine's maps folder, the live 2nd Edition
 * file must agree with the checked-in Balanced fixture on every France fact the planner relies on.
 * Skipped where the map is not installed, so the suite stays offline-safe.
 */
class LiveMapFactsTest {
  static final Path LIVE_2ND_EDITION =
      Path.of(
          System.getProperty("user.home"),
          "triplea",
          "downloadedMaps",
          "world_war_ii_global",
          "map",
          "games",
          "ww2global40_2nd_edition.xml");

  @Test
  void liveSecondEditionMatchesTheFixtureOnFranceFacts() {
    assumeTrue(Files.exists(LIVE_2ND_EDITION), "World War II Global map not installed");
    final ObservationExporter exporter = new ObservationExporter();
    final GameObservation live =
        exporter.export(GameLoading.load(LIVE_2ND_EDITION), List.of("France"));
    final GameObservation fixture =
        exporter.export(TestMapGameData.GLOBAL1940.getGameData(), List.of("France"));

    assertThat(live.gameName()).isEqualTo("World War II Global 1940 2nd Edition");
    final TerritorySummary liveFrance = territory(live, "France");
    final TerritorySummary fixtureFrance = territory(fixture, "France");
    assertThat(liveFrance.units()).isEqualTo(fixtureFrance.units());
    assertThat(liveFrance.neighbors()).isEqualTo(fixtureFrance.neighbors());
    assertThat(liveFrance.capitalOf()).isEqualTo("French");
    assertThat(liveFrance.production()).isEqualTo(4);
    for (final String name :
        List.of("Germans", "Italians", "French", "British", "Russians", "Americans")) {
      assertThat(player(live, name).pus()).as(name).isEqualTo(player(fixture, name).pus());
    }
    for (final String axis : List.of("Germans", "Italians")) {
      assertThat(player(live, axis).objectives()).isNotEmpty();
      assertThat(player(live, axis).objectives())
          .noneMatch(objective -> objective.name().contains("France"));
    }
    assertThat(live.rules().lowLuck()).isFalse();
    assertThat(live.rules().attackerRetreatPlanes()).isTrue();
  }

  private static TerritorySummary territory(final GameObservation observation, final String name) {
    return observation.territories().stream()
        .filter(t -> t.name().equals(name))
        .findFirst()
        .orElseThrow();
  }

  private static PlayerSummary player(final GameObservation observation, final String name) {
    return observation.players().stream()
        .filter(p -> p.name().equals(name))
        .findFirst()
        .orElseThrow();
  }
}
