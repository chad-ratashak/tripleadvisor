package org.triplea.generalstaff.plan;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.engine.data.UnitType;
import games.strategy.triplea.delegate.Matches;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.triplea.generalstaff.observe.GameObservation.UnitCount;
import org.triplea.generalstaff.observe.UnitCounts;
import org.triplea.generalstaff.odds.BattleEstimate;
import org.triplea.generalstaff.odds.BattleEstimate.Outcome;
import org.triplea.generalstaff.odds.BattleOddsService;
import org.triplea.generalstaff.odds.BattleOptions;
import org.triplea.generalstaff.odds.BattleScenario;
import org.triplea.generalstaff.odds.Proportion;
import org.triplea.generalstaff.plan.OperationParisReport.Conditional;
import org.triplea.generalstaff.plan.OperationParisReport.FollowUpEstimate;

/**
 * The Operation Paris proof of concept: reads the position around France and asks the engine's
 * battle calculator how the candidate ways of taking it compare. Forces are chosen by position and
 * range only; nothing here checks move legality or changes the live game data.
 */
public final class OperationParisBaseline {
  /** Survivor sets beyond this many, ranked by frequency, are not simulated. */
  public static final int DEFAULT_MAX_POOLS = 25;

  static final String LABEL_FULL_ATTACK = "german-full-attack";
  static final String LABEL_AIR_ONLY = "german-air-only-strike";
  static final String LABEL_STRAFE = "german-strafe-retreat-after-round-";
  static final String LABEL_FOLLOW_UP = "italian-follow-up-after-air-strike";
  static final String LABEL_ITALY_ALONE = "italy-alone-against-full-garrison";

  private static final int MIN_POOL_RUNS = 200;
  private static final int STRAFE_ROUNDS = 3;

  private final GameData data;
  private final GamePlayer attacker;
  private final GamePlayer ally;
  private final GamePlayer defender;
  private final Territory target;
  private final long seed;
  private final int maxPools;

  public OperationParisBaseline(
      final GameData data,
      final String attackerName,
      final String allyName,
      final String defenderName,
      final String targetName,
      final long seed,
      final int maxPools) {
    this.data = data;
    this.attacker = player(attackerName);
    this.ally = player(allyName);
    this.defender = player(defenderName);
    this.target = territory(targetName);
    this.seed = seed;
    this.maxPools = maxPools;
  }

  /** Germany strikes, Italy captures, France defends the territory named France. */
  public static OperationParisBaseline forGlobal1940(final GameData data, final long seed) {
    return new OperationParisBaseline(
        data, "Germans", "Italians", "French", "France", seed, DEFAULT_MAX_POOLS);
  }

  /** Runs every scenario {@code runs} times. */
  public OperationParisReport run(final int runs) {
    final Collection<Unit> garrison = List.copyOf(target.getUnits());
    final Collection<Unit> attackerAir = ForceSelection.airStrikeForce(data, attacker, target);
    final Collection<Unit> attackerForce =
        concat(ForceSelection.landStrikeForce(data, attacker, target), attackerAir);
    final Collection<Unit> allyForce = ForceSelection.strikeForce(data, ally, target);
    try (BattleOddsService odds = new BattleOddsService(data)) {
      final BattleEstimate fullAttack =
          odds.estimate(
              scenario(
                  LABEL_FULL_ATTACK,
                  attacker,
                  attackerForce,
                  garrison,
                  BattleOptions.fightToTheEnd()),
              runs);
      final BattleEstimate airOnly =
          odds.estimate(
              scenario(LABEL_AIR_ONLY, attacker, attackerAir, garrison, BattleOptions.airStrike()),
              runs);
      final FollowUpEstimate followUp = allyFollowUp(odds, airOnly, allyForce, runs);
      final List<BattleEstimate> strafes =
          IntStream.rangeClosed(1, STRAFE_ROUNDS)
              .mapToObj(
                  round ->
                      odds.estimate(
                          scenario(
                              LABEL_STRAFE + round,
                              attacker,
                              attackerForce,
                              garrison,
                              BattleOptions.fightToTheEnd().retreatingAfterRound(round)),
                          runs))
              .toList();
      final BattleEstimate allyAlone =
          odds.estimate(
              scenario(LABEL_ITALY_ALONE, ally, allyForce, garrison, BattleOptions.fightToTheEnd()),
              runs);
      return new OperationParisReport(
          data.getGameName(),
          runs,
          seed,
          UnitCounts.of(garrison),
          UnitCounts.of(attackerForce),
          UnitCounts.of(allyForce),
          fullAttack,
          airOnly,
          followUp,
          strafes,
          allyAlone,
          ReportText.ASSUMPTIONS);
    }
  }

  /**
   * Samples the ally's attack on the survivors of the air strike: for every air-strike run, one
   * draw from the ally's battle against exactly that survivor set.
   */
  private FollowUpEstimate allyFollowUp(
      final BattleOddsService odds,
      final BattleEstimate airStrike,
      final Collection<Unit> allyForce,
      final int runs) {
    final Random random = new Random(seed);
    final List<Unit> infrastructure =
        target.getUnits().stream().filter(Matches.unitIsInfrastructure()).toList();
    int successes = 0;
    int walkIns = 0;
    int unresolved = 0;
    final List<Conditional> conditionals = new ArrayList<>();
    for (final Outcome outcome : airStrike.defenderSurvivors()) {
      if (outcome.units().isEmpty()) {
        successes += outcome.occurrences();
        walkIns += outcome.occurrences();
        continue;
      }
      if (conditionals.size() >= maxPools) {
        unresolved += outcome.occurrences();
        continue;
      }
      final Collection<Unit> defenders = concat(infrastructure, temporaryUnits(outcome.units()));
      final int poolRuns = Math.max(MIN_POOL_RUNS, outcome.occurrences());
      final BattleEstimate conditional =
          odds.estimate(
              scenario(
                  LABEL_FOLLOW_UP + "/" + UnitCounts.describe(outcome.units()),
                  ally,
                  allyForce,
                  defenders,
                  BattleOptions.fightToTheEnd()),
              poolRuns);
      final int wins = conditional.attackerWins().successes();
      for (int i = 0; i < outcome.occurrences(); i++) {
        if (random.nextInt(poolRuns) < wins) {
          successes++;
        }
      }
      conditionals.add(
          new Conditional(
              outcome.units(), outcome.occurrences(), poolRuns, conditional.attackerWins()));
    }
    return new FollowUpEstimate(
        LABEL_FOLLOW_UP,
        runs,
        Proportion.of(successes, runs),
        Proportion.of(walkIns, runs),
        Proportion.of(unresolved, runs),
        conditionals);
  }

  /** Units that exist only for a calculation and are never registered in the live game data. */
  private Collection<Unit> temporaryUnits(final List<UnitCount> counts) {
    final List<Unit> units = new ArrayList<>();
    for (final UnitCount count : counts) {
      final UnitType type = data.getUnitTypeList().getUnitTypeOrThrow(count.type());
      units.addAll(type.createTemp(count.count(), player(count.owner())));
    }
    return units;
  }

  private BattleScenario scenario(
      final String label,
      final GamePlayer attackingPower,
      final Collection<Unit> attacking,
      final Collection<Unit> defending,
      final BattleOptions options) {
    return new BattleScenario(
        label, attackingPower, defender, target, attacking, defending, options);
  }

  private GamePlayer player(final String name) {
    final GamePlayer player = data.getPlayerList().getPlayerId(name);
    if (player == null) {
      throw new IllegalArgumentException("Unknown player: " + name);
    }
    return player;
  }

  private Territory territory(final String name) {
    final Territory territory = data.getMap().getTerritoryOrNull(name);
    if (territory == null) {
      throw new IllegalArgumentException("Unknown territory: " + name);
    }
    return territory;
  }

  private static Collection<Unit> concat(
      final Collection<Unit> first, final Collection<Unit> second) {
    return Stream.concat(first.stream(), second.stream()).toList();
  }
}
