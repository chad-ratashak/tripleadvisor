package org.triplea.generalstaff.sim;

import games.strategy.engine.data.Change;
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
import java.util.function.Function;
import javax.annotation.Nullable;
import org.triplea.http.client.web.socket.messages.WebSocketMessage;
import org.triplea.sound.HeadlessSoundChannel;
import org.triplea.sound.ISound;

/**
 * The bridge the engine's delegates talk through during a General Staff simulation. It has no
 * display, no sound, writes no history, rolls seeded dice, and records every change it applies in a
 * {@link ChangeJournal} so the copy can be rolled back. A read-only variant refuses changes, for
 * evaluating conditions on the live game.
 */
public final class StaffDelegateBridge implements IDelegateBridge {
  private enum Mode {
    RECORDING,
    READ_ONLY
  }

  private final GameData data;
  @Nullable private final GamePlayer player;
  private final Function<GamePlayer, Player> players;
  private final IRandomSource randomSource;
  private final ChangeJournal journal;
  private final Mode mode;
  private final IDisplay display = new HeadlessDisplay();
  private final ISound sound = new HeadlessSoundChannel();
  private final IDelegateHistoryWriter historyWriter =
      DelegateHistoryWriter.createNoOpImplementation();

  private StaffDelegateBridge(
      final GameData data,
      @Nullable final GamePlayer player,
      final Function<GamePlayer, Player> players,
      final IRandomSource randomSource,
      final ChangeJournal journal,
      final Mode mode) {
    this.data = data;
    this.player = player;
    this.players = players;
    this.randomSource = randomSource;
    this.journal = journal;
    this.mode = mode;
  }

  /**
   * A bridge for a simulation on a copy of the game: changes are applied and recorded so they can
   * be rolled back, and the dice replay for the seed.
   */
  public static StaffDelegateBridge forSimulation(
      final GameData copy, final GamePlayer player, final long seed) {
    final SimulationPlayer simulationPlayer = new SimulationPlayer(player.getName());
    return new StaffDelegateBridge(
        copy,
        player,
        anyone -> simulationPlayer,
        new SeededRandomSource(seed),
        new ChangeJournal(),
        Mode.RECORDING);
  }

  /**
   * A bridge inside a {@link Sandbox}: shared dice, a shared journal and one stand-in player per
   * power, so several delegates can run in turn on the same copy.
   */
  static StaffDelegateBridge inSandbox(
      final GameData copy,
      @Nullable final GamePlayer player,
      final Function<GamePlayer, Player> players,
      final IRandomSource randomSource,
      final ChangeJournal journal) {
    return new StaffDelegateBridge(copy, player, players, randomSource, journal, Mode.RECORDING);
  }

  /** A bridge for evaluating conditions on the live game: any attempt to change it fails. */
  public static StaffDelegateBridge readOnly(final GameData data, final GamePlayer player) {
    final SimulationPlayer simulationPlayer = new SimulationPlayer(player.getName());
    return new StaffDelegateBridge(
        data,
        player,
        anyone -> simulationPlayer,
        new SeededRandomSource(0),
        new ChangeJournal(),
        Mode.READ_ONLY);
  }

  /** Undoes every change applied through this bridge's journal. */
  public void rollback() {
    journal.rollback(data);
  }

  /** How many changes the journal holds. */
  public int changeCount() {
    return journal.size();
  }

  public ChangeJournal journal() {
    return journal;
  }

  @Override
  public void addChange(final Change change) {
    if (mode == Mode.READ_ONLY) {
      throw new UnsupportedOperationException(
          "This bridge is read-only; the live game is never changed from here");
    }
    journal.apply(data, change);
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
    return players.apply(player);
  }

  @Override
  public Player getRemotePlayer(final GamePlayer gamePlayer) {
    return players.apply(gamePlayer);
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
