package org.triplea.generalstaff.plan;

import java.util.List;
import org.triplea.generalstaff.observe.GameObservation.UnitCount;
import org.triplea.generalstaff.odds.BattleEstimate;
import org.triplea.generalstaff.odds.Proportion;

/**
 * The Operation Paris proof-of-concept report: the forces that were compared and the engine's
 * estimate for each candidate way of taking France. Every rate carries its sample size.
 */
public record OperationParisReport(
    String gameName,
    int runsPerScenario,
    long seed,
    List<UnitCount> frenchGarrison,
    List<UnitCount> germanStrikeForce,
    List<UnitCount> italianStrikeForce,
    BattleEstimate germanFullAttack,
    BattleEstimate germanAirOnlyStrike,
    FollowUpEstimate italianFollowUp,
    List<BattleEstimate> germanStrafes,
    BattleEstimate italyAlone,
    List<String> assumptions) {

  /**
   * The chance that Italy owns France at the end of its turn after a German air-only strike, built
   * by sampling the French survivors of each German run and letting Italy attack them.
   */
  public record FollowUpEstimate(
      String label,
      int n,
      Proportion italyOwnsFrance,
      Proportion walkIn,
      Proportion unresolved,
      List<Conditional> conditionals) {}

  /** Italy's odds against one distinct set of French survivors, and how often that set occurred. */
  public record Conditional(
      List<UnitCount> survivors, int occurrences, int poolRuns, Proportion italianWins) {}
}
