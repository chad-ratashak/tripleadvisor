package org.triplea.generalstaff.odds;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.Unit;
import games.strategy.triplea.delegate.Matches;
import games.strategy.triplea.delegate.TerritoryEffectHelper;
import games.strategy.triplea.delegate.battle.BattleResults;
import games.strategy.triplea.odds.calculator.AggregateResults;
import games.strategy.triplea.odds.calculator.ConcurrentBattleCalculator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.triplea.generalstaff.engine.EngineSettings;
import org.triplea.generalstaff.observe.GameObservation.UnitCount;
import org.triplea.generalstaff.observe.UnitCounts;
import org.triplea.generalstaff.odds.BattleEstimate.Outcome;
import org.triplea.generalstaff.odds.BattleEstimate.UnitMean;
import org.triplea.generalstaff.odds.BattleOptions.AirPolicy;
import org.triplea.generalstaff.odds.BattleOptions.LandPolicy;

/**
 * Estimates battle outcomes with the engine's own {@link ConcurrentBattleCalculator}, which fights
 * real battle rounds on private copies of the game data. The live game data is only read.
 *
 * <p>Building the service copies the game data once per worker thread, which is the expensive part;
 * keep one service per game position and close it when done.
 */
public final class BattleOddsService implements AutoCloseable {
  private final GameData data;
  private final ConcurrentBattleCalculator calculator = new ConcurrentBattleCalculator();

  public BattleOddsService(final GameData data) {
    EngineSettings.ensureInitialized();
    this.data = data;
    if (!calculator.setGameData(data).join()) {
      throw new IllegalStateException("The battle calculator could not copy the game data");
    }
  }

  /** Fights {@code scenario} {@code runs} times and summarizes the outcomes. */
  public BattleEstimate estimate(final BattleScenario scenario, final int runs) {
    if (runs <= 0) {
      throw new IllegalArgumentException("runs must be positive, was " + runs);
    }
    final BattleOptions options = scenario.options();
    calculator.setRetreatAfterRound(options.retreatAfterRound());
    calculator.setRetreatAfterXUnitsLeft(BattleOptions.NEVER);
    calculator.setKeepOneAttackingLandUnit(options.landPolicy() == LandPolicy.KEEP_ONE_LAND_UNIT);
    calculator.setAmphibious(false);
    final AggregateResults results =
        calculator.calculate(
            scenario.attacker(),
            scenario.defender(),
            scenario.location(),
            scenario.attackingUnits(),
            scenario.defendingUnits(),
            List.of(),
            TerritoryEffectHelper.getEffects(scenario.location()),
            options.airPolicy() == AirPolicy.RETREAT_WHEN_ONLY_AIR_LEFT,
            runs);
    if (results.getRollCount() != runs) {
      throw new IllegalStateException(
          "The battle calculator returned "
              + results.getRollCount()
              + " results for "
              + runs
              + " requested runs");
    }
    return summarize(scenario, results);
  }

  @Override
  public void close() {
    calculator.cancel();
    calculator.setGameData(null).join();
  }

  private BattleEstimate summarize(final BattleScenario scenario, final AggregateResults results) {
    final List<BattleResults> runs = results.getResults();
    final int n = runs.size();
    final List<UnitCount> attackers = UnitCounts.of(scenario.attackingUnits());
    final List<UnitCount> defenders = UnitCounts.of(scenario.defendingUnits());
    return new BattleEstimate(
        scenario.label(),
        scenario.attacker().getName(),
        scenario.defender().getName(),
        scenario.location().getName(),
        scenario.options(),
        attackers,
        defenders,
        n,
        Proportion.of(count(runs, BattleResults::attackerWon), n),
        Proportion.of(count(runs, BattleResults::defenderWon), n),
        Proportion.of(count(runs, BattleResults::draw), n),
        results.getAverageBattleRoundsFought(),
        averageLeft(runs, BattleResults::getRemainingAttackingUnits),
        averageLeft(runs, BattleResults::getRemainingDefendingUnits),
        results.getAverageTuvSwing(
            scenario.attacker(),
            scenario.attackingUnits(),
            scenario.defender(),
            scenario.defendingUnits(),
            data),
        expectedLosses(attackers, runs, BattleResults::getRemainingAttackingUnits),
        expectedLosses(defenders, runs, BattleResults::getRemainingDefendingUnits),
        survivorDistribution(runs));
  }

  private static int count(
      final List<BattleResults> runs, final java.util.function.Predicate<BattleResults> test) {
    return (int) runs.stream().filter(test).count();
  }

  private static double averageLeft(
      final List<BattleResults> runs, final Function<BattleResults, Collection<Unit>> remaining) {
    return runs.stream()
        .mapToDouble(run -> combatUnits(remaining.apply(run)).size())
        .average()
        .orElse(0.0);
  }

  private static List<UnitMean> expectedLosses(
      final List<UnitCount> start,
      final List<BattleResults> runs,
      final Function<BattleResults, Collection<Unit>> remaining) {
    final Map<String, Integer> remainingTotals = new HashMap<>();
    for (final BattleResults run : runs) {
      for (final Unit unit : remaining.apply(run)) {
        remainingTotals.merge(
            key(unit.getOwner().getName(), unit.getType().getName()), 1, Integer::sum);
      }
    }
    final List<UnitMean> losses = new ArrayList<>();
    for (final UnitCount stack : start) {
      final double meanRemaining =
          remainingTotals.getOrDefault(key(stack.owner(), stack.type()), 0) / (double) runs.size();
      losses.add(new UnitMean(stack.owner(), stack.type(), stack.count() - meanRemaining));
    }
    return losses;
  }

  private static List<Outcome> survivorDistribution(final List<BattleResults> runs) {
    final Map<List<UnitCount>, Integer> occurrences = new HashMap<>();
    for (final BattleResults run : runs) {
      occurrences.merge(
          UnitCounts.of(combatUnits(run.getRemainingDefendingUnits())), 1, Integer::sum);
    }
    return occurrences.entrySet().stream()
        .sorted(
            Comparator.comparing((Map.Entry<List<UnitCount>, Integer> entry) -> entry.getValue())
                .reversed()
                .thenComparing(entry -> UnitCounts.describe(entry.getKey())))
        .map(
            entry ->
                new Outcome(
                    entry.getKey(), entry.getValue(), Proportion.of(entry.getValue(), runs.size())))
        .toList();
  }

  private static List<Unit> combatUnits(final Collection<Unit> units) {
    return units.stream().filter(Matches.unitIsNotInfrastructure()).toList();
  }

  private static String key(final String owner, final String type) {
    return owner + "/" + type;
  }
}
