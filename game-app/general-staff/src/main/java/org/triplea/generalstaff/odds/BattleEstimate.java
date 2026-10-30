package org.triplea.generalstaff.odds;

import java.util.List;
import org.triplea.generalstaff.observe.GameObservation.UnitCount;

/**
 * The outcome distribution of one simulated battle. Rates carry their sample size and interval;
 * unit figures that say "left" or "lost" exclude infrastructure (factories, airfields), which does
 * not fight and is captured rather than destroyed.
 */
public record BattleEstimate(
    String label,
    String attacker,
    String defender,
    String territory,
    BattleOptions options,
    List<UnitCount> attackingUnits,
    List<UnitCount> defendingUnits,
    int runs,
    Proportion attackerWins,
    Proportion defenderWins,
    Proportion draws,
    double averageRounds,
    double averageAttackersLeft,
    double averageDefendersLeft,
    double averageTuvSwing,
    List<UnitMean> expectedAttackerLosses,
    List<UnitMean> expectedDefenderLosses,
    List<Outcome> defenderSurvivors) {

  /** Mean number of units of one owner and type lost per run. */
  public record UnitMean(String owner, String type, double mean) {}

  /** One distinct set of surviving units and how often the runs ended with exactly that set. */
  public record Outcome(List<UnitCount> units, int occurrences, Proportion share) {}
}
