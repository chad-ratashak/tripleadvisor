package org.triplea.generalstaff.rules;

import java.util.List;
import javax.annotation.Nullable;

/**
 * Where each group of a player's units may legally move in one step, as the engine's validator
 * decides it. Units of one type standing in one territory move alike, so they form one group.
 */
public record ReachabilityReport(
    String step, String player, boolean nonCombatMove, List<UnitReach> groups) {

  /** One unit type in one origin territory, with every destination the engine allows. */
  public record UnitReach(
      String origin, String type, int count, String movement, List<Destination> destinations) {}

  /** A legal destination and the route the map's route finder chose for it. */
  public record Destination(
      String territory, @Nullable String owner, boolean enemyOwned, List<String> route) {}
}
