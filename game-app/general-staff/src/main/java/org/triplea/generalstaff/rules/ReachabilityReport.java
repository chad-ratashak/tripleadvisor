package org.triplea.generalstaff.rules;

import java.util.List;
import javax.annotation.Nullable;

/**
 * Where each group of a player's units may legally move in one step, as the engine's validator
 * decides it. Units of one type standing in one territory move alike, so they form one group.
 */
public record ReachabilityReport(
    String step, String player, boolean nonCombatMove, List<UnitReach> groups) {

  /**
   * One unit type in one origin territory, with every destination the engine allows. When there is
   * none, {@code reason} is the engine's refusal of the first candidate tried (candidates within
   * the unit's movement, in name order), or null when no territory was within its movement at all.
   */
  public record UnitReach(
      String origin,
      String type,
      int count,
      String movement,
      List<Destination> destinations,
      @Nullable String reason) {}

  /** A legal destination and the route the map's route finder chose for it. */
  public record Destination(
      String territory, @Nullable String owner, boolean enemyOwned, List<String> route) {}
}
