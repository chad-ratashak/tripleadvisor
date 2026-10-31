package org.triplea.generalstaff.plan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.triplea.generalstaff.odds.Proportion;
import org.triplea.generalstaff.plan.PlanReport.OperationalPlan;

/**
 * Lexicographic ranking: plans that keep the hard constraint (the attacker does not conquer the
 * target) within tolerance and whose orders the engine accepted come first; among them the
 * objective rate (the ally owns the target) decides, then the attacker's losses. Violating plans
 * follow, least violating first, so the commander sees why they were rejected. Feasibility is
 * judged on the observed rate; whether the evidence also settles the verdict is reported
 * separately: a kept plan is settled when the 95% upper bound of the constraint rate is within
 * tolerance, a plan rejected for conquests when the lower bound is above it, and a plan rejected
 * because the engine refused its orders is settled outright. A verdict from a handful of
 * trajectories is therefore visibly provisional either way.
 */
public final class PlanEvaluator {
  private static final double EPSILON = 1e-9;

  private PlanEvaluator() {}

  public static List<OperationalPlan> rank(
      final List<OperationalPlan> plans, final double violationTolerance) {
    return rank(plans, violationTolerance, "Germany", "France");
  }

  /** As above, naming the attacking power and the target in the violation text. */
  public static List<OperationalPlan> rank(
      final List<OperationalPlan> plans,
      final double violationTolerance,
      final String attacker,
      final String target) {
    final List<OperationalPlan> judged = new ArrayList<>();
    for (final OperationalPlan plan : plans) {
      final List<String> violations = new ArrayList<>(plan.constraintViolations());
      final List<String> warnings = new ArrayList<>(plan.warnings());
      final Proportion conquest = plan.metrics().germanyConquersFrance();
      final boolean conquestViolation = conquest.rate() > violationTolerance;
      final boolean ordersRefused = plan.metrics().allOrdersAccepted().rate() < 1.0;
      if (conquestViolation) {
        violations.add(
            String.format(
                Locale.ROOT,
                ReportText.VIOLATION_CONQUEST,
                attacker,
                target,
                conquest.format(),
                violationTolerance * 100));
      }
      if (ordersRefused) {
        violations.add(
            String.format(
                Locale.ROOT,
                ReportText.VIOLATION_ORDERS_REFUSED,
                plan.metrics().allOrdersAccepted().format()));
      }
      final boolean evidenceSufficient =
          ordersRefused
              || (conquestViolation
                  ? conquest.ciLow() > violationTolerance + EPSILON
                  : conquest.ciHigh() <= violationTolerance + EPSILON);
      if (!evidenceSufficient && violations.isEmpty()) {
        warnings.add(
            violationTolerance <= EPSILON
                ? String.format(
                    Locale.ROOT,
                    ReportText.WARNING_THIN_EVIDENCE_ZERO,
                    conquest.successes(),
                    conquest.n(),
                    conquest.ciHigh() * 100)
                : String.format(
                    Locale.ROOT,
                    ReportText.WARNING_THIN_EVIDENCE,
                    conquest.successes(),
                    conquest.n(),
                    conquest.ciHigh() * 100,
                    violationTolerance * 100));
      }
      if (!evidenceSufficient && conquestViolation) {
        warnings.add(
            String.format(
                Locale.ROOT,
                ReportText.WARNING_THIN_REJECTION,
                conquest.successes(),
                conquest.n(),
                conquest.ciLow() * 100,
                violationTolerance * 100));
      }
      judged.add(
          new OperationalPlan(
              plan.id(),
              plan.family(),
              plan.description(),
              0,
              violations.isEmpty(),
              evidenceSufficient,
              plan.retreatAfterRound(),
              plan.orders(),
              plan.metrics(),
              violations,
              warnings,
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
              plan.evidenceSufficient(),
              plan.retreatAfterRound(),
              plan.orders(),
              plan.metrics(),
              plan.constraintViolations(),
              plan.warnings(),
              plan.orderErrors()));
    }
    return ranked;
  }

  /**
   * Runs of feasible ranks the sample does not separate on the objective rate (the ally owns the
   * target): each run starts at a plan and takes every following feasible plan whose 95% interval
   * overlaps that first plan's, so a run says "these could all be the best of the run". Runs of one
   * are left out.
   */
  public static List<List<Integer>> unseparatedRanks(final List<OperationalPlan> ranked) {
    final List<List<Integer>> groups = new ArrayList<>();
    OperationalPlan head = null;
    List<Integer> run = new ArrayList<>();
    for (final OperationalPlan plan : ranked) {
      if (!plan.feasible()) {
        break;
      }
      if (head != null
          && overlap(head.metrics().italyOwnsFrance(), plan.metrics().italyOwnsFrance())) {
        run.add(plan.rank());
        continue;
      }
      if (run.size() > 1) {
        groups.add(List.copyOf(run));
      }
      head = plan;
      run = new ArrayList<>(List.of(plan.rank()));
    }
    if (run.size() > 1) {
      groups.add(List.copyOf(run));
    }
    return groups;
  }

  private static boolean overlap(final Proportion a, final Proportion b) {
    return a.ciLow() <= b.ciHigh() + EPSILON && b.ciLow() <= a.ciHigh() + EPSILON;
  }
}
