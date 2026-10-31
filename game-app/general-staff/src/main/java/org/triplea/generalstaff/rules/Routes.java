package org.triplea.generalstaff.rules;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GameMap;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.Route;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Candidate routes for a move, friendliest first. The map's route finder returns one shortest route
 * and may pick one through enemy ground when an equally short friendly route exists; the validator
 * then refuses it (land units cannot blitz through enemies) although the move is possible. So the
 * first candidate keeps the middle steps on territory the mover owns or is allied with, and the
 * second lets the finder choose freely. Legality is still the validator's call.
 */
public final class Routes {
  private Routes() {}

  public static List<Route> candidates(
      final GameData data,
      final Territory from,
      final Territory to,
      final Predicate<Territory> passable,
      final Collection<Unit> units,
      final GamePlayer player) {
    final GameMap map = data.getMap();
    final Predicate<Territory> friendly =
        passable.and(
            territory ->
                territory.isWater()
                    || (territory.getOwner() != null
                        && !territory.getOwner().isNull()
                        && (player.equals(territory.getOwner())
                            || data.getRelationshipTracker()
                                .isAllied(player, territory.getOwner()))));
    final List<Route> routes = new ArrayList<>();
    final Optional<Route> friendlyRoute = map.getRouteForUnits(from, to, friendly, units, player);
    friendlyRoute.ifPresent(routes::add);
    final Optional<Route> anyRoute = map.getRouteForUnits(from, to, passable, units, player);
    if (anyRoute.isPresent()
        && friendlyRoute.map(route -> !route.equals(anyRoute.get())).orElse(true)) {
      routes.add(anyRoute.get());
    }
    return routes;
  }
}
