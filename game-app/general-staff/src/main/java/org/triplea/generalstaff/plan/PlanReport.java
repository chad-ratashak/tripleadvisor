package org.triplea.generalstaff.plan;

import java.util.List;
import org.triplea.generalstaff.observe.GameObservation.UnitCount;
import org.triplea.generalstaff.odds.Proportion;

/**
 * Ranked candidate plans for Operation Paris, each evaluated by sampled trajectories on cloned
 * games with the real move and battle code. Every rate carries its sample size.
 */
public record PlanReport(
    String gameName,
    int trajectoriesPerPlan,
    long seed,
    double violationTolerance,
    String interveningPolicy,
    List<OperationalPlan> plans,
    List<NotEvaluated> notEvaluated,
    List<String> assumptions) {

  /** One candidate plan with its orders and measured outcomes. */
  public record OperationalPlan(
      String id,
      String family,
      String description,
      int rank,
      boolean feasible,
      int retreatAfterRound,
      List<StepOrders> orders,
      Metrics metrics,
      List<String> constraintViolations,
      List<String> orderErrors) {}

  /** The movement orders of one step, in the words the engine will accept. */
  public record StepOrders(String step, List<OrderSummary> orders) {}

  /** One order: which units along which route. */
  public record OrderSummary(List<UnitCount> units, List<String> route) {}

  /** Measured over the plan's trajectories; infrastructure is excluded from unit figures. */
  public record Metrics(
      int trajectories,
      Proportion italyOwnsFrance,
      Proportion germanyConquersFrance,
      Proportion franceEmptiedByGermany,
      Proportion allOrdersAccepted,
      double meanGermanTuvLost,
      double meanGermanAircraftLost,
      double meanItalianTuvLost,
      double meanFrenchTuvLost,
      double meanItalianPuGain,
      double meanGermanBattleRounds) {}

  /** A plan family the planner knows about but cannot yet evaluate, and why. */
  public record NotEvaluated(String family, String reason) {}
}
