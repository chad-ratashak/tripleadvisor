package org.triplea.generalstaff.sim;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.GameStep;
import games.strategy.engine.data.MoveDescription;
import games.strategy.engine.data.Route;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.engine.delegate.IDelegate;
import games.strategy.engine.framework.GameDataManager;
import games.strategy.engine.framework.GameDataUtils;
import games.strategy.engine.player.Player;
import games.strategy.triplea.delegate.GameStepPropertiesHelper;
import games.strategy.triplea.delegate.move.validation.MoveValidator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.triplea.generalstaff.engine.EngineSettings;
import org.triplea.generalstaff.rules.LegalityChecker;
import org.triplea.generalstaff.rules.MoveJudge;
import org.triplea.generalstaff.rules.MoveVerdict;

/**
 * A private copy of a game, with its delegates, that the engine can be run on: position it at a
 * step, run the step's delegate, perform moves, fight battles. Branching is by {@link #fork}, which
 * deserializes a fresh copy, so nothing ever leaks between trajectories or into the live game.
 */
public final class Sandbox {
  private static final GameDataManager.Options WITH_DELEGATES =
      GameDataManager.Options.builder().withDelegates(true).build();

  private final GameData data;
  private final long seed;
  private final SeededRandomSource random;
  private final ChangeJournal journal = new ChangeJournal();
  private final Map<String, SimulationPlayer> players = new ConcurrentHashMap<>();
  private final Function<GamePlayer, Player> playerLookup;

  private Sandbox(final GameData data, final long seed) {
    this.data = data;
    this.seed = seed;
    this.random = new SeededRandomSource(seed);
    this.playerLookup = gamePlayer -> policy(gamePlayer == null ? "" : gamePlayer.getName());
  }

  /** Copies the live game (with delegates). The live game is only read. */
  public static Sandbox fromLive(final GameData live, final long seed) {
    return fromBytes(snapshotOf(live), seed);
  }

  /** Builds a sandbox from a snapshot written by {@link #snapshot()} or {@link #snapshotOf}. */
  public static Sandbox fromBytes(final byte[] snapshot, final long seed) {
    EngineSettings.ensureInitialized();
    final GameData copy =
        GameDataUtils.createGameDataFromBytes(snapshot)
            .orElseThrow(() -> new IllegalStateException("Could not read the game snapshot"));
    return new Sandbox(copy, seed);
  }

  /** Serializes a game with its delegates; the result can seed any number of sandboxes. */
  public static byte[] snapshotOf(final GameData data) {
    return GameDataUtils.gameDataToBytes(data, WITH_DELEGATES)
        .orElseThrow(() -> new IllegalStateException("Could not serialize the game"));
  }

  /** The current state of this sandbox as bytes, including its delegates. */
  public byte[] snapshot() {
    return snapshotOf(data);
  }

  /** An independent copy of this sandbox's current state with its own dice. */
  public Sandbox fork(final long newSeed) {
    return fromBytes(snapshot(), newSeed);
  }

  public GameData data() {
    return data;
  }

  public long seed() {
    return seed;
  }

  public ChangeJournal journal() {
    return journal;
  }

  /** The stand-in player for a power, whose retreat policy can be set. */
  public SimulationPlayer policy(final String playerName) {
    return players.computeIfAbsent(
        playerName,
        name -> new SimulationPlayer(name, () -> data.getBattleDelegate().getCurrentBattle()));
  }

  public GamePlayer player(final String name) {
    final GamePlayer player = data.getPlayerList().getPlayerId(name);
    if (player == null) {
      throw new IllegalArgumentException("Unknown player: " + name);
    }
    return player;
  }

  public Territory territory(final String name) {
    final Territory territory = data.getMap().getTerritoryOrNull(name);
    if (territory == null) {
      throw new IllegalArgumentException("Unknown territory: " + name);
    }
    return territory;
  }

  /** The sandbox's own instances of units that may come from the live game or another copy. */
  public List<Unit> units(final Collection<Unit> units) {
    final List<Unit> translated = new ArrayList<>();
    for (final Unit unit : units) {
      final Unit inCopy = data.getUnits().get(unit.getId());
      if (inCopy == null) {
        throw new IllegalArgumentException("Unit is not part of this game: " + unit);
      }
      translated.add(inCopy);
    }
    return translated;
  }

  /** The sandbox's own route for a route of another copy of the game. */
  public Route route(final Route route) {
    return new Route(
        territory(route.getStart().getName()),
        route.getSteps().stream().map(step -> territory(step.getName())).toList());
  }

  public GameStep step(final String stepName) {
    for (final GameStep candidate : data.getSequence()) {
      if (stepName.equals(candidate.getName())) {
        return candidate;
      }
    }
    throw new IllegalArgumentException("Unknown step: " + stepName);
  }

  public GameStep currentStep() {
    return data.getSequence().getStep();
  }

  /** A bridge for the given player (null for player-less steps) over this sandbox. */
  public StaffDelegateBridge bridgeFor(final GamePlayer player) {
    return StaffDelegateBridge.inSandbox(data, player, playerLookup, random, journal);
  }

  /**
   * Moves the sequence to the named step and wires the step's delegate to a bridge for the step's
   * player. Does not call the delegate's {@code start()}; {@link TurnSimulator} does.
   */
  public IDelegate position(final String stepName) {
    final GameStep step = step(stepName);
    data.getSequence()
        .setRoundAndStep(data.getSequence().getRound(), step.getDisplayName(), step.getPlayerId());
    final IDelegate delegate = step.getDelegate();
    delegate.setDelegateBridgeAndPlayer(bridgeFor(step.getPlayerId()));
    return delegate;
  }

  /** The engine's dry-run verdict for a move in the step the sandbox is positioned at. */
  public MoveJudge judge() {
    final GameStep step = currentStep();
    final GamePlayer player = step.getPlayerId();
    if (player == null) {
      throw new IllegalStateException("Step " + step.getName() + " has no player");
    }
    final boolean nonCombat = GameStepPropertiesHelper.isNonCombatMove(data, true);
    return new MoveJudge() {
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
        return step.getName();
      }

      @Override
      public boolean isNonCombatMove() {
        return nonCombat;
      }

      @Override
      public MoveVerdict check(final Collection<Unit> units, final Route route) {
        final List<Unit> copyUnits = units(units);
        final Route copyRoute = route(route);
        return LegalityChecker.verdict(
            step.getName(),
            nonCombat,
            player,
            copyUnits,
            copyRoute,
            new MoveValidator(data, nonCombat)
                .validateMove(
                    new MoveDescription(copyUnits, copyRoute),
                    player,
                    data.getMoveDelegate().getMovesMade()));
      }
    };
  }
}
