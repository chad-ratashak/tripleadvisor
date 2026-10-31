package org.triplea.generalstaff.plan;

import java.util.List;
import java.util.Locale;
import org.triplea.generalstaff.observe.UnitCounts;
import org.triplea.generalstaff.plan.PlanReport.Metrics;
import org.triplea.generalstaff.plan.PlanReport.NotEvaluated;
import org.triplea.generalstaff.plan.PlanReport.OperationalPlan;
import org.triplea.generalstaff.plan.PlanReport.OrderSummary;
import org.triplea.generalstaff.plan.PlanReport.StepOrders;

/**
 * LAYOUT ONLY. Renders a {@link PlanReport} as text. If you are here to change wording, you are in
 * the wrong file: see {@link ReportText}.
 */
public final class PlanFormatter {
  private static final String HEADER = "%4s  %-28s %-28s %-28s %-9s %13s %19s %13s %12s  %s%n";
  private static final String ROW = "%4d  %-28s %-28s %-28s %-9s %13.1f %19.2f %13.1f %12.1f  %s%n";

  private PlanFormatter() {}

  public static String toText(final PlanReport report) {
    final StringBuilder out = new StringBuilder();
    out.append(
            String.format(
                Locale.ROOT,
                ReportText.PLAN_TITLE,
                report.attacker(),
                report.ally(),
                report.target()))
        .append('\n');
    out.append(ReportText.GAME).append(": ").append(report.gameName()).append('\n');
    out.append(ReportText.PLAN_TRAJECTORIES)
        .append(": ")
        .append(report.trajectoriesPerPlan())
        .append('\n');
    out.append(ReportText.SEED).append(": ").append(report.seed()).append('\n');
    out.append(ReportText.PLAN_TOLERANCE)
        .append(": ")
        .append(String.format(Locale.ROOT, "%.1f%%", report.violationTolerance() * 100))
        .append('\n');
    out.append(ReportText.PLAN_INTERVENING)
        .append(": ")
        .append(report.interveningPolicy())
        .append('\n');
    if (!report.interveningOrders().isEmpty()) {
      out.append(ReportText.PLAN_INTERVENING_ORDERS).append(':').append('\n');
      for (final OrderSummary order : report.interveningOrders()) {
        out.append("    ")
            .append(UnitCounts.describe(order.units()))
            .append(" -> ")
            .append(String.join(" > ", order.route()))
            .append('\n');
      }
    }
    out.append('\n');
    if (report.plans().isEmpty()) {
      out.append(ReportText.PLAN_NOTHING_RANKED).append('\n').append('\n');
    } else {
      table(out, report);
    }
    if (!report.notEvaluated().isEmpty()) {
      out.append(ReportText.PLAN_NOT_EVALUATED).append('\n');
      for (final NotEvaluated entry : report.notEvaluated()) {
        out.append("  - ").append(entry.family()).append(": ").append(entry.reason()).append('\n');
      }
      out.append('\n');
    }
    out.append(ReportText.ASSUMPTIONS_TITLE).append('\n');
    for (final String assumption : report.assumptions()) {
      out.append("  - ").append(assumption).append('\n');
    }
    return out.toString();
  }

  private static void table(final StringBuilder out, final PlanReport report) {
    final String allyOwns =
        String.format(Locale.ROOT, ReportText.COLUMN_ALLY_OWNS, report.ally(), report.target());
    out.append(
        String.format(
            Locale.ROOT,
            HEADER,
            ReportText.PLAN_COLUMN_RANK,
            ReportText.PLAN_COLUMN_PLAN,
            allyOwns,
            String.format(
                Locale.ROOT,
                ReportText.COLUMN_ATTACKER_CONQUERS,
                report.attacker(),
                report.target()),
            ReportText.PLAN_COLUMN_ORDERS,
            ReportText.PLAN_COLUMN_ATTACKER_TUV,
            ReportText.PLAN_COLUMN_ATTACKER_AIRCRAFT,
            ReportText.PLAN_COLUMN_ALLY_TUV,
            ReportText.PLAN_COLUMN_ALLY_PU,
            ReportText.PLAN_COLUMN_VERDICT));
    for (final OperationalPlan plan : report.plans()) {
      final Metrics m = plan.metrics();
      out.append(
          String.format(
              Locale.ROOT,
              ROW,
              plan.rank(),
              plan.id(),
              m.italyOwnsFrance().format(),
              m.germanyConquersFrance().format(),
              String.format(Locale.ROOT, "%.0f%%", m.allOrdersAccepted().rate() * 100),
              m.meanGermanTuvLost(),
              m.meanGermanAircraftLost(),
              m.meanItalianTuvLost(),
              m.meanItalianPuGain(),
              verdict(plan)));
      for (final String violation : plan.constraintViolations()) {
        out.append("        ! ").append(violation).append('\n');
      }
      for (final String warning : plan.warnings()) {
        out.append("        ? ").append(warning).append('\n');
      }
    }
    for (final List<Integer> run : PlanEvaluator.unseparatedRanks(report.plans())) {
      out.append(
              String.format(
                  Locale.ROOT,
                  ReportText.PLAN_RANKS_OVERLAP,
                  run.get(0),
                  ranks(run.subList(1, run.size())),
                  allyOwns))
          .append('\n');
    }
    if (report.plans().stream().noneMatch(OperationalPlan::feasible)) {
      out.append(ReportText.PLAN_NONE_FEASIBLE).append('\n');
    }
    out.append('\n');
    final OperationalPlan top = report.plans().get(0);
    out.append(ReportText.PLAN_ORDERS)
        .append(" (")
        .append(top.id())
        .append("): ")
        .append(top.description())
        .append('\n');
    for (final StepOrders step : top.orders()) {
      out.append("  ").append(step.step()).append('\n');
      for (final OrderSummary order : step.orders()) {
        out.append("    ")
            .append(UnitCounts.describe(order.units()))
            .append(" -> ")
            .append(String.join(" > ", order.route()))
            .append('\n');
      }
    }
    out.append('\n');
  }

  private static String ranks(final List<Integer> ranks) {
    return ranks.size() == 1
        ? String.format(Locale.ROOT, ReportText.RANK_ONE, ranks.get(0))
        : String.format(
            Locale.ROOT, ReportText.RANK_RANGE, ranks.get(0), ranks.get(ranks.size() - 1));
  }

  private static String verdict(final OperationalPlan plan) {
    if (plan.feasible()) {
      return plan.evidenceSufficient() ? ReportText.PLAN_FEASIBLE : ReportText.PLAN_FEASIBLE_THIN;
    }
    return plan.evidenceSufficient() ? ReportText.PLAN_INFEASIBLE : ReportText.PLAN_INFEASIBLE_THIN;
  }
}
