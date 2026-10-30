package org.triplea.generalstaff.plan;

import java.util.List;
import java.util.Locale;
import org.triplea.generalstaff.observe.UnitCounts;
import org.triplea.generalstaff.odds.BattleEstimate;
import org.triplea.generalstaff.odds.BattleEstimate.UnitMean;
import org.triplea.generalstaff.plan.OperationParisReport.Conditional;
import org.triplea.generalstaff.plan.OperationParisReport.FollowUpEstimate;

/**
 * LAYOUT ONLY. Renders an {@link OperationParisReport} as a one-page text comparison. If you are
 * here to change wording, you are in the wrong file: see {@link ReportText}.
 */
public final class ReportFormatter {
  private static final String ROW = "%-44s %-28s %-28s %6s %8s %8s %8s%n";
  private static final int CONDITIONALS_SHOWN = 12;

  private ReportFormatter() {}

  public static String toText(final OperationParisReport report) {
    final StringBuilder out = new StringBuilder();
    out.append(ReportText.TITLE).append('\n');
    line(out, ReportText.GAME, report.gameName());
    line(out, ReportText.RUNS, Integer.toString(report.runsPerScenario()));
    line(out, ReportText.SEED, Long.toString(report.seed()));
    line(out, ReportText.FRENCH_GARRISON, UnitCounts.describe(report.frenchGarrison()));
    line(out, ReportText.GERMAN_FORCE, UnitCounts.describe(report.germanStrikeForce()));
    line(out, ReportText.ITALIAN_FORCE, UnitCounts.describe(report.italianStrikeForce()));
    out.append('\n');

    out.append(
        String.format(
            Locale.ROOT,
            ROW,
            ReportText.COLUMN_SCENARIO,
            ReportText.COLUMN_ATTACKER_WINS,
            ReportText.COLUMN_DEFENDER_HOLDS,
            ReportText.COLUMN_ROUNDS,
            ReportText.COLUMN_DEFENDERS_LEFT,
            ReportText.COLUMN_ATTACKERS_LEFT,
            ReportText.COLUMN_TUV_SWING));
    row(out, ReportText.SCENARIO_FULL, report.germanFullAttack());
    row(out, ReportText.SCENARIO_AIR_ONLY, report.germanAirOnlyStrike());
    for (final BattleEstimate strafe : report.germanStrafes()) {
      row(
          out,
          String.format(
              Locale.ROOT, ReportText.SCENARIO_STRAFE, strafe.options().retreatAfterRound()),
          strafe);
    }
    row(out, ReportText.SCENARIO_ITALY_ALONE, report.italyAlone());
    out.append(ReportText.STRAFE_NOTE).append('\n').append('\n');

    final FollowUpEstimate followUp = report.italianFollowUp();
    out.append(ReportText.FOLLOW_UP_TITLE).append('\n');
    line(out, ReportText.FOLLOW_UP_SUCCESS, followUp.italyOwnsFrance().format());
    line(out, ReportText.FOLLOW_UP_WALK_IN, followUp.walkIn().format());
    line(out, ReportText.FOLLOW_UP_UNRESOLVED, followUp.unresolved().format());
    out.append(ReportText.FOLLOW_UP_COLUMNS).append('\n');
    for (final Conditional conditional :
        followUp.conditionals().stream().limit(CONDITIONALS_SHOWN).toList()) {
      out.append(
          String.format(
              Locale.ROOT,
              "  %-60s %5d  %s%n",
              UnitCounts.describe(conditional.survivors()),
              conditional.occurrences(),
              conditional.italianWins().format()));
    }
    out.append('\n');

    out.append(ReportText.EXPECTED_LOSSES).append('\n');
    losses(out, ReportText.SCENARIO_FULL, report.germanFullAttack());
    losses(out, ReportText.SCENARIO_AIR_ONLY, report.germanAirOnlyStrike());
    losses(out, ReportText.SCENARIO_ITALY_ALONE, report.italyAlone());
    out.append('\n');

    out.append(ReportText.ASSUMPTIONS_TITLE).append('\n');
    for (final String assumption : report.assumptions()) {
      out.append("  - ").append(assumption).append('\n');
    }
    return out.toString();
  }

  private static void line(final StringBuilder out, final String label, final String value) {
    out.append(label).append(": ").append(value).append('\n');
  }

  private static void row(final StringBuilder out, final String title, final BattleEstimate e) {
    out.append(
        String.format(
            Locale.ROOT,
            ROW,
            title,
            e.attackerWins().format(),
            e.defenderWins().format(),
            String.format(Locale.ROOT, "%.1f", e.averageRounds()),
            String.format(Locale.ROOT, "%.2f", e.averageDefendersLeft()),
            String.format(Locale.ROOT, "%.2f", e.averageAttackersLeft()),
            String.format(Locale.ROOT, "%+.1f", e.averageTuvSwing())));
  }

  private static void losses(final StringBuilder out, final String title, final BattleEstimate e) {
    out.append("  ").append(title).append('\n');
    out.append("    ")
        .append(e.attacker())
        .append(": ")
        .append(means(e.expectedAttackerLosses()))
        .append('\n');
    out.append("    ")
        .append(e.defender())
        .append(": ")
        .append(means(e.expectedDefenderLosses()))
        .append('\n');
  }

  private static String means(final List<UnitMean> means) {
    final StringBuilder out = new StringBuilder();
    for (final UnitMean mean : means) {
      if (!out.isEmpty()) {
        out.append(", ");
      }
      out.append(String.format(Locale.ROOT, "%s/%s %.2f", mean.owner(), mean.type(), mean.mean()));
    }
    return out.isEmpty() ? "none" : out.toString();
  }
}
