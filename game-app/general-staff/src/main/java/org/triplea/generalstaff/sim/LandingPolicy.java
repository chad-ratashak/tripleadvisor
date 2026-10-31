package org.triplea.generalstaff.sim;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GameMap;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.Route;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.triplea.delegate.Matches;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import org.triplea.generalstaff.rules.MoveJudge;
import org.triplea.generalstaff.rules.Routes;
import org.triplea.generalstaff.sim.TurnSimulator.Order;

/**
 * Brings aircraft home during a non-combat move. The engine removes any aircraft that cannot land
 * when the non-combat move ends (a territory taken this turn does not count as a landing place, nor
 * does a sea zone without a carrier), so a plan that forgets this loses every surviving plane. The
 * engine itself says which territories hold aircraft that cannot stay; for each plane there the
 * first destination the engine accepts is ordered: own land first, then allied land, then anything
 * else the validator allows.
 */
public final class LandingPolicy {
  private LandingPolicy() {}

  /** Landing orders for {@code player}'s aircraft that cannot stay where they are. */
  public static List<Order> orders(
      final GameData data, final GamePlayer player, final MoveJudge judge) {
    return orders(
        data, player, data.getMoveDelegate().getTerritoriesWhereAirCantLand(player), judge);
  }

  /** Landing orders for the aircraft of {@code player} in the given stranded territories. */
  public static List<Order> orders(
      final GameData data,
      final GamePlayer player,
      final Collection<Territory> stranded,
      final MoveJudge judge) {
    final GameMap map = data.getMap();
    final List<Order> orders = new ArrayList<>();
    for (final Territory origin : stranded) {
      final List<Unit> aircraft =
          origin.getMatches(
              Matches.unitIsOwnedBy(player)
                  .and(Matches.unitIsAir())
                  .and(Matches.unitHasMovementLeft()));
      for (final Unit plane : aircraft) {
        final List<Territory> candidates =
            new ArrayList<>(
                map.getNeighborsByMovementCost(
                    origin, plane.getMovementLeft(), Matches.territoryIsNotImpassable()));
        candidates.sort(
            Comparator.comparingInt((Territory territory) -> preference(data, player, territory))
                .thenComparing(Territory::getName));
        for (final Territory candidate : candidates) {
          final Route route = legalRoute(data, judge, plane, origin, candidate, player);
          if (route != null) {
            orders.add(new Order(List.of(plane), route));
            break;
          }
        }
      }
    }
    return orders;
  }

  @Nullable
  private static Route legalRoute(
      final GameData data,
      final MoveJudge judge,
      final Unit plane,
      final Territory origin,
      final Territory candidate,
      final GamePlayer player) {
    final Collection<Unit> units = List.of(plane);
    for (final Route route :
        Routes.candidates(
            data, origin, candidate, Matches.territoryIsNotImpassable(), units, player)) {
      if (judge.check(units, route).legal()) {
        return route;
      }
    }
    return null;
  }

  /** Own land first, then allied land, then anything else the engine may still allow. */
  private static int preference(
      final GameData data, final GamePlayer player, final Territory territory) {
    if (territory.isWater() || territory.getOwner() == null || territory.getOwner().isNull()) {
      return 2;
    }
    if (player.equals(territory.getOwner())) {
      return 0;
    }
    return data.getRelationshipTracker().isAllied(player, territory.getOwner()) ? 1 : 3;
  }
}
