package org.triplea.generalstaff.plan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.triplea.generalstaff.plan.PlanReport.OperationalPlan;

/**
 * Lexicographic ranking: plans that keep the hard constraint (Germany does not conquer France)
 * within tolerance and whose orders the engine accepted come first; among them the objective rate
 * (Italy owns France) decides, then the German losses. Violating plans follow, least violating
 * first, so the commander sees why they were rejected.
 */
public final class PlanEvaluator {
  private PlanEvaluator() {}

  public static List<OperationalPlan> rank(
      final List<OperationalPlan> plans, final double violationTolerance) {
    final List<OperationalPlan> judged = new ArrayList<>();
    for (final OperationalPlan plan : plans) {
      final List<String> violations = new ArrayList<>(plan.constraintViolations());
      final double conquest = plan.metrics().germanyConquersFrance().rate();
      if (conquest > violationTolerance) {
        violations.add(
            String.format(
                Locale.ROOT,
                ReportText.VIOLATION_GERMAN_CONQUEST,
                plan.metrics().germanyConquersFrance().format(),
                violationTolerance * 100));
      }
      if (plan.metrics().allOrdersAccepted().rate() < 1.0) {
        violations.add(
            String.format(
                Locale.ROOT,
                ReportText.VIOLATION_ORDERS_REFUSED,
                plan.metrics().allOrdersAccepted().format()));
      }
      judged.add(
          new OperationalPlan(
              plan.id(),
              plan.family(),
              plan.description(),
              0,
              violations.isEmpty(),
              plan.retreatAfterRound(),
              plan.orders(),
              plan.metrics(),
              violations,
              plan.orderErrors()));
    }
    judged.sort(
        Comparator.comparing((OperationalPlan plan) -> !plan.feasible())
            .thenComparing(plan -> plan.feasible() ? -plan.metrics().italyOwnsFrance().rate() : 0.0)
            .thenComparing(
                plan ->
                    plan.feasible()
                        ? plan.metrics().meanGermanTuvLost()
                        : plan.metrics().germanyConquersFrance().rate())
            .thenComparing(OperationalPlan::id));
    final List<OperationalPlan> ranked = new ArrayList<>();
    for (int i = 0; i < judged.size(); i++) {
      final OperationalPlan plan = judged.get(i);
      ranked.add(
          new OperationalPlan(
              plan.id(),
              plan.family(),
              plan.description(),
              i + 1,
              plan.feasible(),
              plan.retreatAfterRound(),
              plan.orders(),
              plan.metrics(),
              plan.constraintViolations(),
              plan.orderErrors()));
    }
    return ranked;
  }
}
