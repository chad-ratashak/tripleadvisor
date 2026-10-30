package org.triplea.generalstaff.sim;

import games.strategy.engine.data.Change;
import games.strategy.engine.data.CompositeChange;
import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.delegate.IDelegateBridge;
import games.strategy.engine.display.IDisplay;
import games.strategy.engine.history.DelegateHistoryWriter;
import games.strategy.engine.history.IDelegateHistoryWriter;
import games.strategy.engine.player.Player;
import games.strategy.engine.random.IRandomSource;
import games.strategy.engine.random.IRandomStats.DiceType;
import games.strategy.triplea.ResourceLoader;
import games.strategy.triplea.ui.display.HeadlessDisplay;
import java.util.Optional;
import org.triplea.http.client.web.socket.messages.WebSocketMessage;
import org.triplea.sound.HeadlessSoundChannel;
import org.triplea.sound.ISound;

/**
 * The bridge the engine's delegates talk through during a General Staff simulation. It has no
 * display, no sound, writes no history, rolls seeded dice, and records every change it applies so
 * the copy can be rolled back. A read-only variant refuses changes, for evaluating conditions on
 * the live game.
 */
public final class StaffDelegateBridge implements IDelegateBridge {
  private enum Mode {
    RECORDING,
    READ_ONLY
  }

  private final GameData data;
  private final GamePlayer player;
  private final Player remotePlayer;
  private final IRandomSource randomSource;
  private final Mode mode;
  private final IDisplay display = new HeadlessDisplay();
  private final ISound sound = new HeadlessSoundChannel();
  private final IDelegateHistoryWriter historyWriter =
      DelegateHistoryWriter.createNoOpImplementation();
  private CompositeChange changes = new CompositeChange();

  private StaffDelegateBridge(
      final GameData data,
      final GamePlayer player,
      final IRandomSource randomSource,
      final Mode mode) {
    this.data = data;
    this.player = player;
    this.randomSource = randomSource;
    this.mode = mode;
    this.remotePlayer = new SimulationPlayer(player.getName());
  }

  /**
   * A bridge for a simulation on a copy of the game: changes are applied and recorded so they can
   * be rolled back, and the dice replay for the seed.
   */
  public static StaffDelegateBridge forSimulation(
      final GameData copy, final GamePlayer player, final long seed) {
    return new StaffDelegateBridge(copy, player, new SeededRandomSource(seed), Mode.RECORDING);
  }

  /** A bridge for evaluating conditions on the live game: any attempt to change it fails. */
  public static StaffDelegateBridge readOnly(final GameData data, final GamePlayer player) {
    return new StaffDelegateBridge(data, player, new SeededRandomSource(0), Mode.READ_ONLY);
  }

  /** Undoes every change applied through this bridge since the last rollback. */
  public void rollback() {
    if (!changes.isEmpty()) {
      data.performChange(changes.invert());
      changes = new CompositeChange();
    }
  }

  /** How many changes have been applied since the last rollback. */
  public int changeCount() {
    return changes.getChanges().size();
  }

  @Override
  public void addChange(final Change change) {
    if (mode == Mode.READ_ONLY) {
      throw new UnsupportedOperationException(
          "This bridge is read-only; the live game is never changed from here");
    }
    changes.add(change);
    data.performChange(change);
  }

  @Override
  public GameData getData() {
    return data;
  }

  @Override
  public GamePlayer getGamePlayer() {
    return player;
  }

  @Override
  public Player getRemotePlayer() {
    return remotePlayer;
  }

  @Override
  public Player getRemotePlayer(final GamePlayer gamePlayer) {
    return remotePlayer;
  }

  @Override
  public int getRandom(
      final int max, final GamePlayer player, final DiceType diceType, final String annotation) {
    return randomSource.getRandom(max, annotation);
  }

  @Override
  public int[] getRandom(
      final int max,
      final int count,
      final GamePlayer player,
      final DiceType diceType,
      final String annotation) {
    return randomSource.getRandom(max, count, annotation);
  }

  @Override
  public IDelegateHistoryWriter getHistoryWriter() {
    return historyWriter;
  }

  @Override
  public IDisplay getDisplayChannelBroadcaster() {
    return display;
  }

  @Override
  public ISound getSoundChannelBroadcaster() {
    return sound;
  }

  @Override
  public void stopGameSequence(final String status, final String title) {}

  @Override
  public void leaveDelegateExecution() {}

  @Override
  public void enterDelegateExecution() {}

  @Override
  public void sendMessage(final WebSocketMessage webSocketMessage) {}

  @Override
  public Optional<ResourceLoader> getResourceLoader() {
    return Optional.empty();
  }
}
