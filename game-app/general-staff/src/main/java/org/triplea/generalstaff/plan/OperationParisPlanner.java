package org.triplea.generalstaff.plan;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.GameStep;
import games.strategy.engine.data.Route;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.engine.data.UnitType;
import games.strategy.triplea.Constants;
import games.strategy.triplea.delegate.Matches;
import games.strategy.triplea.util.TuvCostsCalculator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.triplea.generalstaff.observe.UnitCounts;
import org.triplea.generalstaff.odds.ForceSubsets;
import org.triplea.generalstaff.odds.Proportion;
import org.triplea.generalstaff.plan.PlanReport.Metrics;
import org.triplea.generalstaff.plan.PlanReport.NotEvaluated;
import org.triplea.generalstaff.plan.PlanReport.OperationalPlan;
import org.triplea.generalstaff.plan.PlanReport.OrderSummary;
import org.triplea.generalstaff.plan.PlanReport.StepOrders;
import org.triplea.generalstaff.sim.OpponentPolicy;
import org.triplea.generalstaff.sim.Sandbox;
import org.triplea.generalstaff.sim.SimulationPlayer;
import org.triplea.generalstaff.sim.TurnSimulator;
import org.triplea.generalstaff.sim.TurnSimulator.BattleOutcome;
import org.triplea.generalstaff.sim.TurnSimulator.Order;
import org.triplea.generalstaff.sim.TurnSimulator.StepRun;
import org.triplea.java.collections.IntegerMap;

/**
 * The Operation Paris planner: enumerates candidate plan families (German air strike or strafe with
 * a chosen force and retreat round, then the Italian attack), evaluates each with sampled
 * trajectories on cloned games driven by the engine's own delegates, and ranks them under the hard
 * constraint that Germany must not conquer France. Nothing here touches the live game.
 */
public final class OperationParisPlanner {
  /** Which German force and retreat rule to try, and whether Italy follows up. */
  public record Family(
      String id, String description, int landUnits, int retreatAfterRound, boolean italianAttack) {
    /** Marker for "every land unit of the strike force". */
    public static final int ALL_LAND = Integer.MAX_VALUE;

    /** Marker for "no German action at all". */
    public static final int NO_STRIKE = -1;

    boolean germanStrike() {
      return landUnits != NO_STRIKE;
    }
  }

  /** How the planner samples. */
  public record Settings(
      int trajectories,
      long seed,
      int threads,
      double violationTolerance,
      OpponentPolicy interveningPolicy,
      List<String> interveningPlayers,
      List<Family> families) {
    public static Settings defaults() {
      return new Settings(
          40,
          1L,
          Math.max(1, Math.min(6, Runtime.getRuntime().availableProcessors() - 1)),
          0.0,
          OpponentPolicy.passive(),
          List.of("British"),
          defaultFamilies());
    }

    public Settings withTrajectories(final int value) {
      return new Settings(
          value,
          seed,
          threads,
          violationTolerance,
          interveningPolicy,
          interveningPlayers,
          families);
    }

    public Settings withSeed(final long value) {
      return new Settings(
          trajectories,
          value,
          threads,
          violationTolerance,
          interveningPolicy,
          interveningPlayers,
          families);
    }

    public Settings withThreads(final int value) {
      return new Settings(
          trajectories,
          seed,
          value,
          violationTolerance,
          interveningPolicy,
          interveningPlayers,
          families);
    }

    public Settings withViolationTolerance(final double value) {
      return new Settings(
          trajectories, seed, threads, value, interveningPolicy, interveningPlayers, families);
    }

    public Settings withInterveningPolicy(final OpponentPolicy value) {
      return new Settings(
          trajectories, seed, threads, violationTolerance, value, interveningPlayers, families);
    }

    public Settings withFamilies(final List<Family> value) {
      return new Settings(
          trajectories,
          seed,
          threads,
          violationTolerance,
          interveningPolicy,
          interveningPlayers,
          value);
    }
  }

  /**
   * The families tried by default: Italy alone, round-limited air strikes, strafes, full attack.
   */
  public static List<Family> defaultFamilies() {
    final List<Family> families = new ArrayList<>();
    families.add(
        new Family(
            "italy-alone",
            "No German action; Italy attacks the full garrison.",
            Family.NO_STRIKE,
            SimulationPlayer.NEVER,
            true));
    for (int round = 1; round <= 3; round++) {
      families.add(
          new Family(
              "air-strike-r" + round,
              "German aircraft only, retreating after round " + round + "; Italy attacks.",
              0,
              round,
              true));
    }
    for (final int land : new int[] {3, 6, 9}) {
      for (int round = 1; round <= 2; round++) {
        families.add(
            new Family(
                "strafe-" + land + "land-r" + round,
                "German aircraft plus the "
                    + land
                    + " cheapest land units, retreating after round "
                    + round
                    + "; Italy attacks.",
                land,
                round,
                true));
      }
    }
    families.add(
        new Family(
            "full-attack",
            "The whole German strike force fights to the end; Italy attacks if France is still enemy-held.",
            Family.ALL_LAND,
            SimulationPlayer.NEVER,
            true));
    return families;
  }

  private final GameData live;
  private final String attackerName;
  private final String allyName;
  private final String defenderName;
  private final String targetName;
  private final Settings settings;

  public OperationParisPlanner(
      final GameData live,
      final String attackerName,
      final String allyName,
      final String defenderName,
      final String targetName,
      final Settings settings) {
    this.live = live;
    this.attackerName = attackerName;
    this.allyName = allyName;
    this.defenderName = defenderName;
    this.targetName = targetName;
    this.settings = settings;
  }

  public static OperationParisPlanner forGlobal1940(final GameData live, final Settings settings) {
    return new OperationParisPlanner(live, "Germans", "Italians", "French", "France", settings);
  }

  /** Evaluates every family and returns the ranked report. */
  public PlanReport plan() {
    final Sandbox base = Sandbox.fromLive(live, settings.seed());
    new TurnSimulator(base).initializeIfFresh();
    final byte[] start = base.snapshot();
    final GamePlayer attacker = base.player(attackerName);
    final GamePlayer ally = base.player(allyName);
    final Territory target = base.territory(targetName);
    final List<Unit> air = ForceSelection.airStrikeForce(base.data(), attacker, target);
    final List<Unit> land =
        ForceSubsets.byLandCount(
                ForceSelection.landStrikeForce(base.data(), attacker, target),
                1,
                new TuvCostsCalculator().getCostsForTuv(attacker))
            .stream()
            .reduce((first, second) -> second)
            .map(force -> force.units().stream().filter(Matches.unitIsAir().negate()).toList())
            .orElse(List.of());
    final List<Unit> allyForce = ForceSelection.strikeForce(base.data(), ally, target);
    final Forces forces = new Forces(air, land, allyForce);
    final Steps steps = Steps.of(base, attacker, ally, settings.interveningPlayers());

    final ExecutorService pool = Executors.newFixedThreadPool(settings.threads());
    try {
      final List<OperationalPlan> plans = new ArrayList<>();
      for (final Family family : settings.families()) {
        plans.add(evaluate(family, start, forces, steps, pool));
      }
      return new PlanReport(
          live.getGameName(),
          settings.trajectories(),
          settings.seed(),
          settings.violationTolerance(),
          settings.interveningPolicy().name(),
          PlanEvaluator.rank(plans, settings.violationTolerance()),
          List.of(
              new NotEvaluated(
                  "delayed-conquest",
                  "Strike on the German turn and attack on Italy's next turn: needs a full round of"
                      + " every power's turn, including French purchases and placement.")),
          ReportText.PLAN_ASSUMPTIONS);
    } finally {
      pool.shutdownNow();
    }
  }

  private OperationalPlan evaluate(
      final Family family,
      final byte[] start,
      final Forces forces,
      final Steps steps,
      final ExecutorService pool) {
    final List<Future<Trajectory>> futures = new ArrayList<>();
    for (int i = 0; i < settings.trajectories(); i++) {
      final long seed = settings.seed() * 1_000_003L + family.id().hashCode() * 31L + i;
      futures.add(pool.submit(() -> trajectory(family, start, forces, steps, seed)));
    }
    final List<Trajectory> results = new ArrayList<>();
    for (final Future<Trajectory> future : futures) {
      try {
        results.add(future.get());
      } catch (final ExecutionException e) {
        throw new IllegalStateException("A trajectory of " + family.id() + " failed", e.getCause());
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while planning", e);
      }
    }
    final int n = results.size();
    final Metrics metrics =
        new Metrics(
            n,
            Proportion.of(count(results, Trajectory::italyOwns), n),
            Proportion.of(count(results, Trajectory::germanConquest), n),
            Proportion.of(count(results, Trajectory::franceEmptied), n),
            Proportion.of(count(results, Trajectory::allOrdersAccepted), n),
            results.stream().mapToDouble(Trajectory::germanTuvLost).average().orElse(0),
            results.stream().mapToDouble(Trajectory::germanAircraftLost).average().orElse(0),
            results.stream().mapToDouble(Trajectory::italianTuvLost).average().orElse(0),
            results.stream().mapToDouble(Trajectory::frenchTuvLost).average().orElse(0),
            results.stream().mapToDouble(Trajectory::italianPuGain).average().orElse(0),
            results.stream().mapToDouble(Trajectory::germanBattleRounds).average().orElse(0));
    final Set<String> errors = new HashSet<>();
    results.forEach(result -> errors.addAll(result.orderErrors()));
    return new OperationalPlan(
        family.id(),
        family.id().replaceAll("-r\\d$|-\\d+land|-\\d$", ""),
        family.description(),
        0,
        false,
        family.retreatAfterRound(),
        results.isEmpty() ? List.of() : results.get(0).orders(),
        metrics,
        List.of(),
        errors.stream().sorted().toList());
  }

  /** One sampled trajectory on a fresh copy. */
  private Trajectory trajectory(
      final Family family,
      final byte[] start,
      final Forces forces,
      final Steps steps,
      final long seed) {
    final Sandbox sandbox = Sandbox.fromBytes(start, seed);
    final TurnSimulator simulator = new TurnSimulator(sandbox);
    final GamePlayer attacker = sandbox.player(attackerName);
    final GamePlayer ally = sandbox.player(allyName);
    final GamePlayer defender = sandbox.player(defenderName);
    final Territory target = sandbox.territory(targetName);
    final TuvCostsCalculator tuv = new TuvCostsCalculator();
    final List<Unit> germanForce = sandbox.units(forces.germanForce(family));
    final List<Unit> allyForce = sandbox.units(forces.ally());
    final List<Unit> garrison = List.copyOf(target.getUnits());
    final int germanTuvBefore = tuvOf(germanForce, tuv.getCostsForTuv(attacker));
    final int allyTuvBefore = tuvOf(allyForce, tuv.getCostsForTuv(ally));
    final int frenchTuvBefore = tuvOf(garrison, tuv.getCostsForTuv(defender));
    final int allyPusBefore = ally.getResources().getQuantity(Constants.PUS);
    final List<StepOrders> orderSummaries = new ArrayList<>();
    final List<String> orderErrors = new ArrayList<>();
    boolean germanConquest = false;
    boolean franceEmptied = false;
    double germanBattleRounds = 0;

    if (family.germanStrike()) {
      sandbox.policy(attackerName).retreatAfterRound(family.retreatAfterRound());
      final List<Order> orders = ordersInto(sandbox, attacker, germanForce, target, orderErrors);
      orderSummaries.add(new StepOrders(steps.attackerCombatMove(), summarize(orders)));
      final StepRun combatMove = simulator.runStep(steps.attackerCombatMove(), orders);
      orderErrors.addAll(combatMove.orderErrors());
      final StepRun battle = simulator.runStep(steps.attackerBattle());
      germanBattleRounds =
          battle.battles().stream()
              .filter(outcome -> outcome.territory().equals(targetName))
              .mapToInt(BattleOutcome::rounds)
              .max()
              .orElse(0);
      germanConquest = attacker.equals(target.getOwner());
      franceEmptied =
          !germanConquest
              && target
                  .getMatches(Matches.enemyUnit(ally).and(Matches.unitIsNotInfrastructure()))
                  .isEmpty();
    }

    for (final String step : steps.interveningNonCombatMoves()) {
      sandbox.position(step);
      final GamePlayer player = sandbox.currentStep().getPlayerId();
      final List<Order> orders = settings.interveningPolicy().orders(sandbox, player, step);
      final StepRun run = simulator.runStep(step, orders);
      orderErrors.addAll(run.orderErrors());
    }

    if (family.italianAttack()
        && !ally.equals(target.getOwner())
        && sandbox.data().getRelationshipTracker().isAtWar(ally, target.getOwner())) {
      final List<Order> orders = ordersInto(sandbox, ally, allyForce, target, orderErrors);
      orderSummaries.add(new StepOrders(steps.allyCombatMove(), summarize(orders)));
      final StepRun combatMove = simulator.runStep(steps.allyCombatMove(), orders);
      orderErrors.addAll(combatMove.orderErrors());
      simulator.runStep(steps.allyBattle());
    }

    final Set<UUID> alive = unitsOnTheMap(sandbox);
    final boolean italyOwns = ally.equals(target.getOwner());
    return new Trajectory(
        italyOwns,
        germanConquest,
        franceEmptied,
        orderErrors.isEmpty(),
        germanTuvBefore - tuvOf(alive(germanForce, alive), tuv.getCostsForTuv(attacker)),
        germanForce.stream()
            .filter(Matches.unitIsAir())
            .filter(unit -> !alive.contains(unit.getId()))
            .count(),
        allyTuvBefore - tuvOf(alive(allyForce, alive), tuv.getCostsForTuv(ally)),
        frenchTuvBefore - tuvOf(alive(garrison, alive), tuv.getCostsForTuv(defender)),
        ally.getResources().getQuantity(Constants.PUS) - allyPusBefore,
        germanBattleRounds,
        orderSummaries,
        orderErrors);
  }

  /** One order per origin territory, along the map's cheapest passable route into the target. */
  private static List<Order> ordersInto(
      final Sandbox sandbox,
      final GamePlayer player,
      final List<Unit> force,
      final Territory target,
      final List<String> errors) {
    final Map<Territory, List<Unit>> byOrigin = new LinkedHashMap<>();
    for (final Territory territory : sandbox.data().getMap().getTerritories()) {
      final List<Unit> here = territory.getUnits().stream().filter(force::contains).toList();
      if (!here.isEmpty()) {
        byOrigin.put(territory, here);
      }
    }
    final List<Order> orders = new ArrayList<>();
    byOrigin.forEach(
        (origin, units) -> {
          if (origin.equals(target)) {
            return;
          }
          final Optional<Route> route =
              sandbox
                  .data()
                  .getMap()
                  .getRouteForUnits(
                      origin, target, Matches.territoryIsNotImpassable(), units, player);
          if (route.isEmpty()) {
            errors.add("No route from " + origin.getName() + " to " + target.getName());
          } else {
            orders.add(new Order(units, route.get()));
          }
        });
    return orders;
  }

  private static List<OrderSummary> summarize(final List<Order> orders) {
    return orders.stream()
        .map(
            order ->
                new OrderSummary(
                    UnitCounts.of(order.units()),
                    order.route().getAllTerritories().stream().map(Territory::getName).toList()))
        .toList();
  }

  private static Set<UUID> unitsOnTheMap(final Sandbox sandbox) {
    final Set<UUID> ids = new HashSet<>();
    for (final Territory territory : sandbox.data().getMap().getTerritories()) {
      for (final Unit unit : territory.getUnits()) {
        ids.add(unit.getId());
      }
    }
    return ids;
  }

  private static List<Unit> alive(final Collection<Unit> units, final Set<UUID> alive) {
    return units.stream().filter(unit -> alive.contains(unit.getId())).toList();
  }

  private static int tuvOf(final Collection<Unit> units, final IntegerMap<UnitType> costs) {
    return units.stream().mapToInt(unit -> costs.getInt(unit.getType())).sum();
  }

  private static int count(
      final List<Trajectory> results, final java.util.function.Predicate<Trajectory> test) {
    return (int) results.stream().filter(test).count();
  }

  /** The strike forces, as units of the base sandbox (translated into each trajectory by id). */
  private record Forces(List<Unit> air, List<Unit> landCheapestFirst, List<Unit> ally) {
    List<Unit> germanForce(final Family family) {
      if (!family.germanStrike()) {
        return List.of();
      }
      final List<Unit> force = new ArrayList<>(air);
      force.addAll(
          landCheapestFirst.subList(0, Math.min(family.landUnits(), landCheapestFirst.size())));
      return force;
    }
  }

  /** The step names the plan walks through, read from the game's own sequence. */
  private record Steps(
      String attackerCombatMove,
      String attackerBattle,
      List<String> interveningNonCombatMoves,
      String allyCombatMove,
      String allyBattle) {
    static Steps of(
        final Sandbox sandbox,
        final GamePlayer attacker,
        final GamePlayer ally,
        final List<String> interveningPlayers) {
      final List<String> intervening = new ArrayList<>();
      for (final String name : interveningPlayers) {
        intervening.add(step(sandbox, sandbox.player(name), GameStep::isNonCombatMoveStepName));
      }
      return new Steps(
          step(
              sandbox,
              attacker,
              name ->
                  GameStep.isCombatMoveStepName(name)
                      && !GameStep.isAirborneCombatMoveStepName(name)),
          step(sandbox, attacker, GameStep::isBattleStepName),
          intervening,
          step(
              sandbox,
              ally,
              name ->
                  GameStep.isCombatMoveStepName(name)
                      && !GameStep.isAirborneCombatMoveStepName(name)),
          step(sandbox, ally, GameStep::isBattleStepName));
    }

    private static String step(
        final Sandbox sandbox,
        final GamePlayer player,
        final java.util.function.Predicate<String> nameTest) {
      for (final GameStep step : sandbox.data().getSequence()) {
        if (player.equals(step.getPlayerId()) && nameTest.test(step.getName())) {
          return step.getName();
        }
      }
      throw new IllegalArgumentException(
          String.format(
              Locale.ROOT, "No matching step for %s in the game sequence", player.getName()));
    }
  }

  private record Trajectory(
      boolean italyOwns,
      boolean germanConquest,
      boolean franceEmptied,
      boolean allOrdersAccepted,
      int germanTuvLost,
      long germanAircraftLost,
      int italianTuvLost,
      int frenchTuvLost,
      int italianPuGain,
      double germanBattleRounds,
      List<StepOrders> orders,
      List<String> orderErrors) {}

  static String describeFamilies(final List<Family> families) {
    return families.stream().map(Family::id).collect(Collectors.joining(", "));
  }
}
