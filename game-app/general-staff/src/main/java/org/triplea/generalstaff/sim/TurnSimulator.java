package org.triplea.generalstaff.sim;

import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.GameStep;
import games.strategy.engine.data.MoveDescription;
import games.strategy.engine.data.Route;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.engine.delegate.IDelegate;
import games.strategy.triplea.delegate.GameStepPropertiesHelper;
import games.strategy.triplea.delegate.Matches;
import games.strategy.triplea.delegate.battle.BattleDelegate;
import games.strategy.triplea.delegate.battle.IBattle;
import games.strategy.triplea.delegate.battle.IBattle.BattleType;
import games.strategy.triplea.delegate.data.BattleListing;
import games.strategy.triplea.delegate.remote.IMoveDelegate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import org.triplea.generalstaff.observe.GameObservation.UnitCount;
import org.triplea.generalstaff.observe.UnitCounts;

/**
 * Runs game steps on a {@link Sandbox} exactly as the engine would: the step's delegate is started,
 * the orders are performed through the real move delegate, battles are fought by the real battle
 * code with the sandbox's seeded dice and retreat policies, and the delegate is ended. The live
 * game is never involved.
 */
public final class TurnSimulator {
  private static final int MAX_BATTLE_PASSES = 50;

  /** A movement order: units along a route. Units and route may belong to any copy of the game. */
  public record Order(Collection<Unit> units, Route route) {}

  /** One battle as the engine fought it. */
  public record BattleOutcome(
      String territory,
      String attacker,
      String defender,
      String result,
      int rounds,
      List<UnitCount> attackersLeft,
      List<UnitCount> defendersLeft,
      @Nullable String ownerAfter) {}

  /** What happened in one step. */
  public record StepRun(
      String step,
      @Nullable String player,
      int ordersAccepted,
      List<String> orderErrors,
      List<BattleOutcome> battles) {
    public boolean allOrdersAccepted() {
      return orderErrors.isEmpty();
    }
  }

  private final Sandbox sandbox;

  public TurnSimulator(final Sandbox sandbox) {
    this.sandbox = sandbox;
  }

  public Sandbox sandbox() {
    return sandbox;
  }

  /**
   * Runs the one-time initialization step if the game is fresh (round 1, first step), as the engine
   * does before the first turn: original owners, unit state and the like.
   */
  public void initializeIfFresh() {
    final GameStep first = sandbox.data().getSequence().getStep(0);
    final boolean fresh =
        sandbox.data().getSequence().getRound() <= 1
            && sandbox.data().getSequence().getStepIndex() == 0
            && first.getPlayerId() == null;
    if (fresh) {
      runStep(first.getName(), List.of());
    }
  }

  /** Runs the step with no orders: start the delegate, fight any battles, end the delegate. */
  public StepRun runStep(final String stepName) {
    return runStep(stepName, List.of());
  }

  /** Positions the sandbox at the step, starts its delegate, performs the orders, fights, ends. */
  public StepRun runStep(final String stepName, final List<Order> orders) {
    final IDelegate delegate = sandbox.position(stepName);
    final GameStep step = sandbox.currentStep();
    // Battles were created during the combat move; the delegate's start() may already fight a lone
    // one, so the list must be taken before it.
    final List<IBattle> pending =
        delegate instanceof BattleDelegate battleDelegate
            ? new ArrayList<>(
                battleDelegate.getBattleTracker().getPendingBattles(BattleType.NORMAL))
            : List.of();
    delegate.start();
    final List<String> errors = new ArrayList<>();
    int accepted = 0;
    if (!orders.isEmpty()) {
      if (!(delegate instanceof IMoveDelegate moveDelegate)) {
        throw new IllegalArgumentException("Step " + stepName + " is not a movement step");
      }
      for (final Order order : orders) {
        final Optional<String> error =
            moveDelegate.performMove(
                new MoveDescription(sandbox.units(order.units()), sandbox.route(order.route())));
        if (error.isPresent()) {
          errors.add(describe(order) + ": " + error.get());
        } else {
          accepted++;
        }
      }
    }
    if (delegate instanceof BattleDelegate battleDelegate) {
      fightAll(battleDelegate);
    }
    delegate.end();
    final List<BattleOutcome> battles = new ArrayList<>();
    for (final IBattle battle : pending) {
      battles.add(outcome(battle));
    }
    return new StepRun(
        stepName,
        Optional.ofNullable(step.getPlayerId()).map(GamePlayer::getName).orElse(null),
        accepted,
        errors,
        battles);
  }

  /** Whether the sandbox's current step is a combat move, as the engine reads it. */
  public boolean isCombatMove() {
    return GameStepPropertiesHelper.isCombatMove(sandbox.data(), true);
  }

  private static void fightAll(final BattleDelegate battleDelegate) {
    for (int pass = 0; pass < MAX_BATTLE_PASSES; pass++) {
      final BattleListing listing = battleDelegate.getBattleListing();
      if (listing.isEmpty()) {
        return;
      }
      final List<String> blocked = new ArrayList<>();
      listing.forEachBattle(
          (type, territory) -> {
            final String error = battleDelegate.fightBattle(territory, type.isBombingRun(), type);
            if (error != null && !BattleDelegate.isBattleDependencyErrorMessage(error)) {
              blocked.add(error);
            }
          });
      if (!blocked.isEmpty() && blocked.size() == countBattles(battleDelegate)) {
        return;
      }
    }
  }

  private static int countBattles(final BattleDelegate battleDelegate) {
    final int[] count = {0};
    battleDelegate.getBattleListing().forEachBattle((type, territory) -> count[0]++);
    return count[0];
  }

  private static BattleOutcome outcome(final IBattle battle) {
    final Territory territory = battle.getTerritory();
    final GamePlayer owner = territory.getOwner();
    return new BattleOutcome(
        territory.getName(),
        battle.getAttacker().getName(),
        battle.getDefender() == null ? "" : battle.getDefender().getName(),
        battle.getWhoWon().name(),
        battle.getBattleRound(),
        UnitCounts.of(combatUnits(battle.getRemainingAttackingUnits())),
        UnitCounts.of(combatUnits(battle.getRemainingDefendingUnits())),
        owner == null || owner.isNull() ? null : owner.getName());
  }

  private static List<Unit> combatUnits(final Collection<Unit> units) {
    return units.stream().filter(Matches.unitIsNotInfrastructure()).toList();
  }

  private static String describe(final Order order) {
    return UnitCounts.describe(UnitCounts.of(order.units()))
        + " over "
        + order.route().getAllTerritories().stream().map(Territory::getName).toList();
  }
}
