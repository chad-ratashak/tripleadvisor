package org.triplea.generalstaff.plan;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GameMap;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.triplea.delegate.Matches;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Picks the units a power could throw at a target, by position and range only. This is a candidate
 * generator, not a legality check: the legality checker decides what may actually move.
 */
public final class ForceSelection {
  private ForceSelection() {}

  /** Land units of {@code power} standing next to {@code target} that may move during combat. */
  public static List<Unit> landStrikeForce(
      final GameData data, final GamePlayer power, final Territory target) {
    final Predicate<Unit> eligible =
        Matches.unitIsOwnedBy(power)
            .and(Matches.unitIsLand())
            .and(Matches.unitIsNotInfrastructure())
            .and(Matches.unitCanNotMoveDuringCombatMove().negate());
    return data.getMap().getNeighbors(target).stream()
        .filter(neighbor -> !neighbor.isWater() && power.equals(neighbor.getOwner()))
        .flatMap(neighbor -> neighbor.getMatches(eligible).stream())
        .toList();
  }

  /** Aircraft of {@code power} that can reach {@code target} and still fly back. */
  public static List<Unit> airStrikeForce(
      final GameData data, final GamePlayer power, final Territory target) {
    final GameMap map = data.getMap();
    final Predicate<Unit> eligible = Matches.unitIsOwnedBy(power).and(Matches.unitIsAir());
    final List<Unit> aircraft = new ArrayList<>();
    for (final Territory origin : map.getTerritories()) {
      final int distance = map.getDistance(origin, target);
      if (distance < 0) {
        continue;
      }
      origin.getMatches(eligible).stream()
          .filter(unit -> unit.getUnitAttachment().getMovement(power) >= 2 * distance)
          .forEach(aircraft::add);
    }
    return aircraft;
  }

  /** Land and air strike forces together. */
  public static List<Unit> strikeForce(
      final GameData data, final GamePlayer power, final Territory target) {
    return Stream.concat(
            landStrikeForce(data, power, target).stream(),
            airStrikeForce(data, power, target).stream())
        .toList();
  }
}
