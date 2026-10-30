package org.triplea.generalstaff.rules;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GameMap;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.RelationshipTracker;
import games.strategy.engine.data.Route;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.triplea.delegate.Matches;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;
import org.triplea.generalstaff.rules.ReachabilityReport.Destination;
import org.triplea.generalstaff.rules.ReachabilityReport.UnitReach;

/**
 * Generate-and-test enumeration of legal destinations: candidates come from the map's neighbour
 * search within the unit's remaining movement, routes from the map's route finder, and the verdict
 * from a {@link MoveJudge} (a {@link LegalityChecker} or a sandbox). Nothing here guesses a rule.
 */
public final class ReachabilityIndex {
  private ReachabilityIndex() {}

  /** Every legal destination for every movable unit group of the checker's player and step. */
  public static ReachabilityReport compute(final MoveJudge checker) {
    return compute(checker, Optional.empty());
  }

  /** As {@link #compute(LegalityChecker)}, limited to units standing in {@code origin}. */
  public static ReachabilityReport compute(final MoveJudge checker, final Optional<String> origin) {
    final GameData data = checker.gameCopy();
    final GamePlayer player = checker.player();
    final GameMap map = data.getMap();
    final List<UnitReach> groups = new ArrayList<>();
    for (final Territory territory : map.getTerritories()) {
      if (origin.isPresent() && !origin.get().equals(territory.getName())) {
        continue;
      }
      final Map<String, List<Unit>> byType = new TreeMap<>();
      for (final Unit unit :
          territory.getMatches(Matches.unitIsOwnedBy(player).and(Matches.unitCanMove()))) {
        byType.computeIfAbsent(unit.getType().getName(), type -> new ArrayList<>()).add(unit);
      }
      byType.forEach(
          (type, units) -> {
            final Unit representative = units.get(0);
            final BigDecimal movementLeft = representative.getMovementLeft();
            if (movementLeft.compareTo(BigDecimal.ZERO) <= 0) {
              return;
            }
            groups.add(
                new UnitReach(
                    territory.getName(),
                    type,
                    units.size(),
                    movementLeft.toPlainString(),
                    destinations(checker, map, territory, representative, movementLeft)));
          });
    }
    return new ReachabilityReport(
        checker.stepName(), player.getName(), checker.isNonCombatMove(), groups);
  }

  private static List<Destination> destinations(
      final MoveJudge checker,
      final GameMap map,
      final Territory origin,
      final Unit unit,
      final BigDecimal movementLeft) {
    final GamePlayer player = checker.player();
    final RelationshipTracker relationships = checker.gameCopy().getRelationshipTracker();
    final Predicate<Territory> passable = passableFor(unit, player);
    final List<Destination> destinations = new ArrayList<>();
    final List<Territory> candidates =
        map.getNeighborsByMovementCost(origin, movementLeft, passable).stream()
            .sorted(Comparator.comparing(Territory::getName))
            .toList();
    for (final Territory candidate : candidates) {
      final Optional<Route> route =
          map.getRouteForUnits(origin, candidate, passable, List.of(unit), player);
      if (route.isEmpty() || !checker.check(List.of(unit), route.get()).legal()) {
        continue;
      }
      final GamePlayer owner = candidate.getOwner();
      final boolean neutral = owner == null || owner.isNull();
      destinations.add(
          new Destination(
              candidate.getName(),
              neutral ? null : owner.getName(),
              !neutral && relationships.isAtWar(player, owner),
              route.get().getAllTerritories().stream().map(Territory::getName).toList()));
    }
    return destinations;
  }

  private static Predicate<Territory> passableFor(final Unit unit, final GamePlayer player) {
    if (Matches.unitIsAir().test(unit)) {
      return Matches.territoryIsNotImpassable();
    }
    if (Matches.unitIsSea().test(unit)) {
      return Matches.territoryIsWater();
    }
    return Matches.territoryIsNotImpassableToLandUnits(player);
  }
}
