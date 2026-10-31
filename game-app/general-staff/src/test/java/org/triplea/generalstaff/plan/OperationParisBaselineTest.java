package org.triplea.generalstaff.plan;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import games.strategy.engine.data.GameData;
import games.strategy.engine.framework.GameDataManager;
import games.strategy.engine.framework.GameDataUtils;
import games.strategy.triplea.xml.TestMapGameData;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.triplea.generalstaff.observe.ObservationExporter;
import org.triplea.generalstaff.observe.UnitCounts;
import org.triplea.generalstaff.odds.BattleEstimate;

/**
 * The brief's minimal proof of concept, end to end on the checked-in fixture. It asserts the shape
 * of the report, the sample sizes, and that the live game data is untouched. It does not assert
 * particular odds: those belong to the engine. It is the slow test of the module (about ten
 * seconds): {@code -Dgeneralstaff.runs=<n>} changes the sample size.
 */
class OperationParisBaselineTest {
  private static final int RUNS = Integer.getInteger("generalstaff.runs", 1000);
  private static final Set<String> AIRCRAFT = Set.of("fighter", "tactical_bomber", "bomber");

  @Test
  void runsTheBaselineWithoutTouchingTheLiveGame() {
    final GameData data = TestMapGameData.GLOBAL1940.getGameData();
    final ObservationExporter exporter = new ObservationExporter();
    final String boardBefore = exporter.toJson(exporter.export(data));
    final int unitsBefore = data.getUnits().getUnits().size();
    // The engine fills lazy caches that are part of its serialized form (unit attachments,
    // production rules) on first use, so bytes are only comparable between a first and a later
    // run. The structural checks below still compare against the freshly parsed game.
    OperationParisBaseline.forGlobal1940(data, 7L).run(1);
    final byte[] bytesAfterWarmUp = serialize(data);

    final long started = System.nanoTime();
    final OperationParisReport report = OperationParisBaseline.forGlobal1940(data, 7L).run(RUNS);
    final long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

    final String text = ReportFormatter.toText(report);
    System.out.println(text);
    System.out.println(
        "Baseline wall time: " + elapsedMillis + " ms for " + RUNS + " runs per scenario");

    assertThat(report.gameName()).isEqualTo("World War II G40 Balanced");
    assertThat(report.runsPerScenario()).isEqualTo(RUNS);
    assertThat(UnitCounts.total(report.frenchGarrison())).isEqualTo(14);
    assertThat(report.germanAirOnlyStrike().attackingUnits())
        .allMatch(stack -> AIRCRAFT.contains(stack.type()))
        .isNotEmpty();
    assertThat(report.germanStrikeForce())
        .anyMatch(stack -> stack.type().equals("infantry") && stack.owner().equals("Germans"));
    assertThat(report.italianStrikeForce())
        .allMatch(stack -> stack.owner().equals("Italians"))
        .anyMatch(stack -> stack.type().equals("infantry"));

    for (final BattleEstimate estimate :
        List.of(report.germanFullAttack(), report.germanAirOnlyStrike(), report.italyAlone())) {
      assertThat(estimate.runs()).isEqualTo(RUNS);
      assertThat(estimate.attackerWins().n()).isEqualTo(RUNS);
      assertThat(estimate.defenderSurvivors().stream().mapToInt(o -> o.occurrences()).sum())
          .isEqualTo(RUNS);
    }
    assertThat(report.germanStrafes()).hasSize(3);
    for (int round = 1; round <= 3; round++) {
      final BattleEstimate strafe = report.germanStrafes().get(round - 1);
      assertThat(strafe.options().retreatAfterRound()).isEqualTo(round);
      assertThat(strafe.runs()).isEqualTo(RUNS);
    }
    assertThat(report.italianFollowUp().n()).isEqualTo(RUNS);
    assertThat(report.italianFollowUp().italyOwnsFrance().n()).isEqualTo(RUNS);
    assertThat(report.italianFollowUp().conditionals())
        .allMatch(conditional -> conditional.poolRuns() >= 200);
    assertThat(report.assumptions()).isNotEmpty();

    assertThat(text).contains(ReportText.TITLE).contains(ReportText.FOLLOW_UP_TITLE);
    final String json = new Gson().toJson(report);
    assertThat(JsonParser.parseString(json).getAsJsonObject().has("germanStrafes")).isTrue();

    assertThat(exporter.toJson(exporter.export(data))).isEqualTo(boardBefore);
    assertThat(data.getUnits().getUnits()).hasSize(unitsBefore);
    assertThat(Arrays.mismatch(serialize(data), bytesAfterWarmUp))
        .as("first differing byte of the serialized game data after the run (-1 = identical)")
        .isEqualTo(-1);
  }

  private static byte[] serialize(final GameData data) {
    return GameDataUtils.gameDataToBytes(data, GameDataManager.Options.forBattleCalculator())
        .orElseThrow();
  }
}
