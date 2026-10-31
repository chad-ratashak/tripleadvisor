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
import javax.annotation.Nullable;
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
            final Reach reach = destinations(checker, map, territory, representative, movementLeft);
            groups.add(
                new UnitReach(
                    territory.getName(),
                    type,
                    units.size(),
                    movementLeft.toPlainString(),
                    reach.destinations(),
                    reach.destinations().isEmpty() ? reach.firstRefusal() : null));
          });
    }
    return new ReachabilityReport(
        checker.stepName(), player.getName(), checker.isNonCombatMove(), groups);
  }

  /** The legal destinations of one group, and the engine's first refusal when there was one. */
  private record Reach(List<Destination> destinations, @Nullable String firstRefusal) {}

  private static Reach destinations(
      final MoveJudge checker,
      final GameMap map,
      final Territory origin,
      final Unit unit,
      final BigDecimal movementLeft) {
    final GamePlayer player = checker.player();
    final RelationshipTracker relationships = checker.gameCopy().getRelationshipTracker();
    final Predicate<Territory> passable = passableFor(unit, player);
    final List<Destination> destinations = new ArrayList<>();
    String firstRefusal = null;
    final List<Territory> candidates =
        map.getNeighborsByMovementCost(origin, movementLeft, passable).stream()
            .sorted(Comparator.comparing(Territory::getName))
            .toList();
    for (final Territory candidate : candidates) {
      Route route = null;
      for (final Route candidateRoute :
          Routes.candidates(
              checker.gameCopy(), origin, candidate, passable, List.of(unit), player)) {
        final MoveVerdict verdict = checker.check(List.of(unit), candidateRoute);
        if (verdict.legal()) {
          route = candidateRoute;
          break;
        }
        if (firstRefusal == null) {
          firstRefusal = verdict.reason();
        }
      }
      if (route == null) {
        continue;
      }
      final GamePlayer owner = candidate.getOwner();
      final boolean neutral = owner == null || owner.isNull();
      destinations.add(
          new Destination(
              candidate.getName(),
              neutral ? null : owner.getName(),
              !neutral && relationships.isAtWar(player, owner),
              route.getAllTerritories().stream().map(Territory::getName).toList()));
    }
    return new Reach(destinations, firstRefusal);
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
