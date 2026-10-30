package org.triplea.generalstaff.odds;

import java.util.Locale;
import org.triplea.generalstaff.observe.UnitCounts;
import org.triplea.generalstaff.odds.StrafeSweep.Row;

/**
 * LAYOUT ONLY. Renders a {@link StrafeSweep} as a text grid. If you are here to change wording, you
 * are in the wrong file: see {@link OddsText}.
 */
public final class StrafeSweepFormatter {
  private static final String ROW = "%-16s %-14s %-28s %-28s %9s %9s %9s%n";

  private StrafeSweepFormatter() {}

  public static String toText(final StrafeSweep sweep) {
    final StringBuilder out = new StringBuilder();
    out.append(
            String.format(
                Locale.ROOT,
                OddsText.TITLE,
                sweep.attacker(),
                sweep.defender(),
                sweep.territory(),
                sweep.runs()))
        .append('\n');
    out.append(OddsText.DEFENDERS)
        .append(": ")
        .append(UnitCounts.describe(sweep.defenders()))
        .append('\n')
        .append('\n');
    out.append(
        String.format(
            Locale.ROOT,
            ROW,
            OddsText.COLUMN_FORCE,
            OddsText.COLUMN_RETREAT,
            OddsText.COLUMN_DESTROYED,
            OddsText.COLUMN_HOLDS,
            OddsText.COLUMN_DEFENDERS_LEFT,
            OddsText.COLUMN_ATTACKERS_LEFT,
            OddsText.COLUMN_TUV));
    for (final Row row : sweep.rows()) {
      out.append(
          String.format(
              Locale.ROOT,
              ROW,
              row.force(),
              row.retreatAfterRound() == BattleOptions.NEVER
                  ? OddsText.TO_THE_END
                  : String.format(Locale.ROOT, OddsText.ROUND, row.retreatAfterRound()),
              row.allDefendersDestroyed().format(),
              row.defenderHolds().format(),
              String.format(Locale.ROOT, "%.2f", row.averageDefendersLeft()),
              String.format(Locale.ROOT, "%.2f", row.averageAttackersLeft()),
              String.format(Locale.ROOT, "%+.1f", row.averageTuvSwing())));
    }
    out.append(OddsText.CONQUEST_NOTE).append('\n');
    return out.toString();
  }
}
