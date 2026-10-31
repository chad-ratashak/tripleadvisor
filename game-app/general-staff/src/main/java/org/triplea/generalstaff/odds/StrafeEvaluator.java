package org.triplea.generalstaff.odds;

import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.triplea.delegate.Matches;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.triplea.generalstaff.observe.UnitCounts;
import org.triplea.generalstaff.odds.ForceSubsets.Force;
import org.triplea.generalstaff.odds.StrafeSweep.Row;

/**
 * Sweeps forces and retreat rounds through the battle calculator. Air-only forces fight on when
 * only aircraft are left (there is nothing else), mixed forces pull the aircraft out once the land
 * units are gone, exactly as a player would.
 */
public final class StrafeEvaluator {
  private final BattleOddsService odds;

  public StrafeEvaluator(final BattleOddsService odds) {
    this.odds = odds;
  }

  /**
   * For every force: retreat after rounds 1..{@code maxRound}, then fight to the end. Each cell is
   * {@code runs} simulated battles.
   */
  public StrafeSweep sweep(
      final GamePlayer attacker,
      final GamePlayer defender,
      final Territory location,
      final List<Force> forces,
      final Collection<Unit> defenders,
      final int maxRound,
      final int runs) {
    final List<Row> rows = new ArrayList<>();
    for (final Force force : forces) {
      final boolean landPresent = force.units().stream().anyMatch(Matches.unitIsAir().negate());
      final BattleOptions base =
          landPresent ? BattleOptions.fightToTheEnd() : BattleOptions.airStrike();
      for (int round = 1; round <= maxRound; round++) {
        rows.add(
            row(
                attacker,
                defender,
                location,
                force,
                landPresent,
                defenders,
                base.retreatingAfterRound(round),
                runs));
      }
      rows.add(row(attacker, defender, location, force, landPresent, defenders, base, runs));
    }
    return new StrafeSweep(
        attacker.getName(),
        defender.getName(),
        location.getName(),
        UnitCounts.of(defenders),
        runs,
        rows);
  }

  private Row row(
      final GamePlayer attacker,
      final GamePlayer defender,
      final Territory location,
      final Force force,
      final boolean landPresent,
      final Collection<Unit> defenders,
      final BattleOptions options,
      final int runs) {
    final BattleEstimate estimate =
        odds.estimate(
            new BattleScenario(
                force.label() + " / retreat after round " + options.retreatAfterRound(),
                attacker,
                defender,
                location,
                force.units(),
                defenders,
                options),
            runs);
    return new Row(
        force.label(),
        estimate.attackingUnits(),
        landPresent,
        options.retreatAfterRound(),
        estimate.attackerWins(),
        estimate.defenderWins(),
        estimate.averageDefendersLeft(),
        estimate.averageAttackersLeft(),
        estimate.averageTuvSwing(),
        estimate.expectedAttackerLosses());
  }
}
