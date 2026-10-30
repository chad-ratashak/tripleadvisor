package org.triplea.generalstaff.odds;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.triplea.util.TuvCostsCalculator;
import games.strategy.triplea.xml.TestMapGameData;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.triplea.generalstaff.odds.ForceSubsets.Force;
import org.triplea.generalstaff.odds.StrafeSweep.Row;
import org.triplea.generalstaff.plan.ForceSelection;

class StrafeEvaluatorTest {
  private static final int RUNS = 200;

  @Test
  void sweepsEveryForceAgainstEveryRetreatRule() {
    final GameData data = TestMapGameData.GLOBAL1940.getGameData();
    final GamePlayer germans = data.getPlayerList().getPlayerId("Germans");
    final GamePlayer french = data.getPlayerList().getPlayerId("French");
    final Territory france = data.getMap().getTerritoryOrNull("France");
    final List<Unit> strikeForce = ForceSelection.strikeForce(data, germans, france);
    final List<Force> forces =
        ForceSubsets.byLandCount(strikeForce, 7, new TuvCostsCalculator().getCostsForTuv(germans));

    assertThat(forces)
        .extracting(Force::label)
        .containsExactly("air only", "air + 7 land", "air + 14 land", "all units");
    assertThat(forces.get(0).units()).hasSize(9);
    assertThat(forces.get(3).units()).hasSize(strikeForce.size());

    final StrafeSweep sweep;
    try (BattleOddsService odds = new BattleOddsService(data)) {
      sweep =
          new StrafeEvaluator(odds)
              .sweep(germans, french, france, forces, List.copyOf(france.getUnits()), 2, RUNS);
    }

    assertThat(sweep.rows()).hasSize(forces.size() * 3);
    assertThat(sweep.rows()).allMatch(row -> row.allDefendersDestroyed().n() == RUNS);
    assertThat(sweep.rows())
        .filteredOn(row -> row.force().equals("air only"))
        .allMatch(row -> !row.landPresent());
    assertThat(sweep.rows())
        .filteredOn(row -> row.force().equals("all units"))
        .allMatch(Row::landPresent);
    assertThat(sweep.rows()).extracting(Row::retreatAfterRound).contains(1, 2, BattleOptions.NEVER);
    final String text = StrafeSweepFormatter.toText(sweep);
    assertThat(text).contains("Germans").contains("France").contains("to the end");
    System.out.println(text);
  }
}
