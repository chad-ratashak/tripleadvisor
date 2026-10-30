package org.triplea.generalstaff.plan;

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
  private static final String ROW = "%4d  %-28s %-28s %-28s %-9s %13.1f %19.2f %13.1f %12.1f  %s%n";

  private PlanFormatter() {}

  public static String toText(final PlanReport report) {
    final StringBuilder out = new StringBuilder();
    out.append(ReportText.PLAN_TITLE).append('\n');
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
        .append('\n')
        .append('\n');
    out.append(ReportText.PLAN_COLUMNS).append('\n');
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
              plan.feasible() ? ReportText.PLAN_FEASIBLE : ReportText.PLAN_INFEASIBLE));
      for (final String violation : plan.constraintViolations()) {
        out.append("        ! ").append(violation).append('\n');
      }
    }
    if (report.plans().stream().noneMatch(OperationalPlan::feasible)) {
      out.append(ReportText.PLAN_NONE_FEASIBLE).append('\n');
    }
    out.append('\n');
    if (!report.plans().isEmpty()) {
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
}
