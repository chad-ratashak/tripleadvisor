package org.triplea.generalstaff.observe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonParser;
import games.strategy.engine.data.GameData;
import games.strategy.triplea.xml.TestMapGameData;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.triplea.generalstaff.observe.GameObservation.PlayerSummary;
import org.triplea.generalstaff.observe.GameObservation.Relationship;
import org.triplea.generalstaff.observe.GameObservation.TerritorySummary;
import org.triplea.generalstaff.observe.GameObservation.UnitCount;

/**
 * Pins the Global 1940 facts the Operation Paris planner relies on, so fixture drift is caught, and
 * checks the export itself.
 */
class ObservationExporterTest {
  private static GameData data;
  private static final ObservationExporter exporter = new ObservationExporter();

  @BeforeAll
  static void loadFixture() {
    data = TestMapGameData.GLOBAL1940.getGameData();
  }

  @Test
  void franceIsTheFrenchCapitalWithSixNeighbours() {
    final GameObservation observation = exporter.export(data, List.of("France"));

    assertThat(observation.territories()).hasSize(7);
    final TerritorySummary france = territory(observation, "France");
    assertThat(france.owner()).isEqualTo("French");
    assertThat(france.water()).isFalse();
    assertThat(france.production()).isEqualTo(4);
    assertThat(france.capitalOf()).isEqualTo("French");
    assertThat(france.victoryCity()).isEqualTo(1);
    assertThat(france.neighbors())
        .containsExactly(
            "Holland Belgium",
            "Normandy Bordeaux",
            "Northern Italy",
            "Southern France",
            "Switzerland",
            "Western Germany");
  }

  @Test
  void franceStartsWithTwelveCombatUnitsAndTwoInfrastructureUnits() {
    final TerritorySummary france = territory(exporter.export(data, List.of("France")), "France");

    assertThat(france.units())
        .containsExactly(
            new UnitCount("British", "armour", 1),
            new UnitCount("British", "artillery", 1),
            new UnitCount("French", "aaGun", 1),
            new UnitCount("French", "airfield", 1),
            new UnitCount("French", "armour", 1),
            new UnitCount("French", "artillery", 1),
            new UnitCount("French", "factory_major", 1),
            new UnitCount("French", "fighter", 1),
            new UnitCount("French", "infantry", 6));
    assertThat(UnitCounts.total(france.units())).isEqualTo(14);
  }

  @Test
  void treasuriesMatchTheMapAndNoAxisObjectiveMentionsFrance() {
    final GameObservation observation = exporter.export(data, List.of("France"));

    assertThat(player(observation, "French").pus()).isEqualTo(19);
    assertThat(player(observation, "Germans").pus()).isEqualTo(30);
    assertThat(player(observation, "Italians").pus()).isEqualTo(10);
    assertThat(player(observation, "Germans").capitals()).containsExactly("Germany");
    assertThat(player(observation, "French").capitals()).containsExactly("France");
    for (final String axis : List.of("Germans", "Italians")) {
      assertThat(player(observation, axis).objectives()).isNotEmpty();
      assertThat(player(observation, axis).objectives())
          .noneMatch(objective -> objective.name().contains("France"));
    }
  }

  @Test
  void axisPowersStartAtWarWithFranceButNotRussia() {
    final GameObservation observation = exporter.export(data, List.of("France"));

    assertThat(relationship(observation, "Germans", "French").atWar()).isTrue();
    assertThat(relationship(observation, "Italians", "French").atWar()).isTrue();
    assertThat(relationship(observation, "Germans", "Russians").atWar()).isFalse();
    assertThat(relationship(observation, "Germans", "Italians").allied()).isTrue();
  }

  @Test
  void reportsTheOpeningStepAndRuleFlags() {
    final GameObservation observation = exporter.export(data, List.of("France"));

    assertThat(observation.gameName()).isEqualTo("World War II G40 Balanced");
    assertThat(observation.round()).isEqualTo(1);
    // The game opens on the one-time initialization step, which belongs to no player.
    assertThat(observation.step()).isEqualTo("gameInitDelegate");
    assertThat(observation.stepPlayer()).isNull();
    assertThat(observation.rules().lowLuck()).isFalse();
    assertThat(observation.rules().nationalObjectives()).isTrue();
    assertThat(observation.rules().ww2v3()).isTrue();
  }

  @Test
  void fullExportCoversEveryTerritoryAndSerializesToJson() {
    final GameObservation observation = exporter.export(data);

    assertThat(observation.territories()).hasSize(data.getMap().getTerritories().size());
    final String json = exporter.toJson(observation);
    assertThat(JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("territories"))
        .hasSize(observation.territories().size());
  }

  @Test
  void rejectsUnknownFocusTerritories() {
    assertThatThrownBy(() -> exporter.export(data, List.of("Paris")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Paris");
  }

  private static TerritorySummary territory(final GameObservation observation, final String name) {
    return observation.territories().stream()
        .filter(territory -> territory.name().equals(name))
        .findFirst()
        .orElseThrow();
  }

  private static PlayerSummary player(final GameObservation observation, final String name) {
    return observation.players().stream()
        .filter(player -> player.name().equals(name))
        .findFirst()
        .orElseThrow();
  }

  private static Relationship relationship(
      final GameObservation observation, final String player, final String other) {
    return observation.relationships().stream()
        .filter(
            relationship ->
                (relationship.player().equals(player) && relationship.other().equals(other))
                    || (relationship.player().equals(other) && relationship.other().equals(player)))
        .findFirst()
        .orElseThrow();
  }
}
