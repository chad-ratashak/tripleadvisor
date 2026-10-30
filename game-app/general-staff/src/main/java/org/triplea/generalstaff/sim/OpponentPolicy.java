package org.triplea.generalstaff.sim;

import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.Route;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.triplea.delegate.Matches;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.triplea.generalstaff.rules.MoveJudge;
import org.triplea.generalstaff.rules.ReachabilityIndex;
import org.triplea.generalstaff.rules.ReachabilityReport;
import org.triplea.generalstaff.sim.TurnSimulator.Order;

/**
 * What an intervening power does with a step of its turn in a simulation. Policies are scripted and
 * deliberately simple; every order they produce still has to pass the engine's validator and
 * delegate, so a policy can be wrong but never illegal.
 */
public interface OpponentPolicy {
  String name();

  /** The orders for {@code player} in the step the sandbox is positioned at (possibly none). */
  List<Order> orders(Sandbox sandbox, GamePlayer player, String stepName);

  /** The power does nothing. */
  static OpponentPolicy passive() {
    return new OpponentPolicy() {
      @Override
      public String name() {
        return "passive";
      }

      @Override
      public List<Order> orders(
          final Sandbox sandbox, final GamePlayer player, final String stepName) {
        return List.of();
      }
    };
  }

  /**
   * Every unit of the power that the engine allows to move into {@code territoryName} in this step
   * does so (the largest legal reinforcement, unit group by unit group).
   */
  static OpponentPolicy reinforce(final String territoryName) {
    return new OpponentPolicy() {
      @Override
      public String name() {
        return "reinforce " + territoryName;
      }

      @Override
      public List<Order> orders(
          final Sandbox sandbox, final GamePlayer player, final String stepName) {
        final MoveJudge judge = sandbox.judge();
        final ReachabilityReport reach = ReachabilityIndex.compute(judge);
        final Territory target = sandbox.territory(territoryName);
        final List<Order> orders = new ArrayList<>();
        for (final ReachabilityReport.UnitReach group : reach.groups()) {
          final Optional<ReachabilityReport.Destination> destination =
              group.destinations().stream()
                  .filter(candidate -> candidate.territory().equals(territoryName))
                  .findFirst();
          if (destination.isEmpty()) {
            continue;
          }
          final Territory origin = sandbox.territory(group.origin());
          final List<Unit> units =
              origin.getMatches(
                  Matches.unitIsOwnedBy(player)
                      .and(unit -> unit.getType().getName().equals(group.type()))
                      .and(Matches.unitHasMovementLeft()));
          if (units.isEmpty()) {
            continue;
          }
          final List<Territory> steps =
              destination.get().route().stream().skip(1).map(sandbox::territory).toList();
          orders.add(new Order(units, new Route(origin, steps)));
        }
        if (orders.isEmpty() && target.isWater()) {
          return List.of();
        }
        return orders;
      }
    };
  }
}
