package org.triplea.generalstaff.odds;

import static org.assertj.core.api.Assertions.assertThat;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.engine.data.UnitType;
import games.strategy.triplea.delegate.Matches;
import games.strategy.triplea.util.TuvCostsCalculator;
import games.strategy.triplea.xml.TestMapGameData;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.triplea.generalstaff.odds.ForceSubsets.Force;
import org.triplea.generalstaff.plan.ForceSelection;
import org.triplea.java.collections.IntegerMap;

/**
 * Land units are committed cheapest first. Among units of equal cost and type the caller's
 * tie-break decides, so a planner that names the origin territory commits the same units on every
 * load of the file even though the engine draws new unit ids each time.
 */
class ForceSubsetsTest {
  @Test
  void equalUnitsAreOrderedByTheCallersTieBreakBeforeTheirRandomIds() {
    final GameData data = TestMapGameData.GLOBAL1940.getGameData();
    final GamePlayer germans = data.getPlayerList().getPlayerId("Germans");
    final Territory france = data.getMap().getTerritoryOrNull("France");
    final List<Unit> force = ForceSelection.landStrikeForce(data, germans, france);
    final IntegerMap<UnitType> costs = new TuvCostsCalculator().getCostsForTuv(germans);
    final Map<UUID, String> origins = new HashMap<>();
    for (final Territory territory : data.getMap().getTerritories()) {
      territory.getUnits().forEach(unit -> origins.put(unit.getId(), territory.getName()));
    }
    final Function<Unit, String> byOrigin = unit -> origins.get(unit.getId());
    // The same key, reversed: a name sorting after every real territory name goes first.
    final Function<Unit, String> byOriginReversed =
        unit -> origins.get(unit.getId()).equals("Holland Belgium") ? "zz" : "aa";

    final List<String> forward = infantryOrigins(force, costs, byOrigin, origins);
    final List<String> backward = infantryOrigins(force, costs, byOriginReversed, origins);

    assertThat(forward).contains("Holland Belgium", "Western Germany");
    assertThat(forward).isSorted();
    assertThat(backward).isSortedAccordingTo(Comparator.reverseOrder());
    assertThat(backward).isEqualTo(forward.reversed());
  }

  @Test
  void landUnitsAreCommittedCheapestFirstAndTheFirstSubsetHoldsOneOfThem() {
    final GameData data = TestMapGameData.GLOBAL1940.getGameData();
    final GamePlayer germans = data.getPlayerList().getPlayerId("Germans");
    final Territory france = data.getMap().getTerritoryOrNull("France");
    final List<Unit> force = ForceSelection.landStrikeForce(data, germans, france);
    final IntegerMap<UnitType> costs = new TuvCostsCalculator().getCostsForTuv(germans);

    final List<Force> forces = ForceSubsets.byLandCount(force, 1, costs);
    final List<Unit> land = forces.get(forces.size() - 1).units();

    assertThat(land).hasSameSizeAs(force);
    assertThat(forces.get(0).units()).hasSize(1);
    for (int i = 1; i < land.size(); i++) {
      assertThat(costs.getInt(land.get(i).getType()))
          .isGreaterThanOrEqualTo(costs.getInt(land.get(i - 1).getType()));
    }
  }

  private static List<String> infantryOrigins(
      final List<Unit> force,
      final IntegerMap<UnitType> costs,
      final Function<Unit, String> tieBreak,
      final Map<UUID, String> origins) {
    final List<Force> forces = ForceSubsets.byLandCount(force, 1, costs, tieBreak);
    return forces.get(forces.size() - 1).units().stream()
        .filter(Matches.unitIsAir().negate())
        .filter(unit -> unit.getType().getName().equals("infantry"))
        .map(unit -> origins.get(unit.getId()))
        .toList();
  }
}
