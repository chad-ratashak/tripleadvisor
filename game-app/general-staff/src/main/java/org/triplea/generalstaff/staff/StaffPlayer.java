package org.triplea.generalstaff.staff;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.GameState;
import games.strategy.engine.data.MoveDescription;
import games.strategy.engine.data.Route;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.engine.framework.ServerGame;
import games.strategy.engine.framework.startup.ui.PlayerTypes;
import games.strategy.engine.player.Player;
import games.strategy.triplea.ai.AbstractAi;
import games.strategy.triplea.ai.fast.FastAi;
import games.strategy.triplea.delegate.Matches;
import games.strategy.triplea.delegate.battle.IBattle;
import games.strategy.triplea.delegate.data.MoveValidationResult;
import games.strategy.triplea.delegate.move.validation.MoveValidator;
import games.strategy.triplea.delegate.remote.IAbstractPlaceDelegate;
import games.strategy.triplea.delegate.remote.IMoveDelegate;
import games.strategy.triplea.delegate.remote.IPurchaseDelegate;
import games.strategy.triplea.delegate.remote.ITechDelegate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.Nullable;
import org.triplea.generalstaff.rules.LegalityChecker;
import org.triplea.generalstaff.rules.MoveJudge;
import org.triplea.generalstaff.rules.MoveVerdict;
import org.triplea.generalstaff.sim.LandingPolicy;
import org.triplea.generalstaff.sim.TurnSimulator.Order;
import org.triplea.java.collections.CollectionUtils;

/**
 * The General Staff as a player in a hosted game. Each movement step, it proposes the orders it
 * holds for that step to the {@link ApprovalGate}, blocks the engine until the operator decides,
 * saves the game, and then performs the approved orders through the real move delegate, recording
 * the engine's answer to each. At a non-combat move it also proposes landing orders for every
 * aircraft the engine says cannot stay where it is, so survivors come home instead of being
 * removed. It buys nothing, researches nothing and places nothing on its own.
 */
public final class StaffPlayer extends AbstractAi {
  public static final String LABEL = "General Staff";

  private final ApprovalGate gate;
  private final Map<String, PlannedStep> orders = new ConcurrentHashMap<>();
  private final List<ExecutionRecord> records = new CopyOnWriteArrayList<>();
  @Nullable private volatile ServerGame game;
  @Nullable private volatile Path saveFolder;
  private volatile int retreatAfterRound = PlannedStep.NEVER_RETREAT;
  @Nullable private FastAi delegateAi;

  public StaffPlayer(final String name, final ApprovalGate gate) {
    super(name, LABEL);
    this.gate = gate;
  }

  /** Lets the player save the hosted game before it executes approved orders. */
  public void attach(final ServerGame hostedGame, final Path folderForSaves) {
    this.game = hostedGame;
    this.saveFolder = folderForSaves;
  }

  public void setOrders(final PlannedStep step) {
    orders.put(step.step(), step);
  }

  public Optional<PlannedStep> orders(final String step) {
    return Optional.ofNullable(orders.get(step));
  }

  public List<ExecutionRecord> records() {
    return List.copyOf(records);
  }

  public ApprovalGate gate() {
    return gate;
  }

  /** The player type the engine's game loader uses to put this instance into a game. */
  public PlayerTypes.Type playerType() {
    final StaffPlayer self = this;
    return new PlayerTypes.Type(LABEL) {
      @Override
      public Player newPlayerWithName(final String name) {
        if (!name.equals(self.getName())) {
          throw new IllegalArgumentException(
              "This staff player is " + self.getName() + ", not " + name);
        }
        return self;
      }
    };
  }

  @Override
  protected void move(
      final boolean nonCombat,
      final IMoveDelegate moveDel,
      final GameData data,
      final GamePlayer player) {
    final String step = getPlayerBridge().getStepName();
    final PlannedStep planned = orders.getOrDefault(step, PlannedStep.none(step));
    final List<PlannedOrder> proposed = new ArrayList<>(planned.orders());
    if (nonCombat) {
      proposed.addAll(landingProposals(step, moveDel, data, player));
    }
    final ApprovalGate.Decision decision = gate.await(step, player.getName(), proposed);
    if (!decision.approved()) {
      records.add(
          new ExecutionRecord(
              getName(), step, null, null, "rejected by " + decision.decidedBy(), Instant.now()));
      return;
    }
    saveBefore(step);
    retreatAfterRound = planned.retreatAfterRound();
    for (final PlannedOrder order : decision.orders()) {
      final Optional<String> error = perform(order, moveDel, data, player);
      records.add(
          new ExecutionRecord(
              getName(),
              step,
              order,
              error.orElse(null),
              error.isPresent() ? "refused by the engine" : "performed",
              Instant.now()));
    }
    if (planned.delegateRemainder()) {
      delegateRemainder(step, player);
    }
  }

  /**
   * Hybrid mode: the units the approved orders did not move are handed to the engine's Fast AI for
   * the rest of this step. The AI plays the staff's power through the same delegate.
   */
  private void delegateRemainder(final String step, final GamePlayer player) {
    if (delegateAi == null) {
      delegateAi = new FastAi(getName(), PlayerTypes.FAST_AI.getLabel());
      delegateAi.initialize(getPlayerBridge(), player);
    }
    try {
      delegateAi.start(step);
      records.add(
          new ExecutionRecord(
              getName(),
              step,
              null,
              null,
              "remainder delegated to " + delegateAi.getPlayerLabel(),
              Instant.now()));
    } catch (final RuntimeException e) {
      records.add(
          new ExecutionRecord(
              getName(), step, null, e.toString(), "delegation failed", Instant.now()));
    }
  }

  /**
   * Orders that bring home the aircraft the engine says cannot stay where they are, grouped by
   * route and type so the operator reads them like any other order. Each one has passed the
   * engine's validator against the live game.
   */
  private static List<PlannedOrder> landingProposals(
      final String step,
      final IMoveDelegate moveDel,
      final GameData data,
      final GamePlayer player) {
    final Collection<Territory> stranded = moveDel.getTerritoriesWhereAirCantLand(player);
    if (stranded.isEmpty()) {
      return List.of();
    }
    final MoveJudge judge =
        new MoveJudge() {
          @Override
          public GameData gameCopy() {
            return data;
          }

          @Override
          public GamePlayer player() {
            return player;
          }

          @Override
          public String stepName() {
            return step;
          }

          @Override
          public boolean isNonCombatMove() {
            return true;
          }

          @Override
          public MoveVerdict check(final Collection<Unit> units, final Route route) {
            final MoveValidationResult result =
                new MoveValidator(data, true)
                    .validateMove(
                        new MoveDescription(units, route), player, moveDel.getMovesMade());
            return LegalityChecker.verdict(step, true, player, List.copyOf(units), route, result);
          }
        };
    final Map<String, PlannedOrder> grouped = new LinkedHashMap<>();
    for (final Order order : LandingPolicy.orders(data, player, stranded, judge)) {
      final List<String> route =
          order.route().getAllTerritories().stream().map(Territory::getName).toList();
      for (final Unit unit : order.units()) {
        final String type = unit.getType().getName();
        grouped.merge(
            route + "|" + type,
            new PlannedOrder(route, type, 1),
            (a, b) -> new PlannedOrder(a.route(), a.unitType(), a.count() + b.count()));
      }
    }
    return List.copyOf(grouped.values());
  }

  private static Optional<String> perform(
      final PlannedOrder order,
      final IMoveDelegate moveDel,
      final GameData data,
      final GamePlayer player) {
    final List<Territory> path = new ArrayList<>();
    for (final String name : order.route()) {
      final Territory territory = data.getMap().getTerritoryOrNull(name);
      if (territory == null) {
        return Optional.of("Unknown territory: " + name);
      }
      path.add(territory);
    }
    final Territory from = path.get(0);
    final List<Unit> units =
        from
            .getMatches(
                Matches.unitIsOwnedBy(player)
                    .and(unit -> unit.getType().getName().equals(order.unitType()))
                    .and(Matches.unitHasMovementLeft()))
            .stream()
            .limit(order.count())
            .toList();
    if (units.size() < order.count()) {
      return Optional.of(
          "Only " + units.size() + " " + order.unitType() + " able to move in " + from.getName());
    }
    return moveDel.performMove(
        new MoveDescription(units, new Route(from, path.subList(1, path.size()))));
  }

  private void saveBefore(final String step) {
    final ServerGame hostedGame = game;
    final Path folder = saveFolder;
    if (hostedGame == null || folder == null) {
      return;
    }
    try {
      Files.createDirectories(folder);
      final Path file =
          folder.resolve(
              "before-" + step + "-round" + getGameData().getSequence().getRound() + ".tsvg");
      hostedGame.saveGame(file);
      records.add(
          new ExecutionRecord(
              getName(), step, null, null, "saved " + file.getFileName(), Instant.now()));
    } catch (final RuntimeException | java.io.IOException e) {
      records.add(
          new ExecutionRecord(getName(), step, null, e.toString(), "save failed", Instant.now()));
    }
  }

  @Override
  public Optional<Territory> retreatQuery(
      final UUID battleId,
      final boolean submerge,
      final Territory battleTerritory,
      final Collection<Territory> possibleTerritories,
      final String message) {
    if (submerge
        || retreatAfterRound == PlannedStep.NEVER_RETREAT
        || possibleTerritories.isEmpty()) {
      return Optional.empty();
    }
    // A lone battle is fought by the tracker itself, so the delegate's "current battle" stays
    // empty; the id the engine passes finds it either way.
    final IBattle byId =
        getGameData().getBattleDelegate().getBattleTracker().getPendingBattle(battleId);
    final IBattle battle =
        byId != null ? byId : getGameData().getBattleDelegate().getCurrentBattle();
    if (battle != null && battle.getBattleRound() >= retreatAfterRound) {
      return Optional.of(CollectionUtils.getAny(possibleTerritories));
    }
    return Optional.empty();
  }

  @Override
  protected void politicalActions() {
    // the staff never changes diplomacy on its own
  }

  @Override
  public void stopGame() {
    gate.release();
    if (delegateAi != null) {
      delegateAi.stopGame();
    }
  }

  @Override
  protected void purchase(
      final boolean purchaseForBid,
      final int pusToSpend,
      final IPurchaseDelegate purchaseDelegate,
      final GameData data,
      final GamePlayer player) {
    records.add(
        new ExecutionRecord(
            getName(),
            getPlayerBridge().getStepName(),
            null,
            null,
            "no purchase (not yet delegated to the staff)",
            Instant.now()));
  }

  @Override
  protected void tech(
      final ITechDelegate techDelegate, final GameData data, final GamePlayer player) {
    // no research on the staff's own initiative
  }

  @Override
  protected void place(
      final boolean placeForBid,
      final IAbstractPlaceDelegate placeDelegate,
      final GameState data,
      final GamePlayer player) {
    // nothing bought, nothing to place
  }
}
