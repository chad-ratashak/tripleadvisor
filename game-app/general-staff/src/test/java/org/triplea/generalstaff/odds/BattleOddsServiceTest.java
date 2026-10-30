package org.triplea.generalstaff.odds;

import static games.strategy.triplea.delegate.GameDataTestUtil.germans;
import static games.strategy.triplea.delegate.GameDataTestUtil.infantry;
import static games.strategy.triplea.delegate.GameDataTestUtil.russians;
import static games.strategy.triplea.delegate.GameDataTestUtil.territory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.triplea.xml.TestMapGameData;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.triplea.generalstaff.observe.GameObservation.UnitCount;

class BattleOddsServiceTest {
  private static final int RUNS = 300;

  @Test
  void estimateReportsEveryRunExactlyOnce() {
    final GameData data = TestMapGameData.REVISED.getGameData();
    final Territory germany = territory("Germany", data);
    final List<Unit> attackers = infantry(data).createTemp(3, russians(data));
    final BattleScenario scenario =
        new BattleScenario(
            "three-infantry-into-germany",
            russians(data),
            germans(data),
            germany,
            attackers,
            germany.getUnits(),
            BattleOptions.fightToTheEnd());

    final BattleEstimate estimate;
    try (BattleOddsService odds = new BattleOddsService(data)) {
      estimate = odds.estimate(scenario, RUNS);
    }

    assertThat(estimate.runs()).isEqualTo(RUNS);
    assertThat(
            estimate.attackerWins().successes()
                + estimate.defenderWins().successes()
                + estimate.draws().successes())
        .isEqualTo(RUNS);
    assertThat(estimate.attackingUnits()).containsExactly(new UnitCount("Russians", "infantry", 3));
    assertThat(estimate.expectedAttackerLosses()).hasSize(1);
    assertThat(estimate.expectedAttackerLosses().get(0).mean()).isBetween(0.0, 3.0);
    assertThat(
            estimate.defenderSurvivors().stream().mapToInt(outcome -> outcome.occurrences()).sum())
        .isEqualTo(RUNS);
    assertThat(estimate.averageDefendersLeft()).isGreaterThanOrEqualTo(0.0);
    assertThat(data.getUnits().getUnits()).noneMatch(attackers::contains);
  }

  @Test
  void rejectsNonPositiveRunCounts() {
    final GameData data = TestMapGameData.REVISED.getGameData();
    final Territory germany = territory("Germany", data);
    final BattleScenario scenario =
        new BattleScenario(
            "nothing",
            russians(data),
            germans(data),
            germany,
            infantry(data).createTemp(1, russians(data)),
            germany.getUnits(),
            BattleOptions.fightToTheEnd());

    try (BattleOddsService odds = new BattleOddsService(data)) {
      assertThatThrownBy(() -> odds.estimate(scenario, 0))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}
