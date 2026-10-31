package org.triplea.generalstaff.odds;

import java.util.List;
import org.triplea.generalstaff.observe.GameObservation.UnitCount;
import org.triplea.generalstaff.odds.BattleEstimate.UnitMean;

/**
 * How one attack scales with the force committed and the round the attacker retreats after. A row
 * with {@code retreatAfterRound == -1} fights to the end. When land units are present, "all
 * defenders destroyed" means the territory would be conquered.
 */
public record StrafeSweep(
    String attacker,
    String defender,
    String territory,
    List<UnitCount> defenders,
    int runs,
    List<Row> rows) {

  /** One force and one retreat rule. */
  public record Row(
      String force,
      List<UnitCount> units,
      boolean landPresent,
      int retreatAfterRound,
      Proportion allDefendersDestroyed,
      Proportion defenderHolds,
      double averageDefendersLeft,
      double averageAttackersLeft,
      double averageTuvSwing,
      List<UnitMean> expectedAttackerLosses) {}
}
